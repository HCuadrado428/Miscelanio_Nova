package com.miscelanea.purgatorio;

import com.miscelanea.Miscelanea;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Genera El Purgatorio: un suelo infinito a la misma altura dividido en zonas (la Feria Cerrada y la Sala de Espera, de
 * momento), cada chunk con una de unas pocas piezas que se eligen con un hash de sus coordenadas. No hay ruido, ni
 * cuevas, ni árboles, ni estructuras: cada chunk son unos cientos de bloques puestos a mano, así que se genera al
 * instante y hay muy poco que dibujar (va bien en ordenadores flojos). Es igual en todos los mundos: siempre el mismo
 * sitio, como si ya hubieras estado.
 */
public class PurgatorioGenerador extends ChunkGenerator {
    public static final Codec<PurgatorioGenerador> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(PurgatorioGenerador::new)));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERADORES = DeferredRegister.create(Registries.CHUNK_GENERATOR, Miscelanea.MOD_ID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> PURGATORIO = GENERADORES.register("purgatorio", () -> CODEC);

    /** Primer bloque de aire sobre el suelo. */
    public static final int SUELO = 64;
    /** Zonas: cada región de REGION x REGION chunks es de una (la del origen siempre es la feria). */
    public static final int REGION = 5;

    public enum Zona {FERIA, SALA, RECREATIVA, HOTEL, SIN_TEXTURA}

    public PurgatorioGenerador(BiomeSource b) {
        super(b);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ azar fijo
    static long h(int a, int b, int c) {
        long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return x & Long.MAX_VALUE;
    }

    public static Zona zona(int cx, int cz) {
        int rx = Math.floorDiv(cx, REGION), rz = Math.floorDiv(cz, REGION);
        if (rx == 0 && rz == 0) return Zona.FERIA;
        long k = h(rx, rz, 1) % 100;
        return k < 32 ? Zona.FERIA : k < 52 ? Zona.SALA : k < 70 ? Zona.RECREATIVA : k < 86 ? Zona.HOTEL : Zona.SIN_TEXTURA;
    }

    /** ¿Hay una puerta de salida en este chunk? (más o menos una de cada 60). */
    public static boolean salida(int cx, int cz) {
        return h(cx, cz, 7) % 60 == 0;
    }

    // ------------------------------------------------------------------ escribir el chunk
    private static final class Lienzo {
        final ChunkAccess c;
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final Heightmap a, b;

        Lienzo(ChunkAccess c) {
            this.c = c;
            a = c.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
            b = c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        }

        /** x, z del chunk (0..15); y absoluta. */
        void pon(int x, int y, int z, BlockState s) {
            if (x < 0 || x > 15 || z < 0 || z > 15) return;
            c.setBlockState(p.set(c.getPos().getMinBlockX() + x, y, c.getPos().getMinBlockZ() + z), s, false);
            a.update(x, y, z, s);
            b.update(x, y, z, s);
        }

        void caja(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = x0; x <= x1; x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = z0; z <= z1; z++) pon(x, y, z, s);
        }
    }

    private static BlockState d(Block b) {
        return b.defaultBlockState();
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor e, Blender bl, RandomState r, StructureManager sm, ChunkAccess c) {
        Lienzo l = new Lienzo(c);
        int cx = c.getPos().x, cz = c.getPos().z;
        Zona z = zona(cx, cz);
        switch (z) {
            case FERIA -> feria(l, cx, cz);
            case SALA -> sala(l, cx, cz);
            case RECREATIVA -> recreativa(l, cx, cz);
            case HOTEL -> hotel(l, cx, cz);
            default -> sinTextura(l, cx, cz);
        }
        if (salida(cx, cz)) puertaSalida(l, z);
        return CompletableFuture.completedFuture(c);
    }

    // ------------------------------------------------------------------ Feria Cerrada
    /** Una feria de noche fuera de temporada: asfalto, casetas cerradas con la lona echada, farolas (alguna fundida). */
    private static void feria(Lienzo l, int cx, int cz) {
        int f = SUELO;
        // suelo: asfalto con las calles en cruz (x/z = 7..8 de cada chunk) más claras y con raya discontinua
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean calle = x == 7 || x == 8 || z == 7 || z == 8;
                BlockState s = d(calle ? Blocks.GRAY_CONCRETE : Blocks.BLACK_CONCRETE);
                if ((x == 7 || x == 8) && z % 4 == 1 && z != 7 && z != 8) s = d(Blocks.LIGHT_GRAY_CONCRETE);
                if ((z == 7 || z == 8) && x % 4 == 1 && x != 7 && x != 8) s = d(Blocks.LIGHT_GRAY_CONCRETE);
                l.pon(x, f - 1, z, s);
                l.pon(x, f - 2, z, d(Blocks.STONE));
            }
        // la farola de la esquina (una de cada cuatro, fundida)
        farola(l, 6, 6, h(cx, cz, 2) % 6 != 0);
        if (h(cx, cz, 9) % 2 == 0) farola(l, 9, 9, h(cx, cz, 10) % 4 != 0);
        // cuatro parcelas
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int k = 0; k < 4; k++) {
            int qx = q[k][0], qz = q[k][1];
            if (salida(cx, cz) && k == 0) continue;        // ahí va la puerta
            int tipo = (int) (h(cx, cz, 20 + k) % 10);
            boolean norte = qz == 0;
            if (tipo < 4) caseta(l, qx, norte, (int) (h(cx, cz, 30 + k) % 4));
            else if (tipo < 6) banco(l, qx, qz, norte);
            else if (tipo == 6) tiovivo(l, qx, qz);
            else if (tipo == 7) taquilla(l, qx, qz);
            basura(l, qx, qz, h(cx, cz, 40 + k));
        }
    }

    private static void farola(Lienzo l, int x, int z, boolean encendida) {
        int f = SUELO;
        for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(Blocks.POLISHED_BLACKSTONE_WALL));
        int dz = z < 8 ? 1 : -1;
        l.pon(x, f + 4, z, d(Blocks.POLISHED_BLACKSTONE_SLAB));
        l.pon(x, f + 4, z + dz, d(Blocks.POLISHED_BLACKSTONE_SLAB));
        l.pon(x, f + 3, z + dz, encendida ? Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true) : d(Blocks.CHAIN));
    }

    /** Caseta de feria cerrada: lona a rayas en el tejado y la cortina echada en el mostrador. */
    private static void caseta(Lienzo l, int qx, boolean norte, int color) {
        int f = SUELO;
        Block[] lona = {Blocks.RED_WOOL, Blocks.BLUE_WOOL, Blocks.YELLOW_WOOL, Blocks.GREEN_WOOL};
        Block[] cortina = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.LIME_CONCRETE};
        // en la parcela de 7 x 7: fondo a 2 bloques de la orilla, frente que da a la calle de en medio
        int zf = norte ? 1 : 14, zd = norte ? 5 : 10, paso = norte ? 1 : -1;
        int x0 = qx + 1, x1 = qx + 5;
        for (int z = zf; z != zd + paso; z += paso)
            for (int y = 0; y < 3; y++) {
                l.pon(x0, f + y, z, d(Blocks.SPRUCE_PLANKS));
                l.pon(x1, f + y, z, d(Blocks.SPRUCE_PLANKS));
            }
        for (int x = x0; x <= x1; x++)
            for (int y = 0; y < 3; y++) l.pon(x, f + y, zf, d(Blocks.SPRUCE_PLANKS));
        // mostrador y cortina
        for (int x = x0 + 1; x < x1; x++) {
            l.pon(x, f, zd, d(Blocks.STRIPPED_SPRUCE_LOG));
            l.pon(x, f + 1, zd, d((x & 1) == 0 ? cortina[color] : Blocks.WHITE_CONCRETE));
            l.pon(x, f + 2, zd, d((x & 1) == 0 ? cortina[color] : Blocks.WHITE_CONCRETE));
        }
        // tejado de lona que sobresale uno por delante
        for (int x = x0; x <= x1; x++)
            for (int z = zf; z != zd + 2 * paso; z += paso) l.pon(x, f + 3, z, d((x & 1) == 0 ? lona[color] : Blocks.WHITE_WOOL));
        // una bombilla de feria apagada en cada esquina del toldo
        l.pon(x0, f + 2, zd + paso, d(Blocks.REDSTONE_LAMP));
        l.pon(x1, f + 2, zd + paso, d(Blocks.REDSTONE_LAMP));
    }

    private static void banco(Lienzo l, int qx, int qz, boolean norte) {
        int f = SUELO;
        Direction mira = norte ? Direction.SOUTH : Direction.NORTH;
        int z = norte ? qz + 5 : qz + 1;
        for (int x = qx + 2; x <= qx + 4; x++)
            l.pon(x, f, z, Blocks.DARK_OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, mira.getOpposite()));
        l.pon(qx + 5, f, z, d(Blocks.CAULDRON));               // papelera
        l.pon(qx + 1, f, z, d(Blocks.POTTED_DEAD_BUSH));
    }

    /** Lo que queda de un tiovivo de niños: la valla redonda y el palo del centro. */
    private static void tiovivo(Lienzo l, int qx, int qz) {
        int f = SUELO, cx = qx + 3, cz = qz + 3;
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 2.5 && r < 3.4) l.pon(cx + x, f, cz + z, d(Blocks.OAK_FENCE));
                else if (r <= 2.5) l.pon(cx + x, f - 1, cz + z, d((x + z & 1) == 0 ? Blocks.RED_TERRACOTTA : Blocks.WHITE_TERRACOTTA));
            }
        for (int y = 0; y < 3; y++) l.pon(cx, f + y, cz, d(Blocks.STRIPPED_BIRCH_LOG));
        l.pon(cx, f + 3, cz, d(Blocks.RED_WOOL));
        l.pon(cx + 1, f, cz, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));     // un asiento suelto
    }

    private static void taquilla(Lienzo l, int qx, int qz) {
        int f = SUELO;
        l.caja(qx + 2, f, qz + 2, qx + 4, f + 2, qz + 4, d(Blocks.WHITE_TERRACOTTA));
        l.pon(qx + 3, f + 1, qz + 2, d(Blocks.GLASS_PANE));
        l.pon(qx + 3, f + 1, qz + 4, d(Blocks.GLASS_PANE));
        l.caja(qx + 1, f + 3, qz + 1, qx + 5, f + 3, qz + 5, Blocks.RED_CONCRETE.defaultBlockState());
        l.pon(qx + 3, f + 4, qz + 3, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    /** Papeles y vasos por el suelo (alfombras sueltas). */
    private static void basura(Lienzo l, int qx, int qz, long r) {
        Block[] b = {Blocks.WHITE_CARPET, Blocks.YELLOW_CARPET, Blocks.RED_CARPET, Blocks.LIGHT_GRAY_CARPET};
        int n = (int) (r % 3);
        for (int i = 0; i < n; i++) {
            long s = h((int) r, i, 77);
            int x = qx + (int) (s % 7), z = qz + (int) ((s >> 8) % 7);
            if (x == 6 && z == 6) continue;
            l.pon(x, SUELO, z, d(b[(int) ((s >> 16) % b.length)]));
        }
    }

    // ------------------------------------------------------------------ Sala de Espera
    /**
     * Una sala de espera que no se acaba: baldosas, techo bajo con fluorescentes (alguno fundido), filas de sillas y
     * tabiques con huecos. Hacia la feria siempre hay pared con una puerta.
     */
    private static void sala(Lienzo l, int cx, int cz) {
        int f = SUELO, techo = SUELO + 4;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, d(((x >> 1) + (z >> 1) & 1) == 0 ? Blocks.WHITE_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE));
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean luz = x % 5 == 2 && z % 5 == 2 && h(cx * 16 + x, cz * 16 + z, 3) % 5 != 0;
                l.pon(x, techo, z, d(luz ? Blocks.SEA_LANTERN : Blocks.SMOOTH_QUARTZ));
            }
        // sillas en filas mirando al norte, con pasillo en medio
        int filas = (int) (h(cx, cz, 4) % 3);
        if (filas > 0)
            for (int zr : new int[]{3, 11})
                for (int x = 1; x < 15; x++) {
                    if (x == 7 || x == 8) continue;
                    l.pon(x, f, zr, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                    if (filas == 2) l.pon(x, f, zr + 2, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                }
        else {
            // un mostrador de "turnos" con su planta
            l.caja(5, f, 6, 10, f, 6, d(Blocks.SMOOTH_QUARTZ));
            l.pon(5, f + 1, 6, d(Blocks.POTTED_BAMBOO));
        }
        // tabiques: hacia la feria siempre; entre salas, a veces. Con hueco en el centro para pasar
        Direction[] lados = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        for (Direction dd : lados) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            boolean feria = zona(nx, nz) != Zona.SALA;
            // cada borde se decide una vez para los dos chunks que lo comparten
            int bx = Math.min(cx, nx) * 2 + (dd.getAxis() == Direction.Axis.X ? 1 : 0), bz = Math.min(cz, nz);
            boolean pared = feria || h(bx, bz, 5) % 3 == 0;
            if (!pared) continue;
            for (int i = 0; i < 16; i++) {
                if (i == 7 || i == 8) continue;               // la puerta
                int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
                int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(y == 0 ? Blocks.LIGHT_GRAY_CONCRETE : Blocks.WHITE_CONCRETE));
            }
        }
        // columnas en las esquinas
        for (int x : new int[]{0, 15})
            for (int z : new int[]{0, 15})
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(Blocks.QUARTZ_PILLAR));
    }

    // ------------------------------------------------------------------ Recreativa Infinita
    /** Juegos de las máquinas que aún funcionan (las de un jugador, que caben en un bloque de ancho). */
    private static final String[] JUEGOS_VIVOS = {"astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores", "mazmorra", "pinball"};

    /**
     * Un salón recreativo de los noventa que no se acaba: moqueta de neón, techo negro con luces moradas y filas de
     * máquinas espalda con espalda. Casi todas están fundidas (un bloque normal: no cuesta nada); una de cada seis
     * funciona de verdad y se puede jugar (esas sí llevan su bloque-entidad, por eso son pocas).
     */
    private static void recreativa(Lienzo l, int cx, int cz) {
        int f = SUELO, techo = SUELO + 5;
        BlockState moqueta = com.miscelanea.registry.ModBlocks.MOQUETA_RECREATIVA.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, moqueta);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean pasillo = x == 7 || x == 8 || z == 7 || z == 8;
                boolean luz = pasillo && (x == 7 || z == 7) && ((x + z) % 4 == 2) && h(cx * 16 + x, cz * 16 + z, 3) % 7 != 0;
                l.pon(x, techo, z, d(luz ? ((x + z) % 8 == 2 ? Blocks.PEARLESCENT_FROGLIGHT : Blocks.VERDANT_FROGLIGHT) : Blocks.BLACK_CONCRETE));
            }
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int k = 0; k < 4; k++) {
            int qx = q[k][0], qz = q[k][1];
            if (salida(cx, cz) && k == 0) continue;
            long t = h(cx, cz, 50 + k) % 10;
            if (t < 7) {
                // dos filas espalda con espalda: una mira al norte y la otra al sur
                for (int x = qx + 1; x <= qx + 5; x++) {
                    maquina(l, x, qz + 2, Direction.NORTH, h(cx * 16 + x, cz * 16 + qz, 60));
                    maquina(l, x, qz + 3, Direction.SOUTH, h(cx * 16 + x, cz * 16 + qz, 61));
                }
            } else if (t < 9) {
                // en columna: una mira al oeste y la otra al este
                for (int z = qz + 1; z <= qz + 5; z++) {
                    maquina(l, qx + 2, z, Direction.WEST, h(cx * 16 + qx, cz * 16 + z, 62));
                    maquina(l, qx + 3, z, Direction.EAST, h(cx * 16 + qx, cz * 16 + z, 63));
                }
            } else {
                // un rincón de descanso: sofá de escay rojo y una papelera
                for (int x = qx + 1; x <= qx + 4; x++)
                    l.pon(x, f, qz + 1, Blocks.RED_TERRACOTTA.defaultBlockState());
                for (int x = qx + 1; x <= qx + 4; x++)
                    l.pon(x, f, qz + 2, Blocks.SMOOTH_STONE_SLAB.defaultBlockState());
                l.pon(qx + 5, f, qz + 2, d(Blocks.CAULDRON));
            }
        }
        // paredes negras hacia las zonas que no son recreativa (con su hueco para pasar)
        for (Direction dd : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (zona(cx + dd.getStepX(), cz + dd.getStepZ()) == Zona.RECREATIVA) continue;
            for (int i = 0; i < 16; i++) {
                if (i == 7 || i == 8) continue;
                int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
                int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
                for (int y = 0; y < 5; y++) l.pon(x, f + y, z, d(y == 2 && i % 3 == 0 ? Blocks.PURPLE_STAINED_GLASS : Blocks.BLACK_CONCRETE));
            }
        }
    }

    /** Una máquina en (x, z): fundida casi siempre, viva una de cada seis, y de vez en cuando falta (un hueco). */
    private static void maquina(Lienzo l, int x, int z, Direction mira, long r) {
        if (r % 12 == 0) return;
        int f = SUELO;
        if (r % 6 == 1) {
            String j = JUEGOS_VIVOS[(int) ((r >> 8) % JUEGOS_VIVOS.length)];
            var ro = com.miscelanea.registry.ModBlocks.RECREATIVAS_JUEGO.get(j);
            if (ro != null) {
                Block b = ro.get();
                BlockState s = b.defaultBlockState().setValue(com.miscelanea.arcade.RecreativaBlock.FACING, mira);
                l.pon(x, f, z, s);
                l.pon(x, f + 1, z, s.setValue(com.miscelanea.arcade.RecreativaBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
                if (b instanceof net.minecraft.world.level.block.EntityBlock eb) {
                    BlockPos p = new BlockPos(l.c.getPos().getMinBlockX() + x, f, l.c.getPos().getMinBlockZ() + z);
                    var be = eb.newBlockEntity(p, s);
                    if (be != null) l.c.setBlockEntity(be);
                }
                return;
            }
        }
        BlockState s = com.miscelanea.registry.ModBlocks.RECREATIVA_APAGADA.get().defaultBlockState()
                .setValue(RecreativaApagadaBlock.FACING, mira)
                .setValue(RecreativaApagadaBlock.MUEBLE, (int) ((r >> 12) % RecreativaApagadaBlock.MUEBLES.length));
        l.pon(x, f, z, s);
        l.pon(x, f + 1, z, s.setValue(RecreativaApagadaBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
    }

    // ------------------------------------------------------------------ Hotel del Videoclip
    /**
     * Los pasillos de un hotel que no se acaba (el del videoclip): moqueta de hexágonos, paredes color crema con zócalo de
     * madera, luz cálida en el techo y puertas cerradas a los dos lados. Detrás de cada puerta, una habitación pequeña y
     * siempre igual: la cama hecha, la mesilla con su lámpara y nadie.
     */
    private static void hotel(Lienzo l, int cx, int cz) {
        int f = SUELO, techo = SUELO + 4;
        BlockState moqueta = com.miscelanea.registry.ModBlocks.MOQUETA_HOTEL.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean pasillo = x == 7 || x == 8 || z == 7 || z == 8;
                l.pon(x, f - 1, z, pasillo ? moqueta : d(Blocks.SPRUCE_PLANKS));
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean luz = pasillo && (x == 7 || z == 7) && (x + z) % 5 == 2 && h(cx * 16 + x, cz * 16 + z, 3) % 9 != 0;
                l.pon(x, techo, z, d(luz ? Blocks.OCHRE_FROGLIGHT : Blocks.SMOOTH_SANDSTONE));
            }
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int k = 0; k < 4; k++) {
            int x0 = q[k][0], z0 = q[k][1], x1 = x0 + 6, z1 = z0 + 6;
            if (salida(cx, cz) && k == 0) continue;
            // paredes de la habitación: zócalo de madera y crema encima
            for (int x = x0; x <= x1; x++)
                for (int z = z0; z <= z1; z++) {
                    if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                    for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(y == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.SMOOTH_SANDSTONE));
                }
            // la puerta, en la pared que da al pasillo largo (el de z = 7..8)
            int zp = z0 == 0 ? z1 : z0, xp = x0 + 3;
            Direction mira = z0 == 0 ? Direction.SOUTH : Direction.NORTH;
            BlockState pu = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, mira.getOpposite());
            l.pon(xp, f, zp, pu);
            l.pon(xp, f + 1, zp, pu.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            // un aplique a cada lado de la puerta, en el pasillo
            int zf = zp + (z0 == 0 ? 1 : -1);
            l.pon(xp - 2, f + 2, zp, d(Blocks.SMOOTH_SANDSTONE));
            l.pon(xp - 2, f + 2, zf, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, mira));
            if (h(cx, cz, 110 + k) % 3 != 0)
                l.pon(xp + 2, f + 2, zf, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, mira));
            // y otra en la pared que da al otro pasillo (el de x = 7..8), con su aplique
            int xq = x0 == 0 ? x1 : x0, zq = z0 + 3, xf = xq + (x0 == 0 ? 1 : -1);
            Direction mira2 = x0 == 0 ? Direction.EAST : Direction.WEST;
            BlockState pu2 = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.FACING, mira2.getOpposite());
            l.pon(xq, f, zq, pu2);
            l.pon(xq, f + 1, zq, pu2.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
            if (h(cx, cz, 130 + k) % 2 == 0)
                l.pon(xf, f + 2, zq + 2, Blocks.WALL_TORCH.defaultBlockState().setValue(net.minecraft.world.level.block.WallTorchBlock.FACING, mira2));
            // dentro: la cama (almohada blanca y colcha roja) en el lado de fuera, la mesilla y su lámpara
            int zc = z0 == 0 ? z0 + 1 : z1 - 1, dz = z0 == 0 ? 1 : -1;
            int xa = x0 == 0 ? x0 + 1 : x1 - 1, sx = x0 == 0 ? 1 : -1;
            for (int x = xa; x != xa + 2 * sx; x += sx) {
                l.pon(x, f, zc, d(Blocks.WHITE_WOOL));
                l.pon(x, f, zc + dz, d(Blocks.RED_WOOL));
                l.pon(x, f, zc + 2 * dz, d(Blocks.RED_WOOL));
            }
            l.pon(xa + 2 * sx, f, zc, d(Blocks.SPRUCE_PLANKS));
            l.pon(xa + 2 * sx, f + 1, zc, Blocks.LANTERN.defaultBlockState());
            if (h(cx, cz, 120 + k) % 2 == 0)
                l.pon(xa + 4 * sx, f, zc, Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, x0 == 0 ? Direction.WEST : Direction.EAST));
        }
        // paredes hacia las zonas que no son hotel (el pasillo sigue: es la puerta)
        for (Direction dd : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
            if (zona(cx + dd.getStepX(), cz + dd.getStepZ()) == Zona.HOTEL) continue;
            for (int i = 0; i < 16; i++) {
                if (i == 7 || i == 8) continue;
                int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
                int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(y == 0 ? Blocks.DARK_OAK_PLANKS : Blocks.SMOOTH_SANDSTONE));
            }
        }
    }

    // ------------------------------------------------------------------ Mundo sin Texturas
    /**
     * Donde acaba lo que no cargó: el suelo y casi todo son la textura que falta (cuadros rosa y negro, que se ven igual a
     * oscuras). Hay árboles a los que solo les cargó el tronco, casas a medio cargar con trozos de tejado flotando y
     * bloques sueltos en el aire. Al aire libre y sin techo.
     */
    private static void sinTextura(Lienzo l, int cx, int cz) {
        int f = SUELO;
        BlockState st = com.miscelanea.registry.ModBlocks.SIN_TEXTURA.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                // alguna baldosa sí que cargó (hierba)
                l.pon(x, f - 1, z, h(cx * 16 + x, cz * 16 + z, 70) % 23 == 0 ? d(Blocks.GRASS_BLOCK) : st);
                l.pon(x, f - 2, z, d(Blocks.STONE));
            }
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int k = 0; k < 4; k++) {
            int qx = q[k][0], qz = q[k][1];
            if (salida(cx, cz) && k == 0) continue;
            long r = h(cx, cz, 80 + k);
            int t = (int) (r % 10);
            if (t < 3) {
                // árbol: el tronco cargó, las hojas no
                int ax = qx + 3, az = qz + 3, alto = 4 + (int) ((r >> 8) % 2);
                for (int y = 0; y < alto; y++) l.pon(ax, f + y, az, d(Blocks.OAK_LOG));
                for (int x = -2; x <= 2; x++)
                    for (int z = -2; z <= 2; z++)
                        for (int y = alto - 1; y <= alto; y++)
                            if (Math.abs(x) + Math.abs(z) < 4 && !(x == 0 && z == 0 && y < alto)) l.pon(ax + x, f + y, az + z, st);
                l.pon(ax, f + alto + 1, az, st);
            } else if (t < 5) {
                // casa a medio cargar: paredes de piedra con huecos sin textura y el tejado flotando encima
                for (int x = qx + 1; x <= qx + 5; x++)
                    for (int z = qz + 1; z <= qz + 5; z++) {
                        boolean borde = x == qx + 1 || x == qx + 5 || z == qz + 1 || z == qz + 5;
                        if (!borde) continue;
                        boolean puerta = x == qx + 3 && (z == qz + 5 || z == qz + 1);
                        for (int y = 0; y < 3; y++) {
                            if (puerta && y < 2) continue;
                            long s = h(cx * 16 + x, cz * 16 + z, 90 + y);
                            if (s % 7 == 0) continue;                  // le faltan bloques
                            l.pon(x, f + y, z, s % 3 == 0 ? st : d(Blocks.COBBLESTONE));
                        }
                    }
                for (int x = qx; x <= qx + 6; x++)
                    for (int z = qz; z <= qz + 6; z++)
                        if (h(cx * 16 + x, cz * 16 + z, 95) % 3 != 0) l.pon(x, f + 5 + (int) (h(x, z, 96) % 2), z, (x + z) % 4 == 0 ? st : d(Blocks.OAK_PLANKS));
            } else if (t < 7) {
                // bloques sueltos en el aire, como si se hubieran quedado sin colocar
                int n = 3 + (int) ((r >> 4) % 4);
                for (int i = 0; i < n; i++) {
                    long s = h((int) r, i, 97);
                    l.pon(qx + (int) (s % 7), f + 2 + (int) ((s >> 8) % 7), qz + (int) ((s >> 16) % 7), st);
                }
            } else if (t == 8) {
                // un agujero en el mundo: por ahí se cae fuera del Purgatorio (de vuelta a casa)
                for (int x = qx + 2; x <= qx + 4; x++)
                    for (int z = qz + 2; z <= qz + 4; z++) {
                        boolean centro = x == qx + 3 || z == qz + 3;
                        if (!centro && (x + z) % 2 == 0) continue;     // con los bordes mordidos
                        l.pon(x, f - 1, z, d(Blocks.AIR));
                        l.pon(x, f - 2, z, d(Blocks.AIR));
                    }
            } else if (t == 7) {
                // un "jugador" que no cargó: dos bloques en pie, la pose de la T
                int ax = qx + 3, az = qz + 3;
                l.pon(ax, f, az, st);
                l.pon(ax, f + 1, az, st);
                l.pon(ax - 1, f + 1, az, st);
                l.pon(ax + 1, f + 1, az, st);
                l.pon(ax, f + 2, az, st);
            }
        }
    }

    // ------------------------------------------------------------------ salida
    /** Un marco de puerta suelto con el cartel verde de SALIDA encima: al cruzarlo se vuelve. */
    private static void puertaSalida(Lienzo l, Zona z) {
        int f = SUELO;
        BlockState marco = d(switch (z) {
            case FERIA -> Blocks.WHITE_CONCRETE;
            case SALA -> Blocks.QUARTZ_PILLAR;
            case RECREATIVA -> Blocks.BLACK_CONCRETE;
            case HOTEL -> Blocks.DARK_OAK_PLANKS;
            default -> com.miscelanea.registry.ModBlocks.SIN_TEXTURA.get();
        });
        int zz = 3;
        for (int y = 0; y < 4; y++) {
            l.pon(1, f + y, zz, marco);
            l.pon(5, f + y, zz, marco);
        }
        l.caja(2, f + 3, zz, 4, f + 3, zz, marco);
        l.caja(2, f, zz, 2, f + 2, zz, marco);
        l.caja(4, f, zz, 4, f + 2, zz, marco);
        BlockState puerta = com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get().defaultBlockState();
        l.pon(3, f, zz, puerta);
        l.pon(3, f + 1, zz, puerta);
        l.pon(3, f + 2, zz, com.miscelanea.registry.ModBlocks.CARTEL_SALIDA.get().defaultBlockState());
        // y despejado delante y detrás
        for (int zd : new int[]{zz - 1, zz + 1})
            for (int x = 2; x <= 4; x++)
                for (int y = 0; y < 3; y++) l.pon(x, f + y, zd, d(Blocks.AIR));
    }

    // ------------------------------------------------------------------ lo que pide Minecraft (sin nada que hacer)
    @Override
    public void applyCarvers(WorldGenRegion r, long seed, RandomState rs, BiomeManager bm, StructureManager sm, ChunkAccess c, GenerationStep.Carving s) {}

    @Override
    public void buildSurface(WorldGenRegion r, StructureManager sm, RandomState rs, ChunkAccess c) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion r) {}

    @Override
    public int getGenDepth() {
        return 128;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types t, LevelHeightAccessor a, RandomState r) {
        return SUELO;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor a, RandomState r) {
        BlockState[] col = new BlockState[a.getHeight()];
        Arrays.fill(col, Blocks.AIR.defaultBlockState());
        int i = SUELO - 1 - a.getMinBuildHeight();
        if (i >= 0 && i < col.length) col[i] = Blocks.BLACK_CONCRETE.defaultBlockState();
        if (i - 1 >= 0 && i - 1 < col.length) col[i - 1] = Blocks.STONE.defaultBlockState();
        return new NoiseColumn(a.getMinBuildHeight(), col);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState r, BlockPos p) {
        info.add("Purgatorio: " + zona(p.getX() >> 4, p.getZ() >> 4));
    }
}
