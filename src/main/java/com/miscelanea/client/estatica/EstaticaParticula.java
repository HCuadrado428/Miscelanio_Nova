package com.miscelanea.client.estatica;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Partículas de la estática. Gota: un píxel gris que cae despacio y se apaga. Chispa: píxeles de nieve que saltan de
 * sitio; si nace sin velocidad se queda quieta más tiempo (las siluetas que deja el parpadeo).
 */
public class EstaticaParticula extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final boolean gota, quieta;

    protected EstaticaParticula(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean gota) {
        super(l, x, y, z);
        this.sprites = sprites;
        this.gota = gota;
        this.quieta = !gota && vx == 0 && vy == 0 && vz == 0;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.hasPhysics = gota;
        if (gota) {
            this.gravity = 0.06F;
            this.friction = 0.96F;
            this.lifetime = 18 + random.nextInt(16);
            this.quadSize = 0.035F + random.nextFloat() * 0.03F;
        } else {
            this.gravity = 0F;
            this.friction = 0.7F;
            this.lifetime = quieta ? 22 + random.nextInt(14) : 6 + random.nextInt(10);
            this.quadSize = 0.04F + random.nextFloat() * (quieta ? 0.05F : 0.08F);
        }
        float g = 0.55F + random.nextFloat() * 0.45F;
        setColor(g, g, g * 1.02F);
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (gota) {
            alpha = Math.min(1F, (lifetime - age) / 8F);
            if (random.nextInt(4) == 0) pickSprite(sprites);
            return;
        }
        if (random.nextInt(quieta ? 4 : 3) == 0) {
            double s = quieta ? 0.05 : 0.12;
            setPos(x + (random.nextDouble() - 0.5) * s, y + (random.nextDouble() - 0.5) * s, z + (random.nextDouble() - 0.5) * s);
            xo = x;
            yo = y;
            zo = z;
        }
        if (random.nextInt(2) == 0) pickSprite(sprites);
        if (quieta) alpha = Math.min(1F, (lifetime - age) / 10F) * (random.nextInt(5) == 0 ? 0.3F : 1F);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float pt) {
        return 0xF000F0;
    }

    public static class Gota implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public Gota(SpriteSet s) {
            this.s = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new EstaticaParticula(l, x, y, z, vx, vy, vz, s, true);
        }
    }

    public static class Chispa implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet s;

        public Chispa(SpriteSet s) {
            this.s = s;
        }

        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
            return new EstaticaParticula(l, x, y, z, vx, vy, vz, s, false);
        }
    }
}
