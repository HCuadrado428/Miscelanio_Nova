package com.miscelanea.client.bromas;

import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.client.Articulaciones;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Postura en la rueda de Catalina (capa de playerAnimator por encima de las de los aparatos): en aspa, con los brazos
 * abiertos hacia arriba (muñecas atadas a la llanta) y las piernas separadas (tobillos atados abajo). Tirones contra las
 * correas de vez en cuando y más cuando le hacen daño. El giro del cuerpo entero lo hace RuedaCliente al dibujarlo.
 */
public class AnimRueda implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimRueda(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean enRueda() {
        return AparatosVictimas.tipo(p.getId()) == AparatosVictimas.RUEDA;
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (enRueda() ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || enRueda();
    }

    @Override
    public void setupAnim(float td) {}

    private static Vec3f mezclar(float k, Vec3f v, float x, float y, float z) {
        return new Vec3f(Mth.lerp(k, v.getX(), x), Mth.lerp(k, v.getY(), y), Mth.lerp(k, v.getZ(), z));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tt, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0 || (tt != TransformType.ROTATION && tt != TransformType.BEND)) return v;
        float t = p.tickCount + td;
        float tiron = Mth.sin(t * 1.7F) * Math.max(0F, Mth.sin(t * 0.23F)) * 0.06F + (p.hurtTime > 0 ? Mth.sin(t * 4.1F) * 0.1F : 0F);
        if (tt == TransformType.BEND) {
            // atado por muñecas y tobillos: casi recto, pero al tirar de las correas se le encogen codos y rodillas
            float f = Math.abs(tiron);
            if (Articulaciones.brazo(parte)) return Articulaciones.fijar(parte, v, 0.12F + f * 2.2F, k);
            if (Articulaciones.pierna(parte)) return Articulaciones.fijar(parte, v, 0.08F + f * 1.6F, k);
            return v;
        }
        return switch (parte) {
            case "rightArm" -> mezclar(k, v, tiron * 0.5F, 0F, 2.36F + tiron);
            case "leftArm" -> mezclar(k, v, -tiron * 0.5F, 0F, -2.36F - tiron);
            case "rightLeg" -> mezclar(k, v, tiron * 0.3F, 0F, 0.38F + tiron * 0.4F);
            case "leftLeg" -> mezclar(k, v, -tiron * 0.3F, 0F, -0.38F - tiron * 0.4F);
            case "body" -> mezclar(k, v, 0F, 0F, tiron * 0.15F);
            case "head" -> mezclar(k, v, Mth.clamp(v.getX(), -0.5F, 0.5F), v.getY() * 0.7F, tiron * 0.3F);
            default -> v;
        };
    }
}
