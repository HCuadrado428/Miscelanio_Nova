package com.miscelanea.client.trampolin;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Giro de 360/720º en el aire con postura de estrella (brazos y piernas abiertos). */
public class AnimTrampolin implements IAnimation {
    private final AbstractClientPlayer p;

    public AnimTrampolin(AbstractClientPlayer p) {
        this.p = p;
    }

    private float progreso(float td) {
        long[] t = TrampolinCliente.TRUCOS.get(p.getId());
        if (t == null) return -1;
        float x = (p.level().getGameTime() - t[0] + td) / t[1];
        if (x > 1.05F) {
            TrampolinCliente.TRUCOS.remove(p.getId());
            return -1;
        }
        return Math.min(1F, x);
    }

    @Override
    public void tick() {}

    @Override
    public boolean isActive() {
        return TrampolinCliente.TRUCOS.containsKey(p.getId());
    }

    @Override
    public void setupAnim(float td) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float td, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float x = progreso(td);
        if (x < 0) return v;
        long[] t = TrampolinCliente.TRUCOS.get(p.getId());
        int giros = t == null ? 1 : (int) t[2];
        float e = x * x * (3 - 2 * x);
        float estrella = Mth.sin(x * Mth.PI);       // se abre en el medio del salto
        return switch (modelName) {
            case "body" -> new Vec3f(v.getX(), v.getY() + e * Mth.TWO_PI * giros, v.getZ());
            case "rightArm" -> new Vec3f(Mth.lerp(estrella, v.getX(), -0.3F), v.getY(), Mth.lerp(estrella, v.getZ(), 2.4F));
            case "leftArm" -> new Vec3f(Mth.lerp(estrella, v.getX(), -0.3F), v.getY(), Mth.lerp(estrella, v.getZ(), -2.4F));
            case "rightLeg" -> new Vec3f(v.getX(), v.getY(), Mth.lerp(estrella, v.getZ(), 0.45F));
            case "leftLeg" -> new Vec3f(v.getX(), v.getY(), Mth.lerp(estrella, v.getZ(), -0.45F));
            default -> v;
        };
    }
}
