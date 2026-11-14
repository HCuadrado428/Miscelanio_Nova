package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.registry.ModSounds;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;

/** Las dos partículas baratas de las armas de fuego: el fogonazo y el casquillo. */
public final class FuegoParticulas {
    private FuegoParticulas() {}

    /** Fogonazo: una estrella de fuego que brilla a oscuras (luz llena) y dura 2 ticks. */
    public static class Fogonazo extends TextureSheetParticle {
        protected Fogonazo(ClientLevel l, double x, double y, double z, SpriteSet s) {
            super(l, x, y, z);
            lifetime = 2;
            quadSize = 0.22F + random.nextFloat() * 0.14F;
            roll = random.nextFloat() * (float) (Math.PI * 2);
            oRoll = roll;
            hasPhysics = false;
            gravity = 0;
            xd = yd = zd = 0;
            pickSprite(s);
        }

        @Override
        public void tick() {
            super.tick();
            quadSize *= 0.7F;
            alpha = 1F - age / (float) Math.max(1, lifetime) * 0.5F;
        }

        @Override
        public int getLightColor(float pt) {
            return 0xF000F0;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Casquillo de latón: cae, rebota una vez (con su tintineo) y se queda quieto un momento. */
    public static class Casquillo extends TextureSheetParticle {
        private boolean rebote;

        protected Casquillo(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 40 + random.nextInt(10);
            quadSize = 0.055F;
            gravity = 0.9F;
            friction = 0.96F;
            hasPhysics = true;
            roll = random.nextFloat() * 6.28F;
            pickSprite(s);
        }

        @Override
        public void tick() {
            oRoll = roll;
            super.tick();
            if (!onGround) roll += 0.6F;
            if (onGround && !rebote) {
                rebote = true;
                yd = 0.1;
                xd *= 0.5;
                zd *= 0.5;
                level.playLocalSound(x, y, z, ModSounds.CASQUILLO_SUELO.get(), SoundSource.PLAYERS, 0.25F, 0.9F + random.nextFloat() * 0.3F, false);
            }
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    public record FogonazoProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Fogonazo(l, x, y, z, s);
        }
    }

    public record CasquilloProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Casquillo(l, x, y, z, dx, dy, dz, s);
        }
    }
}
