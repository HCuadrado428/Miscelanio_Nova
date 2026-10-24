package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * HUD del tira y afloja al capturar (sin texto): barra de resistencia del bicho y una flecha grande
 * hacia donde tienes que girar. El bicho tira de tu cámara hacia su lado.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CapturaHud {
    private static boolean activa;
    private static float resistencia, mostrada;
    private static int dir;
    private static boolean tirando;
    private static int ultimo;
    /** Destello verde al capturar (ticks restantes). */
    private static int exito;

    private CapturaHud() {}

    public static void recibir(int mob, float r, int d, boolean t) {
        if (r < 0) {
            if (activa && t) exito = 12;
            activa = false;
            return;
        }
        if (!activa) mostrada = r;
        activa = true;
        resistencia = r;
        dir = d;
        tirando = t;
        ultimo = 0;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (exito > 0) exito--;
        if (!activa) return;
        if (++ultimo > 10) { // el servidor dejó de mandar: se acabó
            activa = false;
            return;
        }
        mostrada += (resistencia - mostrada) * 0.35F;
        // El bicho arrastra la cámara hacia su lado si no le ganas
        var p = Minecraft.getInstance().player;
        if (p != null && !tirando) {
            p.setYRot(p.getYRot() + dir * 0.6F);
        }
    }

    /** Pequeño temblor mientras forcejea. */
    @SubscribeEvent
    public static void onCamara(ViewportEvent.ComputeCameraAngles e) {
        if (!activa) return;
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        float t = (float) (p.tickCount + e.getPartialTick());
        float f = tirando ? 0.35F : 0.8F;
        e.setRoll(e.getRoll() + Mth.sin(t * 1.9F) * f * dir);
        e.setPitch(e.getPitch() + Mth.sin(t * 2.7F) * f * 0.4F);
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        if (exito > 0) {
            int a = (int) (exito / 12.0F * 0x90);
            int r = (int) ((12 - exito) * 5.0F) + 10;
            anillo(g, w / 2, h / 2, r, (a << 24) | 0x7CFFA0);
        }
        if (!activa) return;
        var p = Minecraft.getInstance().player;
        float t = p == null ? 0 : p.tickCount + partial;
        int cx = w / 2, cy = h / 2 + 34;
        RenderSystem.enableBlend();

        // Barra de resistencia (morado → se vacía al ganar)
        int bw = 100, bh = 6, bx = cx - bw / 2;
        g.fill(bx - 2, cy - 2, bx + bw + 2, cy + bh + 2, 0xB0101014);
        g.fill(bx - 1, cy - 1, bx + bw + 1, cy + bh + 1, 0xFF3A2E44);
        int lleno = Math.round(bw * Mth.clamp(mostrada, 0, 1));
        int col = tirando ? 0xFFB070FF : 0xFF8A50D0;
        g.fill(bx, cy, bx + lleno, cy + bh, col);
        g.fill(bx, cy, bx + lleno, cy + 2, 0x50FFFFFF);
        for (int k = 1; k < 5; k++) g.fill(bx + bw * k / 5, cy, bx + bw * k / 5 + 1, cy + bh, 0x40000000);

        // Flecha hacia donde hay que girar (contraria al tirón del bicho)
        int lado = -dir;
        float pulso = tirando ? 1.0F : 0.6F + 0.4F * Mth.sin(t * 0.6F);
        int colFlecha = tirando ? 0x7CFF7C : 0xFFD24A;
        int a = (int) (pulso * 255) << 24;
        int ax = cx + lado * (bw / 2 + 18), ay = cy + bh / 2;
        int empuje = tirando ? (int) (Mth.sin(t * 0.9F) * 3) : 0;
        flecha(g, ax + lado * empuje, ay, lado, a | colFlecha);
        flecha(g, ax + lado * (empuje + 9), ay, lado, ((int) (pulso * 150) << 24) | colFlecha);
        RenderSystem.disableBlend();
    }

    /** Flecha horizontal (punta en x, apuntando hacia "lado"). */
    private static void flecha(GuiGraphics g, int x, int y, int lado, int col) {
        for (int i = 0; i < 7; i++) {
            int xx = x - lado * i;
            g.fill(xx, y - i - 1, xx + 1, y + i + 1, col);
        }
        int a = x - lado * 7, b = x - lado * 12;
        g.fill(Math.min(a, b), y - 2, Math.max(a, b), y + 2, col);
    }

    private static void anillo(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int k = 0; k < 64; k++) {
            double a = k / 64.0 * Math.PI * 2;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x - 1, y - 1, x + 1, y + 1, col);
        }
    }
}
