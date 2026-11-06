package com.miscelanea.laser;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Registro aparte del puntero láser (objeto, sonidos y receta de teñido). */
public final class LaserRegistro {
    private LaserRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECETAS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Miscelanea.MOD_ID);

    public static final RegistryObject<Item> PUNTERO = ITEMS.register("puntero_laser",
            () -> new PunteroLaserItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<SoundEvent> ENCENDER = sonido("laser_encender");
    public static final RegistryObject<SoundEvent> APAGAR = sonido("laser_apagar");

    public static final RegistryObject<RecipeSerializer<LaserTinteRecipe>> TINTE =
            RECETAS.register("laser_tinte", () -> new SimpleCraftingRecipeSerializer<>(LaserTinteRecipe::new));

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        RECETAS.register(modBus);
    }
}
