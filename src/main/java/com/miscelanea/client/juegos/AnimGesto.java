package com.miscelanea.client.juegos;

import com.miscelanea.juegos.Gestos;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Gestos en las mesas de juego: alargar el brazo hacia la casilla, agitar y lanzar los dados, echar carta, golpe de taco,
 * manos en las barras del futbolín y en el mazo del air hockey, celebraciones y lamentos.
 * <p>
 * Ángulos como los de vanilla (ModelPart, radianes): brazo x &lt; 0 hacia delante, y &gt; 0 hacia la derecha del jugador,
 * z &gt; 0 brazo derecho hacia fuera. El "body" de playerAnimator es el cuerpo entero (x &lt; 0 se inclina hacia delante).
 */
public class AnimGesto implements IAnimation {
    private static final int B = 0, H = 1, RA = 2, LA = 3, RL = 4, LL = 5;
    private static final float HOMBRO_Y = 22F / 16 * 0.9375F, HOMBRO_X = 5F / 16 * 0.9375F;

    private final AbstractClientPlayer p;
    /** Pose de este fotograma: rotación de cada parte, peso de cada parte y desplazamiento del cuerpo. */
    private final float[][] rot = new float[6][3];
    private final float[] peso = new float[6];
    private final float[] desp = new float[3];
    private float pesoCont, pesoContO;
    private GestosCliente.Postura ultima;

