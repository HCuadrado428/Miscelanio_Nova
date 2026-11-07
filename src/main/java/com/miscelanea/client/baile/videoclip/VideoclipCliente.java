package com.miscelanea.client.baile.videoclip;

import com.miscelanea.Miscelanea;
import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.client.baile.BaileCliente;
import com.miscelanea.client.baile.BailePoses;
import com.miscelanea.client.baile.Coreografia;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Videoclip de la gramola, solo en la pantalla de los que bailan: mientras suena la canción el mundo de verdad deja de
 * dibujarse (bloques, bloques con dibujo propio, mobs, lluvia, nubes; ver LevelRendererMixin y compañía) y en su lugar
 * se dibuja el decorado de la canción alrededor de la gramola, con su niebla, su luz y sus luces que se mueven con la
 * música. Solo se siguen viendo los que bailan en esa gramola. El decorado se tesela una vez (como un chunk) y luego
 * cada fotograma solo se coloca: no cuesta más que unos pocos chunks.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class VideoclipCliente {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();
    private static final RenderType[] CAPAS = {RenderType.solid(), RenderType.cutoutMipped(), RenderType.cutout()};

    @Nullable
    private static Decorado deco;
    private static BlockPos gramola;
    private static float frente;
    private static Vec3 ancla = Vec3.ZERO;
    private static CancionesBaile.Cancion cancion;
    private static Recorrido recorrido;
    private static long empezo;
    @Nullable
    private static Map<RenderType, VertexBuffer> malla;
    private static boolean fallo;

    private VideoclipCliente() {}

    /** Decorado de cada canción (las que no tienen uno propio, a la discoteca). */
    static Decorado decoradoDe(CancionesBaile.Cancion c) {
        String id = c.id.contains(":") ? c.id.substring(c.id.indexOf(':') + 1) : c.id;
        return switch (id) {
            case "weapon_of_choice" -> new DecoradoHotel();
            default -> new DecoradoDisco();
        };
    }

    public static void empezar(BlockPos g, float frenteYaw, CancionesBaile.Cancion c) {
        parar();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || g == null || c == null) return;
        gramola = g.immutable();
        frente = frenteYaw;
        BlockState st = mc.level.getBlockState(g);
        Direction f = st.hasProperty(com.miscelanea.baile.PistaBaileBlock.FACING) ? st.getValue(com.miscelanea.baile.PistaBaileBlock.FACING)
                : Direction.fromYRot(frenteYaw);
        ancla = com.miscelanea.baile.PistaBaileBlockEntity.hueco(g, f, 0);
        cancion = c;
        Decorado d = decoradoDe(c);
        try {
            d.construir();
        } catch (RuntimeException ex) {
            LOG.warn("No se pudo montar el decorado del videoclip", ex);
            return;
        }
        recorrido = d.viaja() ? new Recorrido(c) : null;
        deco = d;
        fallo = false;
        empezo = System.currentTimeMillis();
    }

    public static void parar() {
        deco = null;
        cancion = null;
        recorrido = null;
        Map<RenderType, VertexBuffer> m = malla;
        malla = null;
        if (m != null) RenderSystem.recordRenderCall(() -> m.values().forEach(VertexBuffer::close));
    }

    public static boolean activo() {
        return deco != null && !fallo && BaileCliente.cancionGramola() != null;
    }

    // ------------------------------------------------------------------ qué se esconde (desde los mixins)
    /** ¿Esta entidad no sale en el videoclip? (todo menos uno mismo y los que bailan en la gramola). */
    public static boolean ocultar(Entity e) {
        if (!activo()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (e == mc.player) return false;
        if (e instanceof Player && BailePoses.baila(e.getId()) && e.position().distanceToSqr(Vec3.atBottomCenterOf(gramola)) < 64) return false;
        return true;
    }

    /** Luz de los que bailan dentro del decorado (la del decorado, no la del sitio de verdad), o -1. */
    public static int luzEntidad(Entity e) {
        if (!activo() || ocultar(e)) return -1;
        return LightTexture.pack(deco.luz(), 0);
    }

    // ------------------------------------------------------------------ el momento del videoclip
    private static float pulso() {
        return cancion == null ? 0 : cancion.pulso(BaileCliente.segundo());
    }

    private static Decorado.Momento momento() {
        float p = pulso();
        float golpe = (float) Math.exp(-(p - Mth.floor(p)) * 5);
        float reloj = (System.currentTimeMillis() - empezo) / 1000F;
        return new Decorado.Momento(p, p < 0 ? 0 : golpe, reloj, Coreografia.pasoEn(cancion, p));
    }

    /** Coloca el sistema del decorado (centrado en el protagonista, ya movido por el paseo o el vuelo). */
    private static void colocar(PoseStack ps, Vec3 cam) {
        ps.translate(ancla.x - cam.x, ancla.y - cam.y, ancla.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(-frente));
        if (recorrido != null) {
            float[] r = recorrido.en(pulso());
            float per = deco.periodo();
            float dz = per > 0 ? r[0] % per : r[0];
            ps.translate(0, -r[1], -dz);
        }
    }

    // ------------------------------------------------------------------ malla
    private static Map<RenderType, VertexBuffer> teselar(Decorado d) {
        Minecraft mc = Minecraft.getInstance();
        BlockRenderDispatcher brd = mc.getBlockRenderer();
        NivelDecorado nivel = new NivelDecorado(d.bloques, d.luz());
        Map<RenderType, BufferBuilder> bufs = new HashMap<>();
        PoseStack ps = new PoseStack();
        RandomSource r = RandomSource.create();
        for (Map.Entry<BlockPos, BlockState> e : d.bloques.entrySet()) {
            BlockState st = e.getValue();
            if (st.getRenderShape() != RenderShape.MODEL) continue;
            BlockPos p = e.getKey();
            BakedModel model = brd.getBlockModel(st);
            r.setSeed(st.getSeed(p));
            for (RenderType rt : model.getRenderTypes(st, r, ModelData.EMPTY)) {
                if (rt != RenderType.solid() && rt != RenderType.cutoutMipped() && rt != RenderType.cutout()) continue;
                BufferBuilder bb = bufs.computeIfAbsent(rt, k -> {
                    BufferBuilder b = new BufferBuilder(1 << 18);
                    b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
                    return b;
                });
                ps.pushPose();
                ps.translate(p.getX(), p.getY(), p.getZ());
                r.setSeed(st.getSeed(p));
                brd.renderBatched(st, p, nivel, ps, bb, true, r, ModelData.EMPTY, rt);
                ps.popPose();
            }
        }
        Map<RenderType, VertexBuffer> out = new HashMap<>();
        for (Map.Entry<RenderType, BufferBuilder> e : bufs.entrySet()) {
            BufferBuilder.RenderedBuffer rb = e.getValue().endOrDiscardIfEmpty();
            if (rb == null) continue;
            VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vb.bind();
            vb.upload(rb);
            VertexBuffer.unbind();
            out.put(e.getKey(), vb);
        }
        return out;
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (!activo()) return;
        try {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) dibujarMalla(e.getPoseStack(), e.getProjectionMatrix(), e.getCamera().getPosition());
            else if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) dibujarLuces(e.getPoseStack(), e.getCamera().getPosition());
        } catch (RuntimeException ex) {
            // si algo no cuadra (otro mod de dibujo, sombreadores...) se deja de esconder el mundo y el baile sigue
            LOG.warn("El videoclip de la gramola no se puede dibujar; se baila sin decorado", ex);
            fallo = true;
        }
    }

    private static void dibujarMalla(PoseStack ps, Matrix4f proj, Vec3 cam) {
        if (malla == null) malla = teselar(deco);
        ps.pushPose();
        colocar(ps, cam);
        ps.translate(-0.5, 0, -0.5);
        Matrix4f mv = ps.last().pose();
        for (RenderType rt : CAPAS) {
            VertexBuffer vb = malla.get(rt);
            if (vb == null) continue;
            rt.setupRenderState();
            ShaderInstance sh = RenderSystem.getShader();
            if (sh != null) {
                if (sh.CHUNK_OFFSET != null) sh.CHUNK_OFFSET.set(0F, 0F, 0F);
                vb.bind();
                vb.drawWithShader(mv, proj, sh);
                VertexBuffer.unbind();
            }
            rt.clearRenderState();
        }
        ps.popPose();
    }

    private static void dibujarLuces(PoseStack ps, Vec3 cam) {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        Decorado.Momento t = momento();
        // sillón del protagonista y de los de fondo cuando se sientan (Weapon of Choice)
        boolean sentado = t.paso().equals("silla") || t.paso().equals("aterrizar") && t.pulso() - Mth.floor(t.pulso()) >= 0;
        List<? extends Player> bailan = mc.level == null ? List.of() : mc.level.players().stream()
                .filter(p -> !ocultar(p) && (p == mc.player || BailePoses.baila(p.getId()))).toList();
        float pt = mc.getFrameTime();
        if (sentado && deco instanceof DecoradoHotel) {
            BlockState sillon = Blocks.CRIMSON_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
            for (Player p : bailan) {
                Vec3 pos = p.getPosition(pt);
                ps.pushPose();
                ps.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
                ps.mulPose(Axis.YP.rotationDegrees(-frente));
                ps.translate(-0.5, 0, -0.62);
                mc.getBlockRenderer().renderSingleBlock(sillon, ps, bs, LightTexture.pack(deco.luz(), 0), OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
            }
            bs.endBatch();
        }
        // luces del decorado
        ps.pushPose();
        colocar(ps, cam);
        Matrix4f m = ps.last().pose();
        deco.luces(m, bs.getBuffer(TiposVideoclip.BRILLO), bs.getBuffer(TiposVideoclip.OPACO), t);
        ps.popPose();
        // un foco suave en el suelo debajo de cada uno que baila
        ps.pushPose();
        Matrix4f mm = ps.last().pose();
        var vc = bs.getBuffer(TiposVideoclip.BRILLO);
        for (Player p : bailan) {
            Vec3 pos = p.getPosition(pt).subtract(cam);
            float suelo = (float) (ancla.y - cam.y) + 0.035F;
            for (int k = 0; k < 3; k++) {
                float r = 1.5F - k * 0.4F;
                Decorado.suelo(vc, mm, (float) pos.x - r, (float) pos.z - r, (float) pos.x + r, (float) pos.z + r, suelo + k * 0.002F, 0xFFF4D8, 0.06F);
            }
        }
        ps.popPose();
        bs.endBatch(TiposVideoclip.OPACO);
        bs.endBatch(TiposVideoclip.BRILLO);
    }

    // ------------------------------------------------------------------ niebla, contorno y fogonazo
    @SubscribeEvent
    public static void niebla(ViewportEvent.RenderFog e) {
        if (!activo()) return;
        e.setNearPlaneDistance(deco.cerca());
        e.setFarPlaneDistance(deco.lejos());
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        if (!activo()) return;
        float[] c = deco.niebla();
        e.setRed(c[0]);
        e.setGreen(c[1]);
        e.setBlue(c[2]);
    }

    @SubscribeEvent
    public static void contorno(RenderHighlightEvent.Block e) {
        if (activo()) e.setCanceled(true);
    }

    /** Al empezar, un fogonazo blanco: el mundo se transforma en el decorado. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void fogonazo(RenderGuiEvent.Post e) {
        if (!activo()) return;
        long d = System.currentTimeMillis() - empezo;
        if (d > 700) return;
        float a = 1 - d / 700F;
        int al = (int) (a * a * 255);
        e.getGuiGraphics().fill(0, 0, e.getGuiGraphics().guiWidth(), e.getGuiGraphics().guiHeight(), (al << 24) | 0xFFFFFF);
    }
}
