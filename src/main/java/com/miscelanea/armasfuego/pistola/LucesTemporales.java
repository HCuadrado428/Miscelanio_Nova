package com.miscelanea.armasfuego.pistola;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Bloques de luz (minecraft:light, invisibles) que pone la bengala para iluminar de verdad. Cada uno caduca solo si
 * nadie lo renueva (la bengala los renueva mientras está encima), así que si la bengala se apaga, se descarga o el
 * servidor se cae, la luz se quita igual: la lista se guarda con el mundo y lo que caduque en un trozo sin cargar se
 * quita al volver a cargarse. Solo se pone en aire y solo se quita si sigue siendo el bloque de luz.
 */
public final class LucesTemporales extends SavedData {
    private static final String NOMBRE = "miscelanea_revolver_luces";

    /** Por dimensión: posición (long) → tick del servidor en que caduca. */
    private final Map<ResourceKey<Level>, Map<Long, Long>> luces = new HashMap<>();

    private static LucesTemporales de(MinecraftServer srv) {
        return srv.overworld().getDataStorage().computeIfAbsent(LucesTemporales::cargar, LucesTemporales::new, NOMBRE);
    }

    private static long ahora(MinecraftServer srv) {
        return srv.overworld().getGameTime();
    }

    /** Pone (o renueva) una luz de {@code nivel} en {@code pos} durante {@code ticks}. */
    public static void poner(ServerLevel l, BlockPos pos, int nivel, int ticks) {
        if (l.isOutsideBuildHeight(pos) || !l.isLoaded(pos)) return;
        LucesTemporales d = de(l.getServer());
        Map<Long, Long> m = d.luces.computeIfAbsent(l.dimension(), k -> new HashMap<>());
        long clave = pos.asLong();
        BlockState st = l.getBlockState(pos);
        if (m.containsKey(clave)) {
            if (!st.is(Blocks.LIGHT)) {
                m.remove(clave);
                d.setDirty();
                return;
            }
        } else {
            if (!st.isAir()) return;
            l.setBlock(pos, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, nivel), 3);
        }
        m.put(clave, ahora(l.getServer()) + ticks);
        d.setDirty();
    }

    static void tick(MinecraftServer srv) {
        if (srv.getTickCount() % 5 != 0) return;
        LucesTemporales d = de(srv);
        if (d.luces.isEmpty()) return;
        long t = ahora(srv);
        for (Iterator<Map.Entry<ResourceKey<Level>, Map<Long, Long>>> it = d.luces.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            ServerLevel l = srv.getLevel(e.getKey());
            if (l == null) continue;
            for (Iterator<Map.Entry<Long, Long>> j = e.getValue().entrySet().iterator(); j.hasNext(); ) {
                var x = j.next();
                if (x.getValue() > t) continue;
                BlockPos p = BlockPos.of(x.getKey());
                if (!l.isLoaded(p)) continue;   // se quita cuando se cargue
                if (l.getBlockState(p).is(Blocks.LIGHT)) l.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                j.remove();
                d.setDirty();
            }
            if (e.getValue().isEmpty()) it.remove();
        }
    }

    /** Cuántas luces hay puestas (para la prueba). */
    public static int cuantas(MinecraftServer srv) {
        int n = 0;
        for (Map<Long, Long> m : de(srv).luces.values()) n += m.size();
        return n;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag lista = new ListTag();
        for (var e : luces.entrySet())
            for (var x : e.getValue().entrySet()) {
                CompoundTag c = new CompoundTag();
                c.putString("Dim", e.getKey().location().toString());
                c.putLong("Pos", x.getKey());
                c.putLong("Hasta", x.getValue());
                lista.add(c);
            }
        tag.put("Luces", lista);
        return tag;
    }

    private static LucesTemporales cargar(CompoundTag tag) {
        LucesTemporales d = new LucesTemporales();
        for (Tag t : tag.getList("Luces", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(c.getString("Dim")));
            d.luces.computeIfAbsent(dim, k -> new HashMap<>()).put(c.getLong("Pos"), c.getLong("Hasta"));
        }
        return d;
    }
}
