package com.miscelanea.programa;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Constantes compartidas por cliente y servidor. */
public final class Programa {
    public static final ResourceKey<Level> DIM = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Miscelanea.MOD_ID, "programa"));

    private Programa() {}
}
