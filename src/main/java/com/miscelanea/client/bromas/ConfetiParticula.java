package com.miscelanea.client.bromas;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Confeti: papelitos de colores vivos que salen disparados, frenan enseguida y caen despacio dando vueltas y
 * balanceándose. Cada uno con un color de la paleta y una de las formas (cuadrado, tira, estrellita).
 */
public class ConfetiParticula extends TextureSheetParticle {
    private static final int[] PALETA = {0xFF3B5C, 0xFFC93B, 0x3BD16F, 0x3BA7FF, 0xB65CFF, 0xFF7AD9, 0xFF8A2B, 0x5CFFF1};
    private final float giro, fase, vaiven;

    protected ConfetiParticula(ClientLevel l, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites) {
        super(l, x, y, z);
        this.xd = xd;
        this.yd = yd + 0.08 + random.nextDouble() * 0.12;
        this.zd = zd;
        int c = PALETA[random.nextInt(PALETA.length)];
        float brillo = 0.85F + random.nextFloat() * 0.15F;
        setColor(((c >> 16) & 0xFF) / 255F * brillo, ((c >> 8) & 0xFF) / 255F * brillo, (c & 0xFF) / 255F * brillo);
        this.quadSize = 0.045F + random.nextFloat() * 0.04F;
        this.lifetime = 50 + random.nextInt(40);
        this.gravity = 0.035F;
        this.friction = 0.9F;
        this.hasPhysics = true;
        this.giro = (random.nextFloat() - 0.5F) * 0.6F;
        this.fase = random.nextFloat() * Mth.TWO_PI;
        this.vaiven = 0.012F + random.nextFloat() * 0.015F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        if (!onGround) {
            roll += giro;
            // vaivén de papel que cae
            float t = age * 0.25F + fase;
            xd += Mth.cos(t) * vaiven * 0.3F;
            zd += Mth.sin(t) * vaiven * 0.3F;
            if (yd < -0.06) yd = -0.06;
        } else {
            xd *= 0.5;
            zd *= 0.5;
        }
        if (age > lifetime - 10) alpha = (lifetime - age) / 10F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double xd, double yd, double zd) {
            return new ConfetiParticula(l, x, y, z, xd, yd, zd, sprites);
        }
    }
}
