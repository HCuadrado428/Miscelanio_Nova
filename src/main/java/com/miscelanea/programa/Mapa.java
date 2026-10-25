package com.miscelanea.programa;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miscelanea.Miscelanea;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapa de la prueba guardado en data/miscelanea/programa/comisaria.json:
 * paleta de estados de bloque, RLE (orden y, z, x) y marcadores (spawn, carro, rejas, cadáveres...).
 */
public final class Mapa {
    public static final ResourceLocation COMISARIA = new ResourceLocation(Miscelanea.MOD_ID, "programa/comisaria.json");

    /** Marcador con posición relativa al origen del mapa y sus datos extra. */
    public record Marca(String tipo, BlockPos pos, JsonObject datos) {
        public String texto(String k) {
            return datos.has(k) ? datos.get(k).getAsString() : "";
        }

        public int entero(String k, int def) {
            return datos.has(k) ? datos.get(k).getAsInt() : def;
        }

        public boolean bool(String k) {
            return datos.has(k) && datos.get(k).getAsBoolean();
        }
    }

    public static final class Datos {
        public int sx, sy, sz;
        public BlockState[] paleta;
        public int[] rle;
        public final List<Marca> marcas = new ArrayList<>();

        public List<Marca> de(String tipo) {
            List<Marca> l = new ArrayList<>();
            for (Marca m : marcas) if (m.tipo().equals(tipo)) l.add(m);
            return l;
        }

        public Marca una(String tipo) {
            for (Marca m : marcas) if (m.tipo().equals(tipo)) return m;
            return null;
        }
    }

    private Mapa() {}

    public static Datos cargar(MinecraftServer srv) throws Exception {
        var res = srv.getResourceManager().getResource(COMISARIA).orElseThrow(() -> new IllegalStateException("Falta " + COMISARIA));
        JsonObject o;
        try (Reader r = res.openAsReader()) {
            o = GsonHelper.parse(r);
        }
        Datos d = new Datos();
        JsonArray size = o.getAsJsonArray("size");
        d.sx = size.get(0).getAsInt();
        d.sy = size.get(1).getAsInt();
        d.sz = size.get(2).getAsInt();
        HolderLookup<Block> bloques = srv.registryAccess().lookupOrThrow(Registries.BLOCK);
        JsonArray pal = o.getAsJsonArray("palette");
        d.paleta = new BlockState[pal.size()];
        for (int i = 0; i < pal.size(); i++) {
            String s = pal.get(i).getAsString();
            try {
                d.paleta[i] = BlockStateParser.parseForBlock(bloques, s, false).blockState();
            } catch (Exception e) {
                Miscelanea.LOGGER.warn("Bloque del mapa no válido: {}", s);
                d.paleta[i] = Blocks.STONE.defaultBlockState();
            }
        }
        JsonArray rle = o.getAsJsonArray("rle");
        d.rle = new int[rle.size()];
        for (int i = 0; i < rle.size(); i++) d.rle[i] = rle.get(i).getAsInt();
        for (JsonElement e : o.getAsJsonArray("markers")) {
            JsonObject m = e.getAsJsonObject();
            d.marcas.add(new Marca(m.get("t").getAsString(),
                    new BlockPos(m.get("x").getAsInt(), m.get("y").getAsInt(), m.get("z").getAsInt()), m));
        }
        return d;
    }

    /** Resultado de colocar el mapa: posiciones especiales encontradas en los bloques. */
    public static final class Colocado {
        public final List<BlockPos> luces = new ArrayList<>();
        public final List<BlockPos> generadores = new ArrayList<>();
        public final List<BlockPos> cajas = new ArrayList<>();
        public final List<BlockPos> puertas = new ArrayList<>();
        public final List<BlockPos> alarmas = new ArrayList<>();
    }

    /** Coloca todo el mapa (incluido el aire, para borrar la partida anterior). */
    public static Colocado colocar(ServerLevel level, BlockPos origen, Datos d) {
        Colocado c = new Colocado();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int k = 0; k < d.rle.length; k += 2) {
            BlockState st = d.paleta[d.rle[k]];
            int n = d.rle[k + 1];
            for (int j = 0; j < n; j++, i++) {
                int x = i % d.sx, z = (i / d.sx) % d.sz, y = i / (d.sx * d.sz);
                p.set(origen.getX() + x, origen.getY() + y, origen.getZ() + z);
                if (level.getBlockState(p) != st) level.setBlock(p, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                if (st.getBlock() instanceof com.miscelanea.carcel.FluorescenteBlock) c.luces.add(p.immutable());
                if (st.getBlock() instanceof GeneradorBlock) c.generadores.add(p.immutable());
                if (st.getBlock() instanceof CajaCerradaBlock caja && !caja.esBotiquin()) c.cajas.add(p.immutable());
                if (st.is(Blocks.SPRUCE_DOOR) && st.getValue(net.minecraft.world.level.block.DoorBlock.HALF)
                        == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER) c.puertas.add(p.immutable());
                if (st.is(com.miscelanea.registry.ModBlocks.ALARMA.get())) c.alarmas.add(p.immutable());
            }
        }
        return c;
    }
}
