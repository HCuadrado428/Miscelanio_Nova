package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.rifle.Cuerda;
import com.miscelanea.armasfuego.rifle.RifleAccionPacket;
import com.miscelanea.armasfuego.rifle.Tirolinas;
import com.miscelanea.armasfuego.rifle.TirolinasPacket;
import com.miscelanea.network.ModNetwork;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Las tirolinas del rifle en el cliente: la lista que manda el servidor, el dibujo (una cuerda de dos cintas cruzadas,
 * pocos tramos, con un gancho de cordel trampa en cada punta) y cómo se cogen: clic derecho mirándolas o saltando
 * debajo para colgarse; clic derecho con unas tijeras para cortarlas.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TirolinasCliente {
    private TirolinasCliente() {}

    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/cuerda_rifle.png");
    public static final List<TirolinasPacket.Linea> LINEAS = new ArrayList<>();
    private static final ItemStack GANCHO = new ItemStack(Items.TRIPWIRE_HOOK);
    private static int esperaSalto;

    public static void recibir(List<TirolinasPacket.Linea> l) {
        LINEAS.clear();
        LINEAS.addAll(l);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        LINEAS.clear();
    }

    // ------------------------------------------------------------------ apuntar a una cuerda

    /** Tirolina en el centro de la mira a menos de alcance bloques: {id, distancia}, o null. */
    public static double[] apuntada(LocalPlayer p, double alcance) {
        Vec3 ojo = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        double[] mejor = null;
        for (TirolinasPacket.Linea x : LINEAS) {
            if (distanciaSegmento(ojo, x.a(), x.b()) > alcance + 2) continue;
            double[] r = Cuerda.rayo(x.a(), x.b(), ojo, dir, alcance);
            if (r[0] < 0.4 && (mejor == null || r[1] < mejor[1])) mejor = new double[]{x.id(), r[1]};
        }
        return mejor;
    }

    private static double distanciaSegmento(Vec3 q, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double l2 = d.lengthSqr();
        double t = l2 < 1e-6 ? 0 : Math.max(0, Math.min(1, q.subtract(a).dot(d) / l2));
        return a.add(d.scale(t)).distanceTo(q);
    }

    /** Clic derecho mirando una cuerda: colgarse (o cortarla, con tijeras). */
    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND || p == null || LINEAS.isEmpty() || p.isPassenger()) return;
        boolean tijeras = Tirolinas.esTijeras(p.getMainHandItem()) || Tirolinas.esTijeras(p.getOffhandItem());
        if (!tijeras && p.isShiftKeyDown()) return;
        double[] m = apuntada(p, tijeras ? 5.0 : 3.5);
        if (m == null) return;
        // si hay un bloque o un mob más cerca en la mira, el clic es para él
        HitResult hr = mc.hitResult;
        if (hr != null && hr.getType() != HitResult.Type.MISS && hr.getLocation().distanceTo(p.getEyePosition()) < m[1] - 0.3) return;
        ModNetwork.CHANNEL.sendToServer(RifleAccionPacket.de(tijeras ? RifleAccionPacket.CORTAR : RifleAccionPacket.COLGAR, (int) m[0]));
        e.setCanceled(true);
        e.setSwingHand(true);
    }

    /** Saltar debajo de una cuerda: te agarras. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (esperaSalto > 0) esperaSalto--;
        if (p == null || LINEAS.isEmpty() || p.isPassenger() || p.onGround() || esperaSalto > 0 || mc.screen != null) return;
        if (!mc.options.keyJump.isDown() || p.getDeltaMovement().y < -0.1) return;
        if (colgarCerca(p, 0.9)) esperaSalto = 10;
    }

    /** Si hay una cuerda al alcance de las manos, se manda colgarse. */
    public static boolean colgarCerca(LocalPlayer p, double radio) {
        double cabeza = p.getY() + p.getBbHeight();
        for (TirolinasPacket.Linea x : LINEAS) {
            Vec3 manos = new Vec3(p.getX(), cabeza + 0.4, p.getZ());
            double t = Cuerda.cercano(x.a(), x.b(), manos);
            Vec3 q = Cuerda.punto(x.a(), x.b(), t);
            double h = Math.hypot(q.x - p.getX(), q.z - p.getZ());
            double dy = q.y - cabeza;
            if (h <= radio && dy > -0.3 && dy < 1.6) {
                ModNetwork.CHANNEL.sendToServer(RifleAccionPacket.de(RifleAccionPacket.COLGAR, x.id()));
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ dibujo

    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LINEAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null || pasadaSombras()) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        double lejos = mc.options.getEffectiveRenderDistance() * 16.0 + 16;
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        List<TirolinasPacket.Linea> vistas = new ArrayList<>();
        for (TirolinasPacket.Linea x : LINEAS) {
            if (distanciaSegmento(cam, x.a(), x.b()) > lejos) continue;
            vistas.add(x);
            cuerda(vc, ps, l, cam, x.a(), x.b(), 0.03);
            // en la punta del tirador la cuerda baja atada hasta el suelo
            if (!Double.isNaN(x.sueloA()) && x.a().y - x.sueloA() > 0.3)
                cuerda(vc, ps, l, cam, x.a(), new Vec3(x.a().x, x.sueloA(), x.a().z), 0.022);
        }
        buf.endBatch(RenderType.entityCutoutNoCull(TEX));
        if (vistas.isEmpty()) return;
        // ganchos en las puntas
        for (TirolinasPacket.Linea x : vistas) {
            Vec3 d = x.b().subtract(x.a());
            float yaw = (float) Math.toDegrees(Math.atan2(d.x, d.z));
            gancho(mc, ps, buf, l, cam, x.b(), yaw);
            gancho(mc, ps, buf, l, cam, Double.isNaN(x.sueloA()) ? x.a() : new Vec3(x.a().x, x.sueloA() + 0.15, x.a().z), yaw + 180);
        }
        buf.endBatch();
    }

    private static void gancho(Minecraft mc, PoseStack ps, MultiBufferSource buf, ClientLevel l, Vec3 cam, Vec3 q, float yaw) {
        ps.pushPose();
        ps.translate(q.x - cam.x, q.y - cam.y, q.z - cam.z);
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.scale(0.6F, 0.6F, 0.6F);
        int luz = LevelRenderer.getLightColor(l, BlockPos.containing(q));
        mc.getItemRenderer().renderStatic(GANCHO, ItemDisplayContext.FIXED, luz, OverlayTexture.NO_OVERLAY, ps, buf, l, 0);
        ps.popPose();
    }

    /** La cuerda de a a b: dos cintas cruzadas, un tramo cada ~1,5 bloques, con la luz de cada tramo. */
    private static void cuerda(VertexConsumer vc, PoseStack ps, ClientLevel l, Vec3 cam, Vec3 a, Vec3 b, double r) {
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        int n = Math.max(2, Math.min(48, (int) (a.distanceTo(b) / 1.5)));
        double acum = 0;
        Vec3 prev = Cuerda.punto(a, b, 0);
        for (int i = 0; i < n; i++) {
            Vec3 p0 = prev, p1 = Cuerda.punto(a, b, (i + 1) / (double) n);
            Vec3 d = p1.subtract(p0);
            Vec3 lado = d.cross(new Vec3(0, 1, 0));
            if (lado.lengthSqr() < 1e-8) lado = d.cross(new Vec3(1, 0, 0));
            lado = lado.normalize().scale(r);
            Vec3 arriba = d.cross(lado).normalize().scale(r);
            int luz = LevelRenderer.getLightColor(l, BlockPos.containing(p0.lerp(p1, 0.5)));
            float v0 = (float) (acum * 2), v1 = (float) ((acum + d.length()) * 2);
            cinta(vc, m, nm, p0.subtract(cam), p1.subtract(cam), lado, v0, v1, luz);
            cinta(vc, m, nm, p0.subtract(cam), p1.subtract(cam), arriba, v0, v1, luz);
            acum += d.length();
            prev = p1;
        }
    }

    private static void cinta(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p0, Vec3 p1, Vec3 off, float v0, float v1, int luz) {
        punto(vc, m, n, p0.subtract(off), 0, v0, luz);
        punto(vc, m, n, p0.add(off), 1, v0, luz);
        punto(vc, m, n, p1.add(off), 1, v1, luz);
        punto(vc, m, n, p1.subtract(off), 0, v1, luz);
    }

    private static void punto(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int luz) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(luz).normal(n, 0, 1, 0).endVertex();
    }

    // con shaders (Oculus/Iris) esta etapa se repite al dibujar las sombras: ahí no
    private static java.lang.reflect.Method irisSombras;
    private static Object irisApi;
    private static boolean sinIris;

    private static boolean pasadaSombras() {
        if (sinIris) return false;
        try {
            if (irisSombras == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = c.getMethod("getInstance").invoke(null);
                irisSombras = c.getMethod("isRenderingShadowPass");
            }
            return (Boolean) irisSombras.invoke(irisApi);
        } catch (Throwable t) {
            sinIris = true;
            return false;
        }
    }
}
