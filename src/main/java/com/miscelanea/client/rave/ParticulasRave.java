package com.miscelanea.client.rave;

import com.miscelanea.rave.ConfetiOpciones;
import com.miscelanea.rave.HumoOpciones;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/** Partículas de la rave: humo de color que se queda flotando, chispas frías y confeti que cae dando vueltas. */
public final class ParticulasRave {
    private ParticulasRave() {}

    /** Humo: sale empujado, frena, se extiende a ras del suelo y se queda un rato; brilla un poco con su color. */
    public static class Humo extends TextureSheetParticle {
        private final SpriteSet sprites;
        private final float tam;

        Humo(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, int color, SpriteSet s) {
            super(l, x, y, z);
            sprites = s;
            xd = vx;
            yd = vy;
            zd = vz;
            friction = 0.94F;
            gravity = -0.002F;
            hasPhysics = true;
            lifetime = 90 + random.nextInt(70);
            tam = 0.45F + random.nextFloat() * 0.35F;
            quadSize = tam;
            float k = 0.8F + random.nextFloat() * 0.2F;
            setColor(((color >> 16) & 255) / 255F * k, ((color >> 8) & 255) / 255F * k, (color & 255) / 255F * k);
            alpha = 0;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float f = age / (float) lifetime;
            quadSize = tam * (1 + f * 3.5F);
            alpha = f < 0.1F ? f * 5.5F : 0.55F * (1 - (f - 0.1F) / 0.9F);
            oRoll = roll;
            roll += 0.01F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public int getLightColor(float pt) {
            int l = super.getLightColor(pt);
            return Math.max(l & 0xFFFF, 0x90) | (l & 0xFFFF0000);
        }

        public record Provider(SpriteSet s) implements ParticleProvider<HumoOpciones> {
            @Override
            public Particle createParticle(HumoOpciones o, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                return new Humo(l, x, y, z, vx, vy, vz, o.color(), s);
            }
        }
    }

    /** Chispa fría: sube, cae con gravedad y se apaga; siempre brilla. */
    public static class Chispa extends TextureSheetParticle {
        private final SpriteSet sprites;

        Chispa(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            sprites = s;
            xd = vx;
            yd = vy;
            zd = vz;
            gravity = 0.8F;
            friction = 0.97F;
            hasPhysics = true;
            lifetime = 16 + random.nextInt(14);
            quadSize = 0.06F + random.nextFloat() * 0.05F;
            setColor(1F, 0.92F - random.nextFloat() * 0.25F, 0.6F - random.nextFloat() * 0.3F);
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            float f = age / (float) lifetime;
            alpha = 1 - f * f;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        @Override
        public int getLightColor(float pt) {
            return 0xF000F0;
        }

        public record Provider(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType o, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                return new Chispa(l, x, y, z, vx, vy, vz, s);
            }
        }
    }

    /** Confeti: papelito de color que sale disparado, frena con el aire y cae despacio dando vueltas. */
    public static class Confeti extends TextureSheetParticle {
        private final float giro;

        Confeti(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, int color, SpriteSet s) {
            super(l, x, y, z);
            xd = vx;
            yd = vy;
            zd = vz;
            gravity = 0.07F;
            friction = 0.9F;
            hasPhysics = true;
            lifetime = 100 + random.nextInt(80);
            quadSize = 0.05F + random.nextFloat() * 0.03F;
            setColor(((color >> 16) & 255) / 255F, ((color >> 8) & 255) / 255F, (color & 255) / 255F);
            giro = (random.nextFloat() - 0.5F) * 0.5F;
            roll = random.nextFloat() * Mth.TWO_PI;
            oRoll = roll;
            pickSprite(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            oRoll = roll;
            if (!onGround) {
                roll += giro;
                // vaivén al caer
                xd += Mth.sin(age * 0.3F + giro * 20) * 0.003;
                zd += Mth.cos(age * 0.27F + giro * 20) * 0.003;
            }
            if (lifetime - age < 20) alpha = (lifetime - age) / 20F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }

        public record Provider(SpriteSet s) implements ParticleProvider<ConfetiOpciones> {
            @Override
            public Particle createParticle(ConfetiOpciones o, ClientLevel l, double x, double y, double z, double vx, double vy, double vz) {
                return new Confeti(l, x, y, z, vx, vy, vz, o.color(), s);
            }
        }
    }
}
