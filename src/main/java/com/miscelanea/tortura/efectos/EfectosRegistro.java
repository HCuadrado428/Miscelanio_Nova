package com.miscelanea.tortura.efectos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de los "efectos" de la sala de juegos: el collar explosivo de Dead Money (con su detonador y los alicates
 * para desactivarlo) y la toxina del miedo del Espantapájaros (granada de gas, dardo y el efecto "miedo"). Va aparte de
 * los demás registros para no pisar otras ramas: basta con llamar a {@link #registrar(IEventBus)} desde el constructor
 * del mod. Todo es solo de creativo (sin recetas).
 */
public final class EfectosRegistro {
    private EfectosRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);

    // ---- tipo de daño (data/miscelanea/damage_type/collar_explosivo.json)
    public static final ResourceKey<DamageType> DANO_COLLAR =
            ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "collar_explosivo"));

    // ---- efecto
    public static final RegistryObject<MobEffect> MIEDO = EFFECTS.register("miedo", MiedoEffect::new);

    // ---- entidades
    public static final RegistryObject<EntityType<GranadaMiedoEntity>> GRANADA_LANZADA = ENTITIES.register("granada_miedo",
            () -> EntityType.Builder.<GranadaMiedoEntity>of(GranadaMiedoEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F).clientTrackingRange(8).updateInterval(2).build("granada_miedo"));
    public static final RegistryObject<EntityType<DardoMiedoEntity>> DARDO_LANZADO = ENTITIES.register("dardo_miedo",
            () -> EntityType.Builder.<DardoMiedoEntity>of(DardoMiedoEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F).clientTrackingRange(8).updateInterval(1).build("dardo_miedo"));

    // ---- objetos
    public static final RegistryObject<Item> COLLAR = ITEMS.register("collar_explosivo",
            () -> new CollarItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> DETONADOR = ITEMS.register("detonador",
            () -> new DetonadorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> ALICATES = ITEMS.register("alicates",
            () -> new AlicatesItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> GRANADA_MIEDO = ITEMS.register("granada_miedo",
            () -> new GranadaMiedoItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> DARDO_MIEDO = ITEMS.register("dardo_miedo",
            () -> new DardoMiedoItem(new Item.Properties().stacksTo(32).rarity(Rarity.RARE)));

    // ---- sonidos
    /** Collar: el pitido (más agudo y seguido al alejarse), el tono continuo antes de estallar, cerrar, abrir y estática. */
    public static final RegistryObject<SoundEvent> COLLAR_PITIDO = sonido("collar_pitido");
    public static final RegistryObject<SoundEvent> COLLAR_TONO = sonido("collar_tono");
    public static final RegistryObject<SoundEvent> COLLAR_CERRAR = sonido("collar_cerrar");
    public static final RegistryObject<SoundEvent> COLLAR_ABRIR = sonido("collar_abrir");
    public static final RegistryObject<SoundEvent> COLLAR_ESTATICA = sonido("collar_estatica");
    public static final RegistryObject<SoundEvent> COLLAR_ESTALLIDO = sonido("collar_estallido");
    public static final RegistryObject<SoundEvent> DETONADOR_CARGA = sonido("detonador_carga");
    /** Toxina del miedo. */
    public static final RegistryObject<SoundEvent> GAS_SISEO = sonido("gas_siseo");
    public static final RegistryObject<SoundEvent> CERBATANA = sonido("cerbatana");
    public static final RegistryObject<SoundEvent> MIEDO_SUSURROS = sonido("miedo_susurros");
    public static final RegistryObject<SoundEvent> MIEDO_LATIDO = sonido("miedo_latido");
    public static final RegistryObject<SoundEvent> MIEDO_SUSTO = sonido("miedo_susto");
    public static final RegistryObject<SoundEvent> MIEDO_ZUMBIDO = sonido("miedo_zumbido");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        ENTITIES.register(modBus);
        EFFECTS.register(modBus);
    }
}
