package com.miscelanea.client.tortura.efectos;

import com.miscelanea.tortura.efectos.MiedoEffect;
import com.miscelanea.tortura.efectos.SustoPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.joml.Vector3f;

/** Golpes de efecto del servidor: el estallido del collar (metralla, sangre, fogonazo) y la nube de gas de la granada. */
public final class EfectosCliente {
    private EfectosCliente() {}

    private static long destelloHasta, destelloDesde;
    private static float destelloFuerza;

    public static void susto(SustoPacket p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        if (p.tipo == SustoPacket.ESTALLIDO) {
            DustParticleOptions sangre = new DustParticleOptions(new Vector3f(0.55F, 0.03F, 0.02F), 1.6F);
            DustParticleOptions oscura = new DustParticleOptions(new Vector3f(0.3F, 0.01F, 0.01F), 1.1F);
            for (int i = 0; i < 46; i++) {
                double vx = r.nextGaussian() * 0.35, vy = r.nextDouble() * 0.45, vz = r.nextGaussian() * 0.35;
                l.addParticle(i % 3 == 0 ? oscura : sangre, p.x, p.y, p.z, vx, vy, vz);
            }
            for (int i = 0; i < 24; i++)
                l.addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, r.nextGaussian() * 0.8, r.nextDouble() * 0.6, r.nextGaussian() * 0.8);
            for (int i = 0; i < 14; i++)
                l.addParticle(ParticleTypes.LARGE_SMOKE, p.x + r.nextGaussian() * 0.3, p.y, p.z + r.nextGaussian() * 0.3, 0, 0.06, 0);
            l.addParticle(ParticleTypes.FLASH, p.x, p.y, p.z, 0, 0, 0);
            if (mc.player != null) {
                double d = mc.player.getEyePosition().distanceTo(new net.minecraft.world.phys.Vec3(p.x, p.y, p.z));
                if (d < 14) {
                    // un solo fogonazo blanco que se apaga (nada de parpadeos)
                    destelloFuerza = (float) (1 - d / 14) * 0.8F;
                    destelloDesde = net.minecraft.Util.getMillis();
                    destelloHasta = destelloDesde + 900;
                }
            }
        } else if (p.tipo == SustoPacket.GAS) {
            float cr = ((MiedoEffect.COLOR >> 16) & 255) / 255F, cg = ((MiedoEffect.COLOR >> 8) & 255) / 255F, cb = (MiedoEffect.COLOR & 255) / 255F;
            for (int i = 0; i < 90; i++) {
                float k = 0.7F + r.nextFloat() * 0.5F;
                DustParticleOptions gas = new DustParticleOptions(new Vector3f(cr * k, cg * k, cb * k * 0.8F), 2.5F + r.nextFloat() * 1.5F);
                double a = r.nextDouble() * Math.PI * 2, v = 0.05 + r.nextDouble() * 0.22;
                l.addParticle(gas, p.x, p.y + 0.2, p.z, Math.cos(a) * v, 0.02 + r.nextDouble() * 0.08, Math.sin(a) * v);
            }
            for (int i = 0; i < 20; i++)
                l.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x + r.nextGaussian() * 0.6, p.y + 0.2, p.z + r.nextGaussian() * 0.6, 0, 0.015, 0);
        }
    }

    /** El fogonazo del estallido (se dibuja desde el HUD del collar). */
    public static void destello(GuiGraphics g, int w, int h, float pt) {
        long ahora = net.minecraft.Util.getMillis();
        if (ahora >= destelloHasta) return;
        float k = 1F - (ahora - destelloDesde) / (float) (destelloHasta - destelloDesde);
        int a = (int) (Mth.clamp(k * k * destelloFuerza, 0F, 1F) * 255);
        RenderSystem.enableBlend();
        g.fill(0, 0, w, h, (a << 24) | 0xFFF4E0);
    }
}
