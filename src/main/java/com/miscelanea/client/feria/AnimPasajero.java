package com.miscelanea.client.feria;

import com.miscelanea.feria.AsientoFeria;
import com.miscelanea.feria.AtraccionBlock;
import com.miscelanea.feria.AtraccionBlockEntity;
import com.miscelanea.feria.CocheChoque;
import com.miscelanea.feria.SustoBlockEntity;
import com.miscelanea.feria.VagonetaBruja;
import com.miscelanea.client.Articulaciones;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * Pasajeros de la feria (capa de playerAnimator, 1150): brazos y cuerpo según la atracción y su estado. Las piernas se
 * quedan con la pose de montar (los asientos están medidos con ella), pero con bendy-lib doblan las rodillas (la
 * espinilla cuelga en vez de ir recta hacia delante); la cabeza, libre (mira donde mira el jugador).
 * <ul>
 * <li>Noria: mirar el paisaje, apoyarse en la barandilla del hueco, señalar fuera, saludar; arriba y en marcha, más euforia.</li>
 * <li>Tiovivo: una mano en la barra y la otra saludando o dando vueltas como un vaquero; sube y baja con el caballo.</li>
 * <li>Pulpo: brazos arriba, manos al volante o al borde de la taza; el cuerpo se va hacia fuera con el giro.</li>
 * <li>Coches de choque: manos al volante, que gira con el coche; sacudida al chocar.</li>
 * <li>Tren de la bruja: agarrado o nervioso; con un susto cerca se tapa los ojos.</li>
 * </ul>
 * Los brazos se describen como direcciones desde el hombro en el marco del jinete (derecha, arriba, delante; bloques)
 * y se pasan a rotaciones del ModelPart; así las mezclas entre gestos no dan saltos. Prototipo y vistas previas:
 * scratchpad/pasajeros_pose.py.
 */
public class AnimPasajero implements IAnimation {
    private static final int NORIA = 1, TIOVIVO = 2, PULPO = 3, COCHE = 4, TREN = 5;
    private static final float HOMBRO_X = 0.293F, HOMBRO_Y = 1.29F, OJOS_Y = 1.64F;
    private static final float[] MUSLO = n(0.05F, -0.78F, 0.62F);

    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int tipo, gesto, gestoAntes, cambio = -100, siguiente;
    // coche de choque
    private float giro, giroO, golpe, golpeO;
    private double vx, vz;
    // pose de este fotograma
    private final float[] ra = new float[3], la = new float[3], cuerpo = new float[3];

    public AnimPasajero(AbstractClientPlayer p) {
        this.p = p;
    }

    private int tipoActual() {
        Entity v = p.getVehicle();
        if (v instanceof AsientoFeria a) {
            AtraccionBlockEntity b = a.be();
            if (b == null) return 0;
            return b.tipo() == AtraccionBlock.NORIA ? NORIA : b.tipo() == AtraccionBlock.PULPO ? PULPO : TIOVIVO;
        }
        if (v instanceof CocheChoque) return COCHE;
        if (v instanceof VagonetaBruja) return TREN;
        return 0;
    }

    /** 0..1: lo rápido que va la atracción respecto a su velocidad de régimen. */
    private static float marcha(AtraccionBlockEntity b) {
        return b == null ? 0 : Mth.clamp(b.vel / b.velMax(), 0, 1);
    }

