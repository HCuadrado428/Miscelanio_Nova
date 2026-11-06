package com.miscelanea.movil;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro del móvil: el objeto, su receta de tinte, sus sonidos (generados por código, sin copyright) y su canal de red.
 * Basta con {@link #registrar(IEventBus)} desde el constructor del mod.
 */
public final class MovilRegistro {
    private MovilRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SONIDOS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECETAS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Miscelanea.MOD_ID);

    public static final RegistryObject<Item> MOVIL = ITEMS.register("movil", () -> new MovilItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<RecipeSerializer<MovilTinteReceta>> TINTE =
            RECETAS.register("movil_tinte", () -> new SimpleCraftingRecipeSerializer<>(MovilTinteReceta::new));

    /** Tonos de llamada (Ajustes → Tono). */
    public static final int TONOS = 3;
    @SuppressWarnings("unchecked")
    public static final RegistryObject<SoundEvent>[] TONO = new RegistryObject[]{sonido("movil_tono_0"), sonido("movil_tono_1"), sonido("movil_tono_2")};
    public static final RegistryObject<SoundEvent> VIBRAR = sonido("movil_vibrar");
    public static final RegistryObject<SoundEvent> MENSAJE = sonido("movil_mensaje");
    public static final RegistryObject<SoundEvent> OBTURADOR = sonido("movil_obturador");
    public static final RegistryObject<SoundEvent> CLIC = sonido("movil_clic");
    public static final RegistryObject<SoundEvent> ESPERA = sonido("movil_espera");
    public static final RegistryObject<SoundEvent> COLGAR = sonido("movil_colgar");
    public static final RegistryObject<SoundEvent> ALARMA = sonido("movil_alarma");
    /** Notas de voz: sonido "en streaming" cuyo audio lo da el propio SoundInstance (el archivo de sounds.json no se usa). */
    public static final RegistryObject<SoundEvent> NOTA_VOZ = sonido("movil_nota_voz");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SONIDOS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus bus) {
        ITEMS.register(bus);
        SONIDOS.register(bus);
        RECETAS.register(bus);
        MovilRed.registrar();
    }

    public static boolean esMovil(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof MovilItem;
    }
}
