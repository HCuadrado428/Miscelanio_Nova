package com.miscelanea.client.pipboy;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Levantar el brazo izquierdo para mirar el Pip-Boy (y bajar la cabeza hacia la muñeca). */
public class AnimPipBoy implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimPipBoy(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (PipBoyCliente.mirando(p) ? 0.2F : -0.2F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || PipBoyCliente.mirando(p);
    }

    private float k(float td) {
        float x = Mth.lerp(td, pesoO, peso);
        return x * x * (3 - 2 * x);
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float k = k(tickDelta);
        if (k <= 0) return v;
        return switch (modelName) {
            case "leftArm" -> new Vec3f(Mth.lerp(k, v.getX(), -1.75F), Mth.lerp(k, v.getY(), 0.95F), Mth.lerp(k, v.getZ(), 0F));
            case "head" -> new Vec3f(Mth.lerp(k, v.getX(), 0.45F), Mth.lerp(k, v.getY(), 0.2F), v.getZ());
            default -> v;
        };
    }
}