    private float azar(int k) {
        int h = (p.getId() * 0x9E3779B1) ^ (k * 0x85EBCA6B);
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    @Override
    public void tick() {
        pesoO = peso;
        int t = tipoActual();
        if (t != 0 && t != tipo) {
            tipo = t;
            gesto = gestoAntes = 0;
            cambio = p.tickCount - 100;
            siguiente = p.tickCount + 20;
        }
        peso = Mth.clamp(peso + (t != 0 ? 0.2F : -0.2F), 0, 1);
        if (t == 0) return;
        if (p.tickCount >= siguiente) {
            gestoAntes = gesto;
            gesto = elegir(azar(p.tickCount));
            cambio = p.tickCount;
            siguiente = p.tickCount + 60 + (int) (azar(p.tickCount + 7) * 80);
        }
        giroO = giro;
        golpeO = golpe;
        if (p.getVehicle() instanceof CocheChoque c) {
            float dy = Mth.wrapDegrees(c.getYRot() - c.yRotO);
            giro += (Mth.clamp(dy / 5F, -1, 1) - giro) * 0.35F;
            double nx = c.getX() - c.xo, nz = c.getZ() - c.zo;
            double acel = Math.sqrt((nx - vx) * (nx - vx) + (nz - vz) * (nz - vz));
            vx = nx;
            vz = nz;
            golpe *= 0.82F;
            if (acel > 0.1) golpe = Math.max(golpe, (float) Math.min(1, acel * 4));
            golpe = Math.max(golpe, c.sacudida);
        } else {
            giro *= 0.5F;
            golpe *= 0.7F;
        }
    }

    /** Gesto nuevo según la atracción y cómo va (pesos de cada gesto). */
    private int elegir(float r) {
        float[] w;
        switch (tipo) {
            case NORIA -> {
                AtraccionBlockEntity b = p.getVehicle() instanceof AsientoFeria a ? a.be() : null;
                float f = marcha(b), ex = f * Mth.clamp(alturaNoria(), 0, 1);
                w = f < 0.2F ? new float[]{3, 2, 1, 1, 0} : new float[]{2, 2, 2, 1 + 3 * ex, 4 * ex};
            }
            case TIOVIVO -> {
                float f = p.getVehicle() instanceof AsientoFeria a ? marcha(a.be()) : 0;
                w = f < 0.2F ? new float[]{1, 0, 2, 3} : new float[]{3, 2, 1, 1};
            }
            case PULPO -> {
                float f = p.getVehicle() instanceof AsientoFeria a ? marcha(a.be()) : 0;
                w = f < 0.2F ? new float[]{0, 2, 0, 2} : new float[]{2 + 2 * f, 2, 2, 1};
            }
            case TREN -> w = new float[]{2, 1};
            default -> w = new float[]{1};
        }
        float s = 0;
        for (float x : w) s += x;
        r *= s;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r < 0) return i;
        }
        return 0;
    }

    /** -1 abajo del todo, 1 arriba del todo. */
    private float alturaNoria() {
        if (!(p.getVehicle() instanceof AsientoFeria a) || a.be() == null) return 0;
        double cy = a.atraccion().getY() + AtraccionBlockEntity.CY_NORIA - AtraccionBlockEntity.ALTO_CABINA;
        return (float) Mth.clamp((a.getY() - cy) / AtraccionBlockEntity.R_NORIA, -1, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0;
    }

    @Override
    public void setupAnim(float td) {
        if (tipo == 0) return;
        float t = p.tickCount + td + (p.getId() % 97) * 11;
        float[][] a = pose(gestoAntes, t), b = pose(gesto, t);
        float k = Mth.clamp((p.tickCount + td - cambio) / 14F, 0, 1);
        k = k * k * (3 - 2 * k);
        set(ra, mezcla(a[0], b[0], k));
        set(la, mezcla(a[1], b[1], k));
        for (int i = 0; i < 3; i++) cuerpo[i] = Mth.lerp(k, a[2][i], b[2][i]);
    }

    /** {brazo derecho, brazo izquierdo, cuerpo (adelante, giro a la derecha, ladeo a la derecha)} */
    private float[][] pose(int g, float t) {
        Entity v = p.getVehicle();
        switch (tipo) {
            case NORIA -> {
                int i = v instanceof AsientoFeria a ? Math.max(0, a.getPassengers().indexOf(p)) : 0;
                boolean ozPos = i % 2 == 1;
                return noria(g, t, (i < 2) == ozPos ? 1 : -1);
            }
            case TIOVIVO -> {
                float sube = 0;
                if (v instanceof AsientoFeria a && a.be() != null) {
                    double tiempo = p.level().getGameTime() + pt();
                    sube = (float) Math.cos(a.be().angulo(tiempo) * 5 + a.indice() * 1.7) * marcha(a.be());
                }
                return tiovivo(g, t, (p.getId() & 1) == 0 ? 1 : -1, sube);
            }
            case PULPO -> {
                int i = v instanceof AsientoFeria a ? Math.max(0, a.getPassengers().indexOf(p)) : 0;
                float lf = 0, lr = 0;
                if (v instanceof AsientoFeria a && a.be() != null) {
                    AtraccionBlockEntity b = a.be();
                    double tiempo = p.level().getGameTime() + pt();
                    double brazo = b.angulo(tiempo) + 2 * Math.PI * a.indice() / b.asientos();
                    double r = Math.toRadians(a.getYRot() + 180);
                    double ox = Math.cos(brazo), oz = Math.sin(brazo);
                    float f = marcha(b) * 0.16F;
                    lf = (float) (ox * -Math.sin(r) + oz * Math.cos(r)) * f;
                    lr = (float) (ox * -Math.cos(r) + oz * -Math.sin(r)) * f;
                }
                return pulpo(g, t, i % 2 == 0 ? 1 : -1, lf, lr);
            }
            case COCHE -> {
                float pt = pt();
                return coche(Mth.lerp(pt, giroO, giro), Mth.lerp(pt, golpeO, golpe), t);
            }
            case TREN -> {
                float susto = 0;
                long edad = p.level().getGameTime() - SustoBlockEntity.ultimoT;
                if (edad >= 0 && edad < 46 && SustoBlockEntity.ultimoPos.distToCenterSqr(p.position()) < 8 * 8) {
                    float e = edad + pt();
                    susto = e < 4 ? e / 4 : e < 34 ? 1 : Math.max(0, 1 - (e - 34) / 12);
                }
                return tren(g, t, susto);
            }
            default -> {
                return new float[][]{MUSLO, espejo(MUSLO), new float[3]};
            }
        }
    }

    private static float pt() {
        return net.minecraft.client.Minecraft.getInstance().getFrameTime();
    }

    // ------------------------------------------------------------------ poses (las mismas que pasajeros_pose.py)
    private static float[][] noria(int g, float t, int lado) {
        float[] r = MUSLO, l = espejo(MUSLO), c;
        switch (g) {
            case 1 -> {
                float[] d = n(lado * 0.78F, -0.42F, 0.46F);
                if (lado > 0) r = d; else l = d;
                c = new float[]{0.06F, lado * 0.12F, lado * 0.13F};
            }
            case 2 -> {
                float[] d = n(lado * 0.82F, 0.36F + 0.05F * Mth.sin(t * 0.3F), 0.45F);
                if (lado > 0) r = d; else l = d;
                c = new float[]{0.03F, lado * 0.15F, lado * 0.06F};
            }
            case 3 -> {
                float[] d = ola(lado, t);
                if (lado > 0) r = d; else l = d;
                c = new float[]{0, lado * 0.08F, lado * 0.04F};
            }
            case 4 -> {
                r = n(0.3F + 0.12F * Mth.sin(t * 0.55F), 0.95F, 0.12F);
                l = n(-0.3F - 0.12F * Mth.sin(t * 0.55F + 1.3F), 0.95F, 0.12F);
                c = new float[]{-0.06F, 0, 0.05F * Mth.sin(t * 0.3F)};
            }
            default -> c = new float[]{0.04F, 0.22F * Mth.sin(t * 0.045F), 0.03F * Mth.sin(t * 0.07F)};
        }
        return new float[][]{r, l, c};
    }

    private static final float BARRA_F = 0.26F;

    private static float[][] tiovivo(int g, float t, int mano, float sube) {
        float[] agarre = hacia(mano, 0, 0.92F, BARRA_F);
        int libre = -mano;
        float[] otro = switch (g) {
            case 0 -> ola(libre, t);
            case 1 -> n(libre * (0.22F + 0.22F * Mth.cos(t * 0.6F)), 0.92F, 0.25F * Mth.sin(t * 0.6F) + 0.1F);
            case 2 -> n(libre * 0.12F, -0.72F, 0.68F);
            default -> hacia(libre, 0, 0.80F, BARRA_F);
        };
        otro = n(otro[0], otro[1] + 0.12F * sube, otro[2]);
        float[] c = {0.08F - 0.10F * sube, 0, 0};
        return mano > 0 ? new float[][]{agarre, otro, c} : new float[][]{otro, agarre, c};
    }

    private static float[][] pulpo(int g, float t, int dentro, float leanF, float leanR) {
        // volante de la taza: centro 0,42 hacia dentro, 0,96 sobre los pies y 0,36 por delante; radio 0,26
        float cx = dentro * 0.42F, cy = 0.96F, cz = 0.36F;
        float[] vIn = hacia(dentro, cx - dentro * 0.20F, cy + 0.03F, cz - 0.14F);
        float[] vOut = hacia(-dentro, cx - dentro * 0.26F, cy, cz + 0.04F);
        float giro = 0.12F * Mth.sin(t * 0.35F);
        float[] aIn, aOut;
        switch (g) {
            case 0 -> {
                aIn = n(dentro * (0.16F + 0.1F * Mth.sin(t * 0.8F)), 0.97F, 0.15F);
                aOut = n(-dentro * (0.22F + 0.1F * Mth.sin(t * 0.8F + 1)), 0.96F, 0.15F);
            }
            case 1 -> {
                aIn = n(vIn[0], vIn[1] + giro, vIn[2]);
                aOut = n(vOut[0], vOut[1] - giro, vOut[2]);
            }
            case 2 -> {
                aIn = vIn;
                aOut = n(-dentro * (0.3F + 0.15F * Mth.sin(t * 0.7F)), 0.93F, 0.15F);
            }
            default -> {
                aIn = vIn;
                aOut = n(-dentro * 0.62F, -0.72F, 0.30F);
            }
        }
        float[] c = {0.05F + leanF, 0, leanR};
        return dentro > 0 ? new float[][]{aIn, aOut, c} : new float[][]{aOut, aIn, c};
    }

    private static float[][] coche(float giro, float golpe, float t) {
        // volante: 0,75 sobre los pies y 0,32 por delante, radio 0,156; gira en el plano derecha-arriba
        float a = -giro * 0.9F, R = 0.156F;
        float[] r = hacia(1, Mth.cos(a) * R, 0.75F + Mth.sin(a) * R, 0.32F);
        float[] l = hacia(-1, -Mth.cos(a) * R, 0.75F - Mth.sin(a) * R, 0.32F);
        if (golpe > 0.01F) {
            float sac = golpe * Mth.sin(t * 2.3F);
            r = n(r[0] + 0.25F * sac, r[1] + 0.5F * golpe, r[2]);
            l = n(l[0] + 0.25F * sac, l[1] + 0.5F * golpe, l[2]);
        }
        float[] c = {0.05F - 0.3F * golpe * Mth.cos(t * 1.7F), 0, -0.12F * giro + 0.1F * golpe * Mth.sin(t * 2.1F)};
        return new float[][]{r, l, c};
    }

    private static float[][] tren(int g, float t, float susto) {
        float[] r, l, c;
        if (g == 0) {
            r = n(0.35F, -0.85F, 0.40F);
            l = espejo(r);
            c = new float[]{0.04F, 0.18F * Mth.sin(t * 0.06F), 0};
        } else {
            r = hacia(1, -0.18F, 1.05F, 0.22F);
            l = hacia(-1, 0.18F, 1.12F, 0.20F);
            c = new float[]{0.12F, 0.25F * Mth.sin(t * 0.09F), 0};
        }
        if (susto > 0) {
            float tb = 0.03F * Mth.sin(t * 3.1F);
            r = mezcla(r, hacia(1, 0.03F, OJOS_Y + tb, 0.30F), susto);
            l = mezcla(l, hacia(-1, -0.03F, OJOS_Y - tb, 0.30F), susto);
            float[] s = {0.22F, 0, 0.04F * Mth.sin(t * 2.7F)};
            for (int i = 0; i < 3; i++) c[i] = Mth.lerp(susto, c[i], s[i]);
        }
        return new float[][]{r, l, c};
    }

    // ------------------------------------------------------------------ utilidades
    private static float[] ola(int lado, float t) {
        return n(lado * (0.42F + 0.28F * Mth.sin(t * 0.7F)), 0.9F, 0.18F);
    }

    private static float[] n(float x, float y, float z) {
        float l = Mth.sqrt(x * x + y * y + z * z);
        return l < 1e-5F ? new float[]{0, -1, 0} : new float[]{x / l, y / l, z / l};
    }

    private static float[] espejo(float[] d) {
        return new float[]{-d[0], d[1], d[2]};
    }

    private static float[] hacia(int lado, float x, float y, float z) {
        return n(x - lado * HOMBRO_X, y - HOMBRO_Y, z);
    }

    private static float[] mezcla(float[] a, float[] b, float k) {
        return n(Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2]));
    }

    private static void set(float[] dst, float[] src) {
        System.arraycopy(src, 0, dst, 0, 3);
    }

    /** ¿Está ese brazo ocupado (golpeando o usando algo)? Entonces se deja como lo pone el juego. */
    private boolean ocupado(HumanoidArm brazo) {
        HumanoidArm principal = p.getMainArm();
        if (p.swinging && (p.swingingArm == InteractionHand.MAIN_HAND ? principal : principal.getOpposite()) == brazo) return true;
        if (p.isUsingItem()) return (p.getUsedItemHand() == InteractionHand.MAIN_HAND ? principal : principal.getOpposite()) == brazo;
        return false;
    }

    private static Vec3f rot(float[] d) {
        // dirección (derecha, arriba, delante) → ModelPart (y abajo, -z delante, +x a la izquierda del jugador)
        return new Vec3f((float) Math.asin(Mth.clamp(-d[2], -1, 1)), 0, (float) Mth.atan2(d[0], -d[1]));
    }

    private static Vec3f mezclar(float k, Vec3f v, Vec3f o) {
        return new Vec3f(Mth.lerp(k, v.getX(), o.getX()), Mth.lerp(k, v.getY(), o.getY()), Mth.lerp(k, v.getZ(), o.getZ()));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tt, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0 || tipo == 0) return v;
        if (tt == TransformType.BEND) {
            // sentado: las rodillas dobladas y la espinilla colgando (con bendy-lib), según el asiento
            if (!Articulaciones.pierna(parte)) return v;
            float f = switch (tipo) {
                case NORIA -> 1.45F;
                case TIOVIVO -> 1.05F;   // a caballo, con los pies en los estribos
                case PULPO, TREN -> 1.35F;
                case COCHE -> 0.95F;     // los pies hacia los pedales
                default -> 0F;
            };
            return Articulaciones.fijar(parte, v, f, k);
        }
        if (tt != TransformType.ROTATION) return v;
        return switch (parte) {
            case "rightArm" -> ocupado(HumanoidArm.RIGHT) ? v : mezclar(k, v, rot(ra));
            case "leftArm" -> ocupado(HumanoidArm.LEFT) ? v : mezclar(k, v, rot(la));
            // giro del cuerpo entero de playerAnimator (en la cadera, 0,7): adelante = -x, derecha = -y y -z
            case "body" -> mezclar(k, v, new Vec3f(-cuerpo[0], -cuerpo[1], -cuerpo[2]));
            default -> v;
        };
    }
}
