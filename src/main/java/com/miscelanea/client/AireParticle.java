package com.miscelanea.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * Bocanada de aire con la textura del humo de fogata.
 * Viaja en línea recta desde donde nace hasta (x + dx, y + dy, z + dz).
 * - Aspiración: acelera al acercarse a la boca y se encoge hasta desaparecer.
 * - Soplido: sale rápido, frena, crece y se desvanece.
 */
public class AireParticle extends TextureSheetParticle {
    private final double sx, sy, sz, dx, dy, dz;
    private final boolean aspirar;
    private final float tamano;
    private final float giro;

    protected AireParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                           SpriteSet sprites, boolean aspirar) {
        super(level, x, y, z);
        this.sx = x;
        this.sy = y;
        this.sz = z;
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
        this.aspirar = aspirar;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        this.lifetime = Mth.clamp((int) (dist * 2.2) + this.random.nextInt(4), 6, 22);
        this.tamano = 0.25F + this.random.nextFloat() * 0.2F;
        this.quadSize = aspirar ? tamano : tamano * 0.5F;
        this.giro = (this.random.nextFloat() - 0.5F) * 0.3F;
        this.roll = this.random.nextFloat() * (float) (Math.PI * 2.0);
        this.oRoll = this.roll;
        float gris = 0.82F + this.random.nextFloat() * 0.15F;
        this.setColor(gris, gris, gris);
        this.alpha = 0.0F;
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.oRoll = this.roll;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float f = this.age / (float) this.lifetime;
        // aspirar: acelera (ease-in); soplar: frena (ease-out)
        float e = aspirar ? f * f : 1.0F - (1.0F - f) * (1.0F - f);
        this.x = sx + dx * e;
        this.y = sy + dy * e;
        this.z = sz + dz * e;
        this.roll += giro;

        if (aspirar) {
            this.quadSize = tamano * (1.0F - f * 0.85F);
            this.alpha = Math.min(1.0F, f * 4.0F) * 0.65F;
        } else {
            this.quadSize = tamano * (0.5F + f * 1.3F);
            this.alpha = (1.0F - f) * 0.6F;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Aspiracion implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Aspiracion(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new AireParticle(level, x, y, z, dx, dy, dz, sprites, true);
        }
    }

    public static class Soplido implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Soplido(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new AireParticle(level, x, y, z, dx, dy, dz, sprites, false);
        }
    }
}
