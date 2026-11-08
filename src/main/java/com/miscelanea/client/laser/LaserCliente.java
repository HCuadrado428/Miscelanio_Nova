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
import org.joml.Vector4f;

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

    /** Matrices del último fotograma dibujado (proyección y vista, relativas a la cámara) y la cámara de entonces. */
    private static final Matrix4f PROY = new Matrix4f(), VISTA = new Matrix4f(), INV = new Matrix4f();
    private static Vec3 camaraPos = Vec3.ZERO;
    private static boolean hayMatrices;
    /** Solo para la prueba automática: dibujar el punto sin el rayo (para medir dónde cae en la captura). */
    public static boolean pruebaSinRayo, pruebaModoAntiguo;
    /** Solo para la prueba: dónde ha caído mi punto en el último fotograma (null si no apuntaba o no daba en nada). */
    public static volatile Vec3 pruebaMiPunto;

    /**
     * El rayo de un jugador tal como se dibuja aquí. El mío, en primera persona, sale de la cámara y pasa por el CENTRO
     * DE LA PANTALLA tal como se ha dibujado este fotograma (deshaciendo la proyección y la vista de verdad): así cae en
     * la mira con cualquier FOV, proporción de ventana, cambio de tamaño, cámara de otros mods o balanceo al andar. El
     * de los demás (y el mío en tercera persona), desde sus ojos y hacia donde miran: lo mismo que calcula el servidor
     * y los demás clientes, así que el punto está donde mira el que lo lleva para todos.
     */
    static Laser.Impacto rayo(Player p, float pt, Camera cam) {
        Minecraft mc = Minecraft.getInstance();
        if (pruebaModoAntiguo && p == mc.player && mc.getCameraEntity() == p && mc.options.getCameraType() != net.minecraft.client.CameraType.THIRD_PERSON_FRONT)
            return Laser.rayo(p, cam.getPosition(), Laser.direccion(p, pt, cam.getXRot(), cam.getYRot()));
        if (p == mc.player && mc.getCameraEntity() == p && mc.options.getCameraType().isFirstPerson() && hayMatrices) {
            Vec3 cerca = desproyectar(0, 0, -1), lejos = desproyectar(0, 0, 0);
            Vec3 d = lejos.subtract(cerca);
            if (d.lengthSqr() > 1e-8) {
                d = d.normalize();
                float xRot = (float) Math.toDegrees(Math.asin(Mth.clamp(-d.y, -1, 1)));
                float yRot = (float) Math.toDegrees(Mth.atan2(-d.x, d.z));
                return Laser.rayo(p, cam.getPosition().add(cerca), Laser.direccion(p, pt, xRot, yRot));
            }
        }
        return Laser.rayo(p, pt);
    }

    /** Punto (relativo a la cámara) que cae en (x, y, z) de la pantalla en coordenadas normalizadas (-1..1). */
    private static Vec3 desproyectar(double x, double y, double z) {
        Vector4f v = INV.transform(new Vector4f((float) x, (float) y, (float) z, 1F));
        if (Math.abs(v.w) < 1e-9) return Vec3.ZERO;
        return new Vec3(v.x / v.w, v.y / v.w, v.z / v.w);
    }

    /** Dónde cae en la pantalla (en píxeles de ancho x alto) un punto del mundo en el último fotograma, o null si está detrás. */
    public static double[] aPantalla(Vec3 mundo, int ancho, int alto) {
        if (!hayMatrices) return null;
        Vec3 r = mundo.subtract(camaraPos);
        Vector4f v = new Matrix4f(PROY).mul(VISTA).transform(new Vector4f((float) r.x, (float) r.y, (float) r.z, 1F));
        if (v.w <= 1e-6) return null;
        return new double[]{(v.x / v.w + 1) * 0.5 * ancho, (1 - v.y / v.w) * 0.5 * alto};
    }

    /** De dónde sale el rayo que se dibuja: la punta del puntero (en primera persona, la del puntero de la pantalla). */
    static Vec3 origen(Player p, float pt) {
        Minecraft mc = Minecraft.getInstance();
        HumanoidArm brazo = p.getUsedItemHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        int i = brazo == HumanoidArm.RIGHT ? 1 : -1;
        if (p == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson() && hayMatrices) {
            // la mano se dibuja con su propia proyección (FOV 70 fijo): se mira en qué punto de la pantalla queda la
            // punta y se busca el punto del mundo que cae en ese mismo píxel, a la misma profundidad
            var w = mc.getWindow();
            float aspecto = (float) w.getWidth() / Math.max(1, w.getHeight());
            Matrix4f mano = new Matrix4f().setPerspective((float) Math.toRadians(70), aspecto, 0.05F, 100F);
            Vector4f s = mano.transform(new Vector4f(i * PP_X, PP_Y, -PP_Z, 1F));
            Vector4f zc = new Matrix4f(PROY).transform(new Vector4f(0, 0, -PP_Z, 1F));
            if (s.w > 1e-6 && Math.abs(zc.w) > 1e-6)
                return camaraPos.add(desproyectar(s.x / s.w, s.y / s.w, zc.z / zc.w));
        }
        float yb = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 pos = p.getPosition(pt);
        // con Pehkui (o las pociones de Gigante y Enano) el jugador cambia de tamaño: la mano está a esa escala
        double k = Math.max(0.05, p.getBbWidth() / 0.6);
        Vec3 hombro = pos.add(-Math.cos(yb) * TP_LADO * i * k, (p.isCrouching() ? TP_ALTO_AGACHADO : TP_ALTO) * k, -Math.sin(yb) * TP_LADO * i * k);
        return hombro.add(p.getViewVector(pt).scale(TP_LARGO * k));
    }

    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Camera cam = e.getCamera();
        Vec3 c = cam.getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        // las matrices con las que se está dibujando de verdad este fotograma (con el balanceo, el FOV dinámico, la
        // proporción de la ventana y lo que hayan puesto otros mods): con ellas se busca el centro de la pantalla
        PROY.set(e.getProjectionMatrix());
        VISTA.set(m);
        INV.set(PROY).mul(VISTA).invert();
        camaraPos = c;
        hayMatrices = true;
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        double t = mc.level.getGameTime() + pt;
        Vec3 miPunto = null;
        for (Player p : mc.level.players()) {
            if (!Laser.apuntando(p) || p.distanceToSqr(c) > 160 * 160) continue;
            if (vc == null) vc = buf.getBuffer(RenderType.lightning());
            Laser.Impacto im = rayo(p, pt, cam);
            if (p == mc.player && im.toca()) miPunto = im.punto();
            int rgb = Laser.rgb(p.getUseItem());
            Vec3 a = origen(p, pt).subtract(c);
            Vec3 b = im.punto().subtract(c);
            // si da en algo, el rayo se queda un pelín antes para que el punto no se hunda en la cara
            Vec3 haciaCam = b.lengthSqr() < 1e-6 ? Vec3.ZERO : b.normalize().scale(-0.03);
            Vec3 bFin = im.toca() ? b.add(haciaCam) : b;
            float fl = 0.85F + 0.15F * (float) Math.sin(t * 2.3 + p.getId());
            if (!pruebaSinRayo) {
                cinta(vc, m, a, bFin, 0.05F, rgb, 0.16F * fl);
                cinta(vc, m, a, bFin, 0.011F, mezcla(rgb, 0xFFFFFF, 0.55F), 0.95F);
            }
            if (im.toca()) {
                Vec3 q = b.add(haciaCam);
                float r = 0.055F + 0.008F * (float) Math.sin(t * 3.7 + p.getId());
                punto(vc, m, cam, q, 0.26F * fl, rgb, 0.55F);
                punto(vc, m, cam, q, r * 1.8F, rgb, 0.9F);
                punto(vc, m, cam, q, r, mezcla(rgb, 0xFFFFFF, 0.75F), 1.0F);
            }
        }
        pruebaMiPunto = miPunto;
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
        // encarado con la vista con la que se dibuja (la de la cámara puede no estar al día si otro mod la ha girado)
        Vector3f up = new Vector3f(VISTA.m01(), VISTA.m11(), VISTA.m21()), left = new Vector3f(-VISTA.m00(), -VISTA.m10(), -VISTA.m20());
        if (up.lengthSquared() < 1e-6F || left.lengthSquared() < 1e-6F) {
            up = cam.getUpVector();
            left = cam.getLeftVector();
        } else {
            up.normalize();
            left.normalize();
        }
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
        // dónde cae en la pantalla el que te apunta (con las matrices con las que se ha dibujado el mundo)
        float sx = w / 2F, sy = h / 2F;
        double[] sp = aPantalla(destelloFuente, w, h);
        if (sp != null) {
            sx = (float) Mth.clamp(sp[0], 0, w);
            sy = (float) Mth.clamp(sp[1], 0, h);
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
