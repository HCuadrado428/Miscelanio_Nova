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
    public static final RegistryObject<Block> PISTA_BAILE = BLOCKS.register("pista_baile",
            () -> new com.miscelanea.baile.PistaBaileBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(1.5F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 8)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.baile.PistaBaileBlockEntity>> PISTA_BAILE_BE = BLOCK_ENTITIES.register("pista_baile",
            () -> BlockEntityType.Builder.of(com.miscelanea.baile.PistaBaileBlockEntity::new, PISTA_BAILE.get()).build(null));
    public static final RegistryObject<Block> NORIA = BLOCKS.register("noria",
            () -> new com.miscelanea.feria.AtraccionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 10), com.miscelanea.feria.AtraccionBlock.NORIA));
    public static final RegistryObject<Block> TIOVIVO = BLOCKS.register("tiovivo",
            () -> new com.miscelanea.feria.AtraccionBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.0F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(st -> 10), com.miscelanea.feria.AtraccionBlock.TIOVIVO));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.AtraccionBlockEntity>> ATRACCION_BE = BLOCK_ENTITIES.register("atraccion",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.AtraccionBlockEntity::new, NORIA.get(), TIOVIVO.get()).build(null));
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
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.feria.MaquinaAltaBlockEntity>> MAQUINA_ALTA_BE = BLOCK_ENTITIES.register("maquina_alta",
            () -> BlockEntityType.Builder.of(com.miscelanea.feria.MaquinaAltaBlockEntity::new, MAQUINA_GANCHO.get(), TRAGAPERRAS.get(), GACHAPON.get()).build(null));
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
    public static final RegistryObject<Block> CARTEL_RECORDS = BLOCKS.register("cartel_records",
            () -> new com.miscelanea.arcade.CartelRecordsBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F)
                    .sound(SoundType.GLASS).noOcclusion().lightLevel(st -> 9)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.arcade.CartelRecordsBlockEntity>> CARTEL_RECORDS_BE = BLOCK_ENTITIES.register("cartel_records",
            () -> BlockEntityType.Builder.of(com.miscelanea.arcade.CartelRecordsBlockEntity::new, CARTEL_RECORDS.get()).build(null));
    public static final RegistryObject<Block> MESA_COCTEL = BLOCKS.register("mesa_coctel",
            () -> new com.miscelanea.arcade.MesaCoctelBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(2.0F)
                    .sound(SoundType.WOOD).noOcclusion().lightLevel(st -> 6)));
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<com.miscelanea.arcade.RecreativaBlockEntity>> RECREATIVA_BE = BLOCK_ENTITIES.register("recreativa",
            () -> BlockEntityType.Builder.of(com.miscelanea.arcade.RecreativaBlockEntity::new, RECREATIVA.get(), RECREATIVA_DOBLE.get(), MESA_COCTEL.get()).build(null));

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
