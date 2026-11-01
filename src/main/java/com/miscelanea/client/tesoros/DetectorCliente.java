package com.miscelanea.client.tesoros;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import com.miscelanea.tesoros.DetectorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Pitidos del detector (cada vez más rápidos y agudos al acercarte) y medidor en pantalla. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class DetectorCliente {
    private static float fuerza, fuerzaVis, dist = 99, ang;
    private static long ultima;
    private static int espera, luz;

    private DetectorCliente() {}

    public static void senal(float f, float d, float a) {
        fuerza = f;
        dist = d;
        ang = a;
        ultima = System.currentTimeMillis();
    }

    private static boolean lleva(LocalPlayer p) {
        return p != null && (p.getMainHandItem().getItem() instanceof DetectorItem || p.getOffhandItem().getItem() instanceof DetectorItem);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (!lleva(p) || System.currentTimeMillis() - ultima > 1000) {
            fuerza = 0;
            fuerzaVis *= 0.8F;
            return;
        }
        fuerzaVis = Mth.lerp(0.3F, fuerzaVis, fuerza);
        if (luz > 0) luz--;
        if (fuerza <= 0.02F) return;
        int intervalo = dist < 1.6F ? 2 : (int) Mth.lerp(fuerza, 30, 3);
        if (--espera <= 0) {
            espera = intervalo;
            luz = 2;
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), ModSounds.DETECTOR_PITIDO.get(), SoundSource.PLAYERS,
                    0.35F + 0.4F * fuerza, 0.8F + fuerza * 0.9F, false);
        }
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (!lleva(mc.player) || mc.options.hideGui) return;
        int x0 = w / 2 + 96, y0 = h - 40;
        g.fill(x0 - 2, y0 - 14, x0 + 84, y0 + 16, 0xA0101418);
        g.fill(x0 - 2, y0 - 14, x0 + 84, y0 - 13, 0xFFE8B828);
        // 10 LEDs que se encienden con la señal (verde → amarillo → rojo)
        int n = Math.round(fuerzaVis * 10);
        for (int i = 0; i < 10; i++) {
            int col = i < 5 ? 0x40E060 : i < 8 ? 0xF0D040 : 0xFF4030;
            boolean on = i < n;
            g.fill(x0 + 2 + i * 8, y0 - 8, x0 + 8 + i * 8, y0 + 2, (on ? 0xFF000000 : 0x50000000) | col);
        }
        String txt;
        int c;
        if (fuerza <= 0.02F) {
            txt = I18n.get("hud.miscelanea.detector.nada");
            c = 0xFF909090;
        } else if (dist < 1.6F) {
            txt = I18n.get("hud.miscelanea.detector.aqui");
            c = (mc.player.tickCount / 4) % 2 == 0 ? 0xFFFFE060 : 0xFFFF8040;
        } else {
            txt = I18n.get("hud.miscelanea.detector.senal") + " " + flecha(ang);
            c = 0xFFE0E0E0;
        }
        g.drawString(mc.font, txt, x0 + 2, y0 + 5, c, false);
        if (luz > 0) g.fill(x0 + 78, y0 - 12, x0 + 82, y0 - 9, 0xFFFF3030);
    }

    private static String flecha(float a) {
        a = Mth.wrapDegrees(a);
        if (Math.abs(a) < 30) return "↑";
        if (Math.abs(a) > 150) return "↓";
        return a > 0 ? (a < 90 ? "↗" : "↘") : (a > -90 ? "↖" : "↙");
    }
}
