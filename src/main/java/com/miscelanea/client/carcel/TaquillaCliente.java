package com.miscelanea.client.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.Escondites;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.network.SalirEsconditePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Lado cliente de la taquilla: te quedas quieto, la vista solo gira un poco a los lados
 * y la pantalla es la puerta por dentro con las rendijas. Saltar o agacharse = salir.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TaquillaCliente {
    private static boolean dentro;
    private static float yawCentro;
    private static int ticksDentro;
    private static boolean pedido;

    private TaquillaCliente() {}

    public static boolean dentro() {
        return dentro;
    }

    public static void cambiar(boolean d, float yaw) {
        var p = Minecraft.getInstance().player;
        dentro = d;
        yawCentro = yaw;
        ticksDentro = 0;
        pedido = false;
        if (p == null) return;
        if (d) {
            Escondites.OCULTOS.add(p.getUUID());
            p.setYRot(yaw);
            p.yRotO = yaw;
            p.setXRot(0);
        } else {
            Escondites.OCULTOS.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent e) {
        if (!dentro) return;
        var in = e.getInput();
        boolean salir = (in.jumping || in.shiftKeyDown) && ticksDentro > 10;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        if (salir && !pedido) {
            pedido = true;
            ModNetwork.CHANNEL.sendToServer(new SalirEsconditePacket());
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !dentro) return;
        ticksDentro++;
        var p = Minecraft.getInstance().player;
        if (p == null) {
            dentro = false;
            return;
        }
        // Mirar solo por las rendijas: ±35° a los lados, ±20° arriba/abajo
        float d = Mth.wrapDegrees(p.getYRot() - yawCentro);
        float lim = Mth.clamp(d, -35, 35);
        if (lim != d) {
            p.setYRot(yawCentro + lim);
        }
        p.setXRot(Mth.clamp(p.getXRot(), -20, 20));
        p.yHeadRot = p.getYRot();
    }

    /** La puerta vista desde dentro: todo oscuro salvo tres rendijas horizontales. */
    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (!dentro) return;
        float entrada = Mth.clamp((ticksDentro + partial) / 6.0F, 0, 1);
        int oscuro = ((int) (entrada * 0xF2) << 24) | 0x0C0D0E;
        int rendijaAlto = Math.max(4, h / 40);
        int separacion = rendijaAlto * 3;
        int centroY = h / 2 - h / 14;
        int ancho = (int) (w * 0.52F);
        int x0 = (w - ancho) / 2;
        int[] ys = {centroY - separacion, centroY, centroY + separacion};

        RenderSystem.enableBlend();
        // Fondo oscuro, dejando huecos
        int arriba = ys[0];
        g.fill(0, 0, w, arriba, oscuro);
        for (int i = 0; i < ys.length; i++) {
            int y = ys[i];
            g.fill(0, y, x0, y + rendijaAlto, oscuro);
            g.fill(x0 + ancho, y, w, y + rendijaAlto, oscuro);
            int sig = i + 1 < ys.length ? ys[i + 1] : h;
            g.fill(0, y + rendijaAlto, w, sig, oscuro);
        }
        // Bisel metálico de cada rendija (luz arriba, sombra abajo)
        int bisel = ((int) (entrada * 0x90) << 24) | 0x4A4F55;
        int sombra = ((int) (entrada * 0xC0) << 24) | 0x000000;
        for (int y : ys) {
            g.fill(x0, y - 2, x0 + ancho, y, bisel);
            g.fill(x0, y + rendijaAlto, x0 + ancho, y + rendijaAlto + 2, sombra);
        }
        RenderSystem.disableBlend();
    }
}
