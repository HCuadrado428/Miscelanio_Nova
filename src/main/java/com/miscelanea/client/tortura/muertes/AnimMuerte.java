package com.miscelanea.client.tortura.muertes;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import com.miscelanea.client.Articulaciones;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Cinemáticas de muerte en los jugadores (capa de playerAnimator por encima de las de los aparatos y de la trampa de
 * oso). Las pistas son DELTAS que se suman a la pose que ya traen las capas de abajo (sentado en la silla, tumbado en el
 * potro, colgado...), con el temblor y la convulsión de la cinemática. Entra en 3 ticks y, al morir, se apaga en medio
 * segundo (manda la animación de muerte normal).
 * <p>
 * Codos y rodillas (con bendy-lib, ver {@link Articulaciones}): las pistas "&lt;pieza&gt;Dobla" suman flexión y las
 * "&lt;pieza&gt;Aj" / "posAj" retocan la pose solo si las piezas se doblan de verdad (arrodillarse sin hundirse...).
 */
public class AnimMuerte implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int muerto;

    public AnimMuerte(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        MuertesCliente.Activa a = MuertesCliente.de(p.getId());
        boolean act = a != null;
        // las que mueren tumbadas (el veneno) no se apagan: el cuerpo se queda en el suelo
        if (act && p.isDeadOrDying() && !a.def.yace) muerto++;
        else muerto = 0;
        float objetivo = !act ? 0F : Math.max(0F, 1F - muerto / 10F);
        peso = peso < objetivo ? Math.min(objetivo, peso + 0.34F) : Math.max(objetivo, peso - 0.2F);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || MuertesCliente.activa(p.getId());
    }

    @Override
    public void setupAnim(float td) {}

    private static int indice(String parte) {
        return switch (parte) {
            case "body" -> 1;
            case "torso" -> 2;
            case "head" -> 3;
            case "rightArm" -> 4;
            case "leftArm" -> 5;
            case "rightLeg" -> 6;
            case "leftLeg" -> 7;
            default -> -1;
        };
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        MuertesCliente.Activa a = MuertesCliente.de(p.getId());
        if (a == null) return v;
        float t = MuertesCliente.tiempo(a, td);
        // tumbada y muerta: se deshace la vuelta de lado de vanilla (setupRotations gira Z alrededor de los pies antes
        // que esto), para que se quede boca abajo
        float d = a.def.yace && parte.equals("body") ? giroMuerte(td) : 0F;
        if (tipo == TransformType.POSITION) {
            float[] o;
            if (parte.equals("body")) o = MuertesCliente.parte(a, 0, t);
            else {
                o = MuertesCliente.posicion(a, parte + "Pos", t);
                if (o == null) return v;
            }
            if (parte.equals("body") && Articulaciones.ACTIVO) {
                Pista aj = a.def.pista("posAj");
                if (aj != null) o = new float[]{o[0] + aj.en(t, 0), o[1] + aj.en(t, 1), o[2] + aj.en(t, 2)};
            }
            float x = v.getX() + o[0] * k, y = v.getY() + o[1] * k, z = v.getZ() + o[2] * k;
            if (d != 0F) {
                // Rz(d)·T(p') = T(p)·Rz(d) con p' = Rz(-d)(p + 0,7) - 0,7 (el giro del cuerpo de playerAnimator es en 0,7)
                float c = Mth.cos(-d), s = Mth.sin(-d), yy = y + 0.7F;
                return new Vec3f(x * c - yy * s, x * s + yy * c - 0.7F, z);
            }
            return new Vec3f(x, y, z);
        }
        if (tipo == TransformType.BEND) {
            if (!Articulaciones.brazo(parte) && !Articulaciones.pierna(parte)) return v;
            return Articulaciones.sumar(parte, v, a.def.x(parte + "Dobla", t, 0F), k);
        }
        if (tipo != TransformType.ROTATION) return v;
        int i = indice(parte);
        if (i < 0) return v;
        float[] o = MuertesCliente.parte(a, i, t);
        if (Articulaciones.ACTIVO) {
            Pista aj = a.def.pista(parte + "Aj");
            if (aj != null) {
                o[0] += aj.en(t, 0);
                o[1] += aj.en(t, 1);
                o[2] += aj.en(t, 2);
            }
        }
        return new Vec3f(v.getX() + o[0] * k, v.getY() + o[1] * k, v.getZ() + o[2] * k - d);
    }

    /** El giro (rad) de la caída de lado de vanilla al morir (LivingEntityRenderer.setupRotations). */
    private float giroMuerte(float td) {
        if (p.deathTime <= 0) return 0F;
        float f = (p.deathTime + td - 1F) / 20F * 1.6F;
        f = Mth.sqrt(Math.max(0F, f));
        if (f > 1F) f = 1F;
        return f * 90F * Mth.DEG_TO_RAD;
    }
}
