package com.miscelanea.client.laser;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.laser.Laser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Cliente del puntero láser: la pose (brazo estirado apuntando), el rayo y su punto (de todos los jugadores que apuntan,
 * calculado aquí con el mismo temblor que en el servidor: no hace falta red) y el deslumbramiento si te dan en los ojos.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class LaserCliente {
    private LaserCliente() {}

    public static HumanoidModel.ArmPose POSE;
    private static final ResourceLocation DESTELLO = new ResourceLocation(Miscelanea.MOD_ID, "textures/misc/laser_destello.png");

    /** Punta del puntero en primera persona (espacio de vista, bloques; x a la derecha, y arriba, z hacia delante) con FOV 70. */
    private static final float PP_X = 0.39F, PP_Y = -0.37F, PP_Z = 0.96F;
    /** Tercera persona: del hombro a la punta a lo largo del brazo, y separación lateral del eje del brazo (bloques). */
    private static final double TP_LARGO = 0.80, TP_LADO = 0.35, TP_ALTO = 1.29, TP_ALTO_AGACHADO = 0.977;

    // deslumbramiento
    private static float destello, destelloAntes;
    private static int destelloColor = 0xFF2A1E;
    private static Vec3 destelloFuente = Vec3.ZERO;

    public static void init() {
        POSE = HumanoidModel.ArmPose.create("MISCELANEA_PUNTERO_LASER", false, (model, e, brazo) -> {
            var parte = brazo == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
            // brazo estirado hacia donde mira la cabeza (como el brazo que tensa un arco, pero sin la otra mano)
            parte.xRot = model.head.xRot - Mth.HALF_PI - (model.crouching ? 0.4F : 0.0F);
            parte.yRot = model.head.yRot;
            parte.zRot = 0.0F;
        });
    }

    public static HumanoidModel.ArmPose pose(LivingEntity e, InteractionHand h) {
        if (POSE != null && Laser.apuntando(e) && e.getUsedItemHand() == h) return POSE;
        return null;
    }

    /** Primera persona: el puntero se adelanta y se queda quieto (con un temblor mínimo) mientras apuntas. */
    public static boolean manoPrimeraPersona(PoseStack ps, LocalPlayer pl, HumanoidArm brazo, float pt) {
        if (!Laser.apuntando(pl)) return false;
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        float t = pl.tickCount + pt;
        ps.translate(i * 0.42F + Mth.sin(t * 0.9F) * 0.002F, -0.38F + Mth.sin(t * 1.3F) * 0.002F, -0.72F);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(t * 0.7F) * 0.25F));
        return true;
    }

    // ------------------------------------------------------------------ rayo

    /** De dónde sale el rayo que se dibuja: la punta del puntero (en primera persona, la del puntero de la pantalla). */
    static Vec3 origen(Player p, float pt) {
        Minecraft mc = Minecraft.getInstance();
        HumanoidArm brazo = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            Camera cam = mc.gameRenderer.getMainCamera();
            Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
            // la mano se dibuja con FOV 70 fijo: se corrige para que la punta caiga en el mismo sitio de la pantalla
            double k = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5)) / Math.tan(Math.toRadians(35));
            double x = i * PP_X * k, y = PP_Y * k, z = PP_Z;
            return cam.getPosition().add(-left.x() * x + up.x() * y + look.x() * z,
                    -left.y() * x + up.y() * y + look.y() * z,
                    -left.z() * x + up.z() * y + look.z() * z);
        }
        float yb = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 pos = p.getPosition(pt);
        Vec3 hombro = pos.add(-Math.cos(yb) * TP_LADO * i, p.isCrouching() ? TP_ALTO_AGACHADO : TP_ALTO, -Math.sin(yb) * TP_LADO * i);
        return hombro.add(p.getViewVector(pt).scale(TP_LARGO));
    }

    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Camera cam = e.getCamera();
        Vec3 c = cam.getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        Matrix4f m = e.getPoseStack().last().pose();
        double t = mc.level.getGameTime() + pt;
        for (Player p : mc.level.players()) {
            if (!Laser.apuntando(p) || p.distanceToSqr(c) > 160 * 160) continue;
            if (vc == null) vc = buf.getBuffer(RenderType.lightning());
            Laser.Impacto im = Laser.rayo(p, pt);
            int rgb = Laser.rgb(p.getUseItem());
            Vec3 a = origen(p, pt).subtract(c);
            Vec3 b = im.punto().subtract(c);
            // si da en algo, el rayo se queda un pelín antes para que el punto no se hunda en la cara
            Vec3 haciaCam = b.lengthSqr() < 1e-6 ? Vec3.ZERO : b.normalize().scale(-0.03);
            Vec3 bFin = im.toca() ? b.add(haciaCam) : b;
            float fl = 0.85F + 0.15F * (float) Math.sin(t * 2.3 + p.getId());
            cinta(vc, m, a, bFin, 0.05F, rgb, 0.16F * fl);
            cinta(vc, m, a, bFin, 0.011F, mezcla(rgb, 0xFFFFFF, 0.55F), 0.95F);
            if (im.toca()) {
                Vec3 q = b.add(haciaCam);
                float r = 0.055F + 0.008F * (float) Math.sin(t * 3.7 + p.getId());
                punto(vc, m, cam, q, 0.26F * fl, rgb, 0.55F);
                punto(vc, m, cam, q, r * 1.8F, rgb, 0.9F);
                punto(vc, m, cam, q, r, mezcla(rgb, 0xFFFFFF, 0.75F), 1.0F);
            }
        }
        if (vc != null) buf.endBatch(RenderType.lightning());
    }

    /** Tira de luz entre a y b, encarada a la cámara, que se difumina hacia los bordes. */
    private static void cinta(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, float ancho, int rgb, float alfa) {
        Vec3 d = b.subtract(a);
        if (d.lengthSqr() < 1e-6) return;
        Vec3 sa = d.cross(a), sb = d.cross(b);
        if (sa.lengthSqr() < 1e-9 || sb.lengthSqr() < 1e-9) return;
        sa = sa.normalize().scale(ancho);
        sb = sb.normalize().scale(ancho);
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, bl = (rgb & 255) / 255F;
        for (int lado = -1; lado <= 1; lado += 2) {
            Vec3 ea = a.add(sa.scale(lado)), eb = b.add(sb.scale(lado));
            quad(vc, m, a, b, eb, ea, r, g, bl, alfa, alfa, 0, 0);
        }
    }

    /** Punto redondo y difuminado (abanico de triángulos) encarado a la cámara. */
    private static void punto(VertexConsumer vc, Matrix4f m, Camera cam, Vec3 q, float radio, int rgb, float alfa) {
        Vector3f up = cam.getUpVector(), left = cam.getLeftVector();
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        int n = 14;
        for (int k = 0; k < n; k++) {
            double a0 = k * Math.PI * 2 / n, a1 = (k + 1) * Math.PI * 2 / n;
            Vec3 p0 = q.add(left.x() * Math.cos(a0) * radio + up.x() * Math.sin(a0) * radio,
                    left.y() * Math.cos(a0) * radio + up.y() * Math.sin(a0) * radio,
                    left.z() * Math.cos(a0) * radio + up.z() * Math.sin(a0) * radio);
            Vec3 p1 = q.add(left.x() * Math.cos(a1) * radio + up.x() * Math.sin(a1) * radio,
                    left.y() * Math.cos(a1) * radio + up.y() * Math.sin(a1) * radio,
                    left.z() * Math.cos(a1) * radio + up.z() * Math.sin(a1) * radio);
            quad(vc, m, q, q, p1, p0, r, g, b, alfa, alfa, 0, 0);
        }
    }

    /** Cuadrilátero por las dos caras (el render type del rayo descarta las caras traseras). */
    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 v0, Vec3 v1, Vec3 v2, Vec3 v3,
                             float r, float g, float b, float a0, float a1, float a2, float a3) {
        v(vc, m, v0, r, g, b, a0); v(vc, m, v1, r, g, b, a1); v(vc, m, v2, r, g, b, a2); v(vc, m, v3, r, g, b, a3);
        v(vc, m, v3, r, g, b, a3); v(vc, m, v2, r, g, b, a2); v(vc, m, v1, r, g, b, a1); v(vc, m, v0, r, g, b, a0);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ deslumbramiento

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        destelloAntes = destello;
        LocalPlayer yo = mc.player;
        if (yo == null || mc.level == null || mc.isPaused()) {
            if (yo == null) destello = destelloAntes = 0;
            return;
        }
        boolean dado = false;
        if (ConfigServidor.laserDeslumbrar()) {
            double alc = Laser.alcance();
            for (Player q : mc.level.players()) {
                if (q == yo || !Laser.apuntando(q) || q.distanceToSqr(yo) > alc * alc) continue;
                Laser.Impacto im = Laser.rayo(q, 1.0F);
                if (im.entidad() != yo || Math.abs(im.punto().y - yo.getEyeY()) > 0.28) continue;
                Vec3 haciaFuente = q.getEyePosition().subtract(yo.getEyePosition()).normalize();
                if (yo.getViewVector(1.0F).dot(haciaFuente) < 0.8) continue; // solo si le estás mirando
                dado = true;
                destelloColor = Laser.rgb(q.getUseItem());
                destelloFuente = q.getEyePosition();
            }
        }
        destello = dado ? Math.min(1.0F, destello + 0.3F) : destello * 0.82F;
        if (destello < 0.01F) destello = 0;
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float a = Mth.lerp(pt, destelloAntes, destello);
        if (a <= 0.005F) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        // dónde cae en la pantalla el que te apunta
        Vec3 v = destelloFuente.subtract(cam.getPosition());
        Vector3f look = cam.getLookVector(), up = cam.getUpVector(), left = cam.getLeftVector();
        double z = v.x * look.x() + v.y * look.y() + v.z * look.z();
        float sx = w / 2F, sy = h / 2F;
        if (z > 0.1) {
            double tanH = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5));
            double x = -(v.x * left.x() + v.y * left.y() + v.z * left.z()) / z, y = (v.x * up.x() + v.y * up.y() + v.z * up.z()) / z;
            sx = (float) Mth.clamp(w / 2.0 + x / tanH * h / 2.0, 0, w);
            sy = (float) Mth.clamp(h / 2.0 - y / tanH * h / 2.0, 0, h);
        }
        float r = (destelloColor >> 16 & 255) / 255F, gr = (destelloColor >> 8 & 255) / 255F, b = (destelloColor & 255) / 255F;
        // velo del color del láser por toda la pantalla
        int alfaVelo = (int) (a * a * 120);
        g.fill(0, 0, w, h, alfaVelo << 24 | destelloColor);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableDepthTest();
        int t1 = (int) (h * (0.7F + 1.1F * a)), t2 = (int) (h * (0.25F + 0.45F * a));
        RenderSystem.setShaderColor(r, gr, b, Math.min(1F, a * 1.2F));
        g.blit(DESTELLO, (int) (sx - t1 / 2F), (int) (sy - t1 / 2F), t1, t1, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1F, 1F, 1F, Math.min(1F, a));
        g.blit(DESTELLO, (int) (sx - t2 / 2F), (int) (sy - t2 / 2F), t2, t2, 0, 0, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
    }
}
