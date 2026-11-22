package com.miscelanea.camaras;

import com.miscelanea.Miscelanea;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

/**
 * La señal de las cámaras. El cliente de un jugador solo puede dibujar lo que tiene cargado, así que solo un jugador con el
 * chunk de la cámara cargado ("transmisor") puede dibujar lo que ve; el servidor elige uno, le manda dibujarla y reenvía sus
 * fotogramas JPEG a quien mira ese canal (que puede estar en otra dimensión o a kilómetros). Si nadie tiene cargado el
 * chunk de la cámara, no hay transmisor y la tele enseña "sin señal". No se transmite nada que nadie mire.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class CamarasServidor {
    private CamarasServidor() {}

    /** Cámaras cargadas ahora mismo en el servidor. */
    private static final List<VigilanciaBlockEntity> CAMARAS = new ArrayList<>();

    private record Ver(Set<Integer> canales, long hasta) {}

    /** Quién mira qué canales (el cliente lo repite cada medio segundo; caduca a los 3 s). */
    private static final Map<UUID, Ver> VIENDO = new HashMap<>();

    private static final class Tx {
        UUID jugador;
        long orden, frame, asignado, ultimoReenvio;
        UUID fallido;
    }

    /** Quién dibuja cada canal. */
    private static final Map<Integer, Tx> TX = new HashMap<>();
    private static long tick;

    // ------------------------------------------------------------------ cámaras cargadas
    static void alta(VigilanciaBlockEntity be) {
        if (!CAMARAS.contains(be)) CAMARAS.add(be);
    }

    static void baja(VigilanciaBlockEntity be) {
        CAMARAS.remove(be);
    }

    /** Número de cámaras cargadas (para las pruebas). */
    public static int camaras() {
        return CAMARAS.size();
    }

    /** Quién está dibujando ese canal ahora (para las pruebas), o null. */
    public static UUID transmisor(int canal) {
        Tx t = TX.get(canal);
        return t == null ? null : t.jugador;
    }

    // ------------------------------------------------------------------ paquetes del cliente
    static void recibir(ServerPlayer p, int tipo, FriendlyByteBuf b) {
        try {
            switch (tipo) {
                case CamarasRed.C_VER -> {
                    int n = Math.min(32, b.readVarInt());
                    Set<Integer> c = new HashSet<>();
                    for (int i = 0; i < n; i++) c.add(b.readVarInt());
                    if (c.isEmpty()) VIENDO.remove(p.getUUID());
                    else {
                        VIENDO.put(p.getUUID(), new Ver(c, tick + 70));
                        if (c.contains(0)) enviarLista(p);
                    }
                }
                case CamarasRed.C_FRAME -> {
                    int canal = b.readVarInt();
                    byte[] jpeg = b.readByteArray(CamarasRed.MAX_FRAME);
                    Tx t = TX.get(canal);
                    // solo vale el del transmisor elegido, con un JPEG de tamaño razonable y sin pasarse de 20 por segundo
                    if (t == null || !t.jugador.equals(p.getUUID()) || jpeg.length < 100 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) return;
                    if (tick - t.ultimoReenvio < 1) return;
                    t.frame = tick;
                    t.ultimoReenvio = tick;
                    MinecraftServer s = p.server;
                    for (Map.Entry<UUID, Ver> e : VIENDO.entrySet()) {
                        if (!e.getValue().canales().contains(canal)) continue;
                        ServerPlayer v = s.getPlayerList().getPlayer(e.getKey());
                        if (v != null) CamarasRed.aJugador(v, CamarasRed.S_FRAME, o -> {
                            o.writeVarInt(canal);
                            o.writeByteArray(jpeg);
                        });
                    }
                }
                case CamarasRed.C_CANAL -> {
                    net.minecraft.core.BlockPos pos = b.readBlockPos();
                    int canal = b.readVarInt();
                    if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > 100 || canal < 0 || canal > 99) return;
                    if (p.level().getBlockEntity(pos) instanceof TeleBlockEntity tv) tv.canal(canal);
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
            // un paquete mal formado no debe romper nada
        }
    }

    // ------------------------------------------------------------------ reparto
    @SubscribeEvent
    public static void servidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        if (tick % 10 == 0) actualizar(e.getServer());
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        VIENDO.remove(id);
        TX.values().removeIf(t -> t.jugador.equals(id));
    }

    @SubscribeEvent
    public static void parado(ServerStoppedEvent e) {
        CAMARAS.clear();
        VIENDO.clear();
        TX.clear();
        tick = 0;
    }

    private static VigilanciaBlockEntity buscar(int canal, UUID preferido, MinecraftServer s) {
        VigilanciaBlockEntity primera = null;
        for (VigilanciaBlockEntity c : CAMARAS) {
            if (c.isRemoved() || c.canal() != canal || !(c.getLevel() instanceof ServerLevel)) continue;
            if (primera == null) primera = c;
            // si hay varias en el mismo canal, la que tenga a alguien cerca
            if (!candidatos(c).isEmpty()) return c;
        }
        return primera;
    }

    /** Jugadores que tienen cargado el chunk de la cámara: los únicos que pueden dibujarla. */
    private static List<ServerPlayer> candidatos(VigilanciaBlockEntity c) {
        if (!(c.getLevel() instanceof ServerLevel sl)) return List.of();
        return sl.getChunkSource().chunkMap.getPlayers(new ChunkPos(c.getBlockPos()), false);
    }

    private static int carga(UUID id) {
        int n = 0;
        for (Tx t : TX.values()) if (t.jugador.equals(id)) n++;
        return n;
    }

    private static void actualizar(MinecraftServer s) {
        VIENDO.values().removeIf(v -> v.hasta() < tick);
        Set<Integer> pedidos = new TreeSet<>();
        for (Ver v : VIENDO.values()) for (int c : v.canales()) if (c > 0) pedidos.add(c);
        if (!CamarasConfig.activado()) pedidos.clear();
        // canales que ya nadie mira: se le dice al transmisor que pare
        for (Iterator<Map.Entry<Integer, Tx>> it = TX.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Tx> en = it.next();
            if (pedidos.contains(en.getKey())) continue;
            parar(s, en.getKey(), en.getValue());
            it.remove();
        }
        for (int canal : pedidos) {
            VigilanciaBlockEntity cam = buscar(canal, null, s);
            Tx tx = TX.get(canal);
            if (cam == null) {
                if (tx != null) {
                    parar(s, canal, tx);
                    TX.remove(canal);
                }
                continue;
            }
            List<ServerPlayer> cand = candidatos(cam);
            ServerPlayer actual = tx == null ? null : s.getPlayerList().getPlayer(tx.jugador);
            boolean sirve = actual != null && cand.contains(actual) && tick - Math.max(tx.frame, tx.asignado) < 80;
            if (!sirve) {
                UUID fallido = null;
                if (tx != null) {
                    // si no mandaba fotogramas se prueba con otro; si era el único, se reintenta con él
                    if (actual != null) {
                        parar(s, canal, tx);
                        if (tick - tx.frame >= 80) fallido = tx.jugador;
                    }
                    TX.remove(canal);
                }
                if (TX.size() >= CamarasConfig.maxActivas()) continue;
                ServerPlayer nuevo = elegir(cand, cam, fallido);
                if (nuevo == null) continue;
                tx = new Tx();
                tx.jugador = nuevo.getUUID();
                tx.asignado = tick;
                tx.fallido = fallido;
                TX.put(canal, tx);
                enviarOrden(nuevo, canal, cam);
                tx.orden = tick;
            } else if (tick - tx.orden >= 40) {
                enviarOrden(actual, canal, cam);
                tx.orden = tick;
            }
        }
        if (tick % 40 == 0)
            for (Map.Entry<UUID, Ver> e : VIENDO.entrySet()) {
                if (!e.getValue().canales().contains(0)) continue;
                ServerPlayer p = s.getPlayerList().getPlayer(e.getKey());
                if (p != null) enviarLista(p);
            }
    }

    /** El más cercano a la cámara que no esté ya muy cargado (y, si se puede, no el que acaba de fallar). */
    private static ServerPlayer elegir(List<ServerPlayer> cand, VigilanciaBlockEntity cam, UUID fallido) {
        Vec3 c = Vec3.atCenterOf(cam.getBlockPos());
        ServerPlayer mejor = null, ultimoRecurso = null;
        double d0 = Double.MAX_VALUE, d1 = Double.MAX_VALUE;
        for (ServerPlayer p : cand) {
            if (p.isRemoved() || carga(p.getUUID()) >= CamarasConfig.maxPorJugador()) continue;
            double d = p.position().distanceToSqr(c);
            if (p.getUUID().equals(fallido)) {
                if (d < d1) {
                    d1 = d;
                    ultimoRecurso = p;
                }
            } else if (d < d0) {
                d0 = d;
                mejor = p;
            }
        }
        return mejor != null ? mejor : ultimoRecurso;
    }

    private static void enviarOrden(ServerPlayer p, int canal, VigilanciaBlockEntity cam) {
        Vec3 o = cam.ojo();
        CamarasRed.aJugador(p, CamarasRed.S_ORDEN, b -> {
            b.writeVarInt(canal);
            b.writeUtf(cam.getLevel().dimension().location().toString());
            b.writeDouble(o.x);
            b.writeDouble(o.y);
            b.writeDouble(o.z);
            b.writeFloat(cam.yaw());
            b.writeFloat(cam.pitch());
            b.writeByte(CamarasConfig.calidad());
        });
    }

    private static void parar(MinecraftServer s, int canal, Tx tx) {
        ServerPlayer p = s.getPlayerList().getPlayer(tx.jugador);
        if (p != null) CamarasRed.aJugador(p, CamarasRed.S_PARAR, b -> b.writeVarInt(canal));
    }

    /** Los canales que tienen una cámara cargada y su nombre (para la tele en automático y para el rótulo). */
    private static void enviarLista(ServerPlayer p) {
        TreeMap<Integer, String> l = new TreeMap<>();
        for (VigilanciaBlockEntity c : CAMARAS) if (!c.isRemoved()) l.merge(c.canal(), c.nombre(), (x, y) -> x.isEmpty() ? y : x);
        CamarasRed.aJugador(p, CamarasRed.S_LISTA, b -> {
            b.writeVarInt(l.size());
            for (Map.Entry<Integer, String> e : l.entrySet()) {
                b.writeVarInt(e.getKey());
                b.writeUtf(e.getValue(), 32);
            }
        });
    }
}
