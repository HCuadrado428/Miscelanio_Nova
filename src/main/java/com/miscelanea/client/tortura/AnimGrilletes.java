package com.miscelanea.client.tortura;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Jugador con grilletes (capa de playerAnimator): brazos juntos por delante, muñecas pegadas, algo de balanceo al andar. */
public class AnimGrilletes implements IAnimation {
    /** Rotación (x, y, z) del brazo derecho; el izquierdo es el espejo. Calculada para que las manos se junten delante. */
    public static final float RX = -0.70F, RY = -0.40F, RZ = -0.45F;

    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimGrilletes(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean atado() {
        return TorturaCliente.atado(p.getId());
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (atado() ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || atado();
    }

    @Override
    public void setupAnim(float td) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float td, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        float bal = Mth.sin((p.tickCount + td) * 0.12F) * 0.04F + Mth.sin(p.walkAnimation.position(td) * 0.6662F) * 0.06F * p.walkAnimation.speed(td);
        return switch (modelName) {
            case "rightArm" -> new Vec3f(Mth.lerp(k, v.getX(), RX + bal), Mth.lerp(k, v.getY(), RY), Mth.lerp(k, v.getZ(), RZ));
            case "leftArm" -> new Vec3f(Mth.lerp(k, v.getX(), RX + bal), Mth.lerp(k, v.getY(), -RY), Mth.lerp(k, v.getZ(), -RZ));
            default -> v;
        };
    }
}
