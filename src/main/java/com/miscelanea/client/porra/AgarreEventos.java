package com.miscelanea.client.porra;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ForcejeoPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
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
 * Lado cliente del agarre:
 * - La víctima queda fija delante del agresor y su cámara se ve obligada a mirarle, temblando en cada descarga.
 * - Sus teclas de movimiento no la mueven: sirven para forcejear (cada pulsación nueva cuenta).
 * - El agresor nota un pequeño temblor en cada descarga.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class AgarreEventos {
    private static boolean prevIzq, prevDer, prevSalto, prevArriba, prevAbajo;
    private static int ultimoEnvio;

    private AgarreEventos() {}

    private static ClientAgarres.Estado estadoLocal() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : ClientAgarres.activo(p.getId());
    }

    /** Punto donde va la víctima: delante del agresor, un poco levantada (igual que en el servidor). */
    static Vec3 puntoVictima(Entity agresor, float pt, float progreso) {
        float yaw = Mth.lerp(pt, agresor.yRotO, agresor.getYRot());
        Vec3 frente = Vec3.directionFromRotation(0, yaw);
        Vec3 pos = new Vec3(Mth.lerp(pt, agresor.xo, agresor.getX()), Mth.lerp(pt, agresor.yo, agresor.getY()),
                Mth.lerp(pt, agresor.zo, agresor.getZ()));
        float k = Mth.clamp(progreso, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        // Igual que en el servidor: la acerca de 1,1 a 0,7 bloques y la levanta 0,2 en los primeros 5 ticks
        return pos.add(frente.scale(Mth.lerp(k, 1.1, 0.7))).add(0, 0.2 * k, 0);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ClientAgarres.limpiar();
            return;
        }
        LocalPlayer p = mc.player;
        ClientAgarres.Estado e = estadoLocal();
        if (p == null || e == null || e.rol != ClientAgarres.Rol.VICTIMA) return;
        Entity ag = mc.level.getEntity(e.otro);
        if (ag == null) return;

        // Fijar a la víctima delante del agresor y mirándole
        Vec3 punto = puntoVictima(ag, 1.0F, (ClientAgarres.ahora(0.0F) - e.inicio) / 5.0F);
        p.setPos(punto.x, punto.y, punto.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0.0F;
        Vec3 d = ag.getEyePosition().subtract(p.getEyePosition());
        float yaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        p.setYRot(yaw);
        p.setXRot(pitch);
        p.setYHeadRot(yaw);
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        ClientAgarres.Estado e = estadoLocal();
        Input in = event.getInput();
        if (e == null || e.rol != ClientAgarres.Rol.VICTIMA) {
            prevIzq = in.left;
            prevDer = in.right;
            prevSalto = in.jumping;
            prevArriba = in.up;
            prevAbajo = in.down;
            return;
        }
        // Cada tecla recién pulsada = un tirón para soltarse
        boolean nueva = (in.left && !prevIzq) || (in.right && !prevDer) || (in.jumping && !prevSalto)
                || (in.up && !prevArriba) || (in.down && !prevAbajo);
        prevIzq = in.left;
        prevDer = in.right;
        prevSalto = in.jumping;
        prevArriba = in.up;
        prevAbajo = in.down;
        LocalPlayer p = Minecraft.getInstance().player;
        if (nueva && p != null && p.tickCount - ultimoEnvio >= 2) {
            ultimoEnvio = p.tickCount;
            e.ultimoForcejeo = ClientAgarres.ahora(0.0F); // respuesta inmediata en la animación
            ModNetwork.CHANNEL.sendToServer(new ForcejeoPacket());
        }
        // No se puede mover
        in.forwardImpulse = 0.0F;
        in.leftImpulse = 0.0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        ClientAgarres.Estado e = estadoLocal();
        if (e == null) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = (float) event.getPartialTick();
        float t = ClientAgarres.ahora(pt);
        float ds = t - e.ultimaDescarga;
        float choque = ds < 10.0F ? 1.0F - ds / 10.0F : 0.0F;

        if (e.rol == ClientAgarres.Rol.VICTIMA) {
            Entity ag = mc.level != null ? mc.level.getEntity(e.otro) : null;
            if (ag != null) {
                // Obligada a mirar a la cara del agresor
                Vec3 cam = event.getCamera().getPosition();
                Vec3 ojo = ag.getEyePosition(pt);
                Vec3 d = ojo.subtract(cam);
                event.setYaw((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90.0F);
                event.setPitch((float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG));
            }
            boolean forcejea = t - e.ultimoForcejeo < 6.0F;
            if (e.ejecucion >= 0 && t - e.ejecucion >= 30.0F) choque = 1.0F;
            float amp = 0.6F + 7.0F * choque + (forcejea ? 2.0F : 0.0F);
            event.setYaw(event.getYaw() + Mth.sin(t * 31.0F) * amp * 0.5F);
            event.setPitch(event.getPitch() + Mth.sin(t * 27.0F + 1.0F) * amp * 0.4F);
            event.setRoll(event.getRoll() + Mth.sin(t * 23.0F + 2.0F) * amp);
        } else if (choque > 0.0F) {
            float amp = 1.5F * choque;
            event.setPitch(event.getPitch() + Mth.sin(t * 29.0F) * amp * 0.4F);
            event.setRoll(event.getRoll() + Mth.sin(t * 21.0F + 1.0F) * amp);
        }
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        ClientAgarres.Estado e = estadoLocal();
        if (e == null || e.rol != ClientAgarres.Rol.VICTIMA) return;
        float t = ClientAgarres.ahora((float) event.getPartialTick());
        float ds = t - e.ultimaDescarga;
        float choque = ds < 10.0F ? 1.0F - ds / 10.0F : 0.0F;
        event.setFOV(event.getFOV() * (0.88 - 0.06 * choque));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAgarres.limpiar();
    }

    /** Barra de forcejeo de la víctima: cuánto le queda al agarre, y las teclas para soltarse. */
    public static void renderForcejeo(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        ClientAgarres.Estado e = estadoLocal();
        if (e == null || e.rol != ClientAgarres.Rol.VICTIMA || e.ejecucion >= 0) return;
        float t = ClientAgarres.ahora(partialTick);
        int ancho = 100;
        int x = (w - ancho) / 2;
        int y = h - 72;
        float f = Mth.clamp(e.resistencia / 100.0F, 0.0F, 1.0F);
        g.fill(x - 1, y - 1, x + ancho + 1, y + 5, 0xC0000000);
        g.fill(x, y, x + Math.round(ancho * f), y + 4, 0xFF4FB3FF);

        // "¡SUÉLTATE!" latiendo y temblando encima de la barra
        float latido = 1.0F + 0.12F * Mth.abs(Mth.sin(t * 0.35F));
        boolean forcejea = t - e.ultimoForcejeo < 6.0F;
        float tiembla = forcejea ? 1.5F : 0.5F;
        net.minecraft.network.chat.Component txt = net.minecraft.network.chat.Component.translatable("hud.miscelanea.sueltate");
        int tw = Minecraft.getInstance().font.width(txt);
        g.pose().pushPose();
        g.pose().translate(w / 2.0F + Mth.sin(t * 7.0F) * tiembla, y - 16 + Mth.sin(t * 9.0F) * tiembla * 0.5F, 0);
        g.pose().scale(1.6F * latido, 1.6F * latido, 1.0F);
        g.drawString(Minecraft.getInstance().font, txt, -tw / 2, -4, forcejea ? 0xFFFFFFFF : 0xFFFF5050, true);
        g.pose().popPose();

        // Teclas que parpadean alternándose
        boolean izq = ((int) (t / 3.0F)) % 2 == 0;
        tecla(g, x - 16, y - 4, "A", izq);
        tecla(g, x + ancho + 4, y - 4, "D", !izq);
    }

    private static void tecla(GuiGraphics g, int x, int y, String letra, boolean activa) {
        g.fill(x, y, x + 12, y + 12, activa ? 0xFFFFFFFF : 0x80FFFFFF);
        g.fill(x + 1, y + 1, x + 11, y + 11, activa ? 0xFF303030 : 0x80303030);
        g.drawString(Minecraft.getInstance().font, letra, x + 3, y + 2, activa ? 0xFFFFFFFF : 0xFF909090, false);
    }
}
