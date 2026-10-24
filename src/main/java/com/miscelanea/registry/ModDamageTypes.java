package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.level.Level;

/** Los tipos de daño en 1.20.1 se definen por datapack (data/miscelanea/damage_type/*.json). */
public final class ModDamageTypes {
    public static final ResourceKey<DamageType> ENEMIGO =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "enemigo"));

    public static final ResourceKey<DamageType> DESCARGA =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "descarga"));

    /** Descarga de la porra; el atacante cuenta para el mensaje de muerte. */
    public static DamageSource descarga(Level level, net.minecraft.world.entity.Entity atacante) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DESCARGA), atacante);
    }

    public static final ResourceKey<DamageType> NUCLEO =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "nucleo"));

    public static DamageSource nucleo(Level level, net.minecraft.world.entity.Entity atacante) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(NUCLEO), atacante);
    }

    public static DamageSource enemigo(Level level) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(ENEMIGO));
    }

    private ModDamageTypes() {}
}
