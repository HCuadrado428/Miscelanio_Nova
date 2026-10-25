package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.effect.CupidoEffect;
import com.miscelanea.effect.EnemigoEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);

    public static final RegistryObject<MobEffect> CUPIDO = EFFECTS.register("cupido", CupidoEffect::new);
    public static final RegistryObject<MobEffect> ENEMIGO = EFFECTS.register("enemigo", EnemigoEffect::new);

    public static final RegistryObject<MobEffect> UBERCARGA = EFFECTS.register("ubercarga", com.miscelanea.medigun.UberEffect::new);

    private ModEffects() {}
}