    public AnimGesto(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoContO = pesoCont;
        GestosCliente.Postura q = GestosCliente.postura(p, 0);
        if (q != null) ultima = q;
        pesoCont = Mth.clamp(pesoCont + (q != null ? 0.2F : -0.2F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return GestosCliente.de(p.getId()) != null || pesoCont > 0 || pesoContO > 0;
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Entra en {@code ent} ticks, se mantiene y sale en {@code sal} ticks antes del final. */
    private static float envolvente(float u, float dur, float ent, float sal) {
        return Math.min(suave(u / ent), suave((dur - u) / sal));
    }

    private void poner(int parte, float w, float x, float y, float z) {
        float[] r = rot[parte];
        if (peso[parte] <= 0) {
            r[0] = x;
            r[1] = y;
            r[2] = z;
            peso[parte] = w;
            return;
        }
        // encima de lo que ya hubiera (la postura continua): mezcla
        r[0] = Mth.lerp(w, r[0], x);
        r[1] = Mth.lerp(w, r[1], y);
        r[2] = Mth.lerp(w, r[2], z);
        peso[parte] = Math.max(peso[parte], w);
    }

    /** {x, y} del brazo (derecho o izquierdo) para que apunte al punto del mundo. */
    private float[] ik(Vec3 obj, boolean derecho, float pt) {
        Vec3 pos = p.getPosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fw = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        Vec3 hombro = pos.add(0, HOMBRO_Y * (p.isCrouching() ? 0.85 : 1), 0).add(der.scale(derecho ? HOMBRO_X : -HOMBRO_X));
        Vec3 d = obj.subtract(hombro);
        double f = d.dot(fw), r = d.dot(der), u = d.y, ln = Math.sqrt(f * f + r * r + u * u);
        if (ln < 1e-4) return new float[]{-1.5F, 0};
        // espacio del modelo: x = -derecha, y = -arriba, z = -delante; el brazo cuelga hacia +y
        double mx = -r / ln, my = -u / ln, mz = -f / ln;
        float xr = (float) -Math.acos(Mth.clamp(my, -1, 1));
        float yr = (float) Math.atan2(-mx, -mz);
        if (yr > Math.PI / 2) yr -= Math.PI;
        if (yr < -Math.PI / 2) yr += Math.PI;
        return new float[]{xr, yr};
    }

    /** {x, y} de la cabeza mirando al punto. */
    private float[] mirar(Vec3 obj, float pt) {
        Vec3 ojo = p.getEyePosition(pt);
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        Vec3 d = obj.subtract(ojo);
        float obYaw = (float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG);
        float rel = Mth.clamp(Mth.wrapDegrees(obYaw - yaw), -70, 70);
        float pitch = (float) -(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * Mth.RAD_TO_DEG);
        return new float[]{Mth.clamp(pitch, -60, 70) * Mth.DEG_TO_RAD, rel * Mth.DEG_TO_RAD};
    }

    /** Ángulo (rad) del punto respecto al frente del cuerpo, positivo a la derecha. */
    private float relativo(Vec3 obj, float pt) {
        Vec3 d = obj.subtract(p.getPosition(pt));
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        return Mth.wrapDegrees((float) (Mth.atan2(-d.x, d.z) * Mth.RAD_TO_DEG) - yaw) * Mth.DEG_TO_RAD;
    }

    @Override
    public void setupAnim(float pt) {
        java.util.Arrays.fill(peso, 0);
        java.util.Arrays.fill(desp, 0);
        // ---- postura continua (futbolín, air hockey)
        float wc = suave(Mth.lerp(pt, pesoContO, pesoCont));
        GestosCliente.Postura q = wc > 0 ? GestosCliente.postura(p, pt) : null;
        if (q == null) q = wc > 0 ? ultima : null;
        if (q != null) {
            float t = p.tickCount + pt;
            if (q.manoD() != null) {
                float[] a = ik(q.manoD(), true, pt);
                float chut = q.chut();
                // chut: tirón seco de la barra (giro de muñeca hacia delante y vuelta)
                poner(RA, wc, a[0] - chut * 0.45F, a[1], 0.05F + chut * 0.15F * Mth.sin(t * 2.2F));
            }
            if (q.manoI() != null) {
                float[] a = ik(q.manoI(), false, pt);
                poner(LA, wc, a[0] - q.chut() * 0.45F, a[1], -0.05F - q.chut() * 0.15F * Mth.sin(t * 2.2F + 1));
            } else if (q.manoD() != null) {
                // air hockey: la otra mano apoyada en el borde de la mesa
                poner(LA, wc * 0.8F, -0.55F, 0.12F, -0.1F);
            }
            poner(B, wc, -0.12F * q.inclina() - 0.06F, 0, 0);
            Vec3 mira = q.manoD() != null ? q.manoD() : q.manoI();
            if (mira != null) {
                float[] h = mirar(mira, pt);
                poner(H, wc * 0.5F, h[0], h[1], 0);
            }
        }
        // ---- gesto puntual
        GestosCliente.Activo g = GestosCliente.de(p.getId());
        if (g == null) return;
        float u = GestosCliente.tiempo(g, pt);
        float dur = GestosCliente.duracion(g.tipo());
        if (u >= dur) return;
        Vec3 o = g.objetivo();
        switch (g.tipo()) {
            case Gestos.ALCANZAR -> alcanzar(o, u, dur, pt);
            case Gestos.DADOS -> dados(o, u, dur, pt);
            case Gestos.CARTA -> carta(o, u, dur, pt);
            case Gestos.TACO -> taco(o, u, dur, pt);
            case Gestos.MANIVELA -> manivela(u, dur);
            case Gestos.PALA -> pala(o, u, dur, pt);
            case Gestos.PUNO -> puno(u, dur);
            case Gestos.APLAUSO -> aplauso(u, dur);
            case Gestos.BAILE -> baile(u, dur);
            case Gestos.FACEPALM -> facepalm(u, dur);
            case Gestos.ENCOGERSE -> encogerse(u, dur);
            default -> {}
        }
    }

    // ------------------------------------------------------------------ gestos

    /** 0,4 s: el brazo sale hacia la casilla (curva suave), se queda un instante y vuelve. */
    private void alcanzar(Vec3 o, float u, float dur, float pt) {
        float w = envolvente(u, dur, 3, 3);
        boolean der = relativo(o, pt) > -0.45F;
        float[] a = ik(o, der, pt);
        // un pelín más allá al tocar (se nota el "toque")
        float toque = Mth.sin(Mth.clamp((u - 2.5F) / 3F, 0, 1) * Mth.PI) * 0.08F;
        poner(der ? RA : LA, w, a[0] - toque, a[1], der ? 0.04F : -0.04F);
        float[] h = mirar(o, pt);
        poner(H, w * 0.8F, h[0], h[1], 0);
        poner(B, w, -0.1F, 0, 0);
    }

    /** Agita los dados en el puño delante del pecho (la otra mano tapando) y los lanza hacia el tablero. */
    private void dados(Vec3 o, float u, float dur, float pt) {
        float w = envolvente(u, dur, 3, 5);
        float[] a = ik(o, true, pt);
        float[] h = mirar(o, pt);
        if (u < 11) {
            float s = Mth.sin(u * 2.6F);
            poner(RA, w, -1.3F + 0.2F * s, -0.4F, 0.06F * Mth.sin(u * 2.6F + 1.2F));
            poner(LA, w, -1.15F + 0.12F * s, 0.5F, 0);
        } else if (u < 14) {
            // recoge el brazo arriba para tomar impulso
            float k = suave((u - 11) / 3F);
            poner(RA, w, Mth.lerp(k, -1.3F, -2.0F), Mth.lerp(k, -0.4F, a[1] * 0.5F), 0);
            poner(LA, w * (1 - k * 0.7F), -1.15F, 0.5F, 0);
        } else {
            // lanzamiento: el brazo baja de golpe hacia el tablero, un poco más allá
            float k = suave((u - 14) / 2.5F);
            poner(RA, w, Mth.lerp(k, -2.0F, a[0] - 0.15F), Mth.lerp(k, a[1] * 0.5F, a[1]), 0.05F);
            poner(LA, w * 0.3F, -0.6F, 0.2F, 0);
        }
        poner(H, w * 0.7F, h[0], h[1], 0);
        poner(B, w, u < 14 ? -0.04F : -0.14F, 0, 0);
    }

    /** Echa la carta con un golpe de muñeca: el brazo sale del pecho hacia la mesa con un latigazo lateral. */
    private void carta(Vec3 o, float u, float dur, float pt) {
        float w = envolvente(u, dur, 2, 4);
        float[] a = ik(o, true, pt);
        float k = suave((u - 1) / 3F);
        float latigo = Mth.sin(Mth.clamp((u - 2) / 4F, 0, 1) * Mth.PI) * 0.35F;
        poner(RA, w, Mth.lerp(k, -1.0F, a[0]), Mth.lerp(k, -0.55F, a[1]) + latigo, 0.05F + latigo * 0.3F);
        float[] h = mirar(o, pt);
        poner(H, w * 0.7F, h[0], h[1], 0);
        poner(B, w, -0.08F, 0, 0);
    }

    /** Golpe de taco: inclinado sobre la mesa, la mano izquierda de puente y la derecha atrás, que empuja hacia delante. */
    private void taco(Vec3 o, float u, float dur, float pt) {
        float w = envolvente(u, dur, 3, 4);
        float ry = Mth.clamp(relativo(o, pt), -0.6F, 0.6F);
        float tirar, empujar;
        if (u < 9) {
            tirar = 0.5F + 0.5F * Mth.sin(u * 0.9F - Mth.HALF_PI);   // dos vaivenes de prueba
            empujar = 0;
        } else {
            tirar = 1 - suave((u - 9) / 1.5F);
            empujar = suave((u - 9) / 1.5F);
        }
        poner(LA, w, -1.45F, ry * 0.8F + 0.18F, -0.05F);
        poner(RA, w, -0.55F + 0.35F * tirar - 0.6F * empujar, ry * 0.6F + 0.08F, 0.1F);
        poner(B, w, -0.32F, 0, 0);
        desp[1] = -0.1F * w;
        poner(H, w, -0.35F, ry * 0.5F, 0);
    }

    /** Giro seco de la barra del futbolín con las dos manos. */
    private void manivela(float u, float dur) {
        float w = envolvente(u, dur, 1, 3);
        float s = Mth.sin(u * 1.6F);
        poner(RA, w, -1.0F - 0.4F * s, 0.1F, 0.12F);
        poner(LA, w, -1.0F - 0.4F * s, -0.1F, -0.12F);
    }

    /** Empujón del mazo hacia el punto. */
    private void pala(Vec3 o, float u, float dur, float pt) {
        float w = envolvente(u, dur, 2, 4);
        float[] a = ik(o, true, pt);
        poner(RA, w, a[0] - 0.15F, a[1], 0.05F);
        poner(B, w, -0.18F, 0, 0);
    }

    /** Puño en alto, tres veces. */
    private void puno(float u, float dur) {
        float w = envolvente(u, dur, 3, 5);
        float pump = Math.abs(Mth.sin(u * 0.32F * Mth.PI / 1.6F));
        poner(RA, w, -2.85F + 0.55F * pump, 0.1F, 0.25F);
        poner(LA, w, -0.25F, 0, -0.18F);
        poner(H, w, -0.25F, 0, 0);
        poner(B, w, 0.06F, 0, 0);
        desp[1] = 0.03F * pump * w;
    }

    /** Aplausos delante del pecho. */
    private void aplauso(float u, float dur) {
        float w = envolvente(u, dur, 3, 5);
        float c = 0.5F + 0.5F * Mth.sin(u * 1.35F);
        poner(RA, w, -1.25F, -0.08F - 0.42F * c, 0.02F);
        poner(LA, w, -1.25F, 0.08F + 0.42F * c, -0.02F);
        poner(H, w, -0.08F, 0, 0);
    }

    /** Bailecito de victoria: brazos arriba alternando, balanceo de cadera y piernas. */
    private void baile(float u, float dur) {
        float w = envolvente(u, dur, 4, 6);
        float f = u * 0.42F, s = Mth.sin(f);
        poner(RA, w, -2.3F + 0.6F * s, 0.1F, 0.4F);
        poner(LA, w, -2.3F - 0.6F * s, -0.1F, -0.4F);
        poner(B, w, 0, 0.1F * s, 0.1F * s);
        poner(H, w, -0.1F, 0, -0.12F * s);
        poner(RL, w, 0.3F * s, 0, 0.04F);
        poner(LL, w, -0.3F * s, 0, -0.04F);
        desp[1] = 0.04F * Math.abs(Mth.cos(f)) * w;
    }

    /** Mano a la cara y cabeza gacha, negando. */
    private void facepalm(float u, float dur) {
        float w = envolvente(u, dur, 4, 7);
        poner(RA, w, -2.05F, -0.62F, 0.05F);
        poner(H, w, 0.45F, 0.14F * Mth.sin(u * 0.55F), 0);
        poner(LA, w, -0.1F, 0, -0.06F);
        poner(B, w, -0.05F, 0, 0);
    }

    /** Encogerse de hombros: brazos abiertos a los lados, cabeza ladeada. */
    private void encogerse(float u, float dur) {
        float w = envolvente(u, dur, 4, 6);
        float k = Mth.sin(Mth.clamp((u - 2) / (dur - 8), 0, 1) * Mth.PI);
        poner(RA, w, -0.5F, 0.25F, 0.4F + 0.25F * k);
        poner(LA, w, -0.5F, -0.25F, -0.4F - 0.25F * k);
        poner(H, w, 0.05F, 0, 0.22F * k);
        desp[1] = 0.035F * k * w;
    }

    // ------------------------------------------------------------------ playerAnimator

    private static int parte(String n) {
        return switch (n) {
            case "body" -> B;
            case "head" -> H;
            case "rightArm" -> RA;
            case "leftArm" -> LA;
            case "rightLeg" -> RL;
            case "leftLeg" -> LL;
            default -> -1;
        };
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String nombre, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        int i = parte(nombre);
        if (i < 0) return v;
        if (tipo == TransformType.POSITION) {
            if (i != B || (desp[0] == 0 && desp[1] == 0 && desp[2] == 0)) return v;
            return new Vec3f(v.getX() + desp[0], v.getY() + desp[1], v.getZ() + desp[2]);
        }
        if (tipo != TransformType.ROTATION) return v;
        float w = peso[i];
        if (w <= 0) return v;
        float[] r = rot[i];
        if (i == B) // el cuerpo entero: se suma a lo que haya (agacharse, etc.)
            return new Vec3f(v.getX() + r[0] * w, v.getY() + r[1] * w, v.getZ() + r[2] * w);
        return new Vec3f(Mth.lerp(w, v.getX(), r[0]), Mth.lerp(w, v.getY(), r[1]), Mth.lerp(w, v.getZ(), r[2]));
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        return FirstPersonMode.NONE;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return new FirstPersonConfiguration(false, false, false, false);
    }
}
