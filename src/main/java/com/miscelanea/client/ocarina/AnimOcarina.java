package com.miscelanea.client.ocarina;

import com.miscelanea.ocarina.OcarinaItem;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Llevarse la ocarina a la boca con las dos manos (lo ven todos: "usar objeto" ya se sincroniza solo). */
public class AnimOcarina implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimOcarina(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (OcarinaItem.tocando(p) ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || OcarinaItem.tocando(p);
    }

    @Override
    public void setupAnim(float tickDelta) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float td, @NotNull Vec3f v) {
        if (type != TransformType.ROTATION) return v;
        float k = Mth.lerp(td, pesoO, peso);
        k = k * k * (3 - 2 * k);
        if (k <= 0) return v;
        // balanceo suave al tocar
        float bal = Mth.sin((p.tickCount + td) * 0.12F) * 0.05F;
        return switch (modelName) {
            case "rightArm" -> new Vec3f(Mth.lerp(k, v.getX(), -1.62F + bal), Mth.lerp(k, v.getY(), -0.62F), Mth.lerp(k, v.getZ(), 0.1F));
            case "leftArm" -> new Vec3f(Mth.lerp(k, v.getX(), -1.62F + bal), Mth.lerp(k, v.getY(), 0.62F), Mth.lerp(k, v.getZ(), -0.1F));
            case "head" -> new Vec3f(Mth.lerp(k, v.getX(), 0.12F + bal), v.getY(), v.getZ());
            case "body" -> new Vec3f(Mth.lerp(k, v.getX(), bal * 0.5F), v.getY(), v.getZ());
            default -> v;
        };
    }
}
