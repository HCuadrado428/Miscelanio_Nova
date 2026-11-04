package com.miscelanea.bromas;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModTabs;
import com.miscelanea.tortura.aparatos.RuedaRegistro;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Registro de las bromas (objetos de creativo para gastar bromas pesadas pero inofensivas a otros jugadores) y de la
 * rueda de Catalina ({@link RuedaRegistro}). Va aparte de los registros comunes para no pisar a otras ramas: basta con
 * llamar a {@link #registrar(IEventBus)} desde el constructor del mod. Todo es solo de creativo (sin recetas).
 * <ul>
 *   <li>Varita uwu: el chat de la víctima sale en "uwu" durante unos minutos (efecto {@link #UWU}).</li>
 *   <li>Poción de controles cambiados: arrojadiza; W↔S y A↔D (efecto {@link #CONTROLES}).</li>
 *   <li>Cartel de "PATÉAME": se le pega en la espalda; cada golpe que recibe suelta confeti y un "boing".</li>
 *   <li>Duende travieso (en un tarro): le revuelve el inventario cada poco (efecto {@link #DUENDE}).</li>
 *   <li>Espejo del revés: la pantalla de la víctima se pone boca abajo (efecto {@link #REVES}).</li>
 * </ul>
 */
public final class BromasRegistro {
    private BromasRegistro() {}

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, Miscelanea.MOD_ID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Miscelanea.MOD_ID);

    /** Color del líquido de la poción de controles cambiados (verde ácido). */
    public static final int COLOR_CONTROLES = 0x9BE22B;

    // ---- efectos
    public static final RegistryObject<MobEffect> UWU = EFFECTS.register("hechizo_uwu",
            () -> new BromaEfecto(MobEffectCategory.NEUTRAL, 0xFF8FD0));
    public static final RegistryObject<MobEffect> CONTROLES = EFFECTS.register("controles_cambiados",
            () -> new BromaEfecto(MobEffectCategory.HARMFUL, COLOR_CONTROLES));
    public static final RegistryObject<MobEffect> DUENDE = EFFECTS.register("duende_travieso",
            () -> new BromaEfecto(MobEffectCategory.HARMFUL, 0x4FB83A));
    public static final RegistryObject<MobEffect> REVES = EFFECTS.register("pantalla_reves",
            () -> new BromaEfecto(MobEffectCategory.HARMFUL, 0x8EC8F0));

    // ---- objetos (solo de creativo)
    public static final RegistryObject<Item> VARITA_UWU = ITEMS.register("varita_uwu",
            () -> new BromaItem(BromaItem.Broma.UWU, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> POCION_CONTROLES = ITEMS.register("pocion_controles",
            () -> new PocionControlesItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CARTEL = ITEMS.register("cartel_pateame",
            () -> new BromaItem(BromaItem.Broma.CARTEL, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> DUENDE_TARRO = ITEMS.register("duende_tarro",
            () -> new BromaItem(BromaItem.Broma.DUENDE, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> ESPEJO = ITEMS.register("espejo_reves",
            () -> new BromaItem(BromaItem.Broma.REVES, new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    // ---- entidad: la poción de controles en vuelo
    public static final RegistryObject<EntityType<PocionControlesEntity>> POCION_LANZADA = ENTITIES.register("pocion_controles",
            () -> EntityType.Builder.<PocionControlesEntity>of(PocionControlesEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F).clientTrackingRange(8).updateInterval(10).build("pocion_controles"));

    // ---- partícula: confeti de colores que cae dando vueltas
    public static final RegistryObject<SimpleParticleType> CONFETI = PARTICLES.register("confeti", () -> new SimpleParticleType(false));

    // ---- sonidos (procedurales, scratchpad/gen_bromas.py)
    public static final RegistryObject<SoundEvent> BOING = sonido("bromas_boing");
    public static final RegistryObject<SoundEvent> MAGIA_UWU = sonido("bromas_uwu");

    private static RegistryObject<SoundEvent> sonido(String n) {
        return SOUNDS.register(n, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, n)));
    }

    public static void registrar(IEventBus modBus) {
        ITEMS.register(modBus);
        EFFECTS.register(modBus);
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        PARTICLES.register(modBus);
        RuedaRegistro.registrar(modBus);
        // antes que Miscelanea.creativeTabs (que comprueba qué objetos se han quedado sin pestaña)
        modBus.addListener(EventPriority.HIGH, BromasRegistro::pestanas);
    }

    private static void pestanas(BuildCreativeModeTabContentsEvent e) {
        if (ModTabs.BROMAS.getKey().equals(e.getTabKey())) {
            e.accept(VARITA_UWU);
            e.accept(POCION_CONTROLES);
            e.accept(CARTEL);
            e.accept(DUENDE_TARRO);
            e.accept(ESPEJO);
        } else if (ModTabs.TORTURA.getKey().equals(e.getTabKey())) {
            e.accept(RuedaRegistro.RUEDA_ITEM);
        }
    }
}
