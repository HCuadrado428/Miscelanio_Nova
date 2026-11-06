package com.miscelanea.client.movil;

import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * Pose de usar el móvil (playerAnimator): el brazo que lo sujeta sube delante de la cara con la pantalla hacia los ojos y
 * la cabeza baja un poco; en modo selfie el brazo se estira más arriba y la cabeza mira al móvil. Los ángulos salen de
 * simular la cadena de vanilla (brazo → ItemInHandLayer → transformación thirdperson del modelo {@code movil.json}).
 */
public class AnimMovil implements IAnimation {
    private static final float[] MIRAR = {-1.362F, -0.472F, 0.034F}, SELFIE = {-1.628F, -0.226F, 0.272F};
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int modo = MovilRed.POSE_MIRAR;

    public AnimMovil(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        int m = MovilCliente.poseDe(p);
        if (m != MovilRed.POSE_NADA) modo = m;
        peso = Mth.clamp(peso + (m != MovilRed.POSE_NADA ? 0.25F : -0.2F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || MovilCliente.poseDe(p) != MovilRed.POSE_NADA;
    }

    @Override
    public void setupAnim(float tickDelta) {}

    /** El brazo que sujeta el móvil: el de la mano que lo tiene, o el de la mano libre. */
    static boolean izquierda(AbstractClientPlayer p) {
        HumanoidArm principal = p.getMainArm();
        boolean enPrincipal = MovilRegistro.esMovil(p.getMainHandItem()), enOtra = MovilRegistro.esMovil(p.getOffhandItem());
        boolean usaPrincipal = enPrincipal || (!enOtra && (p.getMainHandItem().isEmpty() || !p.getOffhandItem().isEmpty()));
        HumanoidArm brazo = usaPrincipal ? principal : principal.getOpposite();
        return brazo == HumanoidArm.LEFT;
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType type, float td, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float x = Mth.lerp(td, pesoO, peso);
        float k = x * x * (3 - 2 * x);
        if (k <= 0) return v;
        float[] a = modo == MovilRed.POSE_SELFIE ? SELFIE : MIRAR;
        boolean izq = izquierda(p);
        return switch (parte) {
            case "rightArm" -> izq ? v : new Vec3f(Mth.lerp(k, v.getX(), a[0]), Mth.lerp(k, v.getY(), a[1]), Mth.lerp(k, v.getZ(), a[2]));
            case "leftArm" -> !izq ? v : new Vec3f(Mth.lerp(k, v.getX(), a[0]), Mth.lerp(k, v.getY(), -a[1]), Mth.lerp(k, v.getZ(), -a[2]));
            case "head" -> new Vec3f(Mth.lerp(k, v.getX(), modo == MovilRed.POSE_SELFIE ? -0.08F : 0.38F),
                    Mth.lerp(k * 0.6F, v.getY(), (izq ? 1 : -1) * (modo == MovilRed.POSE_SELFIE ? 0.12F : 0.18F)), v.getZ());
            default -> v;
        };
    }
}
