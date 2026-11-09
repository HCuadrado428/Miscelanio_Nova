package com.miscelanea.purgatorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.purgatorio.PurgatorioPlano.Grande;
import com.miscelanea.registry.ModBlocks;
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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
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
 * Genera El Purgatorio a partir de su plano ({@link PurgatorioPlano}): tres pisos, uno debajo de otro, con zonas de
 * formas raras, salas grandes de varios chunks, escaleras que bajan de piso, ascensores y pasillos sin sentido. Cada
 * chunk normal lleva una pieza de su zona elegida con un hash de sus coordenadas. No hay ruido, ni cuevas, ni
 * árboles, ni estructuras: cada chunk son unos cientos de bloques por piso puestos a mano (se genera al instante y hay
 * muy poco que dibujar; entre piso y piso hay suelo macizo, así que lo de abajo ni se dibuja). Es igual en todos los
 * mundos: siempre el mismo sitio, como si ya hubieras estado.
 */
public class PurgatorioGenerador extends ChunkGenerator {
    public static final Codec<PurgatorioGenerador> CODEC = RecordCodecBuilder.create(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(g -> g.biomeSource)).apply(i, i.stable(PurgatorioGenerador::new)));

    public static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERADORES = DeferredRegister.create(Registries.CHUNK_GENERATOR, Miscelanea.MOD_ID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> PURGATORIO = GENERADORES.register("purgatorio", () -> CODEC);

    /** Primer bloque de aire sobre el suelo del piso de arriba. */
    public static final int SUELO = PurgatorioPlano.SUELO;

    public enum Zona {FERIA, SALA, RECREATIVA, HOTEL, SIN_TEXTURA, PISCINA, APARCAMIENTO, COLEGIO, CENTRO}

    public PurgatorioGenerador(BiomeSource b) {
        super(b);
    }

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // ------------------------------------------------------------------ atajos al plano
    static long h(int a, int b, int c) {
        return PurgatorioPlano.h(a, b, c);
    }

    /** El hash de siempre en el piso de arriba; en los de abajo, otro (para que no se repita lo mismo debajo). */
    static long hn(int n, int a, int b, int c) {
        return PurgatorioPlano.hn(n, a, b, c);
    }

    /** La zona del chunk en el piso de arriba (para lo que no sabe de pisos). */
    public static Zona zona(int cx, int cz) {
        return PurgatorioPlano.zona(0, cx, cz);
    }

    public static Zona zona(int n, int cx, int cz) {
        return PurgatorioPlano.zona(n, cx, cz);
    }

    /** La zona en un sitio del mundo (el piso sale de la altura). */
    public static Zona zonaEn(double x, double y, double z) {
        return PurgatorioPlano.zona(PurgatorioPlano.nivel(y), (int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4);
    }

    /** ¿Hay una puerta de salida en este chunk del piso de arriba? */
    public static boolean salida(int cx, int cz) {
        return PurgatorioPlano.salida(0, cx, cz);
    }

    // ------------------------------------------------------------------ rincones raros (de vez en cuando)
    /** Sala de Espera vacía y a oscuras, con una sola silla de cara a la pared (una de cada 23). */
    public static boolean salaRara(int n, int cx, int cz) {
        return hn(n, cx, cz, 140) % 23 == 0 && !PurgatorioPlano.salida(n, cx, cz);
    }

    /** Salón recreativo vacío y a oscuras con una sola máquina encendida (uno de cada 15). */
    public static boolean recreativaRara(int n, int cx, int cz) {
        return hn(n, cx, cz, 141) % 15 == 0 && !PurgatorioPlano.salida(n, cx, cz);
    }

    /** La caseta abierta de la feria, con la luz encendida y un peluche (si en la parcela k hay caseta: una de cada 11). */
    public static boolean casetaAbierta(int cx, int cz, int k) {
        return h(cx, cz, 150 + k) % 11 == 0;
    }

    /** La habitación del hotel con la tele encendida sin señal y la puerta entornada (una de cada 8). */
    public static boolean habitacionTele(int n, int cx, int cz, int k) {
        return hn(n, cx, cz, 160 + k) % 8 == 0;
    }

    /** ¿Ocupa algo de fuera (el ascensor, el pilar de un agujero, la salida) la parcela k de este chunk en el piso n? */
    public static boolean ocupada(int n, int cx, int cz, int k) {
        return (k == 0 && PurgatorioPlano.salida(n, cx, cz)) || (k == 1 && PurgatorioPlano.ascensor(cx, cz)) || PurgatorioPlano.tapado(n, cx, cz, k);
    }

    /** ¿Es un chunk normal (sin sala grande ni escalera) y sin nada que tape una parcela? */
    public static boolean entero(int n, int cx, int cz) {
        if (PurgatorioPlano.grande(n, cx, cz) != null || PurgatorioPlano.escalera(n, cx, cz) || (n > 0 && PurgatorioPlano.escalera(n - 1, cx, cz))
                || PurgatorioPlano.ascensor(cx, cz)) return false;
        for (int k = 0; k < 4; k++) if (PurgatorioPlano.tapado(n, cx, cz, k)) return false;
        return true;
    }

    /**
     * Para la prueba: lo raro que hay en el chunk del piso n (si es de esa zona y es un chunk normal). -1 = nada; 0-3 =
     * la parcela (caseta abierta o habitación de la tele); 4 = el chunk entero (sala de la silla, salón de una máquina).
     */
    public static int raro(int n, int cx, int cz) {
        if (PurgatorioPlano.grande(n, cx, cz) != null || PurgatorioPlano.escalera(n, cx, cz) || (n > 0 && PurgatorioPlano.escalera(n - 1, cx, cz))) return -1;
        switch (zona(n, cx, cz)) {
            case SALA -> {
                return entero(n, cx, cz) && salaRara(n, cx, cz) ? 4 : -1;
            }
            case RECREATIVA -> {
                return entero(n, cx, cz) && recreativaRara(n, cx, cz) ? 4 : -1;
            }
            case PISCINA -> {
                return entero(n, cx, cz) && piscinaRara(n, cx, cz) ? 4 : -1;
            }
            case APARCAMIENTO -> {
                return entero(n, cx, cz) && garajeRaro(n, cx, cz) ? 4 : -1;
            }
            case COLEGIO -> {
                return !ocupada(n, cx, cz, 0) && aulaRara(n, cx, cz) ? 0 : -1;
            }
            case CENTRO -> {
                return !ocupada(n, cx, cz, 0) && tiendaRara(n, cx, cz) ? 0 : -1;
            }
            case FERIA -> {
                for (int k = 0; k < 4; k++)
                    if (!ocupada(n, cx, cz, k) && h(cx, cz, 20 + k) % 10 < 4 && casetaAbierta(cx, cz, k)) return k;
            }
            case HOTEL -> {
                for (int k = 0; k < 4; k++)
                    if (!ocupada(n, cx, cz, k) && habitacionTele(n, cx, cz, k)) return k;
            }
            default -> {
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ escribir el chunk
    static final class Lienzo {
        final ChunkAccess c;
        final BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        final Heightmap a, b;
        /** Desplazamiento (para dibujar una sala grande con sus coordenadas: x del chunk = x de la sala - ox). */
        final int ox, oz;

        Lienzo(ChunkAccess c) {
            this(c, c.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG), c.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG), 0, 0);
        }

        private Lienzo(ChunkAccess c, Heightmap a, Heightmap b, int ox, int oz) {
            this.c = c;
            this.a = a;
            this.b = b;
            this.ox = ox;
            this.oz = oz;
        }

        /** El mismo lienzo con otras coordenadas: (0, 0) del chunk es (ox, oz) de las nuevas. */
        Lienzo rel(int ox, int oz) {
            return new Lienzo(c, a, b, ox, oz);
        }

        /** x, z del chunk (0..15, o de la sala si es relativo); y absoluta. */
        void pon(int x, int y, int z, BlockState s) {
            x -= ox;
            z -= oz;
            if (x < 0 || x > 15 || z < 0 || z > 15 || y < 0 || y > 127) return;
            c.setBlockState(p.set(c.getPos().getMinBlockX() + x, y, c.getPos().getMinBlockZ() + z), s, false);
            a.update(x, y, z, s);
            b.update(x, y, z, s);
        }

        /** Como pon, pero con su bloque-entidad (la campana del vestíbulo, las máquinas vivas...). */
        void ponBE(int x, int y, int z, BlockState s) {
            pon(x, y, z, s);
            int lx = x - ox, lz = z - oz;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return;
            if (s.getBlock() instanceof net.minecraft.world.level.block.EntityBlock eb) {
                var be = eb.newBlockEntity(new BlockPos(c.getPos().getMinBlockX() + lx, y, c.getPos().getMinBlockZ() + lz), s);
                if (be != null) c.setBlockEntity(be);
            }
        }

        /** ¿Hay aire ahí? (fuera del chunk: no). */
        boolean libre(int x, int y, int z) {
            int lx = x - ox, lz = z - oz;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15) return false;
            return c.getBlockState(p.set(c.getPos().getMinBlockX() + lx, y, c.getPos().getMinBlockZ() + lz)).isAir();
        }

        /** Lo pone solo si no hay aire (para "descargar" algo que ya está, sin dejar bloques sueltos en el aire). */
        void cambiar(int x, int y, int z, BlockState s) {
            int lx = x - ox, lz = z - oz;
            if (lx < 0 || lx > 15 || lz < 0 || lz > 15 || libre(x, y, z)) return;
            pon(x, y, z, s);
        }

        void caja(int x0, int y0, int z0, int x1, int y1, int z1, BlockState s) {
            for (int x = Math.max(x0, ox); x <= Math.min(x1, ox + 15); x++)
                for (int y = y0; y <= y1; y++)
                    for (int z = Math.max(z0, oz); z <= Math.min(z1, oz + 15); z++) pon(x, y, z, s);
        }
    }

    static BlockState d(Block b) {
        return b.defaultBlockState();
    }

    static BlockState escalon(Block b, Direction sube) {
        return b.defaultBlockState().setValue(StairBlock.FACING, sube);
    }

    static final BlockState AIRE = Blocks.AIR.defaultBlockState();

    /**
     * Barrotes, vallas y cristales ya unidos (al generar no se avisa a los vecinos y saldrían como postes sueltos):
     * ns = unidos de norte a sur, eo = de este a oeste.
     */
    static BlockState unida(Block b, boolean ns, boolean eo) {
        return b.defaultBlockState().setValue(net.minecraft.world.level.block.CrossCollisionBlock.NORTH, ns)
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.SOUTH, ns)
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.EAST, eo)
                .setValue(net.minecraft.world.level.block.CrossCollisionBlock.WEST, eo);
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor e, Blender bl, RandomState r, StructureManager sm, ChunkAccess c) {
        Lienzo l = new Lienzo(c);
        int cx = c.getPos().x, cz = c.getPos().z;
        for (int n = 0; n < PurgatorioPlano.NIVELES; n++) {
            int f = PurgatorioPlano.base(n);
            Grande g = PurgatorioPlano.grande(n, cx, cz);
            Zona z = zona(n, cx, cz);
            if (g != null) {
                granSala(l.rel((cx - g.cx0()) * 16, (cz - g.cz0()) * 16), f, g);
                if (n == 0) contagio(l, f, cx, cz, z);
                continue;
            }
            switch (z) {
                case FERIA -> feria(l, n, f, cx, cz);
                case SALA -> sala(l, n, f, cx, cz);
                case RECREATIVA -> recreativa(l, n, f, cx, cz);
                case HOTEL -> hotel(l, n, f, cx, cz);
                case SIN_TEXTURA -> sinTextura(l, f, cx, cz);
                case PISCINA -> piscina(l, n, f, cx, cz);
                case APARCAMIENTO -> aparcamiento(l, n, f, cx, cz);
                case COLEGIO -> colegio(l, n, f, cx, cz);
                case CENTRO -> centro(l, n, f, cx, cz);
            }
            if (PurgatorioPlano.salida(n, cx, cz)) puertaSalida(l, f, z);
            if (n == 0) contagio(l, f, cx, cz, z);
        }
        if (PurgatorioPlano.ascensor(cx, cz))
            for (int n = 0; n < PurgatorioPlano.NIVELES; n++) ascensor(l, PurgatorioPlano.base(n));
        for (int n = 0; n < PurgatorioPlano.NIVELES - 1; n++)
            if (PurgatorioPlano.escalera(n, cx, cz)) escalera(l, n, cx, cz);
        for (int k = 0; k < 4; k++)
            if (PurgatorioPlano.agujero(cx, cz, k)) pilar(l, PurgatorioPlano.PARCELAS[k][0], PurgatorioPlano.PARCELAS[k][1]);
        return CompletableFuture.completedFuture(c);
    }

    // ------------------------------------------------------------------ Feria Cerrada
    /** Una feria de noche fuera de temporada: asfalto, casetas cerradas con la lona echada, farolas (alguna fundida). */
    private static void feria(Lienzo l, int n, int f, int cx, int cz) {
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
        farola(l, f, 6, 6, h(cx, cz, 2) % 6 != 0);
        if (h(cx, cz, 9) % 2 == 0) farola(l, f, 9, 9, h(cx, cz, 10) % 4 != 0);
        // cuatro parcelas
        for (int k = 0; k < 4; k++) {
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            if (ocupada(n, cx, cz, k)) continue;        // ahí va la puerta (o el ascensor)
            int tipo = (int) (h(cx, cz, 20 + k) % 10);
            boolean norte = qz == 0;
            if (tipo < 4) caseta(l, f, qx, norte, (int) (h(cx, cz, 30 + k) % 4), casetaAbierta(cx, cz, k));
            else if (tipo < 6) banco(l, f, qx, qz, norte);
            else if (tipo == 6) tiovivo(l, f, qx, qz);
            else if (tipo == 7) taquilla(l, f, qx, qz);
            else if (tipo == 8 && h(cx, cz, 45 + k) % 3 == 0) puertaSuelta(l, f, qx, qz, norte);
            basura(l, f, qx, qz, h(cx, cz, 40 + k));
        }
    }

    private static void farola(Lienzo l, int f, int x, int z, boolean encendida) {
        for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(Blocks.POLISHED_BLACKSTONE_WALL));
        int dz = z < 8 ? 1 : -1;
        l.pon(x, f + 4, z, d(Blocks.POLISHED_BLACKSTONE_SLAB));
        l.pon(x, f + 4, z + dz, d(Blocks.POLISHED_BLACKSTONE_SLAB));
        l.pon(x, f + 3, z + dz, encendida ? Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true) : d(Blocks.CHAIN));
    }

    /** Caseta de feria cerrada: lona a rayas en el tejado y la cortina echada en el mostrador. */
    private static void caseta(Lienzo l, int f, int qx, boolean norte, int color, boolean abierta) {
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
        BlockState bombilla = d(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, abierta);
        l.pon(x0, f + 2, zd + paso, bombilla);
        l.pon(x1, f + 2, zd + paso, bombilla);
        if (abierta) {
            // la única abierta de la feria: la cortina subida, la luz de dentro encendida y un peluche en el mostrador,
            // mirando a la calle (nadie lo ha ganado)
            for (int x = x0 + 1; x < x1; x++) {
                l.pon(x, f + 1, zd, AIRE);
                l.pon(x, f + 2, zd, AIRE);
            }
            l.pon(x0 + 2, f + 1, zd, ModBlocks.PELUCHE.get().defaultBlockState()
                    .setValue(com.miscelanea.feria.PelucheBlock.FACING, norte ? Direction.SOUTH : Direction.NORTH)
                    .setValue(com.miscelanea.feria.PelucheBlock.COLOR, qx == 0 ? 1 : 3));
            l.pon(x0 + 3, f + 2, zd - 2 * paso, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    private static void banco(Lienzo l, int f, int qx, int qz, boolean norte) {
        Direction mira = norte ? Direction.SOUTH : Direction.NORTH;
        int z = norte ? qz + 5 : qz + 1;
        for (int x = qx + 2; x <= qx + 4; x++)
            l.pon(x, f, z, escalon(Blocks.DARK_OAK_STAIRS, mira.getOpposite()));
        l.pon(qx + 5, f, z, d(Blocks.CAULDRON));               // papelera
        l.pon(qx + 1, f, z, d(Blocks.POTTED_DEAD_BUSH));
    }

    /** Lo que queda de un tiovivo de niños: la valla redonda y el palo del centro. */
    private static void tiovivo(Lienzo l, int f, int qx, int qz) {
        int cx = qx + 3, cz = qz + 3;
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r > 2.5 && r < 3.4) l.pon(cx + x, f, cz + z, d(Blocks.OAK_FENCE));
                else if (r <= 2.5) l.pon(cx + x, f - 1, cz + z, d((x + z & 1) == 0 ? Blocks.RED_TERRACOTTA : Blocks.WHITE_TERRACOTTA));
            }
        for (int y = 0; y < 3; y++) l.pon(cx, f + y, cz, d(Blocks.STRIPPED_BIRCH_LOG));
        l.pon(cx, f + 3, cz, d(Blocks.RED_WOOL));
        l.pon(cx + 1, f, cz, escalon(Blocks.SPRUCE_STAIRS, Direction.WEST));     // un asiento suelto
    }

    private static void taquilla(Lienzo l, int f, int qx, int qz) {
        l.caja(qx + 2, f, qz + 2, qx + 4, f + 2, qz + 4, d(Blocks.WHITE_TERRACOTTA));
        l.pon(qx + 3, f + 1, qz + 2, unida(Blocks.GLASS_PANE, false, true));
        l.pon(qx + 3, f + 1, qz + 4, unida(Blocks.GLASS_PANE, false, true));
        l.caja(qx + 1, f + 3, qz + 1, qx + 5, f + 3, qz + 5, d(Blocks.RED_CONCRETE));
        l.pon(qx + 3, f + 4, qz + 3, Blocks.SMOOTH_STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
    }

    /** Una puerta suelta en mitad de la parcela, con su marco y sin pared: se abre y al otro lado no hay nada. */
    private static void puertaSuelta(Lienzo l, int f, int qx, int qz, boolean norte) {
        int x = qx + 3, z = qz + 3;
        for (int y = 0; y < 3; y++) {
            l.pon(x - 1, f + y, z, d(Blocks.STRIPPED_DARK_OAK_LOG));
            l.pon(x + 1, f + y, z, d(Blocks.STRIPPED_DARK_OAK_LOG));
        }
        l.pon(x, f + 2, z, d(Blocks.STRIPPED_DARK_OAK_LOG));
        BlockState pu = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, norte ? Direction.NORTH : Direction.SOUTH);
        l.pon(x, f, z, pu);
        l.pon(x, f + 1, z, pu.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** Papeles y vasos por el suelo (alfombras sueltas). */
    private static void basura(Lienzo l, int f, int qx, int qz, long r) {
        Block[] b = {Blocks.WHITE_CARPET, Blocks.YELLOW_CARPET, Blocks.RED_CARPET, Blocks.LIGHT_GRAY_CARPET};
        int n = (int) (r % 3);
        for (int i = 0; i < n; i++) {
            long s = h((int) r, i, 77);
            int x = qx + (int) (s % 7), z = qz + (int) ((s >> 8) % 7);
            if (x == 6 && z == 6 || !l.libre(x, f, z)) continue;   // solo en el suelo libre (no dentro de una caseta)
            l.pon(x, f, z, d(b[(int) ((s >> 16) % b.length)]));
        }
    }

    // ------------------------------------------------------------------ Sala de Espera
    /**
     * Una sala de espera que no se acaba: baldosas, techo bajo con fluorescentes (alguno fundido), filas de sillas y
     * tabiques con huecos. Hacia otras zonas siempre hay pared con una puerta.
     */
    private static void sala(Lienzo l, int n, int f, int cx, int cz) {
        int techo = f + 4;
        // de vez en cuando, una sala vacía a oscuras con una sola silla, de cara a la pared, y una luz encima
        boolean rara = zona(n, cx, cz) == Zona.SALA && raro(n, cx, cz) == 4;
        BlockState suelo = ModBlocks.SUELO_SALA.get().defaultBlockState();
        BlockState placa = ModBlocks.TECHO_SALA.get().defaultBlockState();
        BlockState luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, suelo);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean panel = x % 5 == 2 && z % 5 == 2;
                boolean encendido = rara ? x == 12 && z == 2 : hn(n, cx * 16 + x, cz * 16 + z, 3) % 5 != 0;
                l.pon(x, techo, z, panel ? luz.setValue(FluorescenteBlock.ENCENDIDO, encendido) : placa);
            }
        if (rara) {
            l.pon(12, f, 1, escalon(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
        } else {
            // sillas en filas mirando al norte, con pasillo en medio
            int filas = (int) (hn(n, cx, cz, 4) % 3);
            if (filas > 0)
                for (int zr : new int[]{3, 11})
                    for (int x = 1; x < 15; x++) {
                        if (x == 7 || x == 8) continue;
                        l.pon(x, f, zr, escalon(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
                        if (filas == 2) l.pon(x, f, zr + 2, escalon(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
                    }
            else if (hn(n, cx, cz, 6) % 3 == 0) {
                // una escalera que no lleva a nada: cuatro escalones de dos de ancho que suben hasta el techo
                for (int i = 0; i < 4; i++)
                    for (int z = 12; z <= 13; z++) {
                        for (int y = 0; y < i; y++) l.pon(10 + i, f + y, z, d(Blocks.SMOOTH_QUARTZ));
                        l.pon(10 + i, f + i, z, escalon(Blocks.QUARTZ_STAIRS, Direction.EAST));
                    }
                for (int i = 0; i < 4; i++) l.pon(10 + i, f + i + 1, 14, unida(Blocks.IRON_BARS, false, true));
            } else {
                // un mostrador de "turnos" con su planta
                l.caja(5, f, 6, 10, f, 6, d(Blocks.SMOOTH_QUARTZ));
                l.pon(5, f + 1, 6, d(Blocks.POTTED_BAMBOO));
            }
        }
        // tabiques: hacia otras zonas siempre; entre salas, a veces (y en la sala rara, en los cuatro lados). Con hueco en
        // el centro para pasar, con su dintel
        BlockState pared = ModBlocks.PARED_SALA.get().defaultBlockState();
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            boolean otra = zona(n, nx, nz) != Zona.SALA || PurgatorioPlano.grande(n, nx, nz) != null;
            // cada borde se decide una vez para los dos chunks que lo comparten
            int bx = Math.min(cx, nx) * 2 + (dd.getAxis() == Direction.Axis.X ? 1 : 0), bz = Math.min(cz, nz);
            if (otra || rara || hn(n, bx, bz, 5) % 3 == 0) {
                tabique(l, f, dd, 4, d(Blocks.LIGHT_GRAY_CONCRETE), pared, 3, pared);
                // y de vez en cuando, en el tabique, una puerta que da a la pared
                if (!rara && hn(n, cx * 4 + dd.ordinal(), cz, 8) % 4 == 0) puertaAPared(l, f, dd, 4, Blocks.BIRCH_DOOR);
            }
        }
        // columnas en las esquinas
        for (int x : new int[]{0, 15})
            for (int z : new int[]{0, 15})
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, d(Blocks.QUARTZ_PILLAR));
    }

    static final Direction[] LADOS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /**
     * Un tabique en el borde del chunk (lado dd) de "alto" bloques: zócalo abajo y pared encima, con el hueco de paso de
     * dos de ancho en medio (donde siguen las calles y los pasillos) cerrado por arriba desde la altura "hueco" con el
     * dintel: así se ve una puerta y no un agujero en la pared.
     */
    private static void tabique(Lienzo l, int f, Direction dd, int alto, BlockState zocalo, BlockState pared, int hueco, BlockState dintel) {
        for (int i = 0; i < 16; i++) {
            boolean paso = i == 7 || i == 8;
            int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
            int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
            for (int y = paso ? hueco : 0; y < alto; y++) l.pon(x, f + y, z, paso ? dintel : y == 0 ? zocalo : pared);
        }
    }

    /**
     * Una puerta pegada al tabique del lado dd, en la posición i (lejos del paso): con su marco, cerrada, y al abrirla,
     * ladrillo (el ladrillo ocupa el sitio del tabique). Solo si el sitio está libre.
     */
    private static void puertaAPared(Lienzo l, int f, Direction dd, int i, Block puerta) {
        int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
        int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
        int px = x - dd.getStepX(), pz = z - dd.getStepZ();
        Direction lado = dd.getClockWise();
        for (int y = 0; y < 3; y++)
            for (int s = -1; s <= 1; s++)
                if (!l.libre(px + lado.getStepX() * s, f + y, pz + lado.getStepZ() * s)) return;
        BlockState pu = puerta.defaultBlockState().setValue(DoorBlock.FACING, dd.getOpposite());
        l.pon(px, f, pz, pu);
        l.pon(px, f + 1, pz, pu.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        l.pon(x, f, z, d(Blocks.BRICKS));
        l.pon(x, f + 1, z, d(Blocks.BRICKS));
        for (int y = 0; y < 3; y++) {
            l.pon(px + lado.getStepX(), f + y, pz + lado.getStepZ(), d(Blocks.STRIPPED_BIRCH_LOG));
            l.pon(px - lado.getStepX(), f + y, pz - lado.getStepZ(), d(Blocks.STRIPPED_BIRCH_LOG));
        }
        l.pon(px, f + 2, pz, d(Blocks.STRIPPED_BIRCH_LOG));
    }

    // ------------------------------------------------------------------ Recreativa Infinita
    /** Juegos de las máquinas que aún funcionan (las de un jugador, que caben en un bloque de ancho). */
    private static final String[] JUEGOS_VIVOS = {"astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores", "mazmorra", "pinball"};

    /**
     * Un salón recreativo de los noventa que no se acaba: moqueta de neón, techo negro con luces moradas y filas de
     * máquinas espalda con espalda. Casi todas están fundidas (un bloque normal: no cuesta nada); una de cada seis
     * funciona de verdad y se puede jugar (esas sí llevan su bloque-entidad, por eso son pocas).
     */
    private static void recreativa(Lienzo l, int n, int f, int cx, int cz) {
        int techo = f + 5;
        // de vez en cuando, un salón vacío y a oscuras con una sola máquina encendida en medio
        boolean rara = zona(n, cx, cz) == Zona.RECREATIVA && raro(n, cx, cz) == 4;
        BlockState moqueta = ModBlocks.MOQUETA_RECREATIVA.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, moqueta);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean pasillo = x == 7 || x == 8 || z == 7 || z == 8;
                boolean luz = !rara && pasillo && (x == 7 || z == 7) && ((x + z) % 4 == 2) && hn(n, cx * 16 + x, cz * 16 + z, 3) % 7 != 0;
                l.pon(x, techo, z, d(luz ? ((x + z) % 8 == 2 ? Blocks.PEARLESCENT_FROGLIGHT : Blocks.VERDANT_FROGLIGHT) : Blocks.BLACK_CONCRETE));
            }
        if (rara) maquina(l, f, 11, 11, Direction.NORTH, 1);      // r = 1: viva, la primera de la lista
        for (int k = 0; k < 4 && !rara; k++) {
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            if (ocupada(n, cx, cz, k)) continue;
            long t = hn(n, cx, cz, 50 + k) % 10;
            if (t < 7) {
                // dos filas espalda con espalda: una mira al norte y la otra al sur
                for (int x = qx + 1; x <= qx + 5; x++) {
                    maquina(l, f, x, qz + 2, Direction.NORTH, hn(n, cx * 16 + x, cz * 16 + qz, 60));
                    maquina(l, f, x, qz + 3, Direction.SOUTH, hn(n, cx * 16 + x, cz * 16 + qz, 61));
                }
            } else if (t < 9) {
                // en columna: una mira al oeste y la otra al este
                for (int z = qz + 1; z <= qz + 5; z++) {
                    maquina(l, f, qx + 2, z, Direction.WEST, hn(n, cx * 16 + qx, cz * 16 + z, 62));
                    maquina(l, f, qx + 3, z, Direction.EAST, hn(n, cx * 16 + qx, cz * 16 + z, 63));
                }
            } else {
                // un rincón de descanso: sofá de escay rojo y una papelera
                for (int x = qx + 1; x <= qx + 4; x++)
                    l.pon(x, f, qz + 1, d(Blocks.RED_TERRACOTTA));
                for (int x = qx + 1; x <= qx + 4; x++)
                    l.pon(x, f, qz + 2, d(Blocks.SMOOTH_STONE_SLAB));
                l.pon(qx + 5, f, qz + 2, d(Blocks.CAULDRON));
            }
        }
        // paredes negras hacia las zonas que no son recreativa (con su puerta para pasar)
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.RECREATIVA && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            tabique(l, f, dd, 5, d(Blocks.BLACK_CONCRETE), d(Blocks.BLACK_CONCRETE), 3, d(Blocks.BLACK_CONCRETE));
            for (int i = 0; i < 16; i += 3) {
                if (i == 6 || i == 9) continue;      // junto a la puerta, no
                int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
                int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
                l.pon(x, f + 2, z, d(Blocks.PURPLE_STAINED_GLASS));
            }
        }
    }

    /** Una máquina en (x, z): fundida casi siempre, viva una de cada seis, y de vez en cuando falta (un hueco). */
    private static void maquina(Lienzo l, int f, int x, int z, Direction mira, long r) {
        if (r % 12 == 0) return;
        if (r % 6 == 1) {
            String j = JUEGOS_VIVOS[(int) ((r >> 8) % JUEGOS_VIVOS.length)];
            var ro = ModBlocks.RECREATIVAS_JUEGO.get(j);
            if (ro != null) {
                BlockState s = ro.get().defaultBlockState().setValue(com.miscelanea.arcade.RecreativaBlock.FACING, mira);
                l.ponBE(x, f, z, s);
                l.pon(x, f + 1, z, s.setValue(com.miscelanea.arcade.RecreativaBlock.HALF, DoubleBlockHalf.UPPER));
                return;
            }
        }
        BlockState s = ModBlocks.RECREATIVA_APAGADA.get().defaultBlockState()
                .setValue(RecreativaApagadaBlock.FACING, mira)
                .setValue(RecreativaApagadaBlock.MUEBLE, (int) ((r >> 12) % RecreativaApagadaBlock.MUEBLES.length));
        l.pon(x, f, z, s);
        l.pon(x, f + 1, z, s.setValue(RecreativaApagadaBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // ------------------------------------------------------------------ Hotel del Videoclip
    /**
     * Los pasillos de un hotel que no se acaba (el del videoclip): moqueta de hexágonos, paredes color crema con zócalo de
     * madera, luz cálida en el techo y puertas cerradas a los dos lados. Detrás de cada puerta, una habitación pequeña y
     * siempre igual: la cama hecha, la mesilla con su lámpara y nadie. Alguna puerta, al abrirla, da a la pared.
     */
    private static void hotel(Lienzo l, int n, int f, int cx, int cz) {
        int techo = f + 4;
        BlockState moqueta = ModBlocks.MOQUETA_HOTEL.get().defaultBlockState();
        BlockState papel = ModBlocks.PAPEL_HOTEL.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean pasillo = x == 7 || x == 8 || z == 7 || z == 8;
                l.pon(x, f - 1, z, pasillo ? moqueta : d(Blocks.SPRUCE_PLANKS));
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean luz = pasillo && (x == 7 || z == 7) && (x + z) % 5 == 2 && hn(n, cx * 16 + x, cz * 16 + z, 3) % 9 != 0;
                l.pon(x, techo, z, d(luz ? Blocks.OCHRE_FROGLIGHT : Blocks.SMOOTH_SANDSTONE));
            }
        for (int k = 0; k < 4; k++) {
            int x0 = PurgatorioPlano.PARCELAS[k][0], z0 = PurgatorioPlano.PARCELAS[k][1];
            if (ocupada(n, cx, cz, k)) continue;
            habitacion(l, n, f, cx, cz, k, x0, z0, x0 + 6, z0 + 6, papel);
        }
        // paredes hacia las zonas que no son hotel (el pasillo sigue: es la puerta, con su marco de madera)
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.HOTEL && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            tabique(l, f, dd, 4, d(Blocks.DARK_OAK_PLANKS), papel, 3, d(Blocks.DARK_OAK_PLANKS));
        }
    }

    private static void habitacion(Lienzo l, int n, int f, int cx, int cz, int k, int x0, int z0, int x1, int z1, BlockState papel) {
        // paredes de la habitación: zócalo de madera y crema encima
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, y == 0 ? d(Blocks.DARK_OAK_PLANKS) : papel);
            }
        // de vez en cuando, la habitación de la tele: la puerta entornada, la lámpara apagada y la tele encendida sin
        // señal, de cara a la cama
        boolean tele = habitacionTele(n, cx, cz, k);
        // y de vez en cuando, una puerta que al abrirla da a la pared (ladrillo justo detrás)
        boolean ciega = !tele && hn(n, cx, cz, 180 + k) % 6 == 0;
        // la puerta, en la pared que da al pasillo largo (el de z = 7..8)
        int zp = z0 == 0 ? z1 : z0, xp = x0 + 3;
        Direction mira = z0 == 0 ? Direction.SOUTH : Direction.NORTH;
        BlockState pu = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, mira.getOpposite());
        if (tele) pu = pu.setValue(DoorBlock.OPEN, true);
        l.pon(xp, f, zp, pu);
        l.pon(xp, f + 1, zp, pu.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (ciega) {
            l.pon(xp, f, zp - mira.getStepZ(), d(Blocks.BRICKS));
            l.pon(xp, f + 1, zp - mira.getStepZ(), d(Blocks.BRICKS));
        }
        // un aplique a cada lado de la puerta, en el pasillo
        int zf = zp + (z0 == 0 ? 1 : -1);
        l.pon(xp - 2, f + 2, zf, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, mira));
        if (hn(n, cx, cz, 110 + k) % 3 != 0)
            l.pon(xp + 2, f + 2, zf, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, mira));
        // y otra en la pared que da al otro pasillo (el de x = 7..8), con su aplique
        int xq = x0 == 0 ? x1 : x0, zq = z0 + 3, xf = xq + (x0 == 0 ? 1 : -1);
        Direction mira2 = x0 == 0 ? Direction.EAST : Direction.WEST;
        BlockState pu2 = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, mira2.getOpposite());
        l.pon(xq, f, zq, pu2);
        l.pon(xq, f + 1, zq, pu2.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        if (hn(n, cx, cz, 130 + k) % 2 == 0)
            l.pon(xf, f + 2, zq + 2, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, mira2));
        // dentro: la cama (almohada blanca y colcha roja) en el lado de fuera, la mesilla y su lámpara
        int zc = z0 == 0 ? z0 + 1 : z1 - 1, dz = z0 == 0 ? 1 : -1;
        int xa = x0 == 0 ? x0 + 1 : x1 - 1, sx = x0 == 0 ? 1 : -1;
        for (int x = xa; x != xa + 2 * sx; x += sx) {
            l.pon(x, f, zc, d(Blocks.WHITE_WOOL));
            l.pon(x, f, zc + dz, d(Blocks.RED_WOOL));
            l.pon(x, f, zc + 2 * dz, d(Blocks.RED_WOOL));
        }
        l.pon(xa + 2 * sx, f, zc, d(Blocks.SPRUCE_PLANKS));
        if (tele) {
            // a los pies de la cama, en un mueble bajo, mirando a la almohada
            l.pon(xa, f, zc + 3 * dz, d(Blocks.DARK_OAK_PLANKS));
            l.pon(xa, f + 1, zc + 3 * dz, ModBlocks.TELE.get().defaultBlockState()
                    .setValue(TeleBlock.FACING, dz > 0 ? Direction.NORTH : Direction.SOUTH));
        } else l.pon(xa + 2 * sx, f + 1, zc, Blocks.LANTERN.defaultBlockState());
        if (hn(n, cx, cz, 120 + k) % 2 == 0)
            l.pon(xa + 4 * sx, f, zc, escalon(Blocks.SPRUCE_STAIRS, x0 == 0 ? Direction.WEST : Direction.EAST));
    }

    // ------------------------------------------------------------------ Mundo sin Texturas
    /**
     * Donde acaba lo que no cargó: el suelo y casi todo son la textura que falta (cuadros rosa y negro, que se ven igual a
     * oscuras). Hay árboles a los que solo les cargó el tronco, casas a medio cargar con trozos de tejado flotando y
     * bloques sueltos en el aire. Al aire libre y sin techo (solo en el piso de arriba).
     */
    private static void sinTextura(Lienzo l, int f, int cx, int cz) {
        BlockState st = ModBlocks.SIN_TEXTURA.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                // alguna baldosa sí que cargó (hierba)
                l.pon(x, f - 1, z, h(cx * 16 + x, cz * 16 + z, 70) % 23 == 0 ? d(Blocks.GRASS_BLOCK) : st);
                l.pon(x, f - 2, z, d(Blocks.STONE));
            }
        for (int k = 0; k < 4; k++) {
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            if (ocupada(0, cx, cz, k)) continue;
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
                int nb = 3 + (int) ((r >> 4) % 4);
                for (int i = 0; i < nb; i++) {
                    long s = h((int) r, i, 97);
                    l.pon(qx + (int) (s % 7), f + 2 + (int) ((s >> 8) % 7), qz + (int) ((s >> 16) % 7), st);
                }
            } else if (t == 8) {
                // un agujero en el mundo: por ahí se cae fuera del Purgatorio (de vuelta a casa), atravesando los pisos
                // de abajo (ver pilar)
                if (PurgatorioPlano.agujero(cx, cz, k))
                    for (int x = qx + 2; x <= qx + 4; x++)
                        for (int z = qz + 2; z <= qz + 4; z++) {
                            boolean centro = x == qx + 3 || z == qz + 3;
                            if (!centro && (x + z) % 2 == 0) continue;     // con los bordes mordidos
                            l.pon(x, f - 1, z, AIRE);
                            l.pon(x, f - 2, z, AIRE);
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

    /**
     * El agujero del Mundo sin Texturas visto desde los pisos de abajo: un pilar sin textura de 5 x 5 de suelo a techo,
     * hueco por dentro, por el que se cae de arriba hasta el vacío. Atraviesa los suelos de los pisos.
     */
    private static void pilar(Lienzo l, int qx, int qz) {
        BlockState st = ModBlocks.SIN_TEXTURA.get().defaultBlockState();
        int abajo = PurgatorioPlano.base(PurgatorioPlano.NIVELES - 1) - 2, arriba = PurgatorioPlano.SUELO - 3;
        for (int x = qx + 1; x <= qx + 5; x++)
            for (int z = qz + 1; z <= qz + 5; z++) {
                boolean borde = x == qx + 1 || x == qx + 5 || z == qz + 1 || z == qz + 5;
                for (int y = abajo; y <= arriba; y++) l.pon(x, y, z, borde ? st : AIRE);
            }
    }

    // ------------------------------------------------------------------ costuras con el Mundo sin Texturas
    /** El suelo propio de cada zona (el que asoma entre los cuadros rosas cerca de la raya). */
    static BlockState suelo(Zona z) {
        return switch (z) {
            case FERIA -> d(Blocks.BLACK_CONCRETE);
            case SALA -> ModBlocks.SUELO_SALA.get().defaultBlockState();
            case RECREATIVA -> ModBlocks.MOQUETA_RECREATIVA.get().defaultBlockState();
            case HOTEL -> ModBlocks.MOQUETA_HOTEL.get().defaultBlockState();
            case SIN_TEXTURA -> ModBlocks.SIN_TEXTURA.get().defaultBlockState();
            case PISCINA -> ModBlocks.AZULEJO_PISCINA.get().defaultBlockState();
            case APARCAMIENTO -> ModBlocks.SUELO_APARCAMIENTO.get().defaultBlockState();
            case COLEGIO -> ModBlocks.SUELO_COLEGIO.get().defaultBlockState();
            case CENTRO -> ModBlocks.SUELO_CENTRO.get().defaultBlockState();
        };
    }

    /**
     * La raya con el Mundo sin Texturas no es limpia: como si el mundo se estuviera cargando, cerca del borde del Mundo
     * sin Texturas asoma el suelo de la zona de al lado (cada vez menos al alejarse), y en la zona de al lado el suelo y
     * la pared que dan al Mundo sin Texturas tienen bloques que "no han cargado". Solo se cambian bloques que ya están
     * (nada queda flotando) y las calles, pasillos y puertas siguen libres. En las zonas con cuartos pegados a la pared
     * (el hotel), solo el marco de la puerta: la pared de la raya es también la de una habitación, y desde dentro de la
     * habitación no tiene que verse (antes salían rayas rosas y negras en la pared de alguna habitación).
     */
    private static void contagio(Lienzo l, int f, int cx, int cz, Zona z) {
        BlockState st = ModBlocks.SIN_TEXTURA.get().defaultBlockState();
        int[] pSin = {72, 52, 34, 18, 7}, pOtra = {40, 18, 6};
        boolean soloMarco = z == Zona.HOTEL || z == Zona.COLEGIO || z == Zona.CENTRO;
        for (Direction dd : LADOS) {
            Zona otra = zona(0, cx + dd.getStepX(), cz + dd.getStepZ());
            if (otra == z || (z != Zona.SIN_TEXTURA && otra != Zona.SIN_TEXTURA)) continue;
            int[] prob = z == Zona.SIN_TEXTURA ? pSin : pOtra;
            BlockState pone = z == Zona.SIN_TEXTURA ? suelo(otra) : st;
            for (int dist = 0; dist < prob.length; dist++)
                for (int i = 0; i < 16; i++) {
                    int x = dd == Direction.WEST ? dist : dd == Direction.EAST ? 15 - dist : i;
                    int zz = dd == Direction.NORTH ? dist : dd == Direction.SOUTH ? 15 - dist : i;
                    long r = h(cx * 16 + x, cz * 16 + zz, 170 + dd.ordinal()) % 100;
                    if (r < prob[dist]) l.cambiar(x, f - 1, zz, pone);
                    // y la pared que da al Mundo sin Texturas, con huecos rosas
                    if (dist == 0 && z != Zona.SIN_TEXTURA && z != Zona.FERIA && (!soloMarco || (i >= 6 && i <= 9)))
                        for (int y = 0; y < 4; y++)
                            if (h(cx * 16 + x, f + y, cz * 16 + zz) % 100 < (soloMarco ? 40 : 22)) l.cambiar(x, f + y, zz, st);
                }
        }
    }

    // ------------------------------------------------------------------ salida
    /** Un marco de puerta suelto con el cartel verde de SALIDA encima: al cruzarlo se vuelve. */
    private static void puertaSalida(Lienzo l, int f, Zona z) {
        BlockState marco = switch (z) {
            case FERIA -> d(Blocks.WHITE_CONCRETE);
            case SALA -> d(Blocks.QUARTZ_PILLAR);
            case RECREATIVA -> d(Blocks.BLACK_CONCRETE);
            case HOTEL -> d(Blocks.DARK_OAK_PLANKS);
            case SIN_TEXTURA -> ModBlocks.SIN_TEXTURA.get().defaultBlockState();
            case PISCINA -> ModBlocks.AZULEJO_PISCINA_AZUL.get().defaultBlockState();
            case APARCAMIENTO -> ModBlocks.COLUMNA_APARCAMIENTO_BASE.get().defaultBlockState();
            case COLEGIO -> ModBlocks.PARED_COLEGIO_ZOCALO.get().defaultBlockState();
            case CENTRO -> d(Blocks.SMOOTH_QUARTZ);
        };
        int zz = 3;
        for (int y = 0; y < 4; y++) {
            l.pon(1, f + y, zz, marco);
            l.pon(5, f + y, zz, marco);
        }
        l.caja(2, f + 3, zz, 4, f + 3, zz, marco);
        l.caja(2, f, zz, 2, f + 2, zz, marco);
        l.caja(4, f, zz, 4, f + 2, zz, marco);
        BlockState puerta = ModBlocks.PUERTA_SALIDA.get().defaultBlockState();
        l.pon(3, f, zz, puerta);
        l.pon(3, f + 1, zz, puerta);
        l.pon(3, f + 2, zz, ModBlocks.CARTEL_SALIDA.get().defaultBlockState());
        // y despejado delante y detrás
        for (int zd : new int[]{zz - 1, zz + 1})
            for (int x = 2; x <= 4; x++)
                for (int y = 0; y < 3; y++) l.pon(x, f + y, zd, AIRE);
    }

    // ------------------------------------------------------------------ escaleras entre pisos
    /**
     * La escalera de servicio que baja del piso n al n + 1 por la calle de en medio (x = 7..8) de punta a punta del
     * chunk: se entra por el norte en el piso n (z = 0) y se sale por el sur en el n + 1 (z = 15), 16 escalones abajo. En
     * el piso n se pasa por encima (el suelo sigue desde z = 4, con barandilla); en el n + 1 el hueco de debajo de la
     * escalera es un pasillo que no lleva a ningún sitio. En el garaje, rampa de hormigón con su raya amarilla.
     */
    private static void escalera(Lienzo l, int n, int cx, int cz) {
        int f = PurgatorioPlano.base(n);
        boolean garaje = zona(n, cx, cz) == Zona.APARCAMIENTO || zona(n + 1, cx, cz) == Zona.APARCAMIENTO;
        BlockState pelda = escalon(garaje ? Blocks.STONE_STAIRS : Blocks.POLISHED_ANDESITE_STAIRS, Direction.NORTH);
        BlockState raya = escalon(Blocks.SANDSTONE_STAIRS, Direction.NORTH);
        BlockState bajo = d(garaje ? Blocks.STONE : Blocks.POLISHED_ANDESITE);
        BlockState pared = d(garaje ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE);
        BlockState techo = d(Blocks.SMOOTH_STONE);
        BlockState luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int z = 0; z < 16; z++) {
            int s = f - 1 - z;                     // donde se pisa en el escalón z (el bloque está en s - 1)
            for (int x = 7; x <= 8; x++) {
                l.pon(x, s - 1, z, z == 15 ? bajo : garaje && x == 7 && z % 2 == 0 ? raya : pelda);
                l.pon(x, s - 2, z, bajo);
                for (int y = s; y <= s + 2; y++) l.pon(x, y, z, AIRE);
                // techo del túnel (donde no lo es ya el suelo del piso de arriba)
                if (s + 3 < f - 2) l.pon(x, s + 3, z, z == 5 || z == 11 ? luz : techo);
            }
            // paredes del túnel, y arriba (z = 0..3) la barandilla del hueco en el piso n
            int alto = z <= 3 ? f : s + 3;
            for (int y = s - 2; y <= alto; y++) {
                BlockState b = y == f ? unida(Blocks.IRON_BARS, true, false) : pared;
                l.pon(6, y, z, b);
                l.pon(9, y, z, b);
            }
        }
        // barandilla al final del hueco, en el piso n (para no caerse desde el puente)
        l.pon(7, f, 4, unida(Blocks.IRON_BARS, false, true));
        l.pon(8, f, 4, unida(Blocks.IRON_BARS, false, true));
    }

    // ------------------------------------------------------------------ ascensor
    /**
     * La cabina del ascensor del hotel, en la parcela del noreste: igual en los tres pisos y en el mismo sitio, así que
     * al apretar la botonera se llega a otro piso sin notar el salto (y al salir, otra zona). Madera, moqueta y una luz.
     */
    private static void ascensor(Lienzo l, int f) {
        // se despeja la parcela (menos la orilla del chunk)
        for (int x = 9; x <= 14; x++)
            for (int z = 1; z <= 6; z++)
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, AIRE);
        BlockState pared = d(Blocks.DARK_OAK_PLANKS), marco = d(Blocks.POLISHED_BLACKSTONE);
        for (int x = 10; x <= 14; x++)
            for (int z = 1; z <= 5; z++) {
                boolean borde = x == 10 || x == 14 || z == 1 || z == 5;
                l.pon(x, f - 1, z, borde ? marco : ModBlocks.MOQUETA_HOTEL.get().defaultBlockState());
                l.pon(x, f + 3, z, x == 12 && z == 3 ? d(Blocks.OCHRE_FROGLIGHT) : d(Blocks.SMOOTH_SANDSTONE));
                if (borde) for (int y = 0; y < 3; y++) l.pon(x, f + y, z, pared);
            }
        // la puerta (de cara a la calle) con su marco de metal
        l.pon(12, f, 5, AIRE);
        l.pon(12, f + 1, 5, AIRE);
        for (int y = 0; y < 2; y++) {
            l.pon(11, f + y, 5, marco);
            l.pon(13, f + y, 5, marco);
        }
        l.pon(12, f + 2, 5, marco);
        l.pon(12, f - 1, 6, marco);
        // la botonera por dentro, junto a la puerta
        l.pon(13, f + 1, 4, ModBlocks.BOTONERA.get().defaultBlockState().setValue(BotoneraBlock.FACING, Direction.WEST));
    }

    /** Dónde está la botonera del ascensor de un chunk en el piso n. */
    public static BlockPos botonera(int cx, int cz, int n) {
        return new BlockPos(cx * 16 + 13, PurgatorioPlano.base(n) + 1, cz * 16 + 4);
    }

    // ------------------------------------------------------------------ salas grandes
    /** Lo que va en la pared de una sala grande según la posición a lo largo de la pared (i) y la altura (y). */
    private interface Pared {
        BlockState en(int i, int y);
    }

    /** Las paredes, el suelo y el techo de una sala grande, con un hueco de puerta en cada chunk de la orilla. */
    private static void cascaron(Lienzo l, int f, Grande g, BlockState suelo, BlockState techo, Pared pared, BlockState dintel) {
        int W = g.w() * 16, D = g.d() * 16, alto = g.tipo().alto;
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++) {
                l.pon(x, f - 1, z, suelo);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                l.pon(x, f + alto, z, techo);
                boolean borde = x == 0 || z == 0 || x == W - 1 || z == D - 1;
                if (!borde) continue;
                int i = x == 0 || x == W - 1 ? z : x;
                boolean esquina = (x == 0 || x == W - 1) && (z == 0 || z == D - 1);
                boolean paso = !esquina && ((i & 15) == 7 || (i & 15) == 8);
                for (int y = 0; y < alto; y++) l.pon(x, f + y, z, paso && y < 3 ? AIRE : paso && y == 3 ? dintel : pared.en(i, y));
            }
    }

    private static void granSala(Lienzo l, int f, Grande g) {
        switch (g.tipo()) {
            case PABELLON -> pabellon(l, f, g);
            case VESTIBULO -> vestibulo(l, f, g);
            case BUCLE -> bucle(l, f, g);
            case PISCINA_OLIMPICA -> piscinaOlimpica(l, f, g);
            case GIMNASIO -> gimnasio(l, f, g);
            case ATRIO -> atrio(l, f, g);
            default -> salaEnorme(l, f, g);
        }
    }

    /**
     * El pabellón de la feria (3 x 3 chunks): una nave de lona a rayas con la pista de los coches de choque en medio,
     * los coches parados donde se quedaron, la red de cadenas del techo de la pista, farolillos colgando (alguno
     * fundido), la taquilla, bancos y un tiovivo desmontado en una esquina.
     */
    private static void pabellon(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        cascaron(l, f, g, d(Blocks.SPRUCE_PLANKS), d(Blocks.RED_WOOL),
                (i, y) -> y == 0 ? d(Blocks.WHITE_CONCRETE) : d((i >> 1 & 1) == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL), d(Blocks.WHITE_CONCRETE));
        // techo a rayas
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++)
                l.pon(x, f + 9, z, d((x >> 1 & 1) == 0 ? Blocks.RED_WOOL : Blocks.WHITE_WOOL));
        // la pista: chapa, bordillo de goma amarillo y negro, y la red del techo
        int px0 = 10, px1 = 37, pz0 = 12, pz1 = 35;
        for (int x = px0 - 1; x <= px1 + 1; x++)
            for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                boolean borde = x == px0 - 1 || x == px1 + 1 || z == pz0 - 1 || z == pz1 + 1;
                if (borde) {
                    boolean entrada = (z == pz0 - 1 || z == pz1 + 1) && (x == 23 || x == 24);
                    if (!entrada) l.pon(x, f, z, d(((x + z) & 1) == 0 ? Blocks.YELLOW_CONCRETE : Blocks.BLACK_CONCRETE));
                } else {
                    l.pon(x, f - 1, z, d(Blocks.POLISHED_DEEPSLATE));
                    if ((z & 1) == 0) l.pon(x, f + 7, z, Blocks.CHAIN.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                }
            }
        // los coches de choque, parados donde se quedaron
        Block[] color = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIME_CONCRETE, Blocks.ORANGE_CONCRETE, Blocks.PURPLE_CONCRETE};
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        for (int i = 0; i < 6; i++)
            for (int j = 0; j < 5; j++) {
                long r = h(g.x0() + i, g.z0() + j, 600);
                if (r % 5 >= 2) continue;
                int x = px0 + 2 + i * 4 + (int) (r >> 4 & 1), z = pz0 + 2 + j * 5 + (int) (r >> 5 & 1);
                Direction m = dirs[(int) (r >> 8 & 3)];
                l.pon(x, f, z, d(color[(int) ((r >> 12) % color.length)]));
                int sx = x - m.getStepX(), sz = z - m.getStepZ();
                l.pon(sx, f, sz, escalon(Blocks.DARK_OAK_STAIRS, m.getOpposite()));
                for (int y = 1; y < 7; y++) l.pon(sx, f + y, sz, d(Blocks.CHAIN));
            }
        // farolillos colgando (fuera de la pista, del techo; dentro, de la red)
        for (int x = 4; x < W - 3; x += 4)
            for (int z = 4; z < D - 3; z += 4) {
                if ((x % 8 != 4 || z % 8 != 4) && !(x >= px0 && x <= px1 && z >= pz0 && z <= pz1 && (z & 1) == 0)) continue;
                boolean viva = h(g.x0() + x, g.z0() + z, 601) % 4 != 0;
                boolean pista = x >= px0 && x <= px1 && z >= pz0 && z <= pz1;
                BlockState fa = viva ? Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true) : d(Blocks.CHAIN);
                if (pista) l.pon(x, f + 6, z, fa);
                else {
                    l.pon(x, f + 8, z, d(Blocks.CHAIN));
                    l.pon(x, f + 7, z, fa);
                }
            }
        // la taquilla junto a la entrada, bancos al fondo y el tiovivo en una esquina
        taquilla(l, f, 2, 38);
        for (int x = 12; x <= 35; x++) {
            if ((x & 15) == 7 || (x & 15) == 8 || x % 6 == 0) continue;
            l.pon(x, f, 41, escalon(Blocks.DARK_OAK_STAIRS, Direction.SOUTH));
        }
        tiovivo(l, f, 39, 39);
        for (int z = 14; z <= 33; z += 6) l.pon(4, f, z, d(Blocks.CAULDRON));
    }

    /**
     * La sala de espera enorme (3 x 3 chunks): filas y filas de sillas mirando a un panel de turnos apagado (menos un
     * número), columnas, el techo lleno de fluorescentes y una máquina de coger número junto a la entrada.
     */
    private static void salaEnorme(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        BlockState pared = ModBlocks.PARED_SALA.get().defaultBlockState();
        cascaron(l, f, g, ModBlocks.SUELO_SALA.get().defaultBlockState(), ModBlocks.TECHO_SALA.get().defaultBlockState(),
                (i, y) -> y == 0 ? d(Blocks.LIGHT_GRAY_CONCRETE) : pared, pared);
        BlockState luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++)
                if (x % 4 == 1 && z % 4 == 1 && x > 0 && z > 0 && x < W - 1 && z < D - 1)
                    l.pon(x, f + 7, z, luz.setValue(FluorescenteBlock.ENCENDIDO, h(g.x0() + x, g.z0() + z, 3) % 6 != 0));
        // columnas
        for (int x = 6; x < W; x += 12)
            for (int z = 6; z < D; z += 12)
                for (int y = 0; y < 7; y++) l.pon(x, f + y, z, d(Blocks.QUARTZ_PILLAR));
        // filas de sillas mirando al norte, con pasillos en las calles de cada chunk
        for (int z = 9; z < D - 4; z += 4)
            for (int x = 3; x < W - 3; x++) {
                if ((x & 15) == 7 || (x & 15) == 8 || x % 12 == 6) continue;
                l.pon(x, f, z, escalon(Blocks.QUARTZ_STAIRS, Direction.SOUTH));
            }
        // el panel de turnos en la pared del norte, apagado menos un número
        for (int x = 14; x <= 33; x++)
            for (int y = 3; y <= 5; y++) l.pon(x, f + y, 1, d(Blocks.BLACK_CONCRETE));
        l.pon(24, f + 4, 1, d(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        l.pon(25, f + 4, 1, d(Blocks.REDSTONE_LAMP).setValue(net.minecraft.world.level.block.RedstoneLampBlock.LIT, true));
        // la máquina de coger número, junto a la puerta del sur del medio
        l.pon(21, f, D - 4, d(Blocks.POLISHED_BLACKSTONE));
        l.pon(21, f + 1, D - 4, d(Blocks.RED_CONCRETE));
        for (int[] e : new int[][]{{3, 3}, {W - 4, 3}, {3, D - 4}, {W - 4, D - 4}}) l.pon(e[0], f, e[1], d(Blocks.POTTED_BAMBOO));
    }

    /**
     * El vestíbulo del hotel (3 x 2 chunks): suelo de mármol a cuadros con la alfombra hasta el mostrador de recepción
     * (con su timbre y los casilleros de las llaves detrás), columnas, dos lámparas de araña, sofás, plantas, una
     * escalinata que sube hasta el techo y no lleva a nada, y tres ascensores de puertas de hierro que no abren.
     */
    private static void vestibulo(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        BlockState papel = ModBlocks.PAPEL_HOTEL.get().defaultBlockState();
        cascaron(l, f, g, d(Blocks.QUARTZ_BLOCK), d(Blocks.SMOOTH_SANDSTONE), (i, y) -> y == 0 ? d(Blocks.DARK_OAK_PLANKS) : papel, d(Blocks.DARK_OAK_PLANKS));
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++) {
                if (x == 0 || z == 0 || x == W - 1 || z == D - 1) continue;
                boolean negro = ((x >> 1) + (z >> 1) & 1) == 0;
                boolean alfombra = (x == 23 || x == 24) && z >= 6;
                l.pon(x, f - 1, z, alfombra ? ModBlocks.MOQUETA_HOTEL.get().defaultBlockState() : d(negro ? Blocks.POLISHED_BLACKSTONE : Blocks.QUARTZ_BLOCK));
                if (x % 4 == 2 && z % 4 == 2) l.pon(x, f + 8, z, d(Blocks.OCHRE_FROGLIGHT));
            }
        // columnas, con un aplique a cada lado
        for (int[] c : new int[][]{{12, 10}, {35, 10}, {12, 22}, {35, 22}}) {
            for (int y = 0; y < 8; y++) l.pon(c[0], f + y, c[1], d(Blocks.QUARTZ_PILLAR));
            for (Direction dd : LADOS)
                l.pon(c[0] + dd.getStepX(), f + 3, c[1] + dd.getStepZ(), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, dd));
        }
        // recepción: el mostrador, el timbre, una lámpara y los casilleros de las llaves en la pared
        for (int x = 15; x <= 32; x++) {
            l.pon(x, f, 5, d(Blocks.DARK_OAK_PLANKS));
            l.pon(x, f + 1, 5, d(Blocks.SMOOTH_QUARTZ_SLAB));
        }
        l.ponBE(24, f + 1, 5, d(Blocks.BELL).setValue(net.minecraft.world.level.block.BellBlock.ATTACHMENT, net.minecraft.world.level.block.state.properties.BellAttachType.FLOOR)
                .setValue(net.minecraft.world.level.block.BellBlock.FACING, Direction.SOUTH));
        l.pon(17, f + 1, 5, d(Blocks.LANTERN));
        for (int x = 18; x <= 29; x++)
            for (int y = 1; y <= 3; y++)
                l.pon(x, f + y, 1, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.FACING, Direction.SOUTH)
                        .setValue(TrapDoorBlock.OPEN, true).setValue(TrapDoorBlock.HALF, Half.TOP));
        // la escalinata que no lleva a nada: sube hacia el norte hasta tocar el techo, con barandilla
        for (int i = 0; i < 7; i++) {
            int z = 25 - i;
            for (int x = 30; x <= 35; x++) {
                for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.QUARTZ_BLOCK));
                l.pon(x, f + i, z, escalon(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            }
            for (int x : new int[]{29, 36}) {
                for (int y = 0; y <= i; y++) l.pon(x, f + y, z, d(Blocks.QUARTZ_BLOCK));
                l.pon(x, f + i + 1, z, unida(Blocks.DARK_OAK_FENCE, true, false));
            }
        }
        // los ascensores de la pared del oeste: puertas de hierro (no abren) con su marco y su piloto apagado
        for (int z = 10; z <= 20; z++)
            for (int y = 0; y < 5; y++) {
                l.pon(1, f + y, z, papel);
                l.pon(2, f + y, z, y == 0 ? d(Blocks.DARK_OAK_PLANKS) : papel);
            }
        for (int z : new int[]{12, 15, 18}) {
            for (int y = 0; y < 3; y++) {
                l.pon(2, f + y, z - 1, d(Blocks.POLISHED_BLACKSTONE));
                l.pon(2, f + y, z + 1, d(Blocks.POLISHED_BLACKSTONE));
            }
            BlockState pu = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
            l.pon(2, f, z, pu);
            l.pon(2, f + 1, z, pu.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            l.pon(2, f + 2, z, d(Blocks.POLISHED_BLACKSTONE));
            l.pon(2, f + 3, z, d(Blocks.REDSTONE_LAMP));
        }
        // sofás junto a las columnas y plantas
        for (int[] c : new int[][]{{12, 10}, {35, 10}, {12, 22}}) {
            for (int x = c[0] - 1; x <= c[0] + 1; x++) l.pon(x, f, c[1] + 2, escalon(Blocks.CRIMSON_STAIRS, Direction.NORTH));
            l.pon(c[0] - 2, f, c[1] + 2, d(Blocks.POTTED_FERN));
        }
        // dos lámparas de araña
        for (int[] c : new int[][]{{18, 16}, {40, 16}}) {
            l.pon(c[0], f + 7, c[1], d(Blocks.CHAIN));
            l.pon(c[0], f + 6, c[1], d(Blocks.CHAIN));
            BlockState fa = Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true);
            l.pon(c[0], f + 5, c[1], d(Blocks.SHROOMLIGHT));
            l.pon(c[0], f + 4, c[1], fa);
            for (Direction dd : LADOS) {
                l.pon(c[0] + dd.getStepX(), f + 5, c[1] + dd.getStepZ(), unida(Blocks.IRON_BARS, dd.getAxis() == Direction.Axis.Z, dd.getAxis() == Direction.Axis.X));
                l.pon(c[0] + dd.getStepX(), f + 4, c[1] + dd.getStepZ(), fa);
            }
        }
    }

    /**
     * El pasillo que vuelve al mismo sitio (1 x 3 chunks): se entra por el norte y el pasillo sigue y sigue con las
     * mismas puertas y las mismas luces cada 4 bloques; a los 28 bloques te devuelve 20 atrás sin que se note (ver
     * {@link PurgatorioPlano#bucle}), así que nunca se llega al final. Las puertas de los lados dan a la pared. Con la
     * pinta de la zona que le toque: hotel o sala de espera (y colegio).
     */
    private static void bucle(Lienzo l, int f, Grande g) {
        int D = g.d() * 16;
        BlockState suelo, techo, pared, zocalo, luz;
        Block puerta;
        boolean colegio = g.zona() == Zona.COLEGIO;
        if (colegio) {
            suelo = ModBlocks.SUELO_COLEGIO.get().defaultBlockState();
            techo = ModBlocks.TECHO_SALA.get().defaultBlockState();
            pared = ModBlocks.PARED_COLEGIO.get().defaultBlockState();
            zocalo = ModBlocks.PARED_COLEGIO_ZOCALO.get().defaultBlockState();
            luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
            puerta = Blocks.OAK_DOOR;
        } else if (g.zona() == Zona.HOTEL) {
            suelo = ModBlocks.MOQUETA_HOTEL.get().defaultBlockState();
            techo = d(Blocks.SMOOTH_SANDSTONE);
            pared = ModBlocks.PAPEL_HOTEL.get().defaultBlockState();
            zocalo = d(Blocks.DARK_OAK_PLANKS);
            luz = d(Blocks.OCHRE_FROGLIGHT);
            puerta = Blocks.DARK_OAK_DOOR;
        } else {
            suelo = ModBlocks.SUELO_SALA.get().defaultBlockState();
            techo = ModBlocks.TECHO_SALA.get().defaultBlockState();
            pared = ModBlocks.PARED_SALA.get().defaultBlockState();
            zocalo = d(Blocks.LIGHT_GRAY_CONCRETE);
            luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
            puerta = Blocks.BIRCH_DOOR;
        }
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++) {
                l.pon(x, f - 1, z, x >= 7 && x <= 8 ? suelo : d(Blocks.STONE));
                l.pon(x, f - 2, z, d(Blocks.STONE));
                l.pon(x, f + 4, z, x >= 7 && x <= 8 && z % 4 == 3 ? luz : techo);
                // lo de fuera del pasillo, macizo (no se ve: así ni se dibuja)
                if (x < 6 || x > 9) for (int y = 0; y < 4; y++) l.pon(x, f + y, z, pared);
            }
        for (int z = l.oz; z < l.oz + 16; z++) {
            for (int y = 0; y < 4; y++) {
                l.pon(6, f + y, z, y == 0 ? zocalo : pared);
                l.pon(9, f + y, z, y == 0 ? zocalo : pared);
            }
            if (colegio && z > 0 && z < D - 1 && z % 4 != 1) {
                // en el colegio, taquillas a los dos lados entre puerta y puerta
                taquillas(l, f, 6, z, Direction.EAST);
                taquillas(l, f, 9, z, Direction.WEST);
            }
            if (z % 4 == 1 && z < D - 1) {
                // una puerta a cada lado, que al abrirla da a la pared
                BlockState pe = puerta.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
                BlockState po = puerta.defaultBlockState().setValue(DoorBlock.FACING, Direction.WEST);
                l.pon(6, f, z, pe);
                l.pon(6, f + 1, z, pe.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                l.pon(9, f, z, po);
                l.pon(9, f + 1, z, po.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                l.pon(5, f, z, d(Blocks.BRICKS));
                l.pon(5, f + 1, z, d(Blocks.BRICKS));
                l.pon(10, f, z, d(Blocks.BRICKS));
                l.pon(10, f + 1, z, d(Blocks.BRICKS));
            }
        }
        // la entrada (al norte) y el fondo (al sur, al que nunca se llega)
        for (int x = 6; x <= 9; x++)
            for (int y = 0; y < 4; y++) {
                boolean paso = (x == 7 || x == 8) && y < 3;
                l.pon(x, f + y, 0, paso ? AIRE : y == 3 && (x == 7 || x == 8) ? zocalo : pared);
                l.pon(x, f + y, D - 1, pared);
            }
    }

    // ------------------------------------------------------------------ Piscina vacía
    /** La piscina a oscuras con un solo foco encendido y un flotador en el fondo seco (una de cada 17). */
    public static boolean piscinaRara(int n, int cx, int cz) {
        return hn(n, cx, cz, 142) % 17 == 0 && !PurgatorioPlano.salida(n, cx, cz);
    }

    /**
     * Piscinas cubiertas, de azulejo, sin agua (o con un dedo de agua en el fondo), con su escalerilla, gradas, la silla
     * del socorrista y focos en el techo. Suenan las gotas con eco.
     */
    private static void piscina(Lienzo l, int n, int f, int cx, int cz) {
        boolean rara = zona(n, cx, cz) == Zona.PISCINA && raro(n, cx, cz) == 4;
        BlockState blanco = ModBlocks.AZULEJO_PISCINA.get().defaultBlockState(), azul = ModBlocks.AZULEJO_PISCINA_AZUL.get().defaultBlockState();
        BlockState foco = ModBlocks.FOCO.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, blanco);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean luz = x % 5 == 2 && z % 5 == 2;
                boolean encendido = rara ? x == 12 && z == 12 : hn(n, cx * 16 + x, cz * 16 + z, 3) % 5 != 0;
                l.pon(x, f + 6, z, luz ? (encendido ? foco : d(Blocks.GRAY_CONCRETE)) : d(Blocks.SMOOTH_QUARTZ));
            }
        for (int k = 0; k < 4; k++) {
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            if (ocupada(n, cx, cz, k)) continue;
            if (rara) {
                if (k == 3) pileta(l, f, qx, qz, false, true);
                continue;
            }
            int t = (int) (hn(n, cx, cz, 190 + k) % 10);
            if (t < 6) pileta(l, f, qx, qz, hn(n, cx, cz, 195 + k) % 3 == 0, false);
            else if (t < 8) gradas(l, f, qx, qz, qz == 0);
            else if (t == 8) socorrista(l, f, qx, qz);
            else {
                // un banco del vestuario y una papelera
                for (int x = qx + 2; x <= qx + 4; x++) l.pon(x, f, qz + 3, d(Blocks.BIRCH_SLAB));
                l.pon(qx + 5, f, qz + 3, d(Blocks.CAULDRON));
            }
        }
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.PISCINA && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            tabique(l, f, dd, 6, azul, blanco, 3, blanco);
        }
    }

    /** Una piscina de 5 x 5 en la parcela: dos de hondo, el fondo azul con su raya, el borde y la escalerilla. */
    private static void pileta(Lienzo l, int f, int qx, int qz, boolean agua, boolean flotador) {
        BlockState azul = ModBlocks.AZULEJO_PISCINA_AZUL.get().defaultBlockState(), raya = ModBlocks.AZULEJO_PISCINA_RAYA.get().defaultBlockState();
        BlockState borde = ModBlocks.BORDE_PISCINA.get().defaultBlockState();
        for (int x = qx; x <= qx + 6; x++)
            for (int z = qz; z <= qz + 6; z++) {
                boolean dentro = x > qx && x < qx + 6 && z > qz && z < qz + 6;
                if (dentro) {
                    l.pon(x, f - 1, z, AIRE);
                    l.pon(x, f - 2, z, agua ? ModBlocks.AGUA_POCA.get().defaultBlockState() : AIRE);
                    l.pon(x, f - 3, z, z == qz + 3 ? raya : azul);
                } else {
                    l.pon(x, f - 1, z, borde);
                    l.pon(x, f - 2, z, azul);
                }
            }
        BlockState esc = Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.SOUTH);
        l.pon(qx + 3, f - 2, qz + 1, esc);
        l.pon(qx + 3, f - 1, qz + 1, esc);
        if (flotador) {
            // el flotador en el fondo seco: un anillo rojo y blanco
            int i = 0;
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    if (x != 0 || z != 0) l.pon(qx + 3 + x, f - 2, qz + 3 + z, d(i++ % 2 == 0 ? Blocks.RED_CARPET : Blocks.WHITE_CARPET));
        }
    }

    /** Gradas de tres filas, de espaldas a la calle. */
    private static void gradas(Lienzo l, int f, int qx, int qz, boolean norte) {
        for (int i = 0; i < 3; i++) {
            int z = norte ? qz + 5 - i : qz + 1 + i;
            for (int x = qx + 1; x <= qx + 5; x++) {
                for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.SMOOTH_QUARTZ));
                l.pon(x, f + i, z, escalon(Blocks.QUARTZ_STAIRS, norte ? Direction.NORTH : Direction.SOUTH));
            }
        }
    }

    /** La silla alta del socorrista, vacía, con el flotador colgado. */
    private static void socorrista(Lienzo l, int f, int qx, int qz) {
        int x = qx + 3, z = qz + 3;
        l.pon(x, f, z, d(Blocks.BIRCH_FENCE));
        l.pon(x, f + 1, z, d(Blocks.BIRCH_FENCE));
        l.pon(x, f + 2, z, escalon(Blocks.BIRCH_STAIRS, qz == 0 ? Direction.NORTH : Direction.SOUTH));
        l.pon(x, f + 3, z + (qz == 0 ? -1 : 1), d(Blocks.WHITE_WOOL));
        l.pon(x + 1, f, z, d(Blocks.RED_CARPET));
        l.pon(x + 1, f, z + 1, d(Blocks.WHITE_CARPET));
    }

    // ------------------------------------------------------------------ Aparcamiento subterráneo
    /** La plaza del fondo con el coche de los faros encendidos, y el resto a oscuras (una de cada 19). */
    public static boolean garajeRaro(int n, int cx, int cz) {
        return hn(n, cx, cz, 143) % 19 == 0 && !PurgatorioPlano.salida(n, cx, cz);
    }

    /**
     * Plantas de garaje que no se acaban: hormigón con manchas de aceite, plazas con su raya, flechas, columnas con su
     * letra y su número pintados y la franja amarilla y negra abajo, fluorescentes que zumban (alguno parpadeando) y
     * coches aparcados sin nadie, hechos con bloques. Hay muchas rampas a la planta de abajo.
     */
    private static void aparcamiento(Lienzo l, int n, int f, int cx, int cz) {
        boolean rara = zona(n, cx, cz) == Zona.APARCAMIENTO && raro(n, cx, cz) == 4;
        BlockState suelo = ModBlocks.SUELO_APARCAMIENTO.get().defaultBlockState(), raya = ModBlocks.SUELO_APARCAMIENTO_RAYA.get().defaultBlockState();
        BlockState techo = ModBlocks.TECHO_APARCAMIENTO.get().defaultBlockState(), luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean plaza = (x == 1 || x == 4 || x == 11 || x == 14) && (z >= 1 && z <= 4 || z >= 11 && z <= 14);
                l.pon(x, f - 1, z, plaza ? raya : suelo);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean tubo = (x == 7 && z % 4 == 2) || (z == 7 && x % 4 == 2);
                l.pon(x, f + 4, z, tubo ? luz.setValue(FluorescenteBlock.ENCENDIDO, !rara && hn(n, cx * 16 + x, cz * 16 + z, 3) % 4 != 0) : techo);
            }
        l.pon(7, f - 1, 3, ModBlocks.SUELO_APARCAMIENTO_FLECHA.get().defaultBlockState());
        l.pon(8, f - 1, 12, ModBlocks.SUELO_APARCAMIENTO_FLECHA.get().defaultBlockState());
        // columnas con su letra y su número (cada una distinta)
        var col = (NumeradoBlock) ModBlocks.COLUMNA_APARCAMIENTO.get();
        int[][] cols = {{4, 0}, {11, 0}, {4, 15}, {11, 15}};
        for (int i = 0; i < 4; i++) {
            int x = cols[i][0], z = cols[i][1];
            l.pon(x, f, z, ModBlocks.COLUMNA_APARCAMIENTO_BASE.get().defaultBlockState());
            l.pon(x, f + 1, z, col.con(cx * 2 + (x > 8 ? 1 : 0) + cz * 3 + (z > 8 ? 1 : 0)));
            l.pon(x, f + 2, z, col.con(cx * 2 + (x > 8 ? 1 : 0) + cz * 3 + (z > 8 ? 1 : 0)));
            l.pon(x, f + 3, z, ModBlocks.PARED_APARCAMIENTO.get().defaultBlockState());
        }
        // coches aparcados: en las cuatro plazas, más o menos la mitad
        Block[] color = {Blocks.RED_CONCRETE, Blocks.BLUE_CONCRETE, Blocks.WHITE_CONCRETE, Blocks.BLACK_CONCRETE, Blocks.GRAY_CONCRETE, Blocks.YELLOW_CONCRETE, Blocks.LIGHT_BLUE_CONCRETE};
        for (int k = 0; k < 4; k++) {
            if (ocupada(n, cx, cz, k)) continue;
            int x0 = k % 2 == 0 ? 2 : 12, z0 = k < 2 ? 1 : 11;
            long r = hn(n, cx, cz, 200 + k);
            if (rara ? k == 3 : r % 10 < 6) coche(l, f, x0, z0, k >= 2, color[(int) ((r >> 8) % color.length)], rara && k == 3);
        }
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.APARCAMIENTO && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            BlockState p = ModBlocks.PARED_APARCAMIENTO.get().defaultBlockState(), fr = ModBlocks.PARED_APARCAMIENTO_FRANJA.get().defaultBlockState();
            for (int i = 0; i < 16; i++) {
                boolean paso = i == 7 || i == 8;
                int x = dd == Direction.WEST ? 0 : dd == Direction.EAST ? 15 : i;
                int z = dd == Direction.NORTH ? 0 : dd == Direction.SOUTH ? 15 : i;
                for (int y = paso ? 3 : 0; y < 4; y++) l.pon(x, f + y, z, y == 1 ? fr : p);
            }
        }
    }

    /**
     * Un coche aparcado de 2 x 4: la carrocería de un color, las ventanillas oscuras arriba (el capó y el maletero, más
     * bajos), los faros y los pilotos (botones) y las ruedas (botones negros a los lados). Mira hacia la calle.
     */
    private static void coche(Lienzo l, int f, int x0, int z0, boolean haciaNorte, Block color, boolean faros) {
        Direction delante = haciaNorte ? Direction.NORTH : Direction.SOUTH;
        int zf = haciaNorte ? z0 : z0 + 3, zt = haciaNorte ? z0 + 3 : z0;
        for (int x = x0; x <= x0 + 1; x++) {
            for (int z = z0; z <= z0 + 3; z++) l.pon(x, f, z, d(color));
            l.pon(x, f + 1, z0 + 1, d(Blocks.TINTED_GLASS));
            l.pon(x, f + 1, z0 + 2, d(Blocks.TINTED_GLASS));
            if (faros) l.pon(x, f, zf + 2 * delante.getStepZ(), Blocks.LIGHT.defaultBlockState().setValue(net.minecraft.world.level.block.LightBlock.LEVEL, 15));
            l.pon(x, f, zf + delante.getStepZ(), faros ? Blocks.END_ROD.defaultBlockState().setValue(net.minecraft.world.level.block.EndRodBlock.FACING, delante)
                    : Blocks.BIRCH_BUTTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, delante));
            l.pon(x, f, zt - delante.getStepZ(), Blocks.CRIMSON_BUTTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL)
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, delante.getOpposite()));
        }
        for (int z : new int[]{z0, z0 + 3}) {
            BlockState rueda = Blocks.POLISHED_BLACKSTONE_BUTTON.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL);
            l.pon(x0 - 1, f, z, rueda.setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.WEST));
            l.pon(x0 + 2, f, z, rueda.setValue(net.minecraft.world.level.block.ButtonBlock.FACING, Direction.EAST));
        }
    }

    // ------------------------------------------------------------------ Colegio de noche
    /** El aula con las sillas subidas en los pupitres menos una, de cara a la pizarra y con la luz encendida encima (una de cada 13). */
    public static boolean aulaRara(int n, int cx, int cz) {
        return hn(n, cx, cz, 144) % 13 == 0 && hn(n, cx, cz, 210) % 10 < 8;
    }

    /**
     * Un colegio de noche: pasillos de terrazo con taquillas a los dos lados, aulas con los pupitres y sus sillas, la
     * pizarra borrada y el reloj parado en las cuatro y diez. Casi todas las luces apagadas.
     */
    private static void colegio(Lienzo l, int n, int f, int cx, int cz) {
        BlockState terrazo = ModBlocks.SUELO_COLEGIO.get().defaultBlockState(), placa = ModBlocks.TECHO_SALA.get().defaultBlockState();
        BlockState luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean pasillo = x == 7 || x == 8 || z == 7 || z == 8;
                l.pon(x, f - 1, z, pasillo ? terrazo : d(Blocks.OAK_PLANKS));
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean tubo = pasillo && (x == 7 || z == 7) && (x + z) % 5 == 2;
                l.pon(x, f + 4, z, tubo ? luz.setValue(FluorescenteBlock.ENCENDIDO, hn(n, cx * 16 + x, cz * 16 + z, 3) % 3 == 0) : placa);
            }
        boolean rara = zona(n, cx, cz) == Zona.COLEGIO && raro(n, cx, cz) == 0;
        for (int k = 0; k < 4; k++) {
            if (ocupada(n, cx, cz, k)) continue;
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            if (hn(n, cx, cz, 210 + k) % 10 < 8) aula(l, n, f, cx, cz, k, qx, qz, rara && k == 0);
            else {
                // un rincón del pasillo: taquillas en fila y un banco
                for (int x = qx + 1; x <= qx + 5; x++) taquillas(l, f, x, qz == 0 ? qz + 1 : qz + 5, qz == 0 ? Direction.SOUTH : Direction.NORTH);
                for (int x = qx + 2; x <= qx + 4; x++) l.pon(x, f, qz == 0 ? qz + 4 : qz + 2, d(Blocks.OAK_SLAB));
            }
        }
        BlockState zoc = ModBlocks.PARED_COLEGIO_ZOCALO.get().defaultBlockState(), pared = ModBlocks.PARED_COLEGIO.get().defaultBlockState();
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.COLEGIO && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            tabique(l, f, dd, 4, zoc, pared, 3, pared);
        }
    }

    private static void taquillas(Lienzo l, int f, int x, int z, Direction mira) {
        BlockState t = ModBlocks.TAQUILLA.get().defaultBlockState().setValue(TaquillaBlock.FACING, mira);
        l.pon(x, f, z, t);
        l.pon(x, f + 1, z, t.setValue(MitadBlock.ARRIBA, true));
    }

    /** Un aula en la parcela: la pared que da a cada pasillo hecha de taquillas, la puerta, la pizarra, el reloj y los pupitres. */
    private static void aula(Lienzo l, int n, int f, int cx, int cz, int k, int x0, int z0, boolean rara) {
        int x1 = x0 + 6, z1 = z0 + 6;
        BlockState zoc = ModBlocks.PARED_COLEGIO_ZOCALO.get().defaultBlockState(), pared = ModBlocks.PARED_COLEGIO.get().defaultBlockState();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                if (x != x0 && x != x1 && z != z0 && z != z1) continue;
                for (int y = 0; y < 4; y++) l.pon(x, f + y, z, y == 0 ? zoc : pared);
            }
        boolean norte = z0 == 0, oeste = x0 == 0;
        int zp = norte ? z1 : z0, xq = oeste ? x1 : x0, zb = norte ? z0 : z1, dz = norte ? 1 : -1;
        Direction aPasillo = norte ? Direction.SOUTH : Direction.NORTH, aPasillo2 = oeste ? Direction.EAST : Direction.WEST;
        // las taquillas: la pared que da a los pasillos
        for (int x = x0 + 1; x < x1; x++) if (x != x0 + 3) taquillas(l, f, x, zp, aPasillo);
        for (int z = z0 + 1; z < z1; z++) taquillas(l, f, xq, z, aPasillo2);
        BlockState pu = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, aPasillo.getOpposite());
        l.pon(x0 + 3, f, zp, pu);
        l.pon(x0 + 3, f + 1, zp, pu.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // la pizarra borrada en la pared del fondo y el reloj parado encima
        var piz = ModBlocks.PIZARRA.get().defaultBlockState();
        for (int x = x0 + 2; x <= x0 + 4; x++) {
            l.pon(x, f + 1, zb, piz);
            l.pon(x, f + 2, zb, piz.setValue(MitadBlock.ARRIBA, true));
        }
        l.pon(x0 + 3, f + 3, zb + dz, ModBlocks.RELOJ_PARADO.get().defaultBlockState().setValue(RelojParadoBlock.FACING, norte ? Direction.SOUTH : Direction.NORTH));
        // pupitres (tablero sobre el suelo) con su silla detrás, mirando a la pizarra; pasillo en medio, hasta la puerta
        BlockState mesa = Blocks.SPRUCE_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP);
        Direction respaldo = norte ? Direction.SOUTH : Direction.NORTH;
        for (int fila = 0; fila < 2; fila++) {
            int zm = zb + dz * (2 + fila * 2), zs = zm + dz;
            for (int x : new int[]{x0 + 1, x0 + 2, x0 + 4, x0 + 5}) {
                l.pon(x, f, zm, mesa);
                boolean bajada = !rara || (fila == 1 && x == x0 + 2);
                if (bajada) l.pon(x, f, zs, escalon(Blocks.OAK_STAIRS, respaldo));
                else l.pon(x, f + 1, zm, escalon(Blocks.OAK_STAIRS, respaldo).setValue(StairBlock.HALF, Half.TOP));
            }
        }
        // la luz del aula: apagada (o, en la rara, encendida justo encima de la silla que queda)
        l.pon(rara ? x0 + 2 : x0 + 3, f + 4, zb + dz * 3, ModBlocks.FLUORESCENTE.get().defaultBlockState().setValue(FluorescenteBlock.ENCENDIDO, rara));
    }

    // ------------------------------------------------------------------ Centro comercial cerrado
    /** La tienda de teles con la persiana a medio subir y todas las teles encendidas sin señal (una de cada 15). */
    public static boolean tiendaRara(int n, int cx, int cz) {
        return hn(n, cx, cz, 145) % 15 == 0 && hn(n, cx, cz, 220) % 10 < 8;
    }

    /**
     * Un centro comercial cerrado de noche: suelo brillante, tiendas con la persiana echada y el rótulo apagado, alguna
     * escalera mecánica parada que sube hasta el techo, bancos y macetas, y el hilo musical sonando para nadie.
     */
    private static void centro(Lienzo l, int n, int f, int cx, int cz) {
        BlockState suelo = ModBlocks.SUELO_CENTRO.get().defaultBlockState(), luz = ModBlocks.FLUORESCENTE.get().defaultBlockState();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                l.pon(x, f - 1, z, suelo);
                l.pon(x, f - 2, z, d(Blocks.STONE));
                boolean tubo = (z == 7 || z == 8) && x % 4 == 1 || (x == 7 || x == 8) && z % 4 == 1;
                l.pon(x, f + 6, z, tubo ? luz.setValue(FluorescenteBlock.ENCENDIDO, hn(n, cx * 16 + x, cz * 16 + z, 3) % 2 == 0) : d(Blocks.SMOOTH_QUARTZ));
            }
        boolean rara = zona(n, cx, cz) == Zona.CENTRO && raro(n, cx, cz) == 0;
        for (int k = 0; k < 4; k++) {
            if (ocupada(n, cx, cz, k)) continue;
            int qx = PurgatorioPlano.PARCELAS[k][0], qz = PurgatorioPlano.PARCELAS[k][1];
            long t = hn(n, cx, cz, 220 + k) % 10;
            if (t < 8) tienda(l, n, f, cx, cz, k, qx, qz == 0, rara && k == 0);
            else if (t == 8) escaleraMecanicaANada(l, f, qx, qz == 0);
            else {
                int z = qz == 0 ? 4 : 11;
                for (int x = qx + 2; x <= qx + 4; x++) l.pon(x, f, z, escalon(Blocks.DARK_OAK_STAIRS, qz == 0 ? Direction.NORTH : Direction.SOUTH));
                l.pon(qx + 1, f, z, d(Blocks.POTTED_FERN));
                l.pon(qx + 5, f, z, d(Blocks.POTTED_AZALEA));
            }
        }
        for (Direction dd : LADOS) {
            int nx = cx + dd.getStepX(), nz = cz + dd.getStepZ();
            if (zona(n, nx, nz) == Zona.CENTRO && PurgatorioPlano.grande(n, nx, nz) == null) continue;
            tabique(l, f, dd, 6, d(Blocks.LIGHT_GRAY_CONCRETE), d(Blocks.WHITE_CONCRETE), 3, d(Blocks.WHITE_CONCRETE));
        }
    }

    /** Una tienda de 7 x 6 de cara a la calle de en medio: la persiana echada, el rótulo apagado encima y nada dentro. */
    private static void tienda(Lienzo l, int n, int f, int cx, int cz, int k, int x0, boolean norte, boolean rara) {
        int zf = norte ? 5 : 10, zb = norte ? 0 : 15, dz = norte ? 1 : -1;
        BlockState pared = d(Blocks.WHITE_CONCRETE), persiana = ModBlocks.PERSIANA.get().defaultBlockState();
        for (int z = Math.min(zb, zf); z <= Math.max(zb, zf); z++)
            for (int y = 0; y < 6; y++) {
                l.pon(x0, f + y, z, pared);
                l.pon(x0 + 6, f + y, z, pared);
            }
        for (int x = x0; x <= x0 + 6; x++)
            for (int y = 0; y < 6; y++) l.pon(x, f + y, zb, pared);
        int rotulo = (int) (hn(n, cx, cz, 230 + k) % 6);
        for (int x = x0 + 1; x <= x0 + 5; x++) {
            for (int y = 0; y < 3; y++) l.pon(x, f + y, zf, rara && y < 2 ? AIRE : persiana);
            l.pon(x, f + 4, zf, pared);
            l.pon(x, f + 5, zf, pared);
        }
        for (int x = x0; x <= x0 + 6; x++) l.pon(x, f + 3, zf, ((NumeradoBlock) ModBlocks.ROTULO_TIENDA.get()).con(rara ? 1 : rotulo));
        if (rara) {
            // la tienda de teles: dos filas de teles encendidas sin señal en el fondo, mirando a la persiana, y la luz
            for (int x = x0 + 1; x <= x0 + 5; x++) {
                l.pon(x, f, zb + dz, d(Blocks.SPRUCE_PLANKS));
                for (int y = 1; y <= 2; y++)
                    l.pon(x, f + y, zb + dz, ModBlocks.TELE.get().defaultBlockState().setValue(TeleBlock.FACING, norte ? Direction.SOUTH : Direction.NORTH));
            }
            l.pon(x0 + 3, f + 5, zb + 3 * dz, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
    }

    /** Una escalera mecánica parada que sube hasta el techo y no lleva a ningún sitio, con sus barandillas de cristal. */
    private static void escaleraMecanicaANada(Lienzo l, int f, int qx, boolean norte) {
        BlockState esc = ModBlocks.ESCALERA_MECANICA.get().defaultBlockState().setValue(StairBlock.FACING, norte ? Direction.NORTH : Direction.SOUTH);
        for (int i = 0; i < 6; i++) {
            int z = norte ? 5 - i : 10 + i;
            for (int x = qx + 2; x <= qx + 3; x++) {
                for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.BLACK_CONCRETE));
                l.pon(x, f + i, z, esc);
            }
            for (int x : new int[]{qx + 1, qx + 4}) {
                for (int y = 0; y <= i; y++) l.pon(x, f + y, z, d(Blocks.LIGHT_GRAY_CONCRETE));
                if (i < 5) l.pon(x, f + i + 1, z, d(Blocks.BLACK_CONCRETE));
            }
        }
    }

    // ------------------------------------------------------------------ salas grandes de las zonas nuevas
    /**
     * La piscina olímpica (3 x 2 chunks): un vaso enorme de tres de hondo con sus calles, el dedo de agua en la parte
     * honda, las escalerillas, los poyetes de salida, el trampolín, las gradas y los focos.
     */
    private static void piscinaOlimpica(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        BlockState blanco = ModBlocks.AZULEJO_PISCINA.get().defaultBlockState(), azul = ModBlocks.AZULEJO_PISCINA_AZUL.get().defaultBlockState();
        BlockState raya = ModBlocks.AZULEJO_PISCINA_RAYA.get().defaultBlockState(), borde = ModBlocks.BORDE_PISCINA.get().defaultBlockState();
        cascaron(l, f, g, blanco, d(Blocks.SMOOTH_QUARTZ), (i, y) -> y == 0 ? azul : blanco, blanco);
        BlockState foco = ModBlocks.FOCO.get().defaultBlockState();
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++)
                if (x % 4 == 2 && z % 4 == 2 && h(g.x0() + x, g.z0() + z, 3) % 5 != 0) l.pon(x, f + 8, z, foco);
        int px0 = 6, px1 = 41, pz0 = 7, pz1 = 26;
        for (int x = px0 - 1; x <= px1 + 1; x++)
            for (int z = pz0 - 1; z <= pz1 + 1; z++) {
                boolean dentro = x >= px0 && x <= px1 && z >= pz0 && z <= pz1;
                if (dentro) {
                    for (int y = 1; y <= 3; y++) l.pon(x, f - y, z, AIRE);
                    l.pon(x, f - 4, z, (z - pz0) % 4 == 2 && x > px0 + 1 && x < px1 - 1 ? raya : azul);
                    if (x > px1 - 12 && h(g.x0() + x, g.z0() + z, 610) % 7 != 0) l.pon(x, f - 3, z, ModBlocks.AGUA_POCA.get().defaultBlockState());
                } else {
                    l.pon(x, f - 1, z, borde);
                    l.pon(x, f - 2, z, azul);
                    l.pon(x, f - 3, z, azul);
                }
            }
        // escalerillas en las cuatro esquinas
        for (int[] e : new int[][]{{px0 + 1, pz0, 0}, {px1 - 1, pz0, 0}, {px0 + 1, pz1, 1}, {px1 - 1, pz1, 1}})
            for (int y = 1; y <= 3; y++)
                l.pon(e[0], f - y, e[1], Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, e[2] == 0 ? Direction.SOUTH : Direction.NORTH));
        // poyetes de salida al oeste, uno por calle
        for (int z = pz0 + 2; z <= pz1 - 1; z += 4) {
            l.pon(px0 - 1, f, z, d(Blocks.SMOOTH_QUARTZ));
            l.pon(px0 - 1, f + 1, z, d(Blocks.SMOOTH_QUARTZ_SLAB));
        }
        // el trampolín al este: la escalera, la tabla que sale sobre el vaso
        int tz = (pz0 + pz1) / 2;
        for (int y = 0; y < 3; y++) {
            l.pon(px1 + 3, f + y, tz, d(Blocks.WHITE_CONCRETE));
            l.pon(px1 + 4, f + y, tz, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
        }
        for (int x = px1 - 2; x <= px1 + 3; x++) l.pon(x, f + 3, tz, d(Blocks.BIRCH_SLAB));
        // gradas al norte, con pasillos frente a las puertas
        for (int i = 0; i < 3; i++) {
            int z = 5 - i;
            for (int x = 3; x < W - 3; x++) {
                if ((x & 15) >= 6 && (x & 15) <= 9) continue;
                for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.SMOOTH_QUARTZ));
                l.pon(x, f + i, z, escalon(Blocks.QUARTZ_STAIRS, Direction.NORTH));
            }
        }
        // focos en las paredes largas
        for (int x = 4; x < W - 3; x += 8) {
            l.pon(x, f + 6, 1, foco);
            l.pon(x, f + 6, D - 2, foco);
        }
    }

    /**
     * El gimnasio (2 x 2 chunks): suelo de madera con las rayas de la pista, dos canastas, espalderas en la pared,
     * colchonetas, el potro y unas gradas; los fluorescentes del techo, casi todos apagados.
     */
    private static void gimnasio(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        cascaron(l, f, g, d(Blocks.BIRCH_PLANKS), ModBlocks.TECHO_SALA.get().defaultBlockState(),
                (i, y) -> y == 0 ? ModBlocks.PARED_COLEGIO_ZOCALO.get().defaultBlockState() : ModBlocks.PARED_COLEGIO.get().defaultBlockState(),
                ModBlocks.PARED_COLEGIO.get().defaultBlockState());
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++) {
                if (x <= 0 || z <= 0 || x >= W - 1 || z >= D - 1) continue;
                boolean linea = x == 4 || x == W - 5 || z == 4 || z == D - 5 || z == D / 2;
                double r = Math.sqrt((x - W / 2.0 + 0.5) * (x - W / 2.0 + 0.5) + (z - D / 2.0 + 0.5) * (z - D / 2.0 + 0.5));
                if (r > 3.5 && r < 4.5) linea = true;
                if (linea && x >= 4 && x <= W - 5 && z >= 4 && z <= D - 5) l.pon(x, f - 1, z, d(Blocks.WHITE_CONCRETE));
                if (x % 5 == 2 && z % 5 == 2)
                    l.pon(x, f + 8, z, ModBlocks.FLUORESCENTE.get().defaultBlockState().setValue(FluorescenteBlock.ENCENDIDO, h(g.x0() + x, g.z0() + z, 3) % 3 != 0));
            }
        // las dos canastas (tablero blanco, aro de hierro) en las paredes del norte y del sur
        for (int[] c : new int[][]{{1, 2, 1}, {D - 2, D - 3, -1}}) {
            for (int x = W / 2 - 2; x <= W / 2 + 1; x++)
                for (int y = 4; y <= 6; y++) l.pon(x, f + y, c[0], d(Blocks.WHITE_CONCRETE));
            for (int x = W / 2 - 1; x <= W / 2; x++)
                l.pon(x, f + 4, c[1], Blocks.IRON_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, Half.TOP));
        }
        // espalderas en la pared del este
        for (int z = 10; z <= 21; z++) {
            if ((z & 15) == 7 || (z & 15) == 8) continue;
            for (int y = 0; y < 5; y++)
                l.pon(W - 2, f + y, z, Blocks.LADDER.defaultBlockState().setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.WEST));
        }
        // colchonetas azules y el potro
        for (int x = 6; x <= 9; x++)
            for (int z = 20; z <= 22; z++) l.pon(x, f, z, d(Blocks.BLUE_CARPET));
        l.pon(10, f, 12, d(Blocks.DARK_OAK_FENCE));
        l.pon(12, f, 12, d(Blocks.DARK_OAK_FENCE));
        for (int x = 10; x <= 12; x++) l.pon(x, f + 1, 12, d(Blocks.BROWN_WOOL));
        // gradas en el oeste
        for (int i = 0; i < 3; i++) {
            int x = 2 + i;
            for (int z = 10; z <= 21; z++) {
                if ((z & 15) >= 6 && (z & 15) <= 9) continue;
                for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.OAK_PLANKS));
                l.pon(x, f + i, z, escalon(Blocks.OAK_STAIRS, Direction.WEST));
            }
        }
    }

    /**
     * El atrio del centro comercial (3 x 3 chunks): una fuente seca en medio (con monedas en el fondo), dos escaleras
     * mecánicas paradas que suben a la entreplanta del norte, tiendas cerradas en las paredes, bancos y macetas, y la
     * claraboya negra del techo.
     */
    private static void atrio(Lienzo l, int f, Grande g) {
        int W = g.w() * 16, D = g.d() * 16;
        BlockState persiana = ModBlocks.PERSIANA.get().defaultBlockState();
        var rot = (NumeradoBlock) ModBlocks.ROTULO_TIENDA.get();
        cascaron(l, f, g, ModBlocks.SUELO_CENTRO.get().defaultBlockState(), d(Blocks.SMOOTH_QUARTZ), (i, y) -> {
            int j = i & 15;
            boolean tienda = (j >= 2 && j <= 5) || (j >= 10 && j <= 13);
            if (tienda && y < 3) return persiana;
            if (tienda && y == 3) return rot.con(i / 4);
            return y == 0 ? d(Blocks.LIGHT_GRAY_CONCRETE) : d(Blocks.WHITE_CONCRETE);
        }, d(Blocks.WHITE_CONCRETE));
        for (int x = l.ox; x < l.ox + 16; x++)
            for (int z = l.oz; z < l.oz + 16; z++) {
                if (x <= 0 || z <= 0 || x >= W - 1 || z >= D - 1) continue;
                // la claraboya (negra: aquí no hay cielo) y las luces del techo
                boolean clara = Math.abs(x - W / 2.0 + 0.5) < 8 && Math.abs(z - D / 2.0 + 0.5) < 8;
                if (clara) l.pon(x, f + 10, z, (x + z) % 4 == 0 ? d(Blocks.WHITE_CONCRETE) : d(Blocks.BLACK_STAINED_GLASS));
                else if (x % 4 == 2 && z % 4 == 2) l.pon(x, f + 10, z, ModBlocks.FLUORESCENTE.get().defaultBlockState().setValue(FluorescenteBlock.ENCENDIDO, h(g.x0() + x, g.z0() + z, 3) % 4 != 0));
            }
        // la entreplanta del norte (z = 1..11) con su barandilla de cristal y tiendas cerradas al fondo
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z <= 11; z++) l.pon(x, f + 5, z, d(Blocks.SMOOTH_QUARTZ));
            l.pon(x, f + 6, 11, unida(Blocks.GLASS_PANE, false, true));
            l.pon(x, f + 4, 11, ModBlocks.FLUORESCENTE.get().defaultBlockState().setValue(FluorescenteBlock.ENCENDIDO, x % 8 == 4));
            int j = x & 15;
            if ((j >= 2 && j <= 5) || (j >= 10 && j <= 13)) {
                for (int y = 6; y <= 8; y++) l.pon(x, f + y, 1, persiana);
                l.pon(x, f + 9, 1, rot.con(x / 4 + 3));
            }
        }
        // las dos escaleras mecánicas paradas, de la planta a la entreplanta (6 escalones, hacia el norte)
        for (int ex : new int[]{14, 32}) {
            for (int i = 0; i < 6; i++) {
                int z = 17 - i;
                for (int x = ex; x <= ex + 1; x++) {
                    for (int y = 0; y < i; y++) l.pon(x, f + y, z, d(Blocks.BLACK_CONCRETE));
                    l.pon(x, f + i, z, ModBlocks.ESCALERA_MECANICA.get().defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
                }
                for (int x : new int[]{ex - 1, ex + 2}) {
                    for (int y = 0; y <= i; y++) l.pon(x, f + y, z, d(Blocks.LIGHT_GRAY_CONCRETE));
                    l.pon(x, f + i + 1, z, d(Blocks.BLACK_CONCRETE));
                }
            }
            // el hueco de la barandilla donde llega
            l.pon(ex, f + 6, 11, AIRE);
            l.pon(ex + 1, f + 6, 11, AIRE);
        }
        // la fuente seca: el pilón redondo, el fondo con monedas y el surtidor de tres pisos
        int fx = W / 2, fz = D / 2 + 4;
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                double r = Math.sqrt((x + 0.5) * (x + 0.5) + (z + 0.5) * (z + 0.5));
                if (r > 6.3) continue;
                if (r > 5.3) l.pon(fx + x, f, fz + z, d(Blocks.SMOOTH_QUARTZ));
                else {
                    l.pon(fx + x, f - 1, fz + z, d(Blocks.POLISHED_ANDESITE));
                    if (h(g.x0() + x, g.z0() + z, 620) % 9 == 0) l.pon(fx + x, f, fz + z, d(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE));
                }
            }
        for (int y = 0; y < 3; y++) {
            l.pon(fx, f + y, fz, d(Blocks.QUARTZ_PILLAR));
            l.pon(fx - 1, f + y, fz, d(Blocks.QUARTZ_PILLAR));
            l.pon(fx, f + y, fz - 1, d(Blocks.QUARTZ_PILLAR));
            l.pon(fx - 1, f + y, fz - 1, d(Blocks.QUARTZ_PILLAR));
        }
        for (int x = -2; x <= 1; x++)
            for (int z = -2; z <= 1; z++) l.pon(fx + x, f + 3, fz + z, d(Blocks.SMOOTH_QUARTZ_SLAB));
        l.pon(fx, f + 4, fz, d(Blocks.QUARTZ_PILLAR));
        // bancos y macetas alrededor
        for (int[] b : new int[][]{{fx - 9, fz, 0}, {fx + 8, fz, 0}, {fx, fz + 9, 1}}) {
            for (int k = -1; k <= 1; k++) {
                int x = b[2] == 0 ? b[0] : b[0] + k, z = b[2] == 0 ? b[1] + k : b[1];
                l.pon(x, f, z, escalon(Blocks.DARK_OAK_STAIRS, b[2] == 0 ? (b[0] < fx ? Direction.WEST : Direction.EAST) : Direction.SOUTH));
            }
        }
        for (int[] m : new int[][]{{fx - 9, fz - 4}, {fx + 8, fz - 4}, {fx - 9, fz + 4}, {fx + 8, fz + 4}}) {
            l.pon(m[0], f, m[1], d(Blocks.POLISHED_ANDESITE));
            l.pon(m[0], f + 1, m[1], d(Blocks.POTTED_AZALEA));
        }
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
        for (int n = 0; n < PurgatorioPlano.NIVELES; n++) {
            int i = PurgatorioPlano.base(n) - 1 - a.getMinBuildHeight();
            if (i >= 0 && i < col.length) col[i] = Blocks.BLACK_CONCRETE.defaultBlockState();
            if (i - 1 >= 0 && i - 1 < col.length) col[i - 1] = Blocks.STONE.defaultBlockState();
        }
        return new NoiseColumn(a.getMinBuildHeight(), col);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState r, BlockPos p) {
        int n = PurgatorioPlano.nivel(p.getY());
        Grande g = PurgatorioPlano.grande(n, p.getX() >> 4, p.getZ() >> 4);
        info.add("Purgatorio: piso " + n + ", " + zona(n, p.getX() >> 4, p.getZ() >> 4) + (g != null ? " (" + g.tipo() + ")" : ""));
    }
}
