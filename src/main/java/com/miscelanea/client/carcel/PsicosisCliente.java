package com.miscelanea.client.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Efectos de pantalla de la psicosis: tinte verdoso que late, bordes que se cierran y la cámara que se balancea. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PsicosisCliente {
    private PsicosisCliente() {}

    private static int nivel() {
        var p = Minecraft.getInstance().player;
        if (p == null) return -1;
        var e = p.getEffect(ModEffects.PSICOSIS.get());
        return e == null ? -1 : e.getAmplifier();
    }

    @SubscribeEvent
    public static void onCamara(ViewportEvent.ComputeCameraAngles e) {
        int n = nivel();
        if (n < 0) return;
        double t = (Minecraft.getInstance().player.tickCount + e.getPartialTick()) / 20.0;
        float fuerza = 1.5F + n * 2.0F;
        e.setRoll(e.getRoll() + (float) Math.sin(t * 0.9) * fuerza);
        e.setYaw(e.getYaw() + (float) Math.sin(t * 0.53 + 1) * fuerza * 0.4F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        int n = nivel();
        if (n < 0) return;
        double t = (Minecraft.getInstance().player.tickCount + e.getPartialTick()) / 20.0;
        e.setFOV(e.getFOV() + Math.sin(t * 1.7) * (2 + n * 2));
    }

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        int n = nivel();
        if (n < 0) return;
        var p = Minecraft.getInstance().player;
        float t = (p.tickCount + partial) / 20.0F;
        float latido = 0.5F + 0.5F * Mth.sin(t * 2.4F);
        int a = (int) ((0x18 + n * 0x14) * (0.6F + 0.4F * latido));
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, (a << 24) | 0x5F8A1E);
        // Bordes que se cierran
        int pasos = 10 + n * 4;
        for (int i = 0; i < pasos; i++) {
            int alpha = (int) (0x26 * (1 - i / (float) pasos) * (0.7F + 0.3F * latido));
            int m = i * Math.max(3, w / 120);
            int col = (alpha << 24) | 0x0E1405;
            g.fill(0, 0, w, m, col);
            g.fill(0, h - m, w, h, col);
            g.fill(0, 0, m, h, col);
            g.fill(w - m, 0, w, h, col);
        }
        RenderSystem.disableBlend();
    }
}
