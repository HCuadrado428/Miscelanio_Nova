package com.miscelanea.client.pociones;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Humo de la sombra: una voluta negra violácea (6 dibujos, de mancha densa a cuatro píxeles sueltos) que sube despacio
 * meciéndose, crece un poco y se desvanece. Sin física: atraviesa lo que haga falta.
 */
public class SombraHumoParticula extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float fase, tam0;

    protected SombraHumoParticula(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.94F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.lifetime = 22 + this.random.nextInt(18);
        this.tam0 = 0.2F + this.random.nextFloat() * 0.06F;   // 16 texels = medio bloque: 2 por píxel, como el resto
        this.quadSize = tam0;
        this.fase = this.random.nextFloat() * ((float) (Math.PI * 2));
        this.roll = this.oRoll = this.random.nextFloat() * ((float) (Math.PI * 2));
        this.alpha = 0.9F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float k = age / (float) lifetime;
        // se mece y sube cada vez más despacio; crece y se deshace
        xd += Mth.sin(age * 0.35F + fase) * 0.0016;
        zd += Mth.cos(age * 0.29F + fase) * 0.0016;
        oRoll = roll;
        roll += 0.02F * Mth.sin(fase);
        quadSize = tam0 * (1F + 0.6F * k);
        alpha = 0.9F * (k < 0.6F ? 1F : 1F - (k - 0.6F) / 0.4F);
        setSpriteFromAge(sprites);
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
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new SombraHumoParticula(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
