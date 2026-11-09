package com.miscelanea.dream;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * «Infección de Dream»: lo que le queda a quien ha recibido muchos mordiscos seguidos sin soltarse (ConfigServidor
 * contagio_*). Un jugador infectado puede morder como Dream, aunque más flojo (Mordiscos); un mob infectado envenena un
 * poco con sus golpes (Persecucion). La leche la cura. El efecto en sí no hace nada en el tick.
 */
public class InfeccionEfecto extends MobEffect {
    public InfeccionEfecto() {
        super(MobEffectCategory.HARMFUL, DreamRegistro.COLOR_INFECCION);
    }
}
