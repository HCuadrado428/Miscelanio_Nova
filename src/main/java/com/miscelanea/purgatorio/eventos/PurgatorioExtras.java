package com.miscelanea.purgatorio.eventos;

import com.miscelanea.Miscelanea;
import com.miscelanea.purgatorio.Purgado;
import com.miscelanea.purgatorio.Purgatorio;
import com.miscelanea.purgatorio.PurgatorioConfig;
import com.miscelanea.purgatorio.PurgatorioGenerador;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lo que pasa en el Purgatorio además de las zonas (2.0.0): los restos de errores (objetos tirados con nombres raros),
 * los objetos purgados de los rincones raros y de los habitantes, los logros y la lista de gente para los jugadores
 * fantasma (que son solo del cliente: {@code client.purgatorio.ExtrasCliente}). Todo se mira por jugador y como mucho
 * una vez por segundo, salvo el camino del reloj (cada medio segundo, 21 posiciones por jugador).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PurgatorioExtras {
    private PurgatorioExtras() {}

    private static final String PILLADO = "miscelanea_purg_pillado", ZONAS = "miscelanea_purg_zonas", SILENCIO = "miscelanea_purg_no_molestar";
    /** Por dónde ha pasado cada jugador (para el reloj al revés): una huella cada 10 tics, las de los últimos 10 s. */
    public record Huella(ResourceKey<Level> dim, Vec3 pos, float yaw, float pitch) {}

    private static final Map<UUID, ArrayDeque<Huella>> CAMINO = new ConcurrentHashMap<>();
    private static final Map<UUID, ResourceKey<Level>> DIM = new ConcurrentHashMap<>();
    private static final Map<UUID, Double> ULTIMA_Y = new ConcurrentHashMap<>();
    /** Tiques canjeados: {tic en el que te llaman, número}. */
    static final Map<UUID, int[]> TURNOS = new ConcurrentHashMap<>();
    /** El segundo tono del timbre del turno. */
    static final Map<UUID, Integer> TIMBRES = new ConcurrentHashMap<>();
    /** Parcelas de cada chunk (las mismas que usa el generador para las casetas y las habitaciones). */
    private static final int[][] PARCELA = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};

    private static CompoundTag persistente(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ ganchos (los llaman Purgado y Habitantes)
    /** Le ha pillado un habitante. */
    public static void pillado(ServerPlayer p) {
        persistente(p).putBoolean(PILLADO, true);
        Logros.dar(p, "pillado", "pillado");
    }

    /** ¿Tiene colgado el "No molestar" de la llave del hotel? (los habitantes no le oyen). */
    public static boolean noMolestar(ServerPlayer p) {
        return persistente(p).getLong(SILENCIO) > p.level().getGameTime();
    }

    static void ponerNoMolestar(ServerPlayer p, int tics) {
        persistente(p).putLong(SILENCIO, p.level().getGameTime() + tics);
    }

    /** Le baja de lo que lleve montado (también de la montaña rusa) antes de moverle. */
    static void soltar(ServerPlayer p) {
        if (p.isPassenger()) com.miscelanea.montana.MontanaEventos.bajarSiOSi(p);
        if (p.isVehicle()) p.ejectPassengers();
        if (p.isSleeping()) p.stopSleeping();
    }

    // ------------------------------------------------------------------ el camino (reloj al revés)
    /** Dónde estaba hace 10 s (o lo más viejo que se tenga en este mismo mundo); null si no hay nada. */
    @Nullable
    static Huella haceDiez(ServerPlayer p) {
        ArrayDeque<Huella> d = CAMINO.get(p.getUUID());
        if (d == null || d.size() < 2) return null;
        Huella h = d.peekFirst();
        return h != null && h.dim() == p.level().dimension() ? h : null;
    }

    public static void olvidarCamino(ServerPlayer p) {
        CAMINO.remove(p.getUUID());
    }

    // ------------------------------------------------------------------ cada tic de cada jugador
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        UUID u = p.getUUID();
        ResourceKey<Level> dim = p.level().dimension();
        ResourceKey<Level> antes = DIM.put(u, dim);
        if (antes != dim) {
            CAMINO.remove(u);
            if (dim == Purgatorio.DIMENSION) alEntrar(p);
            else if (antes == Purgatorio.DIMENSION) alSalir(p);
        }
        if (p.tickCount % 10 == 0) {
            ArrayDeque<Huella> d = CAMINO.computeIfAbsent(u, k -> new ArrayDeque<>());
            d.addLast(new Huella(dim, p.position(), p.getYRot(), p.getXRot()));
            while (d.size() > 21) d.pollFirst();
        }
        if (dim != Purgatorio.DIMENSION) return;
        ULTIMA_Y.put(u, p.getY());
        int[] turno = TURNOS.get(u);
        if (turno != null && p.tickCount >= turno[0]) {
            TURNOS.remove(u);
            TiqueTurnoItem.llamar(p, turno[1]);
        }
        Integer timbre = TIMBRES.get(u);
        if (timbre != null && p.tickCount >= timbre) {
            TIMBRES.remove(u);
            p.level().playSound(null, p.getX(), p.getY() + 2, p.getZ(), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 0.9F, 0.94F);
        }
        if (p.tickCount % 20 == 3) {
            zonas(p);
            rincones(p);
        }
        if (p.tickCount % 1200 == 600) RedExtras.enviar(p, nombres(p));
        if (p.tickCount % 400 == 211 && p.getRandom().nextInt(9) == 0 && PurgatorioConfig.activado()) tirarRaro(p, -1);
    }

    @SubscribeEvent
    public static void fuera(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID u = e.getEntity().getUUID();
        CAMINO.remove(u);
        DIM.remove(u);
        ULTIMA_Y.remove(u);
        TURNOS.remove(u);
        TIMBRES.remove(u);
    }

    private static void alEntrar(ServerPlayer p) {
        Logros.dar(p, "raiz", "entrar");
        persistente(p).putBoolean(PILLADO, false);
        RedExtras.enviar(p, nombres(p));
    }

    private static void alSalir(ServerPlayer p) {
        Double y = ULTIMA_Y.remove(p.getUUID());
        TURNOS.remove(p.getUUID());
        if (!PurgatorioConfig.activado()) return;
        // por un agujero del Mundo sin Texturas: se ha caído por debajo del suelo
        if (y != null && y < 0) Logros.dar(p, "caida", "caer");
        if (!persistente(p).getBoolean(PILLADO)) Logros.dar(p, "sin_pillar", "salir");
    }

    /** Apunta la zona en la que está; con todas, el logro. */
    private static void zonas(ServerPlayer p) {
        var z = PurgatorioGenerador.zona(p.getBlockX() >> 4, p.getBlockZ() >> 4);
        CompoundTag t = persistente(p).getCompound(ZONAS);
        if (t.getBoolean(z.name())) return;
        t.putBoolean(z.name(), true);
        persistente(p).put(ZONAS, t);
        for (var o : PurgatorioGenerador.Zona.values()) if (!t.getBoolean(o.name())) return;
        Logros.dar(p, "todas_zonas", "todas");
    }

    // ------------------------------------------------------------------ rincones raros
    /** El centro (x, z) de lo raro del chunk: la parcela (0-3) o el chunk entero (4). */
    static int[] centroRaro(int cx, int cz, int k) {
        if (k >= 0 && k < 4) return new int[]{cx * 16 + PARCELA[k][0] + 3, cz * 16 + PARCELA[k][1] + 3};
        return new int[]{cx * 16 + 8, cz * 16 + 8};
    }

    /** El objeto purgado que da cada rincón raro. */
    static ItemStack premio(PurgatorioGenerador.Zona z, long semilla) {
        return switch (z.name()) {
            case "SALA" -> new ItemStack(ExtrasRegistro.TIQUE.get(), 2);
            case "RECREATIVA" -> new ItemStack(ExtrasRegistro.FICHA_INFINITA.get());
            case "FERIA" -> new ItemStack(ExtrasRegistro.BRUJULA.get());
            case "HOTEL" -> new ItemStack(ExtrasRegistro.LLAVE.get());
            default -> new ItemStack(semilla % 2 == 0 ? ExtrasRegistro.RELOJ.get() : ExtrasRegistro.MISSINGNO_ITEM.get());
        };
    }

    private static void rincones(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int cx = pcx + dx, cz = pcz + dz, k = PurgatorioGenerador.raro(cx, cz);
                if (k < 0) continue;
                int[] c = centroRaro(cx, cz, k);
                double ddx = p.getX() - (c[0] + 0.5), ddz = p.getZ() - (c[1] + 0.5), r = k == 4 ? 9 : 7;
                if (ddx * ddx + ddz * ddz > r * r) continue;
                var zona = PurgatorioGenerador.zona(cx, cz);
                Logros.dar(p, "rincones", zona.name().toLowerCase(java.util.Locale.ROOT));
                long clave = ChunkPos.asLong(cx, cz) * 5 + k;
                RinconesDatos datos = RinconesDatos.de(l);
                if (datos.cogido(clave)) continue;
                AABB caja = new AABB(c[0] - 12, p.getY() - 8, c[1] - 12, c[0] + 12, p.getY() + 8, c[1] + 12);
                boolean yaEsta = !l.getEntitiesOfClass(ItemEntity.class, caja, ie -> ie.getItem().hasTag() && ie.getItem().getTag().contains("MiscelaneaRincon")
                        && ie.getItem().getTag().getLong("MiscelaneaRincon") == clave).isEmpty();
                if (yaEsta) continue;
                BlockPos sitio = libreCerca(l, c[0], p.getBlockY(), c[1], 4);
                if (sitio == null) continue;
                ItemStack st = premio(zona, clave);
                st.getOrCreateTag().putLong("MiscelaneaRincon", clave);
                soltarEn(l, sitio, st);
                Miscelanea.LOGGER.debug("Purgatorio: objeto del rincón raro de {} en {}", zona, sitio);
            }
    }

    /** Un sitio de pie libre (aire, aire y suelo debajo) cerca de (x, y, z), o null. */
    @Nullable
    public static BlockPos libreCerca(ServerLevel l, int x, int y, int z, int radio) {
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= radio; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : dys) {
                        q.set(x + dx, y + dy, z + dz);
                        if (!l.hasChunkAt(q)) continue;
                        BlockPos abajo = q.below();
                        if (l.getBlockState(q).getCollisionShape(l, q).isEmpty() && l.getFluidState(q).isEmpty()
                                && l.getBlockState(q.above()).getCollisionShape(l, q.above()).isEmpty()
                                && !l.getBlockState(abajo).getCollisionShape(l, abajo).isEmpty()
                                && !l.getBlockState(q).is(com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get()))
                            return q.immutable();
                    }
                }
        return null;
    }

    private static ItemEntity soltarEn(ServerLevel l, BlockPos b, ItemStack st) {
        ItemEntity ie = new ItemEntity(l, b.getX() + 0.5, b.getY() + 0.05, b.getZ() + 0.5, st, 0, 0, 0);
        ie.setExtendedLifetime();
        l.addFreshEntity(ie);
        return ie;
    }

    // ------------------------------------------------------------------ restos de errores: objetos tirados
    /** Tira un objeto raro por detrás o a un lado del jugador, a 6-12 bloques (k < 0: uno al azar). */
    public static ItemEntity tirarRaro(ServerPlayer p, int k) {
        ServerLevel l = p.serverLevel();
        if (l.getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(32), ie -> ie.getItem().hasTag()
                && ie.getItem().getTag().getBoolean(ObjetosRaros.MARCA)).size() >= 3 && k < 0) return null;
        var r = p.getRandom();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(p.getYRot() + 90 + r.nextDouble() * 180), d = 6 + r.nextDouble() * 6;
            int x = (int) Math.floor(p.getX() - Math.sin(a) * d), z = (int) Math.floor(p.getZ() + Math.cos(a) * d);
            BlockPos s = libreCerca(l, x, p.getBlockY(), z, 1);
            if (s == null) continue;
            boolean sinTextura = PurgatorioGenerador.zona(x >> 4, z >> 4) == PurgatorioGenerador.Zona.SIN_TEXTURA;
            return soltarEn(l, s, ObjetosRaros.uno(r, k, sinTextura));
        }
        return null;
    }

    @SubscribeEvent
    public static void recoger(EntityItemPickupEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        ItemStack st = e.getItem().getItem();
        CompoundTag t = st.getTag();
        if (t == null) return;
        if (t.contains("MiscelaneaRincon")) {
            if (Purgatorio.en(p.level())) RinconesDatos.de(p.serverLevel()).coger(t.getLong("MiscelaneaRincon"));
            t.remove("MiscelaneaRincon");
        }
        if (t.getBoolean(ObjetosRaros.MARCA)) {
            t.remove(ObjetosRaros.MARCA);
            Logros.dar(p, "objeto_raro", "recoger");
        }
        if (t.isEmpty()) st.setTag(null);
    }

    // ------------------------------------------------------------------ pegarle a un habitante
    /** No les hace nada, pero a veces se les cae algo (una vez cada uno); y pegarle a Herobrine tiene su logro. */
    @SubscribeEvent
    public static void pegar(AttackEntityEvent e) {
        if (!(e.getTarget() instanceof Purgado g) || !(e.getEntity() instanceof ServerPlayer p) || !Purgatorio.en(p.level())) return;
        if (g.variante() == Purgado.Variante.HEROBRINE) Logros.dar(p, "herobrine", "pegar");
        CompoundTag t = g.getPersistentData();
        if (t.getBoolean("MiscelaneaSolto")) return;
        t.putBoolean("MiscelaneaSolto", true);
        if (p.getRandom().nextInt(4) != 0 && !t.getBoolean("MiscelaneaSueltaSiOSi")) return;
        soltarDeHabitante(g, p.getRandom().nextInt(20));
    }

    /** Lo que se le cae a un habitante: el reloj (40 %), un MissingNo. (35 %) o la brújula (25 %). */
    public static ItemEntity soltarDeHabitante(Purgado g, int tirada) {
        ItemStack st = new ItemStack(tirada < 8 ? ExtrasRegistro.RELOJ.get() : tirada < 15 ? ExtrasRegistro.MISSINGNO_ITEM.get() : ExtrasRegistro.BRUJULA.get());
        return g.spawnAtLocation(st, 0.5F);
    }

    // ------------------------------------------------------------------ nombres para los fantasmas
    private static List<UUID> conocidos = List.of();
    private static long conocidosT = -1;

    /** Los UUID de quien se ha conectado alguna vez (los ficheros de playerdata del mundo), mirados cada 5 min. */
    private static List<UUID> conocidos(MinecraftServer s) {
        long ahora = System.currentTimeMillis();
        if (conocidosT >= 0 && ahora - conocidosT < 300_000) return conocidos;
        conocidosT = ahora;
        List<UUID> l = new ArrayList<>();
        try (var fs = java.nio.file.Files.list(s.getWorldPath(LevelResource.PLAYER_DATA_DIR))) {
            fs.limit(400).forEach(f -> {
                String n = f.getFileName().toString();
                if (!n.endsWith(".dat")) return;
                try {
                    l.add(UUID.fromString(n.substring(0, n.length() - 4)));
                } catch (IllegalArgumentException ignorado) {
                }
            });
        } catch (Exception ex) {
            Miscelanea.LOGGER.debug("Purgatorio: no se pudo leer playerdata", ex);
        }
        conocidos = l;
        return l;
    }

    /** Gente del servidor que no está ahora en el Purgatorio (ni él): hasta 32, al azar. */
    public static RedExtras.Nombres nombres(ServerPlayer para) {
        MinecraftServer s = para.server;
        Map<UUID, String> m = new LinkedHashMap<>();
        java.util.Set<UUID> dentro = new java.util.HashSet<>();
        for (ServerPlayer o : s.getPlayerList().getPlayers()) {
            if (Purgatorio.en(o.level())) dentro.add(o.getUUID());
            else m.put(o.getUUID(), o.getGameProfile().getName());
        }
        var cache = s.getProfileCache();
        if (cache != null)
            for (UUID u : conocidos(s))
                if (!m.containsKey(u) && !dentro.contains(u)) cache.get(u).ifPresent(g -> {
                    if (g.getName() != null && !g.getName().isEmpty()) m.put(u, g.getName());
                });
        m.remove(para.getUUID());
        List<UUID> ids = new ArrayList<>(m.keySet());
        Collections.shuffle(ids, new java.util.Random(para.getRandom().nextLong()));
        RedExtras.Nombres n = new RedExtras.Nombres();
        for (UUID u : ids) {
            if (n.ids.size() >= 32) break;
            String nombre = m.get(u);
            if (nombre.length() > 16) continue;
            n.ids.add(u);
            n.nombres.add(nombre);
        }
        return n;
    }
}
