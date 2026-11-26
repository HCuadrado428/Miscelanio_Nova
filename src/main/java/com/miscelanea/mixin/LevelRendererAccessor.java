package com.miscelanea.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ViewArea;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Estado de "qué trozos del mundo se ven" del dibujado. Se calcula para una sola cámara y en segundo plano: al dibujar desde
 * una cámara de vigilancia hay que guardar el del jugador, usar uno propio de la cámara y devolver el del jugador; si no, la
 * vista del jugador y la de la cámara se pisan y faltan trozos de mundo (parpadeos). Ver CamarasCliente.
 */
@Mixin(LevelRenderer.class)
public interface LevelRendererAccessor {
    @SuppressWarnings("rawtypes")
    @Accessor("renderChunkStorage")
    AtomicReference miscelanea$almacen();

    @Accessor("lastFullRenderChunkUpdate")
    Future<?> miscelanea$calculo();

    @Accessor("needsFullRenderChunkUpdate")
    boolean miscelanea$recalcular();

    @Accessor("needsFullRenderChunkUpdate")
    void miscelanea$recalcular(boolean b);

    @Accessor("needsFrustumUpdate")
    AtomicBoolean miscelanea$recortar();

    @Accessor("viewArea")
    ViewArea miscelanea$zona();

    @Accessor("prevCamX")
    double miscelanea$x();

    @Accessor("prevCamX")
    void miscelanea$x(double v);

    @Accessor("prevCamY")
    double miscelanea$y();

    @Accessor("prevCamY")
    void miscelanea$y(double v);

    @Accessor("prevCamZ")
    double miscelanea$z();

    @Accessor("prevCamZ")
    void miscelanea$z(double v);

    @Accessor("prevCamRotX")
    double miscelanea$rx();

    @Accessor("prevCamRotX")
    void miscelanea$rx(double v);

    @Accessor("prevCamRotY")
    double miscelanea$ry();

    @Accessor("prevCamRotY")
    void miscelanea$ry(double v);
}
