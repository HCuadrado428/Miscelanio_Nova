package com.miscelanea.client.pociones;

import com.miscelanea.pociones.PocionesRegistro;
import com.miscelanea.pociones.ReventarPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.joml.Vector3f;

/**
 * Sed de sangre (berserk de Doom) en el cliente: la pantalla teñida de rojo que late con el corazón, y el reventón de
 * las víctimas (un chorro de sangre, "trozos" hechos con partículas de sus botines y un chof bien gordo).
 */
public final class SangreCliente {
    private SangreCliente() {}

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !p.hasEffect(PocionesRegistro.SED_DE_SANGRE.get())) return;
        float t = p.tickCount + pt;
        // latido doble (bum-bum) a ~75 por minuto, nunca más de 2 Hz y nunca a cero: sin destellos
        float fase = (t % 16F) / 16F;
        float latido = Math.max(0, Mth.sin(fase * Mth.PI * 2)) * 0.7F + Math.max(0, Mth.sin((fase - 0.22F) * Mth.PI * 2)) * 0.3F;
        // un velo rojo muy leve en toda la pantalla y una viñeta roja que se cierra un poco con cada latido; por
        // fuera, otra más oscura (bordes casi granate)
        float k = 0.16F + 0.14F * latido;
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, (Math.round(k * 0.6F * 255) << 24) | 0x8A0006);
        RenderSystem.disableBlend();
        Vineta.dibujar(g, w, h, 0x9A0408, 0.55F + 0.35F * latido, 1.02F - 0.05F * latido);
        Vineta.dibujar(g, w, h, 0x2A0002, 0.45F + 0.2F * latido, 1.22F);
    }

    public static void reventar(ReventarPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        double cy = p.y + p.alto * 0.5;
        DustParticleOptions sangre = new DustParticleOptions(new Vector3f(0.6F, 0.03F, 0.02F), 1.4F + p.ancho * 0.6F);
        DustParticleOptions oscura = new DustParticleOptions(new Vector3f(0.3F, 0.01F, 0.01F), 1.0F + p.ancho * 0.4F);
        int n = Math.round(50 + p.ancho * p.alto * 40);
        for (int i = 0; i < n; i++) {
            double vx = r.nextGaussian() * 0.45, vy = r.nextDouble() * 0.6, vz = r.nextGaussian() * 0.45;
            l.addParticle(i % 3 == 0 ? oscura : sangre, p.x + r.nextGaussian() * p.ancho * 0.3, cy + r.nextGaussian() * p.alto * 0.3, p.z + r.nextGaussian() * p.ancho * 0.3, vx, vy, vz);
        }
        // "trozos": las partículas de objeto de sus botines (y de carne cruda si no suelta nada)
        if (p.trozos.isEmpty()) p.trozos.add(new ItemStack(net.minecraft.world.item.Items.BEEF));
        for (ItemStack st : p.trozos) {
            if (st.isEmpty()) continue;
            ItemParticleOption trozo = new ItemParticleOption(ParticleTypes.ITEM, st);
            for (int i = 0; i < 7; i++)
                l.addParticle(trozo, p.x, cy, p.z, r.nextGaussian() * 0.35, 0.15 + r.nextDouble() * 0.45, r.nextGaussian() * 0.35);
        }
        for (int i = 0; i < 6; i++)
            l.addParticle(ParticleTypes.LARGE_SMOKE, p.x + r.nextGaussian() * 0.3, cy, p.z + r.nextGaussian() * 0.3, 0, 0.04, 0);
        l.playLocalSound(p.x, cy, p.z, PocionesRegistro.REVENTAR.get(), SoundSource.HOSTILE, 1.4F, 0.9F + r.nextFloat() * 0.2F, false);
    }
}
