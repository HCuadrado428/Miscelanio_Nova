package com.miscelanea.purgatorio.liminal;

import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;
import java.util.function.Supplier;

/** Registro del atrezo del espacio liminal (2.0.0), aparte del de todo el mod. */
public final class LiminalRegistro {
    private LiminalRegistro() {}

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Miscelanea.MOD_ID);

    private static BlockBehaviour.Properties p(MapColor c, float dureza, SoundType s) {
        return BlockBehaviour.Properties.of().mapColor(c).strength(dureza, 6.0F).sound(s);
    }

    // ------------------------------------------------------------------ Backrooms
    public static final RegistryObject<Block> PARED_BACKROOMS = BLOCKS.register("pared_backrooms", () -> new Block(p(MapColor.COLOR_YELLOW, 1.2F, SoundType.WOOD)));
    public static final RegistryObject<Block> PARED_BACKROOMS_ZOCALO = BLOCKS.register("pared_backrooms_zocalo", () -> new Block(p(MapColor.COLOR_YELLOW, 1.2F, SoundType.WOOD)));
    public static final RegistryObject<Block> MOQUETA_BACKROOMS = BLOCKS.register("moqueta_backrooms", () -> new Block(p(MapColor.COLOR_YELLOW, 0.8F, SoundType.WOOL)));
    public static final RegistryObject<Block> TECHO_BACKROOMS = BLOCKS.register("techo_backrooms", () -> new Block(p(MapColor.SAND, 0.6F, SoundType.STONE)));
    public static final RegistryObject<Block> FLUORESCENTE_PARPADEA = BLOCKS.register("fluorescente_parpadea",
            () -> new Bloques.Parpadea(p(MapColor.SNOW, 0.6F, SoundType.GLASS).lightLevel(s -> 12)));

    // ------------------------------------------------------------------ atrezo
    public static final RegistryObject<Block> COCHE = BLOCKS.register("coche",
            () -> new Bloques.Coche(p(MapColor.METAL, 3.0F, SoundType.METAL).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> HAZ_FAROS = BLOCKS.register("haz_faros",
            () -> new Bloques.Haz(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(-1.0F, 3600000.0F).noLootTable()
                    .lightLevel(s -> s.getValue(Bloques.PARTE4) < 2 ? 15 : 12).pushReaction(PushReaction.DESTROY).replaceable()));
    public static final RegistryObject<Block> SILLA_ESPERA = BLOCKS.register("silla_espera", () -> new Bloques.SillaEspera(p(MapColor.COLOR_BLUE, 1.0F, SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> MAQUINA = BLOCKS.register("maquina_expendedora",
            () -> new Bloques.Maquina(p(MapColor.COLOR_RED, 3.0F, SoundType.METAL).noOcclusion().lightLevel(s -> 7).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Block> CARRITO = BLOCKS.register("carrito", () -> new Bloques.Carrito(p(MapColor.METAL, 1.0F, SoundType.CHAIN).noOcclusion()));
    public static final RegistryObject<Block> PUPITRE = BLOCKS.register("pupitre", () -> new Bloques.Pupitre(p(MapColor.WOOD, 1.0F, SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> SILLA_COLEGIO = BLOCKS.register("silla_colegio",
            () -> new Bloques.Mueble(p(MapColor.WOOD, 0.8F, SoundType.WOOD).noOcclusion(), true, 3, 0, 3.5, 13, 8.2, 13, 3.4, 8.2, 13.2, 12.6, 15, 14.2));
    public static final RegistryObject<Block> ESCALERILLA = BLOCKS.register("escalerilla_piscina", () -> new Bloques.Escalerilla(p(MapColor.METAL, 1.0F, SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> SOCORRISTA = BLOCKS.register("silla_socorrista",
            () -> new Bloques.Socorrista(p(MapColor.SNOW, 1.0F, SoundType.WOOD).noOcclusion().noLootTable()));
    public static final RegistryObject<Block> SENAL_MOJADO = BLOCKS.register("senal_mojado",
            () -> new Bloques.Mueble(p(MapColor.COLOR_YELLOW, 0.3F, SoundType.SCAFFOLDING).noOcclusion().noCollission(), false, 4, 0, 3, 12, 13.4, 13));
    public static final RegistryObject<Block> EXTINTOR = BLOCKS.register("extintor",
            () -> new Bloques.Mueble(p(MapColor.COLOR_RED, 0.5F, SoundType.METAL).noOcclusion(), true, 5.6, 1.5, 11.5, 10.6, 14, 16));
    public static final RegistryObject<Block> TUBERIA = BLOCKS.register("tuberia", () -> new Bloques.Tuberia(p(MapColor.METAL, 1.5F, SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> TUBO = BLOCKS.register("tubo_fluorescente",
            () -> new Bloques.Tubo(p(MapColor.SNOW, 0.5F, SoundType.GLASS).noOcclusion().lightLevel(s -> switch (s.getValue(Bloques.ESTADO)) {
                case 0 -> 14;
                case 2 -> 10;
                default -> 0;
            })));
    public static final RegistryObject<Block> LUZ_PISCINA = BLOCKS.register("luz_piscina", () -> new Bloques.LuzPiscina(p(MapColor.COLOR_LIGHT_BLUE, 1.5F, SoundType.GLASS).lightLevel(s -> 13)));
    public static final RegistryObject<Block> OCULTO = BLOCKS.register("oculto",
            () -> new Bloques.Oculto(BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(2.0F, 6.0F).noOcclusion().noLootTable()));
    public static final RegistryObject<Block> ESCALON = BLOCKS.register("escalon_mecanico",
            () -> new Bloques.Escalon(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F, 6.0F).sound(SoundType.METAL).noOcclusion().noLootTable()));

    // ------------------------------------------------------------------ con GeckoLib
    public static final RegistryObject<Block> MOTOR = BLOCKS.register("escalera_mecanica_motor",
            () -> new AtrezoBE.Motor(p(MapColor.METAL, 2.0F, SoundType.METAL).noOcclusion().noLootTable()));
    public static final RegistryObject<Block> MANIQUI = BLOCKS.register("maniqui", () -> new AtrezoBE.Maniqui(p(MapColor.SNOW, 0.8F, SoundType.BAMBOO_WOOD).noOcclusion()));
    public static final RegistryObject<Block> VENTILADOR = BLOCKS.register("ventilador_techo", () -> new AtrezoBE.Ventilador(p(MapColor.WOOD, 0.8F, SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> FLOTADOR = BLOCKS.register("flotador", () -> new AtrezoBE.Flotador(p(MapColor.COLOR_RED, 0.3F, SoundType.WOOL).noOcclusion()));

    public static final RegistryObject<BlockEntityType<AtrezoBE>> ATREZO_BE = BES.register("atrezo",
            () -> BlockEntityType.Builder.of(AtrezoBE::new, MOTOR.get(), MANIQUI.get(), VENTILADOR.get(), FLOTADOR.get()).build(null));

    public static final RegistryObject<SoundEvent> ZUMBIDO = SOUNDS.register("purgatorio_zumbido",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(Miscelanea.MOD_ID, "purgatorio_zumbido")));

    // ------------------------------------------------------------------ objetos (lo que se puede llevar a casa)
    private static final List<RegistryObject<Item>> OBJETOS = new java.util.ArrayList<>();

    private static void objeto(String n, Supplier<Block> b) {
        OBJETOS.add(ITEMS.register(n, () -> new BlockItem(b.get(), new Item.Properties())));
    }

    static {
        objeto("pared_backrooms", PARED_BACKROOMS);
        objeto("pared_backrooms_zocalo", PARED_BACKROOMS_ZOCALO);
        objeto("moqueta_backrooms", MOQUETA_BACKROOMS);
        objeto("techo_backrooms", TECHO_BACKROOMS);
        objeto("fluorescente_parpadea", FLUORESCENTE_PARPADEA);
        objeto("silla_espera", SILLA_ESPERA);
        objeto("maquina_expendedora", MAQUINA);
        objeto("carrito", CARRITO);
        objeto("pupitre", PUPITRE);
        objeto("silla_colegio", SILLA_COLEGIO);
        objeto("senal_mojado", SENAL_MOJADO);
        objeto("extintor", EXTINTOR);
        objeto("tuberia", TUBERIA);
        objeto("tubo_fluorescente", TUBO);
        objeto("luz_piscina", LUZ_PISCINA);
    }

    public static void registrar(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(LiminalRegistro::pestana);
    }

    private static void pestana(BuildCreativeModeTabContentsEvent e) {
        if (e.getTabKey() != com.miscelanea.registry.ModTabs.VARIOS.getKey()) return;
        for (var o : OBJETOS) e.accept(o);
    }
}
