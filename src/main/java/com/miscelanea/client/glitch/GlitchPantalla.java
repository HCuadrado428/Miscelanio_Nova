package com.miscelanea.client.glitch;

import com.miscelanea.glitch.GlitchRegistro;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Con el efecto «Glitch», la pantalla se rompe un poco: franjas horizontales desplazadas, bloques de color sueltos y
 * alguna línea de interferencia, que cambian a tirones (unas 12 veces por segundo) y se apagan al acabarse el efecto.
 * Solo rectángulos de color: no cuesta nada.
 */
public final class GlitchPantalla {
    private GlitchPantalla() {}

    private static final int[] COLORES = {0xFF00FF, 0x00FFFF, 0x8A2BE2, 0x000000, 0xFFFFFF};
    private static final RandomSource R = RandomSource.create();

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        MobEffectInstance ef = mc.player.getEffect(GlitchRegistro.EFECTO.get());
        if (ef == null) return;
        float k = Math.min(1.0F, ef.getDuration() / 40.0F); // se apaga en los 2 últimos segundos
        long cuadro = Util.getMillis() / 80;
        R.setSeed(cuadro * 31L);
        // a ratos, nada (el glitch va a golpes)
        if (R.nextFloat() > 0.35F + 0.5F * k) return;
        int franjas = 2 + R.nextInt(3 + (int) (6 * k));
        for (int i = 0; i < franjas; i++) {
            int y = R.nextInt(h), alto = 1 + R.nextInt(Math.max(2, h / 18));
            int dx = (R.nextInt(41) - 20) * (R.nextBoolean() ? 1 : 2);
            int c = COLORES[R.nextInt(COLORES.length)];
            int a = (int) ((40 + R.nextInt(90)) * k);
            g.fill(Math.max(0, dx), y, Math.min(w, w + dx), y + alto, a << 24 | c);
        }
        int bloques = R.nextInt(2 + (int) (5 * k));
        for (int i = 0; i < bloques; i++) {
            int bw = 6 + R.nextInt(40), bh = 3 + R.nextInt(16);
            int x = R.nextInt(Math.max(1, w - bw)), y = R.nextInt(Math.max(1, h - bh));
            int a = (int) ((90 + R.nextInt(120)) * k);
            g.fill(x, y, x + bw, y + bh, a << 24 | COLORES[R.nextInt(COLORES.length)]);
        }
        // interferencia: una línea fina de un lado a otro
        if (R.nextInt(3) == 0) {
            int y = R.nextInt(h);
            g.fill(0, y, w, y + 1, (int) (160 * k) << 24 | 0xFFFFFF);
        }
    }
}
