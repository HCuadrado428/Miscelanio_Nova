package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PalmadaPacket;
import com.miscelanea.pociones.PocionesRed;
import com.miscelanea.pociones.PocionesRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ecolocalización (Daredevil / el warden) en el cliente de quien la lleva. Todo se ve negro (niebla negra pegada a la
 * cara; el HUD se sigue viendo) y cada sonido que suena cerca (tus pasos, los mobs, los bloques) lanza desde su origen
 * una onda esférica que, al pasar por los bloques y las entidades, los deja un momento dibujados como aristas y
 * siluetas azul-blancas (como el sculk). Agacharse da una palmada (sonar propio) cada 2 s como mucho.
 * Rendimiento: ondas de 16 bloques, se revelan bloques hasta 13 y solo las caras expuestas (cada bloque se mira una
 * sola vez: cuando el frente de la onda pasa por él), con tope de aristas vivas.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EcoCliente {
    private EcoCliente() {}

    private static final ResourceLocation BLANCO = new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/blanco.png");
    private static final float VEL = 0.55F, RADIO_MAX = 16F, RADIO_REVELA = 13F;
    private static final int DURA_BLOQUE = 30, DURA_ENTE = 32, MAX_BLOQUES = 4500, MAX_ONDAS = 20, ENFRIAMIENTO = 40;
    private static final float CR = 0.55F, CG = 0.86F, CB = 1.0F;

    private static final class Onda {
        final double x, y, z;
        float r, rAntes;

        Onda(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record Bloque(AABB caja, int desde) {}

    private record Ente(Entity e, int desde) {}

    private static final List<Onda> ONDAS = new ArrayList<>();
    private static final LinkedHashMap<Long, Bloque> BLOQUES = new LinkedHashMap<>();
    private static final Map<Integer, Ente> ENTES = new HashMap<>();
    private static int reloj, enfriamiento;
    private static boolean agachadoAntes, estaba;

    public static boolean activo() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.hasEffect(PocionesRegistro.ECOLOCALIZACION.get());
    }

    // ------------------------------------------------------------------ cada sonido es una onda
    @SubscribeEvent
    public static void sonido(PlaySoundEvent e) {
        if (!activo()) return;
        SoundInstance s = e.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || s.isRelative() || mc.player == null) return;
        double x = s.getX(), y = s.getY(), z = s.getZ();
        if (mc.player.distanceToSqr(x, y, z) > (RADIO_MAX + 6) * (RADIO_MAX + 6)) return;
        // varios sonidos a la vez en el mismo sitio (un paso y su bloque): una sola onda
        for (Onda o : ONDAS)
            if (o.r < VEL * 3 && Math.abs(o.x - x) + Math.abs(o.y - y) + Math.abs(o.z - z) < 0.8) return;
        if (ONDAS.size() >= MAX_ONDAS) ONDAS.remove(0);
        ONDAS.add(new Onda(x, y, z));
    }

    // ------------------------------------------------------------------ tick: avanzan las ondas y revelan lo que tocan
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!activo()) {
            if (estaba) limpiar();
            return;
        }
        estaba = true;
        if (mc.isPaused()) return;
        reloj++;
        if (enfriamiento > 0) enfriamiento--;
        boolean agachado = mc.options.keyShift.isDown() && mc.screen == null;
        if (agachado && !agachadoAntes && enfriamiento == 0) {
            enfriamiento = ENFRIAMIENTO;
            PocionesRed.CANAL.sendToServer(new PalmadaPacket());
        }
        agachadoAntes = agachado;

        for (Iterator<Onda> it = ONDAS.iterator(); it.hasNext(); ) {
            Onda o = it.next();
            o.rAntes = o.r;
            o.r += VEL;
            if (o.rAntes < RADIO_REVELA) revelar(mc.level, o, o.rAntes, Math.min(o.r, RADIO_REVELA));
            if (o.r > RADIO_MAX) it.remove();
        }
        BLOQUES.values().removeIf(b -> reloj - b.desde() > DURA_BLOQUE);
        ENTES.values().removeIf(en -> reloj - en.desde() > DURA_ENTE || en.e().isRemoved());
    }

    private static void limpiar() {
        ONDAS.clear();
        BLOQUES.clear();
        ENTES.clear();
        estaba = false;
        agachadoAntes = false;
    }

    /** Los bloques (caras expuestas) y las entidades cuyo centro queda entre r0 y r1 del origen. */
    private static void revelar(ClientLevel l, Onda o, float r0, float r1) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int alcance = Mth.ceil(r1);
        int bx0 = Mth.floor(o.x), by0 = Mth.floor(o.y), bz0 = Mth.floor(o.z);
        double r0c = r0 * r0, r1c = r1 * r1;
        for (int dx = -alcance; dx <= alcance; dx++) {
            double a = (bx0 + dx + 0.5 - o.x);
            a *= a;
            if (a > r1c) continue;
            for (int dy = -alcance; dy <= alcance; dy++) {
                double b = (by0 + dy + 0.5 - o.y);
                b *= b;
                double rem1 = r1c - a - b;
                if (rem1 < 0) continue;
                double rem0 = r0c - a - b;
                double zmax = Math.sqrt(rem1), zmin = rem0 > 0 ? Math.sqrt(rem0) : 0;
                // dos tramos en z: [-zmax, -zmin] y [zmin, zmax] (medidos desde el origen de la onda)
                for (int lado = -1; lado <= 1; lado += 2) {
                    double z0 = o.z + lado * zmin, z1 = o.z + lado * zmax;
                    int za = Mth.floor(Math.min(z0, z1) - 0.5), zb = Mth.ceil(Math.max(z0, z1) - 0.5);
                    for (int bz = za; bz <= zb; bz++) {
                        double c = (bz + 0.5 - o.z);
                        c *= c;
                        double d = a + b + c;
                        if (d <= r0c || d > r1c) continue;
                        m.set(bx0 + dx, by0 + dy, bz);
                        revelarBloque(l, m);
                    }
                    if (zmin == 0) break;   // los dos tramos son el mismo
                }
            }
        }
        // entidades
        Minecraft mc = Minecraft.getInstance();
        AABB zona = new AABB(o.x - r1, o.y - r1, o.z - r1, o.x + r1, o.y + r1, o.z + r1);
        for (Entity en : l.getEntities(mc.player, zona)) {
            Vec3 c = en.position().add(0, en.getBbHeight() * 0.5, 0);
            double d = c.distanceTo(new Vec3(o.x, o.y, o.z));
            if (d > r0 && d <= r1) ENTES.put(en.getId(), new Ente(en, reloj));
        }
    }

    private static void revelarBloque(ClientLevel l, BlockPos.MutableBlockPos m) {
        BlockState st = l.getBlockState(m);
        if (st.isAir()) return;
        VoxelShape forma = st.getShape(l, m);
        if (forma.isEmpty()) return;
        boolean expuesto = false;
        for (Direction dir : Direction.values()) {
            BlockPos v = m.relative(dir);
            if (!l.getBlockState(v).isSolidRender(l, v)) {
                expuesto = true;
                break;
            }
        }
        if (!expuesto) return;
        long clave = m.asLong();
        if (BLOQUES.size() >= MAX_BLOQUES && !BLOQUES.containsKey(clave)) {
            Iterator<Long> it = BLOQUES.keySet().iterator();
            it.next();
            it.remove();
        }
        BLOQUES.put(clave, new Bloque(forma.bounds().move(m.immutable()), reloj));
    }

    // ------------------------------------------------------------------ todo negro
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void niebla(ViewportEvent.RenderFog e) {
        if (!activo()) return;
        e.setNearPlaneDistance(0F);
        e.setFarPlaneDistance(0.02F);
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        if (!activo()) return;
        e.setRed(0F);
        e.setGreen(0F);
        e.setBlue(0F);
    }

    // ------------------------------------------------------------------ dibujar ondas, aristas y siluetas (sin niebla)
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !activo()) return;
        if (ONDAS.isEmpty() && BLOQUES.isEmpty() && ENTES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        float nieblaIni = RenderSystem.getShaderFogStart(), nieblaFin = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E6F);
        RenderSystem.setShaderFogEnd(1.0E6F + 1F);
        try {
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer vc = bs.getBuffer(RenderType.lines());
            for (Bloque b : BLOQUES.values()) {
                float edad = reloj - b.desde() + pt;
                float a = edad < 4 ? 1F : Mth.clamp(1F - (edad - 4) / (DURA_BLOQUE - 4), 0F, 1F);
                a = a * a;
                float k = edad < 4 ? 1F : 0.85F;
                LevelRenderer.renderLineBox(ps, vc, b.caja(), CR * k, CG * k, CB, a);
            }
            for (Ente en : ENTES.values()) {
                Entity ent = en.e();
                float edad = reloj - en.desde() + pt;
                float a = Mth.clamp(1F - edad / DURA_ENTE, 0F, 1F);
                AABB caja = ent.getBoundingBox();
                LevelRenderer.renderLineBox(ps, vc, caja, 0.8F, 0.95F, 1F, a);
            }
            Matrix4f m4 = ps.last().pose();
            Matrix3f m3 = ps.last().normal();
            for (Onda o : ONDAS) {
                float r = Mth.lerp(pt, o.rAntes, o.r);
                float a = Mth.clamp(1F - r / RADIO_MAX, 0F, 1F) * 0.9F;
                for (int plano = 0; plano < 3; plano++) circulo(vc, m4, m3, o.x, o.y, o.z, r, plano, a);
            }
            ps.popPose();
            bs.endBatch(RenderType.lines());
            // siluetas: la entidad entera, en un solo color brillante sobre una textura blanca
            if (!ENTES.isEmpty()) {
                EntityRenderDispatcher rd = mc.getEntityRenderDispatcher();
                rd.setRenderShadow(false);
                try {
                    for (Ente en : ENTES.values()) {
                        Entity ent = en.e();
                        if (ent.isRemoved() || ent == mc.player) continue;
                        float edad = reloj - en.desde() + pt;
                        float a = Mth.clamp(1F - edad / DURA_ENTE, 0F, 1F) * 0.8F;
                        MultiBufferSource solido = rt -> new Solido(bs.getBuffer(RenderType.entityTranslucentEmissive(BLANCO)), CR, CG, CB, a);
                        double x = Mth.lerp(pt, ent.xOld, ent.getX()), y = Mth.lerp(pt, ent.yOld, ent.getY()), z = Mth.lerp(pt, ent.zOld, ent.getZ());
                        float yaw = Mth.lerp(pt, ent.yRotO, ent.getYRot());
                        rd.render(ent, x - cam.x, y - cam.y, z - cam.z, yaw, pt, ps, solido, 0xF000F0);
                    }
                } catch (RuntimeException ex) {
                    ENTES.clear();
                } finally {
                    rd.setRenderShadow(true);
                    bs.endBatch();
                }
            }
        } finally {
            RenderSystem.setShaderFogStart(nieblaIni);
            RenderSystem.setShaderFogEnd(nieblaFin);
        }
    }

    private static void circulo(VertexConsumer vc, Matrix4f m4, Matrix3f m3, double cx, double cy, double cz, float r, int plano, float a) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float x0, y0, z0, x1, y1, z1;
            switch (plano) {
                case 0 -> {   // horizontal
                    x0 = (float) (cx + Math.cos(a0) * r); y0 = (float) cy; z0 = (float) (cz + Math.sin(a0) * r);
                    x1 = (float) (cx + Math.cos(a1) * r); y1 = (float) cy; z1 = (float) (cz + Math.sin(a1) * r);
                }
                case 1 -> {
                    x0 = (float) (cx + Math.cos(a0) * r); y0 = (float) (cy + Math.sin(a0) * r); z0 = (float) cz;
                    x1 = (float) (cx + Math.cos(a1) * r); y1 = (float) (cy + Math.sin(a1) * r); z1 = (float) cz;
                }
                default -> {
                    x0 = (float) cx; y0 = (float) (cy + Math.sin(a0) * r); z0 = (float) (cz + Math.cos(a0) * r);
                    x1 = (float) cx; y1 = (float) (cy + Math.sin(a1) * r); z1 = (float) (cz + Math.cos(a1) * r);
                }
            }
            float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
            float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6F) l = 1F;
            nx /= l; ny /= l; nz /= l;
            vc.vertex(m4, x0, y0, z0).color(CR, CG, CB, a).normal(m3, nx, ny, nz).endVertex();
            vc.vertex(m4, x1, y1, z1).color(CR, CG, CB, a).normal(m3, nx, ny, nz).endVertex();
        }
    }

    /** Envoltorio que pinta todo de un color fijo (sobre la textura blanca: silueta maciza) con su alfa. */
    private record Solido(VertexConsumer d, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            d.color((int) (255 * r), (int) (255 * g), (int) (255 * b), (int) (ca * a));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int cr, int cg, int cb, int ca) {
            d.defaultColor((int) (255 * r), (int) (255 * g), (int) (255 * b), (int) (ca * a));
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------ HUD: una viñeta azulada y el aviso de la palmada
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (!activo()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        RenderSystem.enableBlend();
        g.fillGradient(0, 0, w, h / 4, 0x5A0A2030, 0x00000000);
        g.fillGradient(0, h - h / 4, w, h, 0x00000000, 0x5A0A2030);
        // el enfriamiento de la palmada: una barrita que se rellena
        int bw = 40, bx = w / 2 - bw / 2, by = h - 70;
        int lleno = Math.round(bw * (1F - enfriamiento / (float) ENFRIAMIENTO));
        g.fill(bx, by, bx + bw, by + 2, 0x66203040);
        g.fill(bx, by, bx + lleno, by + 2, 0xCC8CDCFF);
        RenderSystem.disableBlend();
    }
}
