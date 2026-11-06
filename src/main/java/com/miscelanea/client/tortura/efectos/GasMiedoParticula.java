package com.miscelanea.client.tortura.efectos;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Gas del miedo: una voluta de humo como el de vanilla (8 dibujos que van de nube a punto) teñida de verde enfermizo,
 * que se arrastra pegada al suelo, crece y se deshace. Sin física para que no se quede atascada.
 */
public class GasMiedoParticula extends TextureSheetParticle {
    /** Verdes del gas: oliva, verde ácido y casi amarillo. */
    private static final float[][] TONOS = {{0.31F, 0.54F, 0.18F}, {0.42F, 0.68F, 0.22F}, {0.55F, 0.78F, 0.30F}, {0.66F, 0.80F, 0.34F}};
    private final SpriteSet sprites;
    private final float tam0, fase;

    protected GasMiedoParticula(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(l, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.92F;
        this.gravity = 0F;
        this.hasPhysics = false;
        this.lifetime = 40 + random.nextInt(40);
        this.tam0 = 0.32F + random.nextFloat() * 0.2F;
        this.quadSize = tam0;
        this.fase = random.nextFloat() * Mth.TWO_PI;
        this.roll = this.oRoll = random.nextInt(4) * Mth.HALF_PI;     // giros de 90°: el pixel art no se tuerce
        float[] c = TONOS[random.nextInt(TONOS.length)];
        float k = 0.9F + random.nextFloat() * 0.2F;
        setColor(c[0] * k, c[1] * k, c[2] * k);
        this.alpha = 0F;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float k = age / (float) lifetime;
        xd += Mth.sin(age * 0.2F + fase) * 0.0012;
        zd += Mth.cos(age * 0.17F + fase) * 0.0012;
        yd += 0.0006;
        quadSize = tam0 * (1F + 0.9F * k);
        alpha = 0.75F * Math.min(1F, age / 6F) * (k < 0.55F ? 1F : 1F - (k - 0.55F) / 0.45F);
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
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new GasMiedoParticula(l, x, y, z, vx, vy, vz, sprites);
        }
    }
}
