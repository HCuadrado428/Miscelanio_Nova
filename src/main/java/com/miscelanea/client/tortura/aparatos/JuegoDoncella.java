package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.aparatos.AparatoAccionPacket;
import com.miscelanea.tortura.aparatos.AparatosRed;
import com.miscelanea.tortura.aparatos.DoncellaBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Random;

/**
 * APARTARSE DE LOS PINCHOS (dentro de la doncella; al estilo de la pesca de Stardew Valley): el hueco entre los pinchos
 * (zona segura) se mueve solo y se estrecha según se cierran las puertas; tu cuerpo (la marca) sube mientras mantienes
 * SALTAR y cae al soltar. Cada 10 ticks se manda al servidor cuántos has pasado fuera (el servidor pone el daño).
 */
public class JuegoDoncella {
    final BlockPos pos;
    private final Random r;
    private float marca = 0.5F, marcaO = 0.5F, vel;
    private float zona = 0.5F, zonaO = 0.5F, zonaVel, zonaMeta = 0.5F;
    private int fuera, ticks;
    boolean dentro = true;
    private float rojo;

    JuegoDoncella(BlockPos pos, long semilla) {
        this.pos = pos;
        this.r = new Random(semilla);
    }

    void tick(Minecraft mc, float cierre) {
        ticks++;
        marcaO = marca;
        zonaO = zona;
        if (r.nextFloat() < 0.03F + cierre * 0.06F) zonaMeta = 0.1F + r.nextFloat() * 0.8F;
        zonaVel += (zonaMeta - zona) * (0.008F + cierre * 0.028F);
        zonaVel *= 0.86F;
        zona = Mth.clamp(zona + zonaVel, 0.06F, 0.94F);
        boolean sube = com.miscelanea.client.TeclasMod.accionPulsada();
        vel += sube ? 0.0115F : -0.0095F;
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
        float media = DoncellaBlockEntity.zonaMedia(cierre);
        boolean antes = dentro;
        dentro = Math.abs(marca - zona) <= media;
        if (!dentro) {
            fuera++;
            rojo = Math.min(1F, rojo + 0.2F);
            if (antes) mc.getSoundManager().play(SimpleSoundInstance.forUI(TorturaRegistro.QTE_FALLO.get(), 1.6F, 0.25F));
        } else rojo = Math.max(0F, rojo - 0.08F);
        if (ticks % 10 == 0) {
            AparatosRed.CANAL.sendToServer(new AparatoAccionPacket(AparatoAccionPacket.INFORME_DONCELLA, pos, Math.min(10, fuera)));
            fuera = 0;
        }
    }

    void dibujar(Minecraft mc, GuiGraphics g, int w, int h, float pt, float cierre) {
        int alto = 150, ancho = 22;
        int x = w / 2 + 70, y = (h - alto) / 2;
        Dibujo.panelMetal(g, x - 8, y - 8, x + ancho + 8, y + alto + 8, 0xFF2A2624);
        // carril de hierro oxidado con pinchos a los lados
        g.fillGradient(x, y, x + ancho, y + alto, 0xFF3A2C24, 0xFF1E1714);
        float media = DoncellaBlockEntity.zonaMedia(cierre);
        float z = Mth.lerp(pt, zonaO, zona);
        int zy1 = y + alto - (int) ((z + media) * alto), zy2 = y + alto - (int) ((z - media) * alto);
        // pinchos fuera de la zona (dientes de sierra)
        for (int py = y; py < y + alto; py += 6) {
            if (py + 6 > zy1 && py < zy2) continue;
            Dibujo.triangulo(g, x, py, x + 8 + cierre * 3, py + 3, x, py + 6, 0xFF8C8A86);
            Dibujo.triangulo(g, x + ancho, py, x + ancho, py + 6, x + ancho - 8 - cierre * 3, py + 3, 0xFF6E6C68);
        }
        int cz = dentro ? 0xC0F0D8A0 : 0xA0C08050;
        g.fillGradient(x + 2, Math.max(y, zy1), x + ancho - 2, Math.min(y + alto, zy2), cz, Dibujo.mezcla(cz, 0xFF000000, 0.35F));
        g.fill(x + 2, Math.max(y, zy1), x + ancho - 2, Math.max(y, zy1) + 1, 0xFFFFF4D0);
        // la marca (tú): rombo
        float m = Mth.lerp(pt, marcaO, marca);
        float my = y + alto - m * alto, mx = x + ancho / 2F;
        int col = dentro ? 0xFFF8F0E8 : 0xFFFF3A30;
        Dibujo.brillo(g, mx, my, 0.1F, 7, dentro ? 0x60FFFFFF : 0x90FF2020);
        Dibujo.triangulo(g, mx, my - 6, mx + 5, my, mx - 5, my, col);
        Dibujo.triangulo(g, mx - 5, my, mx + 5, my, mx, my + 6, Dibujo.mezcla(col, 0xFF000000, 0.3F));
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.doncella.titulo").withStyle(ChatFormatting.BOLD), x + ancho / 2F, y - 20,
                1F, 0xFFE8D8C0, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.doncella.ayuda", com.miscelanea.client.TeclasMod.MJ_ACCION.getTranslatedKeyMessage()),
                x + ancho / 2F, y + alto + 12, 0.75F, 0xFFBFB8B0, true);
        if (rojo > 0.02F) g.fill(0, 0, w, h, ((int) (rojo * 90) << 24) | 0x900000);
    }
}
