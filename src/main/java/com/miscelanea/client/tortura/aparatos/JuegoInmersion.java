package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatosRed;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * AGUANTAR LA RESPIRACIÓN (bajo el agua, en la silla de inmersión; al estilo de la barra de la pesca de Stardew Valley):
 * la zona de calma se mueve sola y, según se acaba el aire, se estrecha y se agita; tu pulso (la marca) sube mientras
 * mantienes SALTAR y cae al soltar. Cada 10 ticks se manda al servidor cuántos has pasado fuera: dentro el aire baja
 * despacio y fuera deprisa (lo pone el servidor, con el aire de verdad).
 */
public class JuegoInmersion {
    final BlockPos pos;
    private final Random r;
    private float marca = 0.5F, marcaO = 0.5F, vel;
    private float zona = 0.5F, zonaO = 0.5F, zonaVel, zonaMeta = 0.5F;
    private int fuera, ticks;
    boolean dentro = true;
    private float rojo;

    JuegoInmersion(BlockPos pos, long semilla) {
        this.pos = pos;
        this.r = new Random(semilla);
    }

    /** Medio ancho de la zona (0..1 de la barra) según lo que falta de aire (0 lleno .. 1 vacío). */
    static float zonaMedia(float apuro) {
        return 0.21F - 0.11F * Mth.clamp(apuro, 0F, 1F);
    }

    private static float apuro(Minecraft mc) {
        return mc.player == null ? 0F : 1F - Mth.clamp(mc.player.getAirSupply() / (float) mc.player.getMaxAirSupply(), 0F, 1F);
    }

    void tick(Minecraft mc) {
        ticks++;
        marcaO = marca;
        zonaO = zona;
        float ap = apuro(mc);
        if (r.nextFloat() < 0.03F + ap * 0.07F) zonaMeta = 0.1F + r.nextFloat() * 0.8F;
        zonaVel += (zonaMeta - zona) * (0.008F + ap * 0.03F);
        zonaVel *= 0.86F;
        zona = Mth.clamp(zona + zonaVel, 0.06F, 0.94F);
        boolean sube = com.miscelanea.client.TeclasMod.accionPulsada();
        vel += sube ? 0.011F : -0.009F;
        vel *= 0.88F;
        marca += vel;
        if (marca < 0) {
            marca = 0;
            vel = Math.max(0, -vel * 0.3F);
        }
        if (marca > 1) {
            marca = 1;
            vel = Math.min(0, -vel * 0.3F);
        }
        boolean antes = dentro;
        dentro = Math.abs(marca - zona) <= zonaMedia(ap);
        if (!dentro) {
            fuera++;
            rojo = Math.min(1F, rojo + 0.15F);
            if (antes) mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.QTE_FALLO.get(), 1.4F, 0.2F));
        } else rojo = Math.max(0F, rojo - 0.08F);
        if (ticks % 10 == 0) {
            AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(AparatoAccionPacket.INFORME_INMERSION, pos, Math.min(10, fuera)));
            fuera = 0;
        }
    }

    void dibujar(Minecraft mc, GuiGraphics g, int w, int h, float pt) {
        int alto = 140, ancho = 22;
        int x = w / 2 + 70, y = (h - alto) / 2;
        float ap = apuro(mc);
        Dibujo.panel(g, x - 8, y - 8, x + ancho + 8, y + alto + 8, 0xFF3AA0C8);
        // columna de agua con burbujas que suben
        g.fillGradient(x, y, x + ancho, y + alto, 0xFF0E3A52, 0xFF041624);
        float t = ticks + pt;
        for (int i = 0; i < 9; i++) {
            float bx = x + 3 + (i * 37 % (ancho - 6)) + Mth.sin(t * 0.15F + i) * 1.5F;
            float by = y + alto - ((t * (0.8F + i % 3 * 0.35F) + i * 29) % alto);
            Dibujo.disco(g, bx, by, 1.2F + i % 2 * 0.6F, 0x70BFEFFF);
        }
        float media = zonaMedia(ap);
        float z = Mth.lerp(pt, zonaO, zona);
        int zy1 = y + alto - (int) ((z + media) * alto), zy2 = y + alto - (int) ((z - media) * alto);
        int cz = dentro ? 0xB070E0F0 : 0x9060A0B0;
        g.fillGradient(x + 2, Math.max(y, zy1), x + ancho - 2, Math.min(y + alto, zy2), cz, Dibujo.mezcla(cz, 0xFF000000, 0.35F));
        g.fill(x + 2, Math.max(y, zy1), x + ancho - 2, Math.max(y, zy1) + 1, 0xFFE0FFFF);
        // tu pulso: un corazón que late más deprisa cuanto menos aire
        float m = Mth.lerp(pt, marcaO, marca);
        float my = y + alto - m * alto, mx = x + ancho / 2F;
        float late = 1F + 0.18F * Math.max(0F, Mth.sin(t * (0.35F + ap * 0.6F)));
        int col = dentro ? 0xFFF0F8FF : 0xFFFF4A40;
        Dibujo.brillo(g, mx, my, 0.1F, 7, dentro ? 0x60FFFFFF : 0x90FF2020);
        Dibujo.disco(g, mx - 2.2F * late, my - 1.2F * late, 2.8F * late, col);
        Dibujo.disco(g, mx + 2.2F * late, my - 1.2F * late, 2.8F * late, col);
        Dibujo.triangulo(g, mx - 4.9F * late, my - 0.4F * late, mx + 4.9F * late, my - 0.4F * late, mx, my + 5F * late, col);
        // aire que queda (barrita al lado)
        int ax = x + ancho + 3;
        g.fill(ax, y, ax + 3, y + alto, 0xFF0A0A10);
        int lleno = (int) (alto * (1F - ap));
        g.fillGradient(ax, y + alto - lleno, ax + 3, y + alto, 0xFFB8F0FF, 0xFF3A8AC0);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.inmersion.titulo").withStyle(ChatFormatting.BOLD), x + ancho / 2F, y - 20,
                1F, 0xFFD8F0FF, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.inmersion.ayuda", com.miscelanea.client.TeclasMod.MJ_ACCION.getTranslatedKeyMessage()),
                x + ancho / 2F, y + alto + 12, 0.75F, 0xFFB0C8D0, true);
        if (rojo > 0.02F) g.fill(0, 0, w, h, ((int) (rojo * 70) << 24) | 0x600000);
        // se acaba el aire: se oscurece todo por los bordes
        if (ap > 0.6F) {
            int a = (int) ((ap - 0.6F) / 0.4F * 150);
            for (int i = 0; i < 8; i++) {
                int b = (int) (a * (1F - i / 8F));
                int e = 6 + i * 6;
                g.fill(0, 0, w, e, (b / 4) << 24);
                g.fill(0, h - e, w, h, (b / 4) << 24);
                g.fill(0, 0, e, h, (b / 4) << 24);
                g.fill(w - e, 0, w, h, (b / 4) << 24);
            }
        }
    }
}
