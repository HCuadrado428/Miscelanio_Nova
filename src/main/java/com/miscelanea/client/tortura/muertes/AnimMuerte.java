package com.miscelanea.client.tortura.muertes;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Cinemáticas de muerte en los jugadores (capa de playerAnimator por encima de las de los aparatos y de la trampa de
 * oso). Las pistas son DELTAS que se suman a la pose que ya traen las capas de abajo (sentado en la silla, tumbado en el
 * potro, colgado...), con el temblor y la convulsión de la cinemática. Entra en 3 ticks y, al morir, se apaga en medio
 * segundo (manda la animación de muerte normal).
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
        boolean act = MuertesCliente.activa(p.getId());
        if (act && p.isDeadOrDying()) muerto++;
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
        if (tipo == TransformType.POSITION) {
            float[] o;
            if (parte.equals("body")) o = MuertesCliente.parte(a, 0, t);
            else {
                o = MuertesCliente.posicion(a, parte + "Pos", t);
                if (o == null) return v;
            }
            return new Vec3f(v.getX() + o[0] * k, v.getY() + o[1] * k, v.getZ() + o[2] * k);
        }
        if (tipo != TransformType.ROTATION) return v;
        int i = indice(parte);
        if (i < 0) return v;
        float[] o = MuertesCliente.parte(a, i, t);
        return new Vec3f(v.getX() + o[0] * k, v.getY() + o[1] * k, v.getZ() + o[2] * k);
    }
}
