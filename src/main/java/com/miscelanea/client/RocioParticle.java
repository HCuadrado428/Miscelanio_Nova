package com.miscelanea.client;

import com.miscelanea.aspira.RocioOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

/** Chorro del lanzallamas de pociones: nube de color que sale disparada, frena, cae un poco y se abre. */
public class RocioParticle extends TextureSheetParticle {
    private final float tamano;
    private final float giro;

    protected RocioParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz,
                            SpriteSet sprites, int color) {
        super(level, x, y, z);
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.friction = 0.9F;
        this.gravity = 0.12F;
        this.hasPhysics = true;
        this.lifetime = 14 + this.random.nextInt(6);
        this.tamano = 0.18F + this.random.nextFloat() * 0.1F;
        this.quadSize = tamano;
        this.giro = (this.random.nextFloat() - 0.5F) * 0.5F;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2);
        this.oRoll = this.roll;
        float k = 0.85F + this.random.nextFloat() * 0.25F;
        this.setColor(Math.min(1, ((color >> 16) & 255) / 255.0F * k), Math.min(1, ((color >> 8) & 255) / 255.0F * k),
                Math.min(1, (color & 255) / 255.0F * k));
        this.alpha = 0.9F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += giro;
        float f = this.age / (float) this.lifetime;
        this.quadSize = tamano * (1.0F + f * 3.2F);
        this.alpha = 0.9F * (1.0F - f * f);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public int getLightColor(float partial) {
        return 0xF000F0 & 0xF0 | super.getLightColor(partial) & 0xF00000; // un poco luminoso
    }

    public static class Provider implements ParticleProvider<RocioOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(RocioOptions o, ClientLevel level, double x, double y, double z,
                                       double vx, double vy, double vz) {
            return new RocioParticle(level, x, y, z, vx, vy, vz, sprites, o.color());
        }
    }
}
