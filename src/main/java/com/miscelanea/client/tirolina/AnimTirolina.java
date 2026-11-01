package com.miscelanea.client.tirolina;

import com.miscelanea.tirolina.Polea;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Colgado de la polea: brazos arriba agarrando la barra y piernas colgando que se balancean con la velocidad. */
public class AnimTirolina implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimTirolina(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean colgado() {
        return p.getVehicle() instanceof Polea;
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (colgado() ? 0.34F : -0.34F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || colgado();
    }

    @Override
    public void setupAnim(float td) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float td, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        float vel = p.getVehicle() instanceof Polea po ? Math.abs(po.velocidad()) : 0;
        float t = p.tickCount + td;
        float bal = Mth.sin(t * 0.22F) * (0.12F + vel * 0.25F);
        return switch (modelName) {
            case "rightArm" -> new Vec3f(Mth.lerp(k, v.getX(), -2.95F), Mth.lerp(k, v.getY(), 0F), Mth.lerp(k, v.getZ(), 0.18F));
            case "leftArm" -> new Vec3f(Mth.lerp(k, v.getX(), -2.95F), Mth.lerp(k, v.getY(), 0F), Mth.lerp(k, v.getZ(), -0.18F));
            case "rightLeg" -> new Vec3f(Mth.lerp(k, v.getX(), 0.15F + bal + vel * 0.35F), v.getY(), Mth.lerp(k, v.getZ(), 0.06F));
            case "leftLeg" -> new Vec3f(Mth.lerp(k, v.getX(), 0.15F - bal + vel * 0.35F), v.getY(), Mth.lerp(k, v.getZ(), -0.06F));
            case "body" -> new Vec3f(Mth.lerp(k, v.getX(), bal * 0.4F), v.getY(), v.getZ());
            default -> v;
        };
    }
}
