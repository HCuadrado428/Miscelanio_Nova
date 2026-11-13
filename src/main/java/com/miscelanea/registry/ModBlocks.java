package com.miscelanea.registry;

import com.miscelanea.Miscelanea;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

/** Bloques del mod. */
public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Miscelanea.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Miscelanea.MOD_ID);

    /** Para la pestaña de creativo, en el orden de registro. */
    public static final List<RegistryObject<Item>> ITEMS_BLOQUE = new ArrayList<>();

    /** Bloque "sin textura" que deja el aliento del núcleo; se deshace solo (sin ítem). */
    public static final RegistryObject<Block> BLOQUE_CORRUPTO = BLOCKS.register("bloque_corrupto",
            () -> new com.miscelanea.nucleo.BloqueCorrupto(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(0.3F)
                    .sound(SoundType.AMETHYST).lightLevel(s -> 6).noLootTable()));

    /** Vitrina del museo de bichos y peces. */
    public static final RegistryObject<Block> VITRINA = BLOCKS.register("vitrina",
            () -> new com.miscelanea.bichos.VitrinaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
                    .sound(SoundType.GLASS).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.bichos.VitrinaBlockEntity>> VITRINA_BE = BLOCK_ENTITIES.register("vitrina",
            () -> BlockEntityType.Builder.of(com.miscelanea.bichos.VitrinaBlockEntity::new, VITRINA.get()).build(null));

    // ---- Muebles
    public static final RegistryObject<Block> SILLA = BLOCKS.register("silla",
            () -> new com.miscelanea.muebles.SillaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.2F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> SOFA = BLOCKS.register("sofa",
            () -> new com.miscelanea.muebles.SofaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(1.0F).sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> MESA = BLOCKS.register("mesa",
            () -> new com.miscelanea.muebles.MesaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> LAMPARA = BLOCKS.register("lampara",
            () -> new com.miscelanea.muebles.LamparaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F).sound(SoundType.LANTERN)
                    .noOcclusion().lightLevel(st -> st.getValue(com.miscelanea.muebles.LamparaBlock.LIT) ? 15 : 0)));

    // ---- Recreativa
    public static final RegistryObject<Block> RECREATIVA = BLOCKS.register("recreativa",
            () -> new com.miscelanea.arcade.RecreativaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 7)));
    public static final RegistryObject<Block> RECREATIVA_DOBLE = BLOCKS.register("recreativa_doble",
            () -> new com.miscelanea.arcade.RecreativaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 7), true));
    public static final RegistryObject<Block> CABINA_CARRERAS = BLOCKS.register("cabina_carreras",
            () -> new com.miscelanea.arcade.CabinaArcadeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 7), "turbo", true));
    public static final RegistryObject<Block> RECREATIVA_PISTOLA = BLOCKS.register("recreativa_pistola",
            () -> new com.miscelanea.arcade.CabinaArcadeBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 7), "pistola", false));
    /**
     * Recreativas dedicadas, una por juego ("recreativa_<juego>"): arrancan ese juego sin menú; las de dos jugadores son dobles.
     * "pistola" no está: su recreativa dedicada es RECREATIVA_PISTOLA (la de la pistola de luz).
     */
    public static final java.util.Map<String, RegistryObject<Block>> RECREATIVAS_JUEGO = new java.util.LinkedHashMap<>();
    static {
        for (String j : com.miscelanea.arcade.ArcadeJuegos.IDS)
            if (!j.equals("pistola")) RECREATIVAS_JUEGO.put(j, BLOCKS.register("recreativa_" + j,
                    () -> new com.miscelanea.arcade.RecreativaJuegoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F)
                            .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 7), j)));
    }
    public static final RegistryObject<Block> PISTA_BAILE = BLOCKS.register("pista_baile",
            () -> new com.miscelanea.baile.PistaBaileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 8)));
    /** Gramola de videoclip (usa el mismo bloque-entidad que la pista de baile). */
    public static final RegistryObject<Block> GRAMOLA = BLOCKS.register("gramola",
            () -> new com.miscelanea.baile.GramolaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 11)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.baile.PistaBaileBlockEntity>> PISTA_BAILE_BE = BLOCK_ENTITIES.register("pista_baile",
            () -> BlockEntityType.Builder.of(com.miscelanea.baile.PistaBaileBlockEntity::new, PISTA_BAILE.get(), GRAMOLA.get()).build(null));
    // ---- El Espera (2.0.0): la puerta de salida y su cartel (no se pueden romper)
    public static final RegistryObject<Block> PUERTA_SALIDA = BLOCKS.register("puerta_salida",
            () -> new com.miscelanea.espera.PuertaSalidaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
                    .strength(-1.0F, 3600000.0F).noLootTable().noOcclusion().noCollission().lightLevel(st -> 6)));
    public static final RegistryObject<Block> CARTEL_SALIDA = BLOCKS.register("cartel_salida",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0F, 3600000.0F)
                    .noLootTable().sound(SoundType.GLASS).lightLevel(st -> 12)));
    /** La textura que falta (cuadros rosa y negro): se ve igual a oscuras, como en el juego cuando falla algo. Solo sale allí. */
    public static final RegistryObject<Block> SIN_TEXTURA = BLOCKS.register("sin_textura",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_MAGENTA).strength(1.5F, 6.0F)
                    .sound(SoundType.STONE).emissiveRendering((s, l, p) -> true)));
    /** Moqueta de salón recreativo de los noventa (la de los dibujitos de neón). Solo sale allí. */
    public static final RegistryObject<Block> MOQUETA_RECREATIVA = BLOCKS.register("moqueta_recreativa",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.8F).sound(SoundType.WOOL)));
    /** Moqueta de hexágonos del Hotel del Videoclip. Solo sale allí. */
    public static final RegistryObject<Block> MOQUETA_HOTEL = BLOCKS.register("moqueta_hotel",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.8F).sound(SoundType.WOOL)));
    /** Sala de Espera: suelo de linóleo, placas de techo, pared pintada y el panel de fluorescentes (encendido o fundido). */
    public static final RegistryObject<Block> SUELO_SALA = BLOCKS.register("suelo_sala",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.2F, 4.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> TECHO_SALA = BLOCKS.register("techo_sala",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(0.6F).sound(SoundType.WOOL)));
    public static final RegistryObject<Block> PARED_SALA = BLOCKS.register("pared_sala",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.2F, 4.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> FLUORESCENTE = BLOCKS.register("fluorescente",
            () -> new com.miscelanea.espera.FluorescenteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.6F)
                    .sound(SoundType.GLASS).lightLevel(st -> st.getValue(com.miscelanea.espera.FluorescenteBlock.ENCENDIDO) ? 15 : 0)));
    /** Papel pintado del Hotel del Videoclip (crema, con rayas y su dibujito). */
    public static final RegistryObject<Block> PAPEL_HOTEL = BLOCKS.register("papel_hotel",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.2F, 4.0F).sound(SoundType.WOOD)));
    /** La tele de tubo sin señal de alguna habitación del hotel. */
    public static final RegistryObject<Block> TELE = BLOCKS.register("tele_sin_senal",
            () -> new com.miscelanea.espera.TeleBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 9)));
    /** La botonera del ascensor del hotel del Espera (te deja en otro piso). */
    public static final RegistryObject<Block> BOTONERA = BLOCKS.register("botonera",
            () -> new com.miscelanea.espera.BotoneraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.0F)
                    .sound(SoundType.METAL).noOcclusion().noCollission().lightLevel(st -> 4)));
    // El Espera (2.0.0): la piscina vacía, el garaje, el colegio de noche y el centro comercial cerrado
    public static final RegistryObject<Block> AZULEJO_PISCINA = BLOCKS.register("azulejo_piscina",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> AZULEJO_PISCINA_AZUL = BLOCKS.register("azulejo_piscina_azul",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> AZULEJO_PISCINA_RAYA = BLOCKS.register("azulejo_piscina_raya",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> BORDE_PISCINA = BLOCKS.register("borde_piscina",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> AGUA_POCA = BLOCKS.register("agua_poca",
            () -> new com.miscelanea.espera.AguaPocaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(0.2F).sound(SoundType.WET_GRASS).noCollission().noOcclusion()));
    public static final RegistryObject<Block> FOCO = BLOCKS.register("foco",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.GLASS).lightLevel(st -> 15)));
    public static final RegistryObject<Block> SUELO_APARCAMIENTO = BLOCKS.register("suelo_aparcamiento",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> SUELO_APARCAMIENTO_RAYA = BLOCKS.register("suelo_aparcamiento_raya",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> SUELO_APARCAMIENTO_FLECHA = BLOCKS.register("suelo_aparcamiento_flecha",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> TECHO_APARCAMIENTO = BLOCKS.register("techo_aparcamiento",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_APARCAMIENTO = BLOCKS.register("pared_aparcamiento",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_APARCAMIENTO_FRANJA = BLOCKS.register("pared_aparcamiento_franja",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> COLUMNA_APARCAMIENTO = BLOCKS.register("columna_aparcamiento",
            () -> new com.miscelanea.espera.NumeradoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> COLUMNA_APARCAMIENTO_BASE = BLOCKS.register("columna_aparcamiento_base",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_METRO = BLOCKS.register("pared_metro",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_METRO_FRANJA = BLOCKS.register("pared_metro_franja",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.CLAY).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> SUELO_METRO = BLOCKS.register("suelo_metro",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> BORDE_ANDEN = BLOCKS.register("borde_anden",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_YELLOW).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> VIA_METRO = BLOCKS.register("via_metro",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F, 6.0F).sound(SoundType.GRAVEL)));
    public static final RegistryObject<Block> CHAPA_TREN = BLOCKS.register("chapa_tren",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F, 6.0F).sound(SoundType.METAL)));
    public static final RegistryObject<Block> SUELO_COLEGIO = BLOCKS.register("suelo_colegio",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_COLEGIO = BLOCKS.register("pared_colegio",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PARED_COLEGIO_ZOCALO = BLOCKS.register("pared_colegio_zocalo",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> TAQUILLA = BLOCKS.register("taquilla",
            () -> new com.miscelanea.espera.TaquillaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.0F, 6.0F).sound(SoundType.METAL)));
    public static final RegistryObject<Block> PIZARRA = BLOCKS.register("pizarra",
            () -> new com.miscelanea.espera.MitadBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(1.5F).sound(SoundType.WOOD)));
    public static final RegistryObject<Block> RELOJ_PARADO = BLOCKS.register("reloj_parado",
            () -> new com.miscelanea.espera.RelojParadoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.WOOD).noCollission().noOcclusion()));
    public static final RegistryObject<Block> SUELO_CENTRO = BLOCKS.register("suelo_centro",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F, 6.0F).sound(SoundType.STONE)));
    public static final RegistryObject<Block> PERSIANA = BLOCKS.register("persiana",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)));
    public static final RegistryObject<Block> ROTULO_TIENDA = BLOCKS.register("rotulo_tienda",
            () -> new com.miscelanea.espera.NumeradoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.0F).sound(SoundType.GLASS)));
    public static final RegistryObject<Block> ESCALERA_MECANICA = BLOCKS.register("escalera_mecanica",
            () -> new net.minecraft.world.level.block.StairBlock(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState(), BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL)));
    /** Recreativa fundida de la Recreativa Infinita (sin bloque-entidad: no se enciende ni suelta nada). */
    public static final RegistryObject<Block> RECREATIVA_APAGADA = BLOCKS.register("recreativa_apagada",
            () -> new com.miscelanea.espera.RecreativaApagadaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .strength(2.0F).sound(SoundType.WOOD).noOcclusion().noLootTable()));
    // ---- Montaña rusa (2.0.0): vías de bloque normales (sin bloque-entidad) y el rizo, que dibuja su vuelta
    private static BlockBehaviour.Properties via() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5F).sound(SoundType.METAL).noOcclusion();
    }
    public static final RegistryObject<Block> VIA_MONTANA = BLOCKS.register("via_montana",
            () -> new com.miscelanea.montana.ViaMontanaBlock(via(), com.miscelanea.montana.ViaMontanaBlock.Tipo.NORMAL));
    public static final RegistryObject<Block> VIA_MONTANA_CADENA = BLOCKS.register("via_montana_cadena",
            () -> new com.miscelanea.montana.ViaMontanaBlock(via(), com.miscelanea.montana.ViaMontanaBlock.Tipo.CADENA));
    public static final RegistryObject<Block> VIA_MONTANA_ESTACION = BLOCKS.register("via_montana_estacion",
            () -> new com.miscelanea.montana.ViaMontanaBlock(via(), com.miscelanea.montana.ViaMontanaBlock.Tipo.ESTACION));
    public static final RegistryObject<Block> VIA_MONTANA_FRENO = BLOCKS.register("via_montana_freno",
            () -> new com.miscelanea.montana.ViaMontanaBlock(via(), com.miscelanea.montana.ViaMontanaBlock.Tipo.FRENO));
    public static final RegistryObject<Block> RIZO = BLOCKS.register("rizo_montana",
            () -> new com.miscelanea.montana.RizoBlock(via()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.montana.RizoBlock.RizoBlockEntity>> RIZO_BE = BLOCK_ENTITIES.register("rizo_montana",
            () -> BlockEntityType.Builder.of(com.miscelanea.montana.RizoBlock.RizoBlockEntity::new, RIZO.get()).build(null));
    /** Pilar de acero que sale solo debajo de la vía en el aire (no se fabrica ni suelta nada). */
    public static final RegistryObject<Block> PILAR_MONTANA = BLOCKS.register("pilar_montana",
            () -> new com.miscelanea.montana.PilarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5F)
                    .sound(SoundType.METAL).noOcclusion().noLootTable()));
    public static final RegistryObject<Block> NORIA = BLOCKS.register("noria",
            () -> new com.miscelanea.feria.AtraccionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 10), com.miscelanea.feria.AtraccionBlock.NORIA));
    public static final RegistryObject<Block> TIOVIVO = BLOCKS.register("tiovivo",
            () -> new com.miscelanea.feria.AtraccionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 10), com.miscelanea.feria.AtraccionBlock.TIOVIVO));
    public static final RegistryObject<Block> PULPO = BLOCKS.register("pulpo",
            () -> new com.miscelanea.feria.AtraccionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(3.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 10), com.miscelanea.feria.AtraccionBlock.PULPO));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.AtraccionBlockEntity>> ATRACCION_BE = BLOCK_ENTITIES.register("atraccion",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.AtraccionBlockEntity::new, NORIA.get(), TIOVIVO.get(), PULPO.get()).build(null));
    public static final RegistryObject<Block> SUSTO = BLOCKS.register("susto",
            () -> new com.miscelanea.feria.SustoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 3)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.SustoBlockEntity>> SUSTO_BE = BLOCK_ENTITIES.register("susto",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.SustoBlockEntity::new, SUSTO.get()).build(null));
    public static final RegistryObject<Block> CASETA_TIRO = BLOCKS.register("caseta_tiro",
            () -> new com.miscelanea.feria.CasetaTiroBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 9)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.CasetaTiroBlockEntity>> CASETA_TIRO_BE = BLOCK_ENTITIES.register("caseta_tiro",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.CasetaTiroBlockEntity::new, CASETA_TIRO.get()).build(null));
    public static final RegistryObject<Block> PELUCHE = BLOCKS.register("peluche",
            () -> new com.miscelanea.feria.PelucheBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5F)
                    .sound(SoundType.WOOL).noOcclusion()));
    public static final RegistryObject<Block> ALGODONERA = BLOCKS.register("algodonera",
            () -> new com.miscelanea.feria.MaquinaFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 6), com.miscelanea.feria.MaquinaFeriaBlock.ALGODON));
    public static final RegistryObject<Block> PALOMITERA = BLOCKS.register("palomitera",
            () -> new com.miscelanea.feria.MaquinaFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 8), com.miscelanea.feria.MaquinaFeriaBlock.PALOMITAS));
    public static final RegistryObject<Block> CASTILLO_FUEGOS = BLOCKS.register("castillo_fuegos",
            () -> new com.miscelanea.feria.MaquinaFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 0), com.miscelanea.feria.MaquinaFeriaBlock.FUEGOS));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.MaquinaFeriaBlockEntity>> MAQUINA_FERIA_BE = BLOCK_ENTITIES.register("maquina_feria",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.MaquinaFeriaBlockEntity::new, ALGODONERA.get(), PALOMITERA.get(), CASTILLO_FUEGOS.get()).build(null));
    public static final RegistryObject<Block> MAQUINA_GANCHO = BLOCKS.register("maquina_gancho",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PINK).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 9), com.miscelanea.feria.MaquinaAltaBlock.GANCHO));
    public static final RegistryObject<Block> TRAGAPERRAS = BLOCKS.register("tragaperras",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 9), com.miscelanea.feria.MaquinaAltaBlock.TRAGAPERRAS));
    public static final RegistryObject<Block> GACHAPON = BLOCKS.register("gachapon",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 6), com.miscelanea.feria.MaquinaAltaBlock.GACHAPON));
    public static final RegistryObject<Block> MAQUINA_CANASTA = BLOCKS.register("maquina_canasta",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 9), com.miscelanea.feria.MaquinaAltaBlock.CANASTA));
    public static final RegistryObject<Block> MAQUINA_BOXEO = BLOCKS.register("maquina_boxeo",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 8), com.miscelanea.feria.MaquinaAltaBlock.BOXEO));
    public static final RegistryObject<Block> MARTILLO_FUERZA = BLOCKS.register("martillo_fuerza",
            () -> new com.miscelanea.feria.MaquinaAltaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 6), com.miscelanea.feria.MaquinaAltaBlock.MARTILLO));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.MaquinaAltaBlockEntity>> MAQUINA_ALTA_BE = BLOCK_ENTITIES.register("maquina_alta",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.MaquinaAltaBlockEntity::new, MAQUINA_GANCHO.get(), TRAGAPERRAS.get(), GACHAPON.get(),
                    MAQUINA_CANASTA.get(), MAQUINA_BOXEO.get(), MARTILLO_FUERZA.get()).build(null));
    public static final RegistryObject<Block> PISTA_BOLOS = BLOCKS.register("pista_bolos",
            () -> new com.miscelanea.bolos.PistaBolosBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> BOLERA = BLOCKS.register("bolera",
            () -> new com.miscelanea.bolos.BoleraBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 6)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.bolos.BoleraBlockEntity>> BOLERA_BE = BLOCK_ENTITIES.register("bolera",
            () -> BlockEntityType.Builder.of(com.miscelanea.bolos.BoleraBlockEntity::new, BOLERA.get()).build(null));
    public static final RegistryObject<Block> DIANA_DARDOS = BLOCKS.register("diana_dardos",
            () -> new com.miscelanea.dardos.DianaDardosBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F)
                    .sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.dardos.DianaDardosBlockEntity>> DIANA_DARDOS_BE = BLOCK_ENTITIES.register("diana_dardos",
            () -> BlockEntityType.Builder.of(com.miscelanea.dardos.DianaDardosBlockEntity::new, DIANA_DARDOS.get()).build(null));
    public static final RegistryObject<Block> TORRE = BLOCKS.register("torre_bloques",
            () -> new com.miscelanea.torre.TorreBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                    .sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.torre.TorreBlockEntity>> TORRE_BE = BLOCK_ENTITIES.register("torre_bloques",
            () -> BlockEntityType.Builder.of(com.miscelanea.torre.TorreBlockEntity::new, TORRE.get()).build(null));
    public static final RegistryObject<Block> TOMATERA = BLOCKS.register("tomatera",
            () -> new com.miscelanea.cocina.Cultivo(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WHEAT), () -> ModItems.SEMILLAS_TOMATE.get()));
    public static final RegistryObject<Block> ARROZAL = BLOCKS.register("arrozal",
            () -> new com.miscelanea.cocina.Cultivo(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.WHEAT), () -> ModItems.ARROZ.get()));
    public static final RegistryObject<Block> FOGON = BLOCKS.register("fogon",
            () -> new com.miscelanea.cocina.FogonBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5F)
                    .sound(SoundType.STONE).noOcclusion().lightLevel(st -> st.getValue(com.miscelanea.cocina.FogonBlock.LIT) ? 12 : 0)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.cocina.FogonBlockEntity>> FOGON_BE = BLOCK_ENTITIES.register("fogon",
            () -> BlockEntityType.Builder.of(com.miscelanea.cocina.FogonBlockEntity::new, FOGON.get()).build(null));
    public static final RegistryObject<Block> PAELLA = BLOCKS.register("paella",
            () -> new com.miscelanea.cocina.PaellaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F)
                    .sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Block> MESA_CARTAS = BLOCKS.register("mesa_cartas",
            () -> new com.miscelanea.cartas.MesaCartasBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F)
                    .sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.cartas.MesaCartasBlockEntity>> MESA_CARTAS_BE = BLOCK_ENTITIES.register("mesa_cartas",
            () -> BlockEntityType.Builder.of(com.miscelanea.cartas.MesaCartasBlockEntity::new, MESA_CARTAS.get()).build(null));
    public static final RegistryObject<Block> PESCA_PATITOS = BLOCKS.register("pesca_patitos",
            () -> new com.miscelanea.feria.PuestoFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 4), com.miscelanea.feria.PuestoFeriaBlock.PATITOS));
    public static final RegistryObject<Block> TOMBOLA = BLOCKS.register("tombola",
            () -> new com.miscelanea.feria.PuestoFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 8), com.miscelanea.feria.PuestoFeriaBlock.TOMBOLA));
    public static final RegistryObject<Block> CHURRERIA = BLOCKS.register("churreria",
            () -> new com.miscelanea.feria.PuestoFeriaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 8), com.miscelanea.feria.PuestoFeriaBlock.CHURRERIA));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.PuestoFeriaBlockEntity>> PUESTO_FERIA_BE = BLOCK_ENTITIES.register("puesto_feria",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.PuestoFeriaBlockEntity::new, PESCA_PATITOS.get(), TOMBOLA.get(), CHURRERIA.get()).build(null));
    public static final RegistryObject<Block> TABURETE = BLOCKS.register("taburete",
            () -> new com.miscelanea.muebles.TabureteBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> BILLAR = BLOCKS.register("billar",
            () -> new com.miscelanea.mesas.BillarBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion()));
    public static final RegistryObject<Block> FUTBOLIN = BLOCKS.register("futbolin",
            () -> new com.miscelanea.mesas.FutbolinBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5F)
                    .sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.mesas.BillarBlockEntity>> BILLAR_BE = BLOCK_ENTITIES.register("billar",
            () -> BlockEntityType.Builder.of(com.miscelanea.mesas.BillarBlockEntity::new, BILLAR.get()).build(null));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.mesas.FutbolinBlockEntity>> FUTBOLIN_BE = BLOCK_ENTITIES.register("futbolin",
            () -> BlockEntityType.Builder.of(com.miscelanea.mesas.FutbolinBlockEntity::new, FUTBOLIN.get()).build(null));
    public static final RegistryObject<Block> AIRHOCKEY = BLOCKS.register("air_hockey",
            () -> new com.miscelanea.mesas.AirHockeyBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 5)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.mesas.AirHockeyBlockEntity>> AIRHOCKEY_BE = BLOCK_ENTITIES.register("air_hockey",
            () -> BlockEntityType.Builder.of(com.miscelanea.mesas.AirHockeyBlockEntity::new, AIRHOCKEY.get()).build(null));
    public static final RegistryObject<Block> CARTEL_RECORDS = BLOCKS.register("cartel_records",
            () -> new com.miscelanea.arcade.CartelRecordsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(st -> 9)));
    public static final RegistryObject<Block> PIZARRA_FERIA = BLOCKS.register("pizarra_feria",
            () -> new com.miscelanea.arcade.CartelRecordsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                    .sound(SoundType.WOOD).noOcclusion(), com.miscelanea.arcade.CartelRecordsBlock.FERIA));
    public static final RegistryObject<Block> TABLON_RECORDS = BLOCKS.register("tablon_records",
            () -> new com.miscelanea.arcade.TablonRecordsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 6)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.arcade.CartelRecordsBlockEntity>> CARTEL_RECORDS_BE = BLOCK_ENTITIES.register("cartel_records",
            () -> BlockEntityType.Builder.of(com.miscelanea.arcade.CartelRecordsBlockEntity::new, CARTEL_RECORDS.get(), PIZARRA_FERIA.get(), TABLON_RECORDS.get()).build(null));
    public static final RegistryObject<Block> MESA_COCTEL = BLOCKS.register("mesa_coctel",
            () -> new com.miscelanea.arcade.MesaCoctelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 6)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.arcade.RecreativaBlockEntity>> RECREATIVA_BE = BLOCK_ENTITIES.register("recreativa",
            () -> BlockEntityType.Builder.of(com.miscelanea.arcade.RecreativaBlockEntity::new, bloquesRecreativa()).build(null));

    /** Todos los bloques que llevan la entidad de recreativa: las genéricas, la mesa cóctel, las cabinas y las dedicadas a un juego. */
    private static Block[] bloquesRecreativa() {
        List<Block> l = new ArrayList<>(List.of(RECREATIVA.get(), RECREATIVA_DOBLE.get(), MESA_COCTEL.get(), CABINA_CARRERAS.get(), RECREATIVA_PISTOLA.get()));
        for (RegistryObject<Block> r : RECREATIVAS_JUEGO.values()) l.add(r.get());
        return l.toArray(new Block[0]);
    }

    // ---- Acuario y terrario
    public static final RegistryObject<Block> ACUARIO = BLOCKS.register("acuario",
            () -> new com.miscelanea.tanques.TanqueBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WATER).strength(0.6F).sound(SoundType.GLASS)
                    .noOcclusion().lightLevel(s -> 5), true));
    public static final RegistryObject<Block> TERRARIO = BLOCKS.register("terrario",
            () -> new com.miscelanea.tanques.TanqueBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GRASS).strength(0.6F).sound(SoundType.GLASS)
                    .noOcclusion(), false));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.tanques.TanqueBlockEntity>> TANQUE_BE = BLOCK_ENTITIES.register("tanque",
            () -> BlockEntityType.Builder.of(com.miscelanea.tanques.TanqueBlockEntity::new, ACUARIO.get(), TERRARIO.get()).build(null));

    // ---- Juegos de mesa
    public static final RegistryObject<Block> TABLERO = BLOCKS.register("tablero",
            () -> new com.miscelanea.juegos.TableroBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.juegos.TableroBlockEntity>> TABLERO_BE = BLOCK_ENTITIES.register("tablero",
            () -> BlockEntityType.Builder.of(com.miscelanea.juegos.TableroBlockEntity::new, TABLERO.get()).build(null));

    // ---- Tirolina y trampolín
    public static final RegistryObject<Block> POSTE_TIROLINA = BLOCKS.register("poste_tirolina",
            () -> new com.miscelanea.tirolina.PosteTirolinaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.tirolina.PosteTirolinaBlockEntity>> POSTE_TIROLINA_BE = BLOCK_ENTITIES.register("poste_tirolina",
            () -> BlockEntityType.Builder.of(com.miscelanea.tirolina.PosteTirolinaBlockEntity::new, POSTE_TIROLINA.get()).build(null));
    public static final RegistryObject<Block> TRAMPOLIN = BLOCKS.register("trampolin",
            () -> new com.miscelanea.trampolin.TrampolinBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.WOOL).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.trampolin.TrampolinBlockEntity>> TRAMPOLIN_BE = BLOCK_ENTITIES.register("trampolin",
            () -> BlockEntityType.Builder.of(com.miscelanea.trampolin.TrampolinBlockEntity::new, TRAMPOLIN.get()).build(null));

    public static final RegistryObject<Block> IMPERIO = BLOCKS.register("imperio",
            () -> new com.miscelanea.imperio.ImperioBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.imperio.ImperioBlockEntity>> IMPERIO_BE = BLOCK_ENTITIES.register("imperio",
            () -> BlockEntityType.Builder.of(com.miscelanea.imperio.ImperioBlockEntity::new, IMPERIO.get()).build(null));

    public static final RegistryObject<Block> PARCHIS = BLOCKS.register("parchis",
            () -> new com.miscelanea.parchis.ParchisBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.parchis.ParchisBlockEntity>> PARCHIS_BE = BLOCK_ENTITIES.register("parchis",
            () -> BlockEntityType.Builder.of(com.miscelanea.parchis.ParchisBlockEntity::new, PARCHIS.get()).build(null));

    public static final RegistryObject<Block> OCA = BLOCKS.register("oca",
            () -> new com.miscelanea.oca.OcaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.oca.OcaBlockEntity>> OCA_BE = BLOCK_ENTITIES.register("oca",
            () -> BlockEntityType.Builder.of(com.miscelanea.oca.OcaBlockEntity::new, OCA.get()).build(null));

    public static final RegistryObject<Block> CUATRO = BLOCKS.register("cuatro",
            () -> new com.miscelanea.cuatro.CuatroBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.cuatro.CuatroBlockEntity>> CUATRO_BE = BLOCK_ENTITIES.register("cuatro",
            () -> BlockEntityType.Builder.of(com.miscelanea.cuatro.CuatroBlockEntity::new, CUATRO.get()).build(null));

    public static final RegistryObject<Block> FLOTA = BLOCKS.register("flota",
            () -> new com.miscelanea.flota.FlotaBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.flota.FlotaBlockEntity>> FLOTA_BE = BLOCK_ENTITIES.register("flota",
            () -> BlockEntityType.Builder.of(com.miscelanea.flota.FlotaBlockEntity::new, FLOTA.get()).build(null));

    public static final RegistryObject<Block> DOMINO = BLOCKS.register("domino",
            () -> new com.miscelanea.domino.DominoBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.8F).sound(SoundType.WOOD).noOcclusion()));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.domino.DominoBlockEntity>> DOMINO_BE = BLOCK_ENTITIES.register("domino",
            () -> BlockEntityType.Builder.of(com.miscelanea.domino.DominoBlockEntity::new, DOMINO.get()).build(null));

    private ModBlocks() {}
}
