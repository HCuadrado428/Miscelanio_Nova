package com.miscelanea.client.laboratorio;

import com.miscelanea.laboratorio.LabRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/**
 * El visor de constantes: con las gafas de seguridad puestas, un panel en la esquina enseña el pulso y el miedo de los
 * sujetos que haya a menos de 16 bloques (los que están en un aparato, asustados o con un suero encima). Las cifras se
 * actualizan cada medio segundo para que se puedan leer.
 */
public final class VisorConstantes {
    private VisorConstantes() {}

    /** Lo último que ha medido el servidor: {id, pulso, miedo %} de cada sujeto. */
    private static List<int[]> medidas = List.of();
    private static long recibido;

    /** Llega del servidor cada medio segundo mientras se llevan las gafas. */
    public static void recibir(List<int[]> l) {
        medidas = List.copyOf(l);
        recibido = net.minecraft.Util.getMillis();
    }

    public static void dibujar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.screen != null) return;
        if (!mc.player.getItemBySlot(EquipmentSlot.HEAD).is(LabRegistro.GAFAS.get())) return;
        // si el servidor deja de mandar (se acaba de poner las gafas, lag...), no se enseñan cifras viejas
        List<int[]> datos = net.minecraft.Util.getMillis() - recibido > 2000 ? List.of() : medidas;
        List<LivingEntity> sujetos = new java.util.ArrayList<>();
        List<int[]> filas = new java.util.ArrayList<>();
        for (int[] m : datos)
            if (mc.level.getEntity(m[0]) instanceof LivingEntity e) {
                sujetos.add(e);
                filas.add(m);
            }
        int x = 6, y = 6, ancho = 150, alto = 14 + Math.max(1, sujetos.size()) * 11;
        g.fill(x - 3, y - 3, x + ancho, y + alto, 0x99081410);
        g.fill(x - 3, y - 3, x + ancho, y - 2, 0xFF4CFF8A);
        g.drawString(mc.font, Component.translatable("laboratorio.miscelanea.visor.titulo"), x, y, 0xFF7DFFB0, false);
        int ty = y + 12;
        if (sujetos.isEmpty()) g.drawString(mc.font, Component.translatable("laboratorio.miscelanea.visor.nadie"), x, ty, 0xFF8FA39A, false);
        for (int i = 0; i < sujetos.size(); i++) {
            LivingEntity e = sujetos.get(i);
            int[] m = {filas.get(i)[1], filas.get(i)[2]};
            int col = m[1] >= 70 ? 0xFFFF6A5A : m[1] >= 30 ? 0xFFFFD36A : 0xFF9BFFB8;
            String nombre = e.getName().getString();
            if (nombre.length() > 9) nombre = nombre.substring(0, 9);
            g.drawString(mc.font, nombre + "  " + m[0] + " ppm  " + m[1] + " %", x, ty, col, false);
            ty += 11;
        }
    }
}
