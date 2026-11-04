package com.miscelanea.client.estatica;

import com.miscelanea.client.Articulaciones;
import com.miscelanea.client.tortura.muertes.Pista;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Capa de playerAnimator del hombre de estática: la transformación (deltas de {@link EstaticaCine}, que se suman a la pose
 * de debajo) y el forcejeo de quien está agarrado por los tentáculos (brazos y piernas a tirones).
 */
public class AnimEstatica implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO, lucha, luchaO;

    public AnimEstatica(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        luchaO = lucha;
        float obj = EstaticaCine.activa(p.getId()) ? 1F : 0F;
        peso = peso < obj ? Math.min(obj, peso + 0.34F) : Math.max(obj, peso - 0.2F);
        float ol = EfectosMundo.agarrado(p.getId()) ? 1F : 0F;
        lucha = lucha < ol ? Math.min(ol, lucha + 0.4F) : Math.max(ol, lucha - 0.15F);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || lucha > 0 || luchaO > 0 || EstaticaCine.activa(p.getId()) || EfectosMundo.agarrado(p.getId());
    }

    @Override
    public void setupAnim(float td) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso), kl = Mth.lerp(td, luchaO, lucha);
        if (k > 0) {
            EstaticaCine.Activa a = EstaticaCine.de(p.getId());
            if (a != null) {
                float t = EstaticaCine.tiempo(a, td);
                float c = Articulaciones.K;
                if (tipo == TransformType.POSITION && parte.equals("body")) {
                    float[] o = EstaticaCine.parte(a, "pos", t), aj = EstaticaCine.ajuste(a, "pos", t);
                    v = new Vec3f(v.getX() + (o[0] + aj[0] * c) * k, v.getY() + (o[1] + aj[1] * c) * k, v.getZ() + (o[2] + aj[2] * c) * k);
                } else if (tipo == TransformType.ROTATION) {
                    String n = parte.equals("body") ? "body" : parte;
                    if (n.equals("body") || n.equals("torso") || n.equals("head") || n.endsWith("Arm") || n.endsWith("Leg")) {
                        float[] o = EstaticaCine.parte(a, n, t), aj = EstaticaCine.ajuste(a, n, t);
                        v = new Vec3f(v.getX() + (o[0] + aj[0] * c) * k, v.getY() + (o[1] + aj[1] * c) * k, v.getZ() + (o[2] + aj[2] * c) * k);
                    }
                } else if (tipo == TransformType.BEND) {
                    v = Articulaciones.sumar(parte, v, EstaticaCine.dobla(a, parte, t), k);
                }
            }
        }
        if (kl > 0 && tipo == TransformType.ROTATION) {
            float t = (p.tickCount + td) * 1.0F;
            int s = switch (parte) {
                case "rightArm" -> 1;
                case "leftArm" -> 2;
                case "rightLeg" -> 3;
                case "leftLeg" -> 4;
                case "head" -> 5;
                case "torso" -> 6;
                default -> 0;
            };
            if (s > 0) {
                boolean brazo = s <= 2, pierna = s == 3 || s == 4;
                float ax = brazo ? -1.6F + Pista.sacudida(t, s * 7) * 0.9F : pierna ? Pista.sacudida(t, s * 7) * 0.7F : Pista.sacudida(t, s * 7) * 0.3F;
                float az = brazo ? (s == 1 ? 0.5F : -0.5F) + Pista.sacudida(t, s * 7 + 3) * 0.4F : Pista.sacudida(t, s * 7 + 3) * 0.15F;
                v = new Vec3f(v.getX() + ax * kl, v.getY() + Pista.sacudida(t, s * 7 + 5) * 0.2F * kl, v.getZ() + az * kl);
            }
        }
        if (kl > 0 && tipo == TransformType.BEND && (Articulaciones.brazo(parte) || Articulaciones.pierna(parte))) {
            // agarrado: codos y rodillas a tirones
            float t = (p.tickCount + td) * 1.0F;
            int s = parte.startsWith("right") ? 1 : 2;
            float f = Articulaciones.brazo(parte) ? 0.6F + Pista.sacudida(t, s * 13) * 0.5F : 0.3F + Pista.sacudida(t, s * 13 + 4) * 0.3F;
            v = Articulaciones.sumar(parte, v, f, kl);
        }
        return v;
    }
}
