package com.miscelanea.ranking;

import com.miscelanea.Miscelanea;
import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.arcade.ArcadeRecordsPacket;
import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranking de todo el servidor (se guarda con el mundo): la mejor marca de cada jugador en cada categoría. Empates: gana
 * quien la consiguió antes.
 * <h2>Categorías</h2>
 * <ul>
 *   <li>Juegos de recreativa y casetas de la feria: su id tal cual ({@code chatarra}, {@code tiro}, {@code bolos}...), los de
 *       {@link ArcadeJuegos#IDS} y {@link ArcadeJuegos#FERIA}. Llegan por {@code ArcadeRecords.registrar}.</li>
 *   <li>Paso Doble por canción y dificultad: {@code paso/<canción>/<0..2>}; pista de baile: {@code pista/<canción>/<0..2>}.</li>
 *   <li>Victorias en juegos de mesa: {@code mesa/<juego>} (se suman). Gancho para los juegos de mesa:
 *       {@link #victoria(ServerPlayer, String)} o {@link #victoria(MinecraftServer, UUID, String, String)}; cualquier otra
 *       cosa que cuente puede usar {@link #sumar} o {@link #mejor} con su propia categoría.</li>
 *   <li>Resúmenes globales (calculados): {@code global/campeones} (primeros puestos), {@code global/podios} (10 puntos el 1.º,
 *       9 el 2.º... en cada categoría de puntos) y {@code global/victorias} (todas las victorias de mesa juntas).</li>
 * </ul>
 * A los clientes se les mandan solo los 10 primeros de cada categoría (en {@link ArcadeRecordsPacket#cliente}, mismo
 * formato que las tablas de las recreativas: lista de {n: nombre, p: puntos}).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public class Ranking extends SavedData {
    private static final String NOMBRE = "miscelanea_ranking";
    public static final String PASO = "paso/", PISTA = "pista/", MESA = "mesa/", GLOBAL = "global/";
    public static final String[] GLOBALES = {"global/campeones", "global/podios", "global/victorias"};
    public static final int TOP = 10;

    /** categoría → (clave del jugador → {n: nombre, p: puntos, t: cuándo (ms)}). La clave es el UUID, o "n:nombre" (récords viejos). */
    private CompoundTag cats = new CompoundTag();
    private boolean migrado;

    public static Ranking de(MinecraftServer s) {
        Ranking r = s.overworld().getDataStorage().computeIfAbsent(Ranking::cargar, Ranking::new, NOMBRE);
        if (!r.migrado) r.migrar(s);
        return r;
    }

    private static Ranking cargar(CompoundTag t) {
        Ranking r = new Ranking();
        r.cats = t.getCompound("cats");
        r.migrado = t.getBoolean("migrado");
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag t) {
        t.put("cats", cats);
        t.putBoolean("migrado", migrado);
        return t;
    }

    /** Las tablas antiguas de las recreativas (10 mejores sin UUID) pasan al ranking, en su orden. */
    private void migrar(MinecraftServer s) {
        migrado = true;
        // victorias de mesa que ya tenía apuntadas Victorias antes del ranking
        for (Map.Entry<UUID, Map<String, Integer>> en : com.miscelanea.juegos.Victorias.todas().entrySet()) {
            String n = com.miscelanea.juegos.Victorias.nombre(en.getKey());
            for (Map.Entry<String, Integer> j : en.getValue().entrySet())
                if (j.getValue() > 0) apuntar(MESA + j.getKey(), en.getKey(), n.isEmpty() ? "?" : n, j.getValue(), true);
        }
        CompoundTag viejas = com.miscelanea.arcade.ArcadeRecords.viejas(s);
        long t = 1;
        for (String juego : viejas.getAllKeys()) {
            ListTag l = viejas.getList(juego, Tag.TAG_COMPOUND);
            for (int i = 0; i < l.size(); i++) {
                CompoundTag e = l.getCompound(i);
                String n = e.getString("n");
                if (n.isEmpty()) continue;
                CompoundTag cat = cats.getCompound(juego);
                String k = "n:" + n;
                if (!cat.contains(k) || cat.getCompound(k).getInt("p") < e.getInt("p")) {
                    CompoundTag x = new CompoundTag();
                    x.putString("n", n);
                    x.putInt("p", e.getInt("p"));
                    x.putLong("t", t++);
                    cat.put(k, x);
                }
                cats.put(juego, cat);
            }
        }
        setDirty();
    }

    // ------------------------------------------------------------------ categorías
    public static String categoriaPaso(String cancion, int dif) {
        return PASO + cancion + "/" + Math.max(0, Math.min(2, dif));
    }

    public static String categoriaPista(String cancion, int dif) {
        return PISTA + cancion + "/" + Math.max(0, Math.min(2, dif));
    }

    public static boolean esGlobal(String cat) {
        return cat.startsWith(GLOBAL);
    }

    /** ¿Las marcas se suman (victorias) en vez de quedarse con la mejor? */
    public static boolean deSuma(String cat) {
        return cat.startsWith(MESA) || esGlobal(cat);
    }

    /** Nombre para enseñar de una categoría (se traduce en el cliente). */
    public static MutableComponent nombre(String cat) {
        if (cat.startsWith(PASO) || cat.startsWith(PISTA)) {
            boolean paso = cat.startsWith(PASO);
            String resto = cat.substring(paso ? PASO.length() : PISTA.length());
            int b = resto.lastIndexOf('/');
            String id = b < 0 ? resto : resto.substring(0, b);
            int d = 1;
            try {
                d = b < 0 ? 1 : Integer.parseInt(resto.substring(b + 1));
            } catch (NumberFormatException ignored) {
            }
            CancionesBaile.Cancion c = CancionesBaile.de(id);
            Component titulo = c == null ? Component.literal(id.substring(id.indexOf(':') + 1))
                    : c.tituloClave != null ? Component.translatableWithFallback(c.tituloClave, c.titulo) : Component.literal(c.titulo);
            return Component.translatable(paso ? "ranking.miscelanea.cat.paso" : "ranking.miscelanea.cat.pista", titulo,
                    Component.translatable("paso.miscelanea." + CancionesBaile.DIFICULTADES[Math.max(0, Math.min(2, d))]));
        }
        if (cat.startsWith(MESA)) {
            String j = cat.substring(MESA.length());
            return Component.translatable("ranking.miscelanea.cat.mesa", Component.translatableWithFallback("ranking.miscelanea.mesa." + j, j));
        }
        if (esGlobal(cat)) return Component.translatable("ranking.miscelanea." + cat.replace('/', '.'));
        return Component.translatableWithFallback("juego.miscelanea." + cat, cat);
    }

    /** Todas las categorías en orden: las globales, las recreativas, la feria y luego las que tienen marcas (canciones, mesas...). */
    public List<String> categorias() {
        return categorias(false);
    }

    /** Igual, pero si conMarcas solo las que tienen alguna marca (más las globales). */
    public List<String> categorias(boolean conMarcas) {
        List<String> l = new ArrayList<>(List.of(GLOBALES));
        if (conMarcas) {
            List<String> todas = categorias(false);
            for (String c : todas) if (!esGlobal(c) && !cats.getCompound(c).isEmpty()) l.add(c);
            return l;
        }
        for (String s : ArcadeJuegos.IDS) l.add(s);
        for (String s : ArcadeJuegos.FERIA) l.add(s);
        for (String s : com.miscelanea.juegos.Victorias.JUEGOS) l.add(MESA + s);
        List<String> resto = new ArrayList<>();
        for (String k : cats.getAllKeys()) if (!l.contains(k) && !cats.getCompound(k).isEmpty()) resto.add(k);
        // también las canciones que hay aunque nadie las haya bailado aún
        for (CancionesBaile.Cancion c : CancionesBaile.lista())
            for (int d = 0; d < 3; d++) {
                for (String k : new String[]{categoriaPaso(c.id, d), categoriaPista(c.id, d)}) if (!resto.contains(k)) resto.add(k);
            }
        resto.sort(Comparator.comparingInt((String k) -> k.startsWith(PASO) ? 0 : k.startsWith(PISTA) ? 1 : k.startsWith(MESA) ? 2 : 3)
                .thenComparing(k -> k));
        l.addAll(resto);
        return l;
    }

    // ------------------------------------------------------------------ marcas
    /** Una entrada del ranking ya ordenado. */
    public record Entrada(String clave, String nombre, int puntos, long cuando) {}

    private static final Comparator<Entrada> ORDEN = Comparator.comparingInt((Entrada e) -> -e.puntos).thenComparingLong(Entrada::cuando)
            .thenComparing(Entrada::nombre);

    private List<Entrada> crudo(String cat) {
        List<Entrada> l = new ArrayList<>();
        CompoundTag c = cats.getCompound(cat);
        for (String k : c.getAllKeys()) {
            CompoundTag e = c.getCompound(k);
            l.add(new Entrada(k, e.getString("n"), e.getInt("p"), e.getLong("t")));
        }
        l.sort(ORDEN);
        return l;
    }

    /** Todo el ranking de una categoría, ordenado (las globales se calculan). */
    public List<Entrada> ranking(String cat) {
        if (!esGlobal(cat)) return crudo(cat);
        Map<String, long[]> suma = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (String k : cats.getAllKeys()) {
            boolean mesa = k.startsWith(MESA);
            if (cat.equals("global/victorias") != mesa) continue;
            List<Entrada> l = crudo(k);
            for (int i = 0; i < l.size(); i++) {
                Entrada e = l.get(i);
                long v = switch (cat) {
                    case "global/campeones" -> i == 0 ? 1 : 0;
                    case "global/podios" -> Math.max(0, TOP - i);
                    default -> e.puntos;
                };
                if (v <= 0) continue;
                long[] a = suma.computeIfAbsent(e.clave, x -> new long[]{0, 0});
                a[0] += v;
                a[1] = Math.max(a[1], e.cuando);       // empate: quien completó antes su marca
                nombres.put(e.clave, e.nombre);
            }
        }
        List<Entrada> l = new ArrayList<>();
        for (Map.Entry<String, long[]> en : suma.entrySet())
            l.add(new Entrada(en.getKey(), nombres.get(en.getKey()), (int) Math.min(Integer.MAX_VALUE, en.getValue()[0]), en.getValue()[1]));
        l.sort(ORDEN);
        return l;
    }

    public List<Entrada> top(String cat) {
        List<Entrada> l = ranking(cat);
        return l.size() > TOP ? l.subList(0, TOP) : l;
    }

    /** Puesto (1..) del jugador en la categoría, o 0 si no tiene marca. */
    public int puesto(String cat, String clave) {
        List<Entrada> l = ranking(cat);
        for (int i = 0; i < l.size(); i++) if (l.get(i).clave.equals(clave)) return i + 1;
        return 0;
    }

    /**
     * Apunta una marca; se queda la mejor de cada jugador (o se suma, si suma). Devuelve el puesto que tiene ahora si la
     * marca ha mejorado (1..), o 0 si no ha cambiado nada.
     */
    public int apuntar(String cat, UUID id, String nombre, int puntos, boolean suma) {
        if (cat.isEmpty() || cat.length() > 200 || esGlobal(cat) || puntos <= 0 || id == null) return 0;
        CompoundTag c = cats.getCompound(cat);
        String k = id.toString();
        CompoundTag e = c.getCompound(k);
        // un récord viejo con el mismo nombre (sin UUID) se junta con el del jugador
        String vieja = "n:" + nombre;
        if (c.contains(vieja)) {
            CompoundTag v = c.getCompound(vieja);
            c.remove(vieja);
            if (!e.contains("p") || v.getInt("p") > e.getInt("p")) e = v.copy();
        }
        int antes = e.contains("p") ? e.getInt("p") : 0;
        long nuevo = suma ? (long) antes + puntos : puntos;
        if (!suma && nuevo <= antes) {
            if (!c.contains(k) && e.contains("p")) {          // se ha juntado con el viejo: guardarlo igualmente
                c.put(k, e);
                cats.put(cat, c);
                setDirty();
            }
            return 0;
        }
        e.putString("n", nombre);
        e.putInt("p", (int) Math.min(Integer.MAX_VALUE, nuevo));
        e.putLong("t", System.currentTimeMillis());
        c.put(k, e);
        cats.put(cat, c);
        setDirty();
        return puesto(cat, k);
    }

    // ------------------------------------------------------------------ API para los juegos
    /** Mejor marca del jugador en la categoría (puntos de una partida). Avisa si entra en el top 10. */
    public static void mejor(ServerPlayer p, String cat, int puntos) {
        if (p == null || cat == null || puntos <= 0) return;
        Ranking r = de(p.server);
        int pos = r.apuntar(cat, p.getUUID(), p.getGameProfile().getName(), puntos, false);
        if (pos == 0) return;
        r.enviar(p.server, cat);
        if (pos == 1)
            p.server.getPlayerList().broadcastSystemMessage(Component.translatable("msg.miscelanea.arcade.record_mundial", p.getDisplayName(),
                    nombre(cat), puntos).withStyle(ChatFormatting.GOLD), false);
        else if (pos <= TOP)
            p.displayClientMessage(Component.translatable("msg.miscelanea.arcade.top", pos).withStyle(ChatFormatting.YELLOW), true);
    }

    /** Suma n a la cuenta del jugador en la categoría (victorias, partidas...). */
    public static void sumar(MinecraftServer s, UUID id, String nombre, String cat, int n) {
        if (s == null || id == null || cat == null || n <= 0) return;
        Ranking r = de(s);
        if (r.apuntar(cat, id, nombre == null ? "?" : nombre, n, true) > 0) r.enviar(s, cat);
    }

    /** Gancho de los juegos de mesa: una victoria más del jugador en ese juego (categoría mesa/&lt;juego&gt;). */
    public static void victoria(ServerPlayer p, String juego) {
        if (p != null) victoria(p.server, p.getUUID(), p.getGameProfile().getName(), juego);
    }

    public static void victoria(MinecraftServer s, UUID id, String nombre, String juego) {
        if (s == null || juego == null || juego.isEmpty()) return;
        // si el ranking aún no se ha estrenado, al pasar las victorias de Victorias ya entra esta (no se cuenta dos veces)
        Ranking r = s.overworld().getDataStorage().computeIfAbsent(Ranking::cargar, Ranking::new, NOMBRE);
        if (!r.migrado) {
            r.migrar(s);
            r.enviar(s, MESA + juego);
            return;
        }
        sumar(s, id, nombre, MESA + juego, 1);
    }

    // ------------------------------------------------------------------ clientes
    private ListTag lista(String cat) {
        ListTag l = new ListTag();
        for (Entrada e : top(cat)) {
            CompoundTag x = new CompoundTag();
            x.putString("n", e.nombre);
            x.putInt("p", e.puntos);
            l.add(x);
        }
        return l;
    }

    /** Las tablas (10 primeros) para los clientes: todas, o solo una categoría y las globales. */
    public CompoundTag paraClientes(@Nullable String cat) {
        CompoundTag t = new CompoundTag();
        if (cat == null) {
            for (String k : cats.getAllKeys()) t.put(k, lista(k));
            t.putBoolean(ArcadeRecordsPacket.TODO, true);
        } else t.put(cat, lista(cat));
        for (String g : GLOBALES) t.put(g, lista(g));
        return t;
    }

    private void enviar(MinecraftServer s, String cat) {
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), new ArcadeRecordsPacket(paraClientes(cat)));
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p)
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new ArcadeRecordsPacket(de(p.server).paraClientes(null)));
    }

    /** Categorías con sus nombres, para el comando. */
    public Map<String, Component> nombres() {
        Map<String, Component> m = new LinkedHashMap<>();
        for (String c : categorias()) m.put(c, nombre(c));
        return m;
    }
}
