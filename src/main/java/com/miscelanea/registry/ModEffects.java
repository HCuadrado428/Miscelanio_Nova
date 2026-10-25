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

    public static final RegistryObject<MobEffect> PSICOSIS = EFFECTS.register("psicosis", com.miscelanea.carcel.PsicosisEffect::new);
    public static final RegistryObject<MobEffect> INMUNIDAD_GAS = EFFECTS.register("inmunidad_gas", com.miscelanea.carcel.InmunidadEffect::new);

    /** Adrenalina: corres más y no gastas aguante. */
    public static final RegistryObject<MobEffect> ADRENALINA = EFFECTS.register("adrenalina",
            () -> new MobEffect(net.minecraft.world.effect.MobEffectCategory.BENEFICIAL, 0xE8D040) {}
                    .addAttributeModifier(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED,
                            "6b1e0c7a-3a51-4d0e-9f22-2c4a5e9b1a01", 0.3, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));

    private ModEffects() {}
}
