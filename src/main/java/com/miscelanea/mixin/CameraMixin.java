package com.miscelanea.mixin;

import com.miscelanea.client.combate.CamaraCombate;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cámara de combate: en tercera persona, con un arma con estilo, se coloca como en su juego (ver CamaraCombate). */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"))
    private void miscelanea$camaraCombate(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (!detached || reverse) return;
        double[] r = CamaraCombate.colocar(level, entity, partial);
        if (r == null) return;
        setRotation((float) r[3], (float) r[4]);
        setPosition(r[0], r[1], r[2]);
    }
}
