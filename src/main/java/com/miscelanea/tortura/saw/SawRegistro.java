package com.miscelanea.tortura.saw;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de los juegos de Jigsaw (Saw): la caja de agua con su tanque. Va aparte del resto de la sala de juegos para no pisar a otras ramas:
 * basta con llamar a {@link #registrar(IEventBus)} desde el constructor del mod. Todo es solo de creativo (sin recetas).
 */
public final class SawRegistro {
    private SawRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);

    // ---- tipos de daño (data/miscelanea/damage_type/*.json)
    public static final ResourceKey<DamageType> DANO_CAJA = dano("caja_agua");
    public static final ResourceKey<DamageType> DANO_CUCHILLAS = dano("caja_agua_cuchillas");

    private static ResourceKey<DamageType> dano(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    // ---- bloques
    public static final RegistryObject<Block> TANQUE = BLOCKS.register("tanque_agua", () -> new TanqueAguaBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()
                    .isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false)));

    // ---- objetos
    public static final RegistryObject<Item> CAJA_AGUA = ITEMS.register("caja_agua",
            () -> new CajaAguaItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> TANQUE_ITEM = ITEMS.register("tanque_agua",
            () -> new SawBlockItem(TANQUE.get(), new Item.Properties().stacksTo(16).rarity(Rarity.EPIC), 2));

    // ---- sonidos (scratchpad/gen_tortura_saw.py)
    public static final RegistryObject<SoundEvent> JUEGO = sonido("saw_juego");
    public static final RegistryObject<SoundEvent> CAJA_LLENAR = sonido("saw_caja_llenar");
    public static final RegistryObject<SoundEvent> CAJA_BURBUJAS = sonido("saw_caja_burbujas");
    public static final RegistryObject<SoundEvent> CAJA_CUCHILLAS = sonido("saw_caja_cuchillas");
    public static final RegistryObject<SoundEvent> CAJA_VACIAR = sonido("saw_caja_vaciar");
    public static final RegistryObject<SoundEvent> CAJA_ABRIR = sonido("saw_caja_abrir");
    public static final RegistryObject<SoundEvent> TANQUE_PALANCA = sonido("saw_tanque_palanca");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        BLOCKS.register(modBus);
        SOUNDS.register(modBus);
    }

}
