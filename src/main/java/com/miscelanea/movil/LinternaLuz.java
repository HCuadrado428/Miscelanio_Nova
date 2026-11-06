package com.miscelanea.movil;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Linterna "de verdad": mientras un jugador tiene la linterna encendida (y el móvil encima), el servidor pone hasta dos
 * bloques de luz invisibles ({@code minecraft:light}) en el aire: uno donde da el haz (a {@link MovilConfig#linternaAlcance()}
 * bloques como mucho) y otro más flojo junto al jugador. Se recolocan unas 5 veces por segundo al moverse o girar.
 * <p>
 * Reglas para no dejar basura ni romper nada:
 * <ul>
 *   <li>solo se coloca en bloques de aire (nunca se sustituye otra cosa) y solo se quita si sigue siendo nuestra luz;</li>
 *   <li>cada luz puesta queda apuntada en un registro que se guarda con el mundo ({@code data/miscelanea_linterna.dat});</li>
 *   <li>se quitan al apagarla, al soltar el móvil, al salir, morir o cambiar de dimensión y al parar el servidor;</li>
 *   <li>si una luz quedó en un chunk que ya no está cargado (o el servidor se cerró de golpe), se quita en cuanto vuelve a
 *       cargarse ese chunk.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class LinternaLuz {
    private LinternaLuz() {}

    private static final Set<UUID> ENCENDIDAS = new HashSet<>();
    /** Luces puestas ahora por cada jugador. */
    private static final Map<UUID, List<GlobalPos>> PUESTAS = new HashMap<>();

    static void cambiar(ServerPlayer p, boolean on) {
        boolean antes = ENCENDIDAS.contains(p.getUUID());
        if (on && MovilServidor.tieneMovil(p)) ENCENDIDAS.add(p.getUUID());
        else {
            ENCENDIDAS.remove(p.getUUID());
            quitar(p.server, p.getUUID());
        }
        boolean ahora = ENCENDIDAS.contains(p.getUUID());
        if (antes != ahora) MovilRed.aQuienLoVe(p, MovilRed.S_LINTERNA, b -> {
            b.writeVarInt(p.getId());
            b.writeBoolean(ahora);
        });
    }

    static void apagar(ServerPlayer p) {
        if (ENCENDIDAS.contains(p.getUUID())) cambiar(p, false);
        else quitar(p.server, p.getUUID());
    }

    public static boolean encendida(UUID u) {
        return ENCENDIDAS.contains(u);
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent
    public static void muerte(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) apagar(p);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) quitar(p.server, p.getUUID());
    }

    @SubscribeEvent
    public static void verJugador(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer otro && e.getEntity() instanceof ServerPlayer yo && ENCENDIDAS.contains(otro.getUUID()))
            MovilRed.aJugador(yo, MovilRed.S_LINTERNA, b -> {
                b.writeVarInt(otro.getId());
                b.writeBoolean(true);
            });
    }

    @SubscribeEvent
    public static void parar(ServerStoppingEvent e) {
        for (UUID u : new ArrayList<>(PUESTAS.keySet())) quitar(e.getServer(), u);
        ENCENDIDAS.clear();
        limpiarPendientes(e.getServer(), true);
    }

    // ------------------------------------------------------------------ tick
    static void tick(MinecraftServer s) {
        if (s.getTickCount() % 4 != 0) return;
        if (s.getTickCount() % 100 == 0) limpiarPendientes(s, false);
        if (ENCENDIDAS.isEmpty()) return;
        for (Iterator<UUID> it = ENCENDIDAS.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            ServerPlayer p = s.getPlayerList().getPlayer(u);
            if (p == null || !p.isAlive() || p.isSpectator() || !MovilServidor.tieneMovil(p)) {
                it.remove();
                quitar(s, u);
                if (p != null) MovilRed.aQuienLoVe(p, MovilRed.S_LINTERNA, b -> {
                    b.writeVarInt(p.getId());
                    b.writeBoolean(false);
                });
                continue;
            }
            if (MovilConfig.linternaReal()) colocar(p);
            else quitar(s, u);
        }
    }

    private static void colocar(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int nivel = MovilConfig.linternaNivel();
        Vec3 ojo = p.getEyePosition(), dir = p.getLookAngle();
        Vec3 fin = ojo.add(dir.scale(MovilConfig.linternaAlcance()));
        BlockHitResult hit = l.clip(new ClipContext(ojo, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, p));
        List<BlockPos> quiero = new ArrayList<>();
        // donde da el haz: el último bloque de aire antes del impacto (retrocediendo si hace falta)
        Vec3 punto = hit.getType() == HitResult.Type.MISS ? fin : hit.getLocation().subtract(dir.scale(0.3));
        for (int i = 0; i < 6; i++) {
            BlockPos bp = BlockPos.containing(punto);
            if (libre(l, bp, p)) {
                quiero.add(bp);
                break;
            }
            punto = punto.subtract(dir.scale(0.7));
            if (punto.distanceToSqr(ojo) < 2.5) break;
        }
        // junto al jugador (más flojo): a la altura de la cabeza o un poco delante
        BlockPos cerca = BlockPos.containing(ojo.add(dir.scale(1.2)));
        if (!quiero.contains(cerca) && libre(l, cerca, p)) quiero.add(cerca);
        List<GlobalPos> antes = PUESTAS.getOrDefault(p.getUUID(), List.of());
        List<GlobalPos> nuevas = new ArrayList<>();
        Registro r = Registro.de(p.server);
        for (int i = 0; i < quiero.size(); i++) {
            BlockPos bp = quiero.get(i);
            GlobalPos gp = GlobalPos.of(l.dimension(), bp);
            int lv = i == 0 && quiero.size() > 1 ? nivel : Math.max(1, nivel - 4);
            if (quiero.size() == 1) lv = nivel;
            BlockState st = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lv);
            BlockState ya = l.getBlockState(bp);
            if (ya.isAir()) {
                l.setBlock(bp, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                r.anotar(gp);
            } else if (!(ya.is(Blocks.LIGHT) && antes.contains(gp))) continue;
            else if (ya.getValue(LightBlock.LEVEL) != lv) l.setBlock(bp, st, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            nuevas.add(gp);
        }
        for (GlobalPos gp : antes) if (!nuevas.contains(gp)) quitarUna(p.server, gp, r);
        if (nuevas.isEmpty()) PUESTAS.remove(p.getUUID());
        else PUESTAS.put(p.getUUID(), nuevas);
    }

    /** ¿Se puede poner (o mantener) una luz aquí? Solo aire, dentro del mundo, sin luz de otro jugador. */
    private static boolean libre(ServerLevel l, BlockPos bp, ServerPlayer p) {
        if (!l.isLoaded(bp) || l.isOutsideBuildHeight(bp)) return false;
        BlockState st = l.getBlockState(bp);
        if (st.isAir()) return true;
        GlobalPos gp = GlobalPos.of(l.dimension(), bp);
        return st.is(Blocks.LIGHT) && PUESTAS.getOrDefault(p.getUUID(), List.of()).contains(gp);
    }

    private static void quitar(MinecraftServer s, UUID u) {
        List<GlobalPos> l = PUESTAS.remove(u);
        if (l == null) return;
        Registro r = Registro.de(s);
        for (GlobalPos gp : l) quitarUna(s, gp, r);
    }

    /** Quita la luz si sigue siendo una luz (y el chunk está cargado); si no está cargado, queda pendiente en el registro. */
    private static void quitarUna(MinecraftServer s, GlobalPos gp, Registro r) {
        ServerLevel l = s.getLevel(gp.dimension());
        if (l == null) {
            r.quitar(gp);
            return;
        }
        if (!l.isLoaded(gp.pos())) return;
        BlockState st = l.getBlockState(gp.pos());
        // si le ha entrado agua, se deja el agua (createLegacyBlock da aire si no hay fluido)
        if (st.is(Blocks.LIGHT))
            l.setBlock(gp.pos(), st.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        r.quitar(gp);
    }

    /** Quita las luces del registro que ya no son de nadie (de un cierre brusco o de chunks que se descargaron). */
    private static void limpiarPendientes(MinecraftServer s, boolean todo) {
        Registro r = Registro.de(s);
        if (r.luces.isEmpty()) return;
        Set<GlobalPos> activas = new HashSet<>();
        if (!todo) for (List<GlobalPos> l : PUESTAS.values()) activas.addAll(l);
        for (GlobalPos gp : new ArrayList<>(r.luces)) if (!activas.contains(gp)) quitarUna(s, gp, r);
    }

    // ------------------------------------------------------------------ registro persistente
    public static final class Registro extends SavedData {
        final Set<GlobalPos> luces = new HashSet<>();

        static Registro de(MinecraftServer s) {
            return s.overworld().getDataStorage().computeIfAbsent(Registro::cargar, Registro::new, "miscelanea_linterna");
        }

        void anotar(GlobalPos gp) {
            if (luces.add(gp)) setDirty();
        }

        void quitar(GlobalPos gp) {
            if (luces.remove(gp)) setDirty();
        }

        @Override
        public CompoundTag save(CompoundTag t) {
            ListTag l = new ListTag();
            for (GlobalPos gp : luces) {
                CompoundTag c = new CompoundTag();
                c.putString("d", gp.dimension().location().toString());
                c.putLong("p", gp.pos().asLong());
                l.add(c);
            }
            t.put("luces", l);
            return t;
        }

        static Registro cargar(CompoundTag t) {
            Registro r = new Registro();
            ListTag l = t.getList("luces", Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag c = l.getCompound(i);
                ResourceLocation rl = ResourceLocation.tryParse(c.getString("d"));
                if (rl == null) continue;
                ResourceKey<Level> k = ResourceKey.create(Registries.DIMENSION, rl);
                r.luces.add(GlobalPos.of(k, BlockPos.of(c.getLong("p"))));
            }
            return r;
        }
    }
}
