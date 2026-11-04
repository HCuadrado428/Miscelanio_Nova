package com.miscelanea.client.glitch;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Píxeles del glitch: un cuadradito que no se mueve pero salta de sitio y cambia de dibujo a tirones. */
public class GlitchParticula extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected GlitchParticula(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.6F;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = 5 + this.random.nextInt(10);
        this.quadSize = 0.05F + this.random.nextFloat() * 0.08F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (random.nextInt(3) == 0) {
            // salto: se teletransporta un par de píxeles
            double s = 0.12;
            setPos(x + (random.nextDouble() - 0.5) * s, y + (random.nextDouble() - 0.5) * s, z + (random.nextDouble() - 0.5) * s);
            xo = x;
            yo = y;
            zo = z;
        }
        if (random.nextInt(2) == 0) pickSprite(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    @Override
    public int getLightColor(float partial) {
        return 0xF000F0; // brilla en la oscuridad
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new GlitchParticula(level, x, y, z, vx, vy, vz, sprites);
        }
    }
}
