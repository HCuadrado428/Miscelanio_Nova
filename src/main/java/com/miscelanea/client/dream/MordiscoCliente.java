package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.dream.Mordiscos;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lado cliente del mordisco:
 * <ul>
 *   <li>La víctima queda pegada delante de quien muerde, sin poder andar, y su cámara se ve obligada a mirarle; tiembla
 *       con cada mordisco y la pantalla late con una viñeta verde oscuro (el veneno).</li>
 *   <li>Quien muerde nota un pequeño tirón en la cámara con cada mordisco.</li>
 * </ul>
 * El forcejeo de la víctima es el minijuego (QteCliente se encarga de sus teclas).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class MordiscoCliente {
    private MordiscoCliente() {}

    private static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/misc/dream_vineta.png");

    /** Punto donde va la víctima (igual que en el servidor, Mordiscos.sujetar). */
    static Vec3 puntoVictima(Entity mordedor, Entity victima, float pt, float progreso) {
        float yaw = Mth.lerp(pt, mordedor.yRotO, mordedor.getYRot());
        Vec3 frente = Vec3.directionFromRotation(0, yaw);
        Vec3 pos = new Vec3(Mth.lerp(pt, mordedor.xo, mordedor.getX()), Mth.lerp(pt, mordedor.yo, mordedor.getY()),
                Mth.lerp(pt, mordedor.zo, mordedor.getZ()));
        float k = Mth.clamp(progreso, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        double dist = 1.0 + (Mordiscos.DISTANCIA + victima.getBbWidth() * 0.5 - 0.3 - 1.0) * k;
        return pos.add(frente.scale(dist));
    }

    private static float tiron(ClientMordiscos.Estado e, float t) {
        float dm = t - e.ultimoMordisco;
        return dm < 12.0F ? 1.0F - dm / 12.0F : 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ClientMordiscos.limpiar();
            return;
        }
        LocalPlayer p = mc.player;
        ClientMordiscos.Estado e = ClientMordiscos.local();
        if (p == null || e == null || e.rol != ClientMordiscos.Rol.VICTIMA) return;
        Entity m = mc.level.getEntity(e.otro);
        if (m == null) return;
        Vec3 punto = puntoVictima(m, p, 1.0F, (ClientMordiscos.ahora(0.0F) - e.inicio) / 5.0F);
        p.setPos(punto.x, punto.y, punto.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        Vec3 d = m.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        ClientMordiscos.Estado e = ClientMordiscos.local();
        if (e == null || e.rol != ClientMordiscos.Rol.VICTIMA) return;
        Input in = event.getInput();
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientMordiscos.Estado e = ClientMordiscos.local();
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = (float) event.getPartialTick();
        float t = ClientMordiscos.ahora(pt);
        float k = tiron(e, t);
        if (e.rol == ClientMordiscos.Rol.VICTIMA) {
            Entity m = mc.level != null ? mc.level.getEntity(e.otro) : null;
            if (m != null) {
                Vec3 d = m.getEyePosition(pt).subtract(event.getCamera().getPosition());
                event.setYaw((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
                event.setPitch((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
            }
            float amp = 0.5F + 6.0F * k;
            event.setYaw(event.getYaw() + Mth.sin(t * 29.0F) * amp * 0.5F);
            event.setPitch(event.getPitch() + Mth.sin(t * 25.0F + 1.0F) * amp * 0.4F);
            event.setRoll(event.getRoll() + Mth.sin(t * 21.0F + 2.0F) * amp + 4.0F * k);
        } else if (k > 0.0F) {
            // el tirón de quien muerde: la cabeza baja de golpe
            event.setPitch(event.getPitch() + 3.0F * k * k);
            event.setRoll(event.getRoll() + Mth.sin(t * 19.0F) * 1.2F * k);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        ClientMordiscos.Estado e = ClientMordiscos.local();
        if (e == null || e.rol != ClientMordiscos.Rol.VICTIMA) return;
        float k = tiron(e, ClientMordiscos.ahora((float) event.getPartialTick()));
        event.setFOV(event.getFOV() * (0.9 - 0.07 * k));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientMordiscos.limpiar();
    }

    /** Viñeta verde veneno de la víctima: siempre un poco y un latido fuerte con cada mordisco. */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ClientMordiscos.Estado e = ClientMordiscos.de(mc.player.getId());
        if (e == null || e.rol != ClientMordiscos.Rol.VICTIMA) return;
        float t = ClientMordiscos.ahora(pt);
        float salida = e.fin >= 0 ? 1.0F - Mth.clamp((t - e.fin) / 6.0F, 0.0F, 1.0F) : 1.0F;
        float entrada = Mth.clamp((t - e.inicio) / 8.0F, 0.0F, 1.0F);
        float k = tiron(e, t);
        float a = (0.35F + 0.08F * Mth.sin(t * 0.25F) + 0.6F * k * k) * salida * entrada;
        if (a <= 0.01F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        g.setColor(0.16F, 0.32F, 0.08F, Math.min(1.0F, a));
        g.blit(VINETA, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        // encima, un velo casi negro que oscurece los bordes con cada mordisco
        if (k > 0.0F) {
            g.setColor(0.02F, 0.04F, 0.01F, Math.min(1.0F, 0.75F * k * salida));
            g.blit(VINETA, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
