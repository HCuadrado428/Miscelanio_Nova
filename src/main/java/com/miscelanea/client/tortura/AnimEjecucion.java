package com.miscelanea.client.tortura;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Ejecución de la trampa de oso (capa de playerAnimator por encima de todas las de la sala de juegos), por fotogramas
 * clave con curvas y un temblor de tensión que crece hasta el estallido:
 * <ul>
 *   <li>0-12: oye el clic, se encoge y se lleva las manos a la trampa;</li>
 *   <li>12-38: tirones hacia arriba arqueando la espalda, pasos de tropiezo hacia atrás, «no, no, no» con la cabeza;</li>
 *   <li>38-45: se le doblan las rodillas y cae (con rebote), grita con la cabeza hacia atrás mientras una mano hace
 *       palanca en las mandíbulas;</li>
 *   <li>52: revienta: latigazo hacia atrás, los brazos salen despedidos y caen por su peso, el cuerpo se dobla;</li>
 *   <li>al morir se apaga en medio segundo (manda la animación de muerte normal).</li>
 * </ul>
 */
public class AnimEjecucion implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int muerto;

    public AnimEjecucion(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean activa() {
        return EjecucionCliente.activa(p.getId());
    }

    @Override
    public void tick() {
        pesoO = peso;
        boolean act = activa();
        if (act && p.isDeadOrDying()) muerto++;
        else muerto = 0;
        float objetivo = !act ? 0F : Math.max(0F, 1F - muerto / 10F);
        peso = peso < objetivo ? Math.min(objetivo, peso + 0.34F) : Math.max(objetivo, peso - 0.2F);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || activa();
    }

    @Override
    public void setupAnim(float td) {}

    // ---------------------------------------------------------------- fotogramas clave (tick, x, y, z, curva)
    // generados con scratchpad/ejec2.py (la misma animación que la vista previa); curvas: 0 lineal, 1 suave, 2 frena, 3 acelera, 4 se pasa
    private static final float[][] K_POS = {{0F, 0F, 0F, 0F, 1}, {5F, 0F, -0.04F, 0F, 2}, {12F, 0F, 0F, 0F, 1}, {16F, 0F, 0F, 0.12F, 2}, {20F, 0F, 0F, 0.05F, 1}, {26F, 0.05F, 0F, 0.15F, 2}, {31F, -0.06F, 0F, 0.02F, 1}, {35F, 0.04F, 0F, 0.1F, 1}, {38F, 0F, 0F, 0.06F, 1}, {42F, 0F, -0.47F, 0.06F, 3}, {45F, 0F, -0.4F, 0.06F, 2}, {48F, 0F, -0.42F, 0.06F, 1}, {52F, 0F, -0.42F, 0.06F, 1}, {54F, 0F, -0.36F, 0.06F, 2}, {60F, 0F, -0.42F, 0.06F, 3}};
    private static final float[][] K_B = {{0F, 0F, 0F, 0F, 1}, {5F, -0.06F, 0F, 0F, 2}, {12F, 0F, 0F, 0F, 1}, {16F, 0.14F, 0.1F, 0F, 2}, {20F, 0.05F, 0F, 0F, 1}, {26F, 0.18F, -0.15F, 0.05F, 2}, {31F, 0.02F, 0.2F, -0.06F, 1}, {35F, 0.1F, -0.1F, 0.04F, 1}, {38F, 0F, 0F, 0F, 1}, {42F, -0.12F, 0F, 0F, 3}, {46F, -0.04F, 0F, 0F, 2}, {52F, 0.02F, 0F, 0F, 1}, {54F, 0.22F, 0F, 0F, 2}, {60F, -0.3F, 0F, 0.05F, 3}};
    private static final float[][] K_TORSO = {{0F, 0F, 0F, 0F, 1}, {5F, 0.12F, 0F, 0F, 2}, {12F, 0.05F, 0F, 0F, 1}, {16F, -0.15F, 0.15F, 0F, 2}, {20F, 0F, 0F, 0F, 1}, {26F, -0.22F, -0.25F, 0.06F, 2}, {31F, 0.05F, 0.3F, -0.06F, 1}, {35F, -0.05F, -0.15F, 0.05F, 1}, {38F, 0.05F, 0F, 0F, 1}, {42F, 0.3F, 0F, 0F, 3}, {46F, 0.18F, 0F, 0F, 2}, {50F, -0.1F, 0F, 0F, 1}, {52F, -0.12F, 0F, 0F, 1}, {54F, -0.4F, 0F, 0F, 2}, {60F, 0.6F, 0F, 0.08F, 3}};
    private static final float[][] K_HD = {{0F, 0F, 0F, 0F, 1}, {3F, -0.25F, 0F, 0F, 2}, {8F, 0.15F, 0.25F, 0F, 1}, {12F, 0.05F, 0F, 0F, 1}, {16F, -0.35F, 0.1F, 0.1F, 2}, {20F, 0F, 0F, 0F, 1}, {23F, 0.05F, 0.6F, 0.15F, 2}, {26F, -0.1F, -0.65F, -0.15F, 1}, {29F, 0.05F, 0.55F, 0.12F, 1}, {32F, 0F, -0.5F, -0.1F, 1}, {35F, 0.1F, 0.35F, 0.05F, 1}, {38F, 0.15F, 0F, 0F, 1}, {42F, 0.35F, 0F, 0F, 3}, {45F, -0.6F, 0.05F, 0F, 2}, {50F, -0.7F, 0F, 0.05F, 1}, {52F, -0.7F, 0F, 0F, 1}};
    private static final float[][] K_RA = {{0F, 0F, 0F, 0F, 1}, {4F, -1.3F, 0F, 0.2F, 2}, {9F, -2.75F, 0.0F, -0.12F, 1}, {12F, -2.75F, 0.0F, -0.12F, 1}, {16F, -3.05F, 0F, -0.2F, 2}, {20F, -2.7F, 0F, -0.08F, 1}, {26F, -3.1F, 0.05F, -0.24F, 2}, {29F, -2.65F, 0F, -0.06F, 1}, {34F, -3.0F, 0F, -0.2F, 2}, {38F, -2.75F, 0.0F, -0.12F, 1}, {42F, -2.55F, 0F, -0.05F, 3}, {45F, -2.2F, 0.35F, 0.1F, 2}, {47F, -2.35F, 0.3F, 0.05F, 1}, {49F, -2.15F, 0.38F, 0.12F, 1}, {52F, -2.25F, 0.33F, 0.08F, 1}, {55F, -3.2F, 0F, -0.95F, 2}, {61F, -0.2F, 0F, 0.18F, 3}, {64F, -0.4F, 0F, 0.12F, 2}, {68F, -0.2F, 0F, 0.15F, 1}};
    private static final float[][] K_LA = {{0F, 0F, 0F, 0F, 1}, {5F, -1.2F, 0F, -0.2F, 2}, {10F, -2.75F, 0.0F, 0.12F, 1}, {12F, -2.75F, 0.0F, 0.12F, 1}, {16F, -3.05F, 0F, 0.2F, 2}, {20F, -2.7F, 0F, 0.08F, 1}, {26F, -3.1F, -0.05F, 0.24F, 2}, {29F, -2.65F, 0F, 0.06F, 1}, {34F, -3.0F, 0F, 0.2F, 2}, {38F, -2.75F, 0.0F, 0.12F, 1}, {42F, -2.6F, 0F, 0.08F, 3}, {45F, -2.85F, 0F, 0.2F, 2}, {48F, -2.7F, 0F, 0.14F, 1}, {50F, -2.9F, 0F, 0.22F, 1}, {52F, -2.85F, 0F, 0.2F, 1}, {55F, -3.1F, 0F, 0.95F, 2}, {61F, -0.25F, 0F, -0.18F, 3}, {64F, -0.45F, 0F, -0.12F, 2}, {68F, -0.25F, 0F, -0.15F, 1}};
    private static final float[][] K_RL = {{0F, 0F, 0F, 0F, 1}, {14F, 0F, 0F, 0F, 1}, {16F, 0.45F, 0F, 0F, 2}, {19F, 0F, 0F, 0F, 1}, {24F, -0.1F, 0F, 0F, 1}, {26F, 0.4F, 0F, 0.05F, 2}, {29F, -0.3F, 0F, 0F, 1}, {32F, 0.3F, 0F, 0F, 1}, {35F, -0.2F, 0F, 0F, 1}, {38F, 0F, 0F, 0.05F, 1}, {42F, 1.15F, 0F, 0.12F, 3}, {45F, 1.08F, 0F, 0.1F, 2}};
    private static final float[][] K_LL = {{0F, 0F, 0F, 0F, 1}, {14F, 0F, 0F, 0F, 1}, {16F, -0.3F, 0F, 0F, 2}, {19F, 0F, 0F, 0F, 1}, {24F, 0.1F, 0F, 0F, 1}, {26F, -0.35F, 0F, 0F, 2}, {29F, 0.35F, 0F, -0.05F, 1}, {32F, -0.25F, 0F, 0F, 1}, {35F, 0.25F, 0F, 0F, 1}, {38F, 0F, 0F, -0.05F, 1}, {42F, 1.15F, 0F, -0.12F, 3}, {45F, 1.08F, 0F, -0.1F, 2}};

    private static float curva(int e, float x) {
        x = Mth.clamp(x, 0F, 1F);
        switch (e) {
            case 1: return x * x * (3F - 2F * x);
            case 2: { float u = 1F - x; return 1F - u * u * u; }
            case 3: return x * x * x;
            case 4: { float c = 1.9F, u = x - 1F; return 1F + (c + 1F) * u * u * u + c * u * u; }
            default: return x;
        }
    }

    private static float[] valor(float[][] ks, float t) {
        if (t <= ks[0][0]) return new float[]{ks[0][1], ks[0][2], ks[0][3]};
        for (int i = 1; i < ks.length; i++) {
            float[] a = ks[i - 1], b = ks[i];
            if (t <= b[0]) {
                float k = curva((int) b[4], (t - a[0]) / (b[0] - a[0]));
                return new float[]{a[1] + (b[1] - a[1]) * k, a[2] + (b[2] - a[2]) * k, a[3] + (b[3] - a[3]) * k};
            }
        }
        float[] u = ks[ks.length - 1];
        return new float[]{u[1], u[2], u[3]};
    }

    private static float ruido(float t, int sem) {
        return Mth.sin(t * 2.3F + sem * 1.7F) * 0.5F + Mth.sin(t * 3.9F + sem * 2.9F) * 0.3F + Mth.sin(t * 6.1F + sem * 0.7F) * 0.2F;
    }

    /** Tensión: crece mientras forcejea y es máxima justo antes de reventar; después, espasmos que se apagan. */
    private static float temblor(float t) {
        if (t < EjecucionCliente.ESTALLIDO) {
            float r = t / EjecucionCliente.ESTALLIDO;
            return 0.012F + 0.05F * r * r;
        }
        float s = t - EjecucionCliente.ESTALLIDO;
        return s > 3F ? 0.06F * (float) Math.exp(-s * 0.35F) : 0F;
    }

    private static float[] conTemblor(float[] v, float t, int i, float f) {
        float tr = temblor(t) * f, q = t * 4F;
        return new float[]{v[0] + ruido(q, i * 3 + 1) * tr, v[1] + ruido(q, i * 3 + 2) * 0.6F * tr, v[2] + ruido(q, i * 3 + 3) * tr};
    }

    private static Vec3f mezclar(float k, Vec3f v, float[] o) {
        return new Vec3f(Mth.lerp(k, v.getX(), o[0]), Mth.lerp(k, v.getY(), o[1]), Mth.lerp(k, v.getZ(), o[2]));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        EjecucionCliente.Ejec ej = EjecucionCliente.de(p.getId());
        if (ej == null) return v;
        float t = EjecucionCliente.tiempo(ej, td);
        boolean revento = t >= EjecucionCliente.ESTALLIDO;
        if (tipo == TransformType.POSITION) {
            if (!parte.equals("body")) return v;
            float[] o = valor(K_POS, t);
            return new Vec3f(v.getX() + o[0] * k, v.getY() + o[1] * k, v.getZ() + o[2] * k);
        }
        if (tipo != TransformType.ROTATION) return v;
        switch (parte) {
            case "body": {
                float[] o = valor(K_B, t);
                return new Vec3f(v.getX() + o[0] * k, v.getY() + o[1] * k, v.getZ() + o[2] * k);
            }
            case "torso": return mezclar(k, v, conTemblor(valor(K_TORSO, t), t, 3, 0.6F));
            // sin cabeza: el muñón sigue al torso (los dos giran sobre el mismo punto del cuello)
            case "head": return mezclar(k, v, revento ? conTemblor(valor(K_TORSO, t), t, 3, 0.6F) : conTemblor(valor(K_HD, t), t, 2, 0.6F));
            case "rightArm": return mezclar(k, v, conTemblor(valor(K_RA, t), t, 0, 1F));
            case "leftArm": return mezclar(k, v, conTemblor(valor(K_LA, t), t, 1, 1F));
            case "rightLeg": return mezclar(k, v, valor(K_RL, t));
            case "leftLeg": return mezclar(k, v, valor(K_LL, t));
            default: return v;
        }
    }
}
