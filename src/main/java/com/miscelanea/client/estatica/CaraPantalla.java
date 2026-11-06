package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.arcade.MesaCoctelBlock;
import com.miscelanea.arcade.RecreativaBlock;
import com.miscelanea.estatica.EstaticaRed;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Mientras alguien de estática viaja por las pantallas, la pantalla por la que está mirando enseña SU CARA a todos los de
 * alrededor: la cara de su piel (la de dentro y la capa de fuera) ampliada píxel a píxel, con la mitad hecha nieve (el
 * mismo lado que en su cuerpo), los ojos blancos que brillan, líneas de barrido, parpadeos y de vez en cuando una barra
 * que baja rodando. Al cambiar de pantalla la cara se va con él y la vieja vuelve a lo suyo tras un golpe de nieve.
 * Las recreativas y la mesa cóctel la ponen en su pantalla con el gancho RecreativaRenderer.PANTALLA_ESPECIAL; el resto
 * de pantallas (la tragaperras...) con un quad un pelín por delante. El golpe de nieve se dibuja siempre así.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CaraPantalla {
    private CaraPantalla() {}

    private static final ResourceLocation NIEVE = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/estatica_destello.png");
    /** Ancho de la imagen (texels); el alto va según la forma de la pantalla. */
    private static final int ANCHO = 64;

    private static final class Cara {
        final int id, semilla;
        final UUID quien;
        BlockPos pos;
        long desde;
        @Nullable DynamicTexture tex;
        @Nullable ResourceLocation rl;
        int w, h;

        Cara(int id, UUID quien, int semilla, BlockPos pos, long desde) {
            this.id = id;
            this.quien = quien;
            this.semilla = semilla;
            this.pos = pos;
            this.desde = desde;
        }

        void cerrar() {
            if (rl != null) Minecraft.getInstance().getTextureManager().release(rl);
            rl = null;
            tex = null;
        }
    }

    static {
        // las recreativas y la mesa cóctel enseñan la cara en lugar de su imagen (gancho de su renderer, encadenado)
        var antes = com.miscelanea.client.arcade.RecreativaRenderer.PANTALLA_ESPECIAL;
        com.miscelanea.client.arcade.RecreativaRenderer.PANTALLA_ESPECIAL = (pos, lvl) -> {
            for (Cara c : CARAS.values()) if (c.rl != null && c.pos.equals(pos)) return c.rl;
            return antes != null ? antes.apply(pos, lvl) : null;
        };
    }

    /** Las que dibuja el renderer de la recreativa con el gancho (el resto se dibuja aquí con un quad delante). */
    private static boolean conGancho(BlockState st) {
        return st.getBlock() instanceof RecreativaBlock || st.getBlock() instanceof MesaCoctelBlock;
    }

    /** Jugador (id de entidad) → su cara. */
    private static final Map<Integer, Cara> CARAS = new HashMap<>();
    /** Pantallas que acaban de quedarse sin cara: golpe de nieve hasta este tick. */
    private static final Map<BlockPos, Long> GOLPES = new HashMap<>();

    static void recibir(EstaticaRed.CaraPantalla p) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        long t = l.getGameTime();
        Cara c = CARAS.get(p.id);
        if (!p.activa) {
            if (c != null) {
                GOLPES.put(c.pos, t + 8);
                c.cerrar();
                CARAS.remove(p.id);
            }
            return;
        }
        if (c == null) {
            c = new Cara(p.id, p.quien, p.semilla, p.pos, t);
            CARAS.put(p.id, c);
            GOLPES.put(p.pos, t + 6);
        } else if (!c.pos.equals(p.pos)) {
            GOLPES.put(c.pos, t + 8);
            GOLPES.put(p.pos, t + 6);
            c.pos = p.pos;
            c.desde = t;
            c.cerrar();          // otra forma de pantalla: otra imagen
        }
    }

    /** ¿Se está viendo una cara en esta pantalla? (para quien quiera esconder su imagen normal). */
    public static boolean ocupada(BlockPos pos) {
        for (Cara c : CARAS.values()) if (c.pos.equals(pos)) return true;
        return false;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        for (Cara c : CARAS.values()) c.cerrar();
        CARAS.clear();
        GOLPES.clear();
        PIELES.clear();
    }

    // ------------------------------------------------------------------ dónde está la pantalla de cada máquina
    /**
     * Matriz (en el espacio del bloque base ya girado como su modelo: delante = norte) que lleva (x 0..1, y 0..1 de arriba
     * abajo, z) al plano de la pantalla; z positivo = hacia fuera. [0] = la matriz, la forma (ancho / alto) aparte.
     */
    @Nullable
    private static Matrix4f plano(BlockState st, float[] forma) {
        float s = 1 / 16F;
        if (st.getBlock() instanceof MesaCoctelBlock) {
            float ancho = 12F, alto = 9F;
            forma[0] = ancho / alto;
            return new Matrix4f(-ancho * s, 0, 0, 0, 0, 0, -alto * s, 0, 0, 0.05F * s, 0, 0, 14F * s, 12.56F * s, 12.5F * s, 1);
        }
        if (st.getBlock() instanceof RecreativaBlock rb) {
            boolean doble = rb.doble;
            float ancho = doble ? 14F : 12F, alto = doble ? 10.5F : 9F, x0 = doble ? 15F : 14F, y0 = doble ? 28.8F : 28.4F;
            forma[0] = ancho / alto;
            return new Matrix4f(-ancho * s, 0, 0, 0, 0, -alto * s, alto * 0.21F * s, 0, 0, 0, -0.05F * s, 0, x0 * s, y0 * s, 8.7F * s, 1);
        }
        if (st.getBlock() == com.miscelanea.registry.ModBlocks.TRAGAPERRAS.get()) {
            // la ventana de los rodillos (parte de arriba)
            forma[0] = 10.8F / 6F;
            return new Matrix4f(-10.8F * s, 0, 0, 0, 0, -6F * s, 0, 0, 0, 0, -0.05F * s, 0, 13.4F * s, 23.8F * s, 0.5F * s, 1);
        }
        // cualquier otra pantalla de la etiqueta: un rectángulo en su cara de delante
        forma[0] = 4F / 3F;
        return new Matrix4f(-10F * s, 0, 0, 0, 0, -7.5F * s, 0, 0, 0, 0, -0.05F * s, 0, 13F * s, 13.5F * s, -0.1F * s, 1);
    }

    private static Direction cara(BlockState st) {
        return st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? st.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    // ------------------------------------------------------------------ la cara de la piel (8x8, con la capa de fuera)
    private record CaraPiel(int[] px, int[] ojos) {}

    private static final Map<ResourceLocation, CaraPiel> PIELES = new HashMap<>();
    private static final Map<ResourceLocation, Long> REINTENTO = new HashMap<>();

    private static ResourceLocation piel(Cara c) {
        var mc = Minecraft.getInstance();
        if (mc.level != null && mc.level.getEntity(c.id) instanceof net.minecraft.client.player.AbstractClientPlayer p) return p.getSkinTextureLocation();
        PlayerInfo pi = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(c.quien);
        return pi != null ? pi.getSkinLocation() : DefaultPlayerSkin.getDefaultSkin(c.quien);
    }

    /** ABGR de los 8x8 píxeles de la cara (la capa de fuera encima) y los ojos (columna, fila dentro de la cara). */
    @Nullable
    private static CaraPiel leerCara(ResourceLocation rl) {
        CaraPiel cp = PIELES.get(rl);
        if (cp != null) return cp;
        long ahora = net.minecraft.Util.getMillis();
        Long r = REINTENTO.get(rl);
        if (r != null && ahora < r) return null;
        REINTENTO.put(rl, ahora + 1000);
        if (!RenderSystem.isOnRenderThread()) return null;
        AbstractTexture t = Minecraft.getInstance().getTextureManager().getTexture(rl, null);
        if (t == null) return null;
        RenderSystem.bindTexture(t.getId());
        int w = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GlStateManager._getTexLevelParameter(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        if (w < 64 || w % 64 != 0 || w > 1024 || (h != w && h * 2 != w)) return null;
        int[] px = new int[64];
        try (NativeImage im = new NativeImage(w, h, false)) {
            im.downloadTexture(0, false);
            int k = w / 64;
            int opacos = 0;
            for (int y = 0; y < 8; y++)
                for (int x = 0; x < 8; x++) {
                    int base = im.getPixelRGBA((8 + x) * k + k / 2, (8 + y) * k + k / 2);
                    int capa = im.getPixelRGBA((40 + x) * k + k / 2, (8 + y) * k + k / 2);
                    int c = ((capa >>> 24) & 0xFF) > 100 ? capa : base;
                    if (((base >>> 24) & 0xFF) > 0) opacos++;
                    px[y * 8 + x] = c | 0xFF000000;
                }
            if (opacos < 16) return null;          // aún no se ha subido
        } catch (RuntimeException ex) {
            return null;
        }
        // los ojos que ya sabe encontrar la máscara del cuerpo (posiciones de la capa base, en unidades de 64)
        Mascara.Piel pl = Mascara.piel(rl, false);
        int[] oj = pl != null ? pl.ojos : new int[]{9, 12, 10, 12, 13, 12, 14, 12};
        int[] ojos = new int[oj.length];
        for (int i = 0; i + 1 < oj.length; i += 2) {
            ojos[i] = Math.floorMod(oj[i] - 8, 8);
            ojos[i + 1] = Math.floorMod(oj[i + 1] - 8, 8);
        }
        cp = new CaraPiel(px, ojos);
        if (PIELES.size() > 64) PIELES.clear();
        PIELES.put(rl, cp);
        return cp;
    }

    // ------------------------------------------------------------------ pintar la imagen (20 veces por segundo)
    private static final int[] GRIS = {18, 40, 62, 84, 106, 128, 150, 170, 190, 208, 224, 240};

    private static int abgr(int r, int g, int b) {
        return 0xFF000000 | (Math.min(255, Math.max(0, b)) << 16) | (Math.min(255, Math.max(0, g)) << 8) | Math.min(255, Math.max(0, r));
    }

    private static void pintar(Cara c, CaraPiel cp, long t) {
        NativeImage im = c.tex.getPixels();
        if (im == null) return;
        int w = c.w, h = c.h;
        int lado = Mascara.lado(c.semilla);
        // tamaño de cada píxel de la cara: lo más grande que quepa con un pequeño margen
        int esc = Math.max(1, Math.min((h - 4) / 8, (w - 8) / 8));
        int fw = esc * 8, x0 = (w - fw) / 2, y0 = (h - fw) / 2;
        int edad = (int) (t - c.desde);
        float parpadeo = 0.86F + 0.18F * Mascara.hash((int) t, c.id, 1, 9);
        boolean rasgado = Mascara.hash((int) (t / 3), c.id, 2, 9) < 0.08F;
        // barra que baja rodando de vez en cuando
        boolean barra = Mascara.hash((int) (t / 70), c.id, 3, 9) < 0.55F;
        int yb = (int) ((t % 70) * (h + 12) / 70) - 6;
        // al llegar, la nieve se va apartando poco a poco
        float llegada = Math.max(0F, 1F - edad / 10F);
        for (int y = 0; y < h; y++) {
            int dx = rasgado && Mascara.hash(y / 3, (int) t, 4, 9) < 0.3F ? (int) ((Mascara.hash(y / 3, (int) t, 5, 9) - 0.5F) * 8) : 0;
            float fila = (y & 1) == 1 ? 0.72F : 1F;                        // líneas de barrido
            if (barra && y >= yb && y < yb + 5) fila *= 1.25F;
            for (int x = 0; x < w; x++) {
                int sx = x - dx;
                float ruido = Mascara.hash(sx, y, (int) t, c.id);
                int r, g, b;
                int fx = sx - x0, fy = y - y0;
                boolean enCara = fx >= 0 && fy >= 0 && fx < fw && fy < fw && ruido >= llegada;
                if (enCara) {
                    int cx = fx / esc, cy = fy / esc;
                    // la mitad del lado de la estática (el derecho del personaje se ve a la izquierda) es nieve
                    boolean nieve = lado > 0 ? cx < 4 : cx >= 4;
                    // el borde entre las dos mitades se deshace
                    if (!nieve && (cx == 3 || cx == 4) && Mascara.hash(cx, cy, (int) (t / 2), c.id + 7) < 0.35F) nieve = true;
                    boolean ojo = false;
                    for (int i = 0; i + 1 < cp.ojos.length; i += 2) if (cp.ojos[i] == cx && cp.ojos[i + 1] == cy) ojo = true;
                    if (ojo) {
                        int v = 236 + (int) (19 * Mascara.hash((int) t, c.id, 6, 9));
                        r = g = v;
                        b = 255;
                    } else if (nieve) {
                        int v = GRIS[(int) (Mascara.hash(sx / 2, y / 2, (int) t, c.id + 3) * GRIS.length) % GRIS.length];
                        r = g = v;
                        b = v + 8;
                        // halo de los ojos sobre la nieve
                        for (int i = 0; i + 1 < cp.ojos.length; i += 2)
                            if (Math.abs(cp.ojos[i] - cx) <= 1 && Math.abs(cp.ojos[i + 1] - cy) <= 1) {
                                r += 40;
                                g += 40;
                                b += 44;
                            }
                    } else {
                        int px = cp.px[cy * 8 + cx];
                        int pr = px & 0xFF, pg = (px >>> 8) & 0xFF, pb = (px >>> 16) & 0xFF;
                        // la piel se ve algo apagada y con grano, como en una tele vieja
                        float gr = 0.82F + 0.3F * ruido;
                        r = (int) (pr * gr);
                        g = (int) (pg * gr);
                        b = (int) (pb * gr + 6);
                    }
                } else {
                    // alrededor: nieve oscura
                    int v = GRIS[(int) (ruido * 7)] / 2 + (int) (llegada * 110 * Mascara.hash(sx, y, (int) t + 1, c.id));
                    r = g = v;
                    b = v + 6;
                }
                im.setPixelRGBA(x, y, abgr((int) (r * fila * parpadeo), (int) (g * fila * parpadeo), (int) (b * fila * parpadeo)));
            }
        }
        c.tex.upload();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!CARAS.isEmpty()) {
                for (Cara c : CARAS.values()) c.cerrar();
                CARAS.clear();
            }
            GOLPES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long t = mc.level.getGameTime();
        GOLPES.values().removeIf(h -> h < t || h > t + 200);
        for (Iterator<Cara> it = CARAS.values().iterator(); it.hasNext(); ) {
            Cara c = it.next();
            // si deja de llegar el repaso del servidor (se fue lejos, se desconectó...), se quita sola
            if (t - c.desde > 20 * 60 * 10 || !mc.level.isLoaded(c.pos)) {
                c.cerrar();
                it.remove();
                continue;
            }
            if (mc.player != null && mc.player.distanceToSqr(Vec3.atCenterOf(c.pos)) > 96 * 96) continue;
            BlockState st = mc.level.getBlockState(c.pos);
            float[] forma = {4F / 3F};
            if (plano(st, forma) == null) continue;
            int h = Math.max(16, Math.round(ANCHO / forma[0]));
            if (c.tex == null || c.w != ANCHO || c.h != h) {
                c.cerrar();
                c.w = ANCHO;
                c.h = h;
                c.tex = new DynamicTexture(ANCHO, h, true);
                c.rl = mc.getTextureManager().register("miscelanea_estatica_cara_" + (c.id & 0x7FFFFFFF), c.tex);
            }
            CaraPiel cp = leerCara(piel(c));
            if (cp == null) cp = leerCara(DefaultPlayerSkin.getDefaultSkin(c.quien));
            if (cp == null) continue;
            pintar(c, cp, t);
        }
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || (CARAS.isEmpty() && GOLPES.isEmpty())) return;
        var mc = Minecraft.getInstance();
        var l = mc.level;
        if (l == null) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        long t = l.getGameTime();
        float pt = e.getPartialTick();
        for (Cara c : CARAS.values()) {
            if (c.rl == null || c.pos.distToCenterSqr(cam) > 64 * 64) continue;
            if (conGancho(l.getBlockState(c.pos))) continue;
            RenderType rt = RenderType.text(c.rl);
            quad(ps, bs.getBuffer(rt), l.getBlockState(c.pos), c.pos, cam, 0F, 1F, 0F, 1F, 255);
            bs.endBatch(rt);
        }
        if (!GOLPES.isEmpty()) {
            RenderType rt = RenderType.text(NIEVE);
            VertexConsumer vc = bs.getBuffer(rt);
            int f = (int) (t % 8);
            for (Map.Entry<BlockPos, Long> g : GOLPES.entrySet()) {
                if (g.getKey().distToCenterSqr(cam) > 64 * 64) continue;
                float queda = (g.getValue() - t - pt) / 8F;
                if (queda <= 0) continue;
                quad(ps, vc, l.getBlockState(g.getKey()), g.getKey(), cam, 0F, 2F, f / 8F, (f + 1) / 8F, (int) (255 * Math.min(1F, queda * 1.5F)));
            }
            bs.endBatch(rt);
        }
    }

    private static void quad(PoseStack ps, VertexConsumer vc, BlockState st, BlockPos pos, Vec3 cam, float u0, float u1, float v0, float v1, int a) {
        float[] forma = {4F / 3F};
        Matrix4f m = plano(st, forma);
        if (m == null) return;
        Direction f = cara(st);
        ps.pushPose();
        ps.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180 - f.toYRot()));
        ps.translate(-0.5, 0, -0.5);
        ps.last().pose().mul(m);
        Matrix4f pm = ps.last().pose();
        // un poco por delante de la imagen normal y de sus textos (z positivo = hacia fuera)
        float z = 1.5F;
        int luz = 0xF000F0;
        vc.vertex(pm, 0, 0, z).color(255, 255, 255, a).uv(u0, v0).uv2(luz).endVertex();
        vc.vertex(pm, 0, 1, z).color(255, 255, 255, a).uv(u0, v1).uv2(luz).endVertex();
        vc.vertex(pm, 1, 1, z).color(255, 255, 255, a).uv(u1, v1).uv2(luz).endVertex();
        vc.vertex(pm, 1, 0, z).color(255, 255, 255, a).uv(u1, v0).uv2(luz).endVertex();
        ps.popPose();
    }
}
