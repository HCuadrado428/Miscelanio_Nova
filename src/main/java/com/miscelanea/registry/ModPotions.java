package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.brewing.PotionMixRecipe;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModPotions {
    public static final DeferredRegister<Potion> POTIONS =
            DeferredRegister.create(ForgeRegistries.POTIONS, Miscelanea.MOD_ID);

    // --- Cupido (3:00 / 8:00 / 1:30 nivel II) ---
    public static final RegistryObject<Potion> CUPIDO = POTIONS.register("cupido",
            () -> new Potion("cupido", new MobEffectInstance(ModEffects.CUPIDO.get(), 3600)));
    public static final RegistryObject<Potion> LONG_CUPIDO = POTIONS.register("long_cupido",
            () -> new Potion("cupido", new MobEffectInstance(ModEffects.CUPIDO.get(), 9600)));
    public static final RegistryObject<Potion> STRONG_CUPIDO = POTIONS.register("strong_cupido",
            () -> new Potion("cupido", new MobEffectInstance(ModEffects.CUPIDO.get(), 1800, 1)));

    // --- Enemigo (1:00 / 2:00 / 0:30 nivel II) ---
    public static final RegistryObject<Potion> ENEMIGO = POTIONS.register("enemigo",
            () -> new Potion("enemigo", new MobEffectInstance(ModEffects.ENEMIGO.get(), 1200)));
    public static final RegistryObject<Potion> LONG_ENEMIGO = POTIONS.register("long_enemigo",
            () -> new Potion("enemigo", new MobEffectInstance(ModEffects.ENEMIGO.get(), 2400)));
    public static final RegistryObject<Potion> STRONG_ENEMIGO = POTIONS.register("strong_enemigo",
            () -> new Potion("enemigo", new MobEffectInstance(ModEffects.ENEMIGO.get(), 600, 1)));

    /**
     * Cupido:  Poción rara + Pétalos rosas
     * Enemigo: Cupido + Ojo de araña fermentado (el amor corrompido)
     * Redstone alarga, polvo de piedra luminosa potencia. Pólvora y aliento de dragón funcionan como en vanilla.
     */
    public static void registerBrewingRecipes() {
        add(Potions.AWKWARD, Items.PINK_PETALS, CUPIDO.get());
        add(CUPIDO.get(), Items.REDSTONE, LONG_CUPIDO.get());
        add(CUPIDO.get(), Items.GLOWSTONE_DUST, STRONG_CUPIDO.get());

        add(CUPIDO.get(), Items.FERMENTED_SPIDER_EYE, ENEMIGO.get());
        add(LONG_CUPIDO.get(), Items.FERMENTED_SPIDER_EYE, LONG_ENEMIGO.get());
        add(STRONG_CUPIDO.get(), Items.FERMENTED_SPIDER_EYE, STRONG_ENEMIGO.get());
        add(ENEMIGO.get(), Items.REDSTONE, LONG_ENEMIGO.get());
        add(ENEMIGO.get(), Items.GLOWSTONE_DUST, STRONG_ENEMIGO.get());
    }

    private static void add(Potion from, net.minecraft.world.item.Item ingredient, Potion to) {
        BrewingRecipeRegistry.addRecipe(new PotionMixRecipe(from, ingredient, to));
    }

    private ModPotions() {}
}
