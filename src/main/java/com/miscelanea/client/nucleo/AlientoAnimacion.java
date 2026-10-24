package com.miscelanea.client.nucleo;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Aliento del núcleo, a lo aliento atómico.
 * Carga: se arquea hacia atrás, echa la cabeza atrás mirando al cielo, brazos abiertos y hacia atrás,
 *        piernas abiertas y temblor creciente.
 * Disparo: latigazo de cabeza hacia delante (la cabeza sigue a donde miras), torso inclinado hacia el rayo,
 *          brazos atrás del todo y el cuerpo sacudido por el retroceso.
 * Al acabar vuelve a la pose normal en 8 ticks.
 */
public class AlientoAnimacion implements IAnimation {
    private final AbstractClientPlayer player;
    private float f = -1, fin = -1, t;

    public AlientoAnimacion(AbstractClientPlayer player) {
        this.player = player;
    }

    @Override
    public boolean isActive() {
        float a = NucleoCliente.fase(player.getId(), 0);
        float b = NucleoCliente.desdeFin(player.getId(), 0);
        return a >= 0 || (b >= 0 && b < 8);
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    @Override
    public void setupAnim(float tickDelta) {
        f = NucleoCliente.fase(player.getId(), tickDelta);
        fin = NucleoCliente.desdeFin(player.getId(), tickDelta);
        t = player.tickCount + tickDelta;
    }

    /** 0..1 cuánto de "carga", 0..1 cuánto de "disparo", y el peso total. */
    private float[] mezcla() {
        float carga, disparo, peso;
        if (f >= 0 && f < 1) {
            carga = suave(f / 0.7F);
            disparo = 0;
            peso = suave(f / 0.2F);
        } else if (f >= 1) {
            float u = (f - 1.0F) * 60.0F; // ticks de disparo
            disparo = suave(u / 3.0F);    // latigazo rápido
            carga = 1.0F - disparo;
            peso = 1;
        } else {
            carga = 0;
            disparo = 1;
            peso = 1.0F - suave(fin / 8.0F);
        }
        return new float[]{carga, disparo, peso};
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v) {
        float[] m = mezcla();
        float c = m[0], d = m[1], w = m[2];
        if (w <= 0.001F) return v;
        float temblor = (f >= 0 && f < 1 ? f * f * 0.05F : 0) + (f >= 1 ? 0.035F : 0);
        float n1 = Mth.sin(t * 2.9F) * temblor, n2 = Mth.sin(t * 3.7F + 1.3F) * temblor;

        if (type == TransformType.POSITION) {
            if (part.equals("body")) {
                // se agacha un poco al cargar; con el rayo, retrocede a golpecitos
                float y = -0.08F * c - 0.05F * d;
                float z = 0.04F * d * (0.6F + 0.4F * Mth.sin(t * 2.0F));
                return new Vec3f(v.getX(), v.getY() + y * w, v.getZ() + z * w);
            }
            return v;
        }
        if (type != TransformType.ROTATION) return v;
        switch (part) {
            case "body": {
                // playerAnimator: x>0 arquea hacia atrás, x<0 se inclina hacia delante
                float x = 0.14F * c - 0.12F * d + n1;
                return new Vec3f(Mth.lerp(w, v.getX(), x), Mth.lerp(w, v.getY(), n2 * 0.5F), Mth.lerp(w, v.getZ(), n2 * 0.4F));
            }
            case "head": {
                // carga: mirando al cielo; disparo: la cabeza sigue a donde miras (valor original) con un cabeceo
                float x = v.getX() - 0.75F * c + 0.08F * d + n2;
                return new Vec3f(Mth.lerp(w, v.getX(), x), v.getY(), Mth.lerp(w, v.getZ(), n1));
            }
            case "rightArm": {
                float x = 0.45F * c + 0.75F * d + n1;
                float z = 0.55F * c + 0.85F * d;
                return new Vec3f(Mth.lerp(w, v.getX(), x), Mth.lerp(w, v.getY(), 0), Mth.lerp(w, v.getZ(), z));
            }
            case "leftArm": {
                float x = 0.45F * c + 0.75F * d + n2;
                float z = -(0.55F * c + 0.85F * d);
                return new Vec3f(Mth.lerp(w, v.getX(), x), Mth.lerp(w, v.getY(), 0), Mth.lerp(w, v.getZ(), z));
            }
            case "rightLeg": {
                float z = 0.14F * c + 0.2F * d;
                return new Vec3f(Mth.lerp(w, v.getX(), -0.1F * d), v.getY(), Mth.lerp(w, v.getZ(), z));
            }
            case "leftLeg": {
                float z = -(0.14F * c + 0.2F * d);
                return new Vec3f(Mth.lerp(w, v.getX(), 0.25F * d), v.getY(), Mth.lerp(w, v.getZ(), z));
            }
            default:
                return v;
        }
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
