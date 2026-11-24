package com.miscelanea.client.laboratorio;

import com.miscelanea.laboratorio.Experimentos;
import com.miscelanea.laboratorio.LabRegistro;
import com.miscelanea.laboratorio.Sueros;
import com.miscelanea.tortura.aparatos.Aparatos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El visor de constantes: con las gafas de seguridad puestas, un panel en la esquina enseña el pulso y el miedo de los
 * sujetos que haya a menos de 16 bloques (los que están en un aparato, asustados o con un suero encima). Las cifras se
 * actualizan cada medio segundo para que se puedan leer.
 */
public final class VisorConstantes {
    private VisorConstantes() {}

    private static final Map<Integer, int[]> MEDIDAS = new HashMap<>();
    private static long ultima = -100;

    private static boolean sujeto(LivingEntity e) {
        if (Aparatos.en(e) || Experimentos.miedo(e) > 0.1) return true;
        for (int i = 0; i < Sueros.TIPOS; i++) if (e.hasEffect(Sueros.efecto(i))) return true;
        return false;
    }

    public static void dibujar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!mc.player.getItemBySlot(EquipmentSlot.HEAD).is(LabRegistro.GAFAS.get())) return;
        List<LivingEntity> sujetos = mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(16), e -> e != mc.player && e.isAlive() && sujeto(e));
        sujetos.sort(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player)));
        if (sujetos.size() > 4) sujetos = sujetos.subList(0, 4);
        long t = mc.level.getGameTime();
        if (t - ultima >= 10) {
            ultima = t;
            MEDIDAS.clear();
            for (LivingEntity e : sujetos) MEDIDAS.put(e.getId(), new int[]{Experimentos.pulso(e), (int) Math.round(Experimentos.miedo(e) * 100)});
        }
        int x = 6, y = 6, ancho = 150, alto = 14 + Math.max(1, sujetos.size()) * 11;
        g.fill(x - 3, y - 3, x + ancho, y + alto, 0x99081410);
        g.fill(x - 3, y - 3, x + ancho, y - 2, 0xFF4CFF8A);
        g.drawString(mc.font, Component.translatable("laboratorio.miscelanea.visor.titulo"), x, y, 0xFF7DFFB0, false);
        int ty = y + 12;
        if (sujetos.isEmpty()) g.drawString(mc.font, Component.translatable("laboratorio.miscelanea.visor.nadie"), x, ty, 0xFF8FA39A, false);
        for (LivingEntity e : sujetos) {
            int[] m = MEDIDAS.getOrDefault(e.getId(), new int[]{0, 0});
            int col = m[1] >= 70 ? 0xFFFF6A5A : m[1] >= 30 ? 0xFFFFD36A : 0xFF9BFFB8;
            String nombre = e.getName().getString();
            if (nombre.length() > 9) nombre = nombre.substring(0, 9);
            g.drawString(mc.font, nombre + "  " + m[0] + " ppm  " + m[1] + " %", x, ty, col, false);
            ty += 11;
        }
    }
}
