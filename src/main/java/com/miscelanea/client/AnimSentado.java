package com.miscelanea.client;

import com.miscelanea.muebles.Asiento;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Sentado en las sillas y sofás del mod (capa de playerAnimator, la más baja): solo dobla las rodillas (con bendy-lib,
 * ver {@link Articulaciones}) para que la espinilla cuelgue hasta el suelo en vez de ir recta hacia delante. La pose
 * de montar la sigue poniendo el juego.
 */
public class AnimSentado implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;

    public AnimSentado(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean sentado() {
        return Articulaciones.ACTIVO && p.getVehicle() instanceof Asiento;
    }

    @Override
    public void tick() {
        pesoO = peso;
        peso = Mth.clamp(peso + (sentado() ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || sentado();
    }

    @Override
    public void setupAnim(float td) {}

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        if (tipo != TransformType.BEND || !Articulaciones.pierna(parte)) return v;
        float k = Mth.lerp(td, pesoO, peso);
        return k <= 0 ? v : Articulaciones.fijar(parte, v, 1.45F, k);
    }
}
