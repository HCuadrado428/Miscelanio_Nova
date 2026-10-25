package com.miscelanea.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Tinta de Splatoon pintada sobre las caras de los bloques (no cambia los bloques).
 * El servidor la guarda por mundo y la manda a los clientes que ven cada chunk. Se seca sola a los 3 minutos.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Tinta {
    public static final int SECADO = 20 * 60 * 3;

    /** Manchas de un bloque: color (0 = sin tinta) y tick de secado por cara. */
    public static final class Mancha {
        public final int[] color = new int[6];
        public final long[] hasta = new long[6];

        boolean vacia() {
            for (int c : color) if (c != 0) return false;
            return true;
        }
    }

    /** Servidor: por dimensión → por chunk → por bloque. */
    private static final Map<ResourceKey<Level>, Map<Long, Map<Long, Mancha>>> SERVIDOR = new HashMap<>();
    /** Cliente: solo el mundo en el que está. */
    public static final Map<Long, Map<Long, Mancha>> CLIENTE = new HashMap<>();
    private static final Map<ResourceKey<Level>, Map<Long, List<TintaPacket.Cambio>>> PENDIENTES = new HashMap<>();

    private Tinta() {}

    private static Map<Long, Map<Long, Mancha>> mapa(Level level) {
        return level.isClientSide ? CLIENTE : SERVIDOR.computeIfAbsent(level.dimension(), k -> new HashMap<>());
    }

    /** Color de la tinta en una cara (0 si no hay). Funciona en cliente y servidor. */
    public static int color(Level level, BlockPos pos, Direction cara) {
        Map<Long, Mancha> ch = mapa(level).get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (ch == null) return 0;
        Mancha m = ch.get(pos.asLong());
        return m == null ? 0 : m.color[cara.get3DDataValue()];
    }

    /** Color de la tinta del suelo que pisa una entidad (cara de arriba del bloque de debajo). */
    public static int suelo(Level level, Vec3 pies) {
        BlockPos b = BlockPos.containing(pies.x, pies.y - 0.05, pies.z);
        return color(level, b, Direction.UP);
    }

    private static boolean pintable(Level level, BlockPos pos, Direction cara) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir() || !s.isFaceSturdy(level, pos, cara)) return false;
        BlockPos fuera = pos.relative(cara);
        return !level.getBlockState(fuera).isFaceSturdy(level, fuera, cara.getOpposite());
    }

    /** Pinta una cara (solo servidor). */
    public static void pintar(ServerLevel level, BlockPos pos, Direction cara, int color) {
        if (!pintable(level, pos, cara)) return;
        long ck = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Mancha m = mapa(level).computeIfAbsent(ck, k -> new HashMap<>()).computeIfAbsent(pos.asLong(), k -> new Mancha());
        int i = cara.get3DDataValue();
        m.hasta[i] = level.getGameTime() + SECADO + level.random.nextInt(200);
        if (m.color[i] == color) return;
        m.color[i] = color;
        PENDIENTES.computeIfAbsent(level.dimension(), k -> new HashMap<>()).computeIfAbsent(ck, k -> new ArrayList<>())
                .add(new TintaPacket.Cambio(pos.asLong(), (byte) i, color));
    }

    /** Pinta todas las caras visibles dentro de una esfera (salpicadura). */
    public static void salpicar(ServerLevel level, Vec3 c, double radio, int color) {
        int r = (int) Math.ceil(radio) + 1;
        BlockPos b = BlockPos.containing(c);
        for (BlockPos p : BlockPos.betweenClosed(b.offset(-r, -r, -r), b.offset(r, r, r))) {
            for (Direction d : Direction.values()) {
                Vec3 centroCara = Vec3.atCenterOf(p).add(d.getStepX() * 0.5, d.getStepY() * 0.5, d.getStepZ() * 0.5);
                if (centroCara.distanceTo(c) <= radio) pintar(level, p.immutable(), d, color);
            }
        }
    }

    /** Pinta el suelo en una franja (rodillo): todas las caras de arriba bajo los puntos dados. */
    public static void pintarSuelo(ServerLevel level, Vec3 punto, int color) {
        BlockPos b = BlockPos.containing(punto);
        for (int dy = 0; dy >= -2; dy--) {
            BlockPos p = b.offset(0, dy, 0);
            if (pintable(level, p, Direction.UP)) {
                pintar(level, p, Direction.UP, color);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ servidor: envío y secado

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.level instanceof ServerLevel sl)) return;
        long t = sl.getGameTime();
        if (t % 20 == 0) {
            Map<Long, Map<Long, Mancha>> mapa = mapa(sl);
            for (var ech : mapa.entrySet()) {
                for (Iterator<Map.Entry<Long, Mancha>> it = ech.getValue().entrySet().iterator(); it.hasNext(); ) {
                    var eb = it.next();
                    Mancha m = eb.getValue();
                    for (int i = 0; i < 6; i++) {
                        if (m.color[i] != 0 && m.hasta[i] < t) {
                            m.color[i] = 0;
                            PENDIENTES.computeIfAbsent(sl.dimension(), k -> new HashMap<>()).computeIfAbsent(ech.getKey(), k -> new ArrayList<>())
                                    .add(new TintaPacket.Cambio(eb.getKey(), (byte) i, 0));
                        }
                    }
                    if (m.vacia()) it.remove();
                }
            }
            mapa.values().removeIf(Map::isEmpty);
        }
        Map<Long, List<TintaPacket.Cambio>> pend = PENDIENTES.remove(sl.dimension());
        if (pend == null) return;
        for (var e2 : pend.entrySet()) {
            ChunkPos cp = new ChunkPos(e2.getKey());
            if (!sl.hasChunk(cp.x, cp.z)) continue;
            ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunk(cp.x, cp.z)),
                    new TintaPacket(false, e2.getKey(), e2.getValue()));
        }
    }

    /** Cuando un jugador empieza a ver un chunk le mandamos su tinta. */
    @SubscribeEvent
    public static void verChunk(ChunkWatchEvent.Watch e) {
        ServerPlayer p = e.getPlayer();
        Map<Long, Mancha> ch = mapa(e.getLevel()).get(e.getPos().toLong());
        List<TintaPacket.Cambio> lista = new ArrayList<>();
        if (ch != null) {
            for (var eb : ch.entrySet()) {
                for (int i = 0; i < 6; i++) if (eb.getValue().color[i] != 0) lista.add(new TintaPacket.Cambio(eb.getKey(), (byte) i, eb.getValue().color[i]));
            }
        }
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TintaPacket(true, e.getPos().toLong(), lista));
    }

    @SubscribeEvent
    public static void descargarMundo(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel sl) SERVIDOR.remove(sl.dimension());
        else if (e.getLevel().isClientSide()) CLIENTE.clear();
    }

    // ------------------------------------------------------------------ cliente

    public static void recibir(TintaPacket p) {
        Map<Long, Mancha> ch = CLIENTE.computeIfAbsent(p.chunk, k -> new HashMap<>());
        if (p.completo) ch.clear();
        for (TintaPacket.Cambio c : p.cambios) {
            Mancha m = ch.computeIfAbsent(c.pos(), k -> new Mancha());
            m.color[c.cara()] = c.color();
            if (m.vacia()) ch.remove(c.pos());
        }
        if (ch.isEmpty()) CLIENTE.remove(p.chunk);
    }
}
