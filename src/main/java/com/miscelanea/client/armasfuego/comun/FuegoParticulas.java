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

/** Las partículas de las armas de fuego: fogonazo, casquillo, humo de pólvora, chispa, marca de bala y la bala que se ve pasar. */
public final class FuegoParticulas {
    private FuegoParticulas() {}

    /**
     * Fogonazo pixelado: cuatro fotogramas (estrella, llamarada, rescoldo y voluta) que pasan según la edad, sin girar
     * (los píxeles se quedan rectos, como en vanilla). Brilla a oscuras. El tamaño de cada arma llega en la velocidad x
     * (0 = el normal): la Derringer echa uno pequeño y el cañón de mano uno enorme.
     */
    public static class Fogonazo extends TextureSheetParticle {
        private final SpriteSet sprites;

        protected Fogonazo(ClientLevel l, double x, double y, double z, double escala, SpriteSet s) {
            super(l, x, y, z);
            sprites = s;
            lifetime = 4;
            float e = escala > 0.05 ? (float) escala : 1F;
            quadSize = (0.2F + random.nextFloat() * 0.06F) * e;
            hasPhysics = false;
            gravity = 0;
            xd = yd = zd = 0;
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (!removed) setSpriteFromAge(sprites);
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

    /** Casquillo de latón: sale girando, rebota dos veces (con su tintineo), se queda tumbado y se encoge al final. */
    public static class Casquillo extends TextureSheetParticle {
        private int rebotes;

        protected Casquillo(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 60 + random.nextInt(20);
            quadSize = 0.06F;
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
            else if (rebotes < 2) {
                rebotes++;
                yd = rebotes == 1 ? 0.12 : 0.05;
                xd *= 0.5;
                zd *= 0.5;
                level.playLocalSound(x, y, z, ModSounds.CASQUILLO_SUELO.get(), SoundSource.PLAYERS, rebotes == 1 ? 0.25F : 0.12F,
                        0.9F + random.nextFloat() * 0.3F, false);
            } else {
                // tumbado: deja de girar y de resbalar
                roll = Math.round(roll / 1.5708F) * 1.5708F;
                xd = zd = 0;
            }
            if (lifetime - age < 8) quadSize *= 0.85F;
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
        }
    }

    /** Humo de pólvora: una bocanada que sale con el tiro, frena, sube despacio, crece y se deshace en cuatro fotogramas. */
    public static class Humo extends TextureSheetParticle {
        private final SpriteSet sprites;

        protected Humo(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            sprites = s;
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 28 + random.nextInt(16);
            quadSize = 0.07F + random.nextFloat() * 0.03F;
            gravity = -0.012F;
            friction = 0.88F;
            hasPhysics = false;
            float g = 0.85F + random.nextFloat() * 0.15F;
            setColor(g, g, g);
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            quadSize = Math.min(0.24F, quadSize * 1.035F);
            alpha = Math.min(1F, (lifetime - age) / 10F);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /** Chispa: salta del metal o la piedra, cae, rebota una vez y se apaga (brilla a oscuras). */
    public static class Chispa extends TextureSheetParticle {
        private final SpriteSet sprites;

        protected Chispa(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            sprites = s;
            xd = vx;
            yd = vy;
            zd = vz;
            lifetime = 6 + random.nextInt(6);
            quadSize = 0.05F;
            gravity = 0.8F;
            friction = 0.9F;
            hasPhysics = true;
            setSpriteFromAge(s);
        }

        @Override
        public void tick() {
            super.tick();
            if (removed) return;
            setSpriteFromAge(sprites);
            if (onGround) {
                yd = 0.04;
                xd *= 0.4;
                zd *= 0.4;
            }
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

    /**
     * Marca de bala: un agujero pegado a la cara del bloque (no mira a la cámara: está en el plano de la cara), que dura
     * 10 segundos y se desvanece el último. La normal de la cara llega en la velocidad.
     */
    public static class Marca extends TextureSheetParticle {
        /** Las marcas creadas desde que se abrió el juego (la prueba del CI mira que salen). */
        public static volatile int creadas;
        private final float nx, ny, nz;

        protected Marca(ClientLevel l, double x, double y, double z, double vx, double vy, double vz, SpriteSet s) {
            super(l, x, y, z);
            nx = (float) vx;
            ny = (float) vy;
            nz = (float) vz;
            creadas++;
            xd = yd = zd = 0;
            lifetime = 200;
            quadSize = 0.07F + random.nextFloat() * 0.02F;
            gravity = 0;
            hasPhysics = false;
            roll = random.nextInt(4) * 1.5708F;
            pickSprite(s);
        }

        @Override
        public void tick() {
            xo = x;
            yo = y;
            zo = z;
            if (age++ >= lifetime) remove();
            alpha = Math.min(1F, (lifetime - age) / 40F);
        }

        @Override
        public void render(com.mojang.blaze3d.vertex.VertexConsumer vc, net.minecraft.client.Camera cam, float pt) {
            net.minecraft.world.phys.Vec3 c = cam.getPosition();
            float px = (float) (x - c.x) + nx * 0.004F, py = (float) (y - c.y) + ny * 0.004F, pz = (float) (z - c.z) + nz * 0.004F;
            // dos ejes en el plano de la cara
            float ax, ay, az, bx, by, bz;
            if (Math.abs(ny) > 0.5F) { ax = 1; ay = 0; az = 0; bx = 0; by = 0; bz = 1; }
            else if (Math.abs(nx) > 0.5F) { ax = 0; ay = 0; az = 1; bx = 0; by = 1; bz = 0; }
            else { ax = 1; ay = 0; az = 0; bx = 0; by = 1; bz = 0; }
            float h = quadSize;
            float u0 = getU0(), u1 = getU1(), v0 = getV0(), v1 = getV1();
            int luz = getLightColor(pt);
            float[][] q = {{-1, -1}, {-1, 1}, {1, 1}, {1, -1}};
            float[][] uv = {{u1, v1}, {u1, v0}, {u0, v0}, {u0, v1}};
            // las dos caras (que se vea aunque el juego recorte la cara de atrás)
            for (int lado = 0; lado < 2; lado++)
                for (int k = 0; k < 4; k++) {
                    int i = lado == 0 ? k : 3 - k;
                    float a = q[i][0] * h, b = q[i][1] * h;
                    vertice(vc, px + ax * a + bx * b, py + ay * a + by * b, pz + az * a + bz * b, uv[i][0], uv[i][1], alpha, luz);
                }
        }

        /** Un vértice de la marca (en 1.21.1 la API de VertexConsumer es otra: solo cambia este método). */
        private void vertice(com.mojang.blaze3d.vertex.VertexConsumer vc, float x, float y, float z, float u, float v, float a, int luz) {
            vc.vertex(x, y, z).uv(u, v).color(rCol, gCol, bCol, a).uv2(luz).endVertex();
        }

        @Override
        public ParticleRenderType getRenderType() {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    /**
     * La bala que se ve pasar: un punto de luz que recorre el tiro a 6 bloques por tick y se apaga al llegar. El
     * vector del tiro (de la boca a donde da) viene en la velocidad.
     */
    public static class Trazo extends TextureSheetParticle {
        protected Trazo(ClientLevel l, double x, double y, double z, double dx, double dy, double dz, SpriteSet s, float r, float g, float b) {
            super(l, x, y, z);
            double largo = Math.sqrt(dx * dx + dy * dy + dz * dz);
            lifetime = Math.max(1, (int) Math.ceil(largo / 6.0));
            xd = dx / lifetime;
            yd = dy / lifetime;
            zd = dz / lifetime;
            hasPhysics = false;
            gravity = 0;
            friction = 1F;
            quadSize = 0.07F;
            setColor(r, g, b);
            pickSprite(s);
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

    public record TrazoProv(SpriteSet s, float r, float g, float b) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Trazo(l, x, y, z, dx, dy, dz, s, r, g, b);
        }
    }

    public record FogonazoProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Fogonazo(l, x, y, z, dx, s);
        }
    }

    public record HumoProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Humo(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record ChispaProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Chispa(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record MarcaProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Marca(l, x, y, z, dx, dy, dz, s);
        }
    }

    public record CasquilloProv(SpriteSet s) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType t, ClientLevel l, double x, double y, double z, double dx, double dy, double dz) {
            return new Casquillo(l, x, y, z, dx, dy, dz, s);
        }
    }
}
