package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import com.miscelanea.medigun.MedigunTinteRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Miscelanea.MOD_ID);

    public static final RegistryObject<RecipeSerializer<MedigunTinteRecipe>> MEDIGUN_TINTE =
            SERIALIZERS.register("medigun_tinte", () -> new SimpleCraftingRecipeSerializer<>(MedigunTinteRecipe::new));

    private ModRecipes() {}
}
