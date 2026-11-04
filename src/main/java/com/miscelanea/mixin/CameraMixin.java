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

    /** Ejecución de la trampa de oso: tercera persona de frente, más cerca y más baja (ver EjecucionCliente). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void miscelanea$camaraEjecucion(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (!detached || !reverse) return;
        double[] r = com.miscelanea.client.tortura.EjecucionCliente.colocarCamara(level, entity, partial);
        // cinemáticas de muerte de los aparatos (ver MuertesCliente)
        if (r == null) r = com.miscelanea.client.tortura.muertes.MuertesCliente.colocarCamara(level, entity, partial);
        if (r == null) return;
        setRotation((float) r[3], (float) r[4]);
        setPosition(r[0], r[1], r[2]);
    }

    /** Colgado boca abajo del gancho: en primera persona, los ojos van abajo, en la cabeza (ver InterrogaCliente). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void miscelanea$camaraColgado(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (detached) return;
        double[] r = com.miscelanea.client.tortura.aparatos.InterrogaCliente.ojoColgado(entity, partial);
        if (r != null) setPosition(r[0], r[1], r[2]);
    }

    /** Toxina del miedo: el dolly del vértigo (la cámara avanza mientras se abre el campo de visión; ver MiedoCamara). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void miscelanea$camaraVertigo(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (detached) return;
        net.minecraft.world.phys.Vec3 v = com.miscelanea.client.tortura.efectos.MiedoCamara.dolly(level, (net.minecraft.client.Camera) (Object) this);
        if (v != null) setPosition(v.x, v.y, v.z);
    }

    /** Pista de baile: cámara de cine que mira al que baila y cambia de plano con la música (ver CamaraBaile). */
    @Inject(method = "setup", at = @At("TAIL"))
    private void miscelanea$camaraBaile(BlockGetter level, Entity entity, boolean detached, boolean reverse, float partial, CallbackInfo ci) {
        if (!detached) return;
        double[] r = com.miscelanea.client.baile.CamaraBaile.colocar(level, entity, partial);
        if (r == null) return;
        setRotation((float) r[3], (float) r[4]);
        setPosition(r[0], r[1], r[2]);
    }
}
