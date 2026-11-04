package com.miscelanea.domino;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Dominó doble seis: 7 fichas por jugador, sale el doble más alto, se encaja por cualquiera de los dos extremos.
 * Sin jugada se roba del pozo (si queda) o se pasa. Gana quien se quede sin fichas; si la partida se cierra, quien sume menos puntos.
 */
public class DominoPartida {
    public static final int LOBBY = 0, JUGAR = 1, FIN = 2;
    public static final int[] A = new int[28], B = new int[28];

    static {
        int k = 0;
        for (int a = 0; a <= 6; a++) for (int b = a; b <= 6; b++) {
            A[k] = a;
            B[k] = b;
            k++;
        }
    }

    public static final class Jugador {
        public String nombre = "";
        public UUID uuid;
        public int asiento;
        public final List<Integer> mano = new ArrayList<>();

        public boolean cpu() {
            return uuid == null;
        }

        public int puntos() {
            int s = 0;
            for (int f : mano) if (f >= 0) s += A[f] + B[f];
            return s;
        }
    }

    /** Ficha colocada en la cadena: valor que toca la cadena (dentro) y valor que queda libre (fuera). */
    public record Pieza(int ficha, int dentro, int fuera) {}

    public final List<Jugador> js = new ArrayList<>();
    public final List<Integer> pozo = new ArrayList<>();
    public final List<Pieza> izq = new ArrayList<>(), der = new ArrayList<>();
    public int centro = -1;
    public int fase = LOBBY, turno, ganador = -1, pasadas, sel = -1;
    public int ultLado = 0;
    public long tUlt;
    public final List<String> log = new ArrayList<>();

    public Jugador actual() {
        return js.isEmpty() ? null : js.get(turno);
    }

    void anotar(String s) {
        log.add(s);
        while (log.size() > 6) log.remove(0);
    }

    public static boolean doble(int f) {
        return A[f] == B[f];
    }

    /** Valor libre del extremo: -1 izquierdo, +1 derecho. */
    public int extremo(int lado) {
        if (centro < 0) return -1;
        List<Pieza> l = lado < 0 ? izq : der;
        if (!l.isEmpty()) return l.get(l.size() - 1).fuera();
        return lado < 0 ? A[centro] : B[centro];
    }

    public boolean encaja(int f, int lado) {
        if (f < 0 || f >= 28) return false;   // ficha oculta (en el cliente)
        int e = extremo(lado);
        return e >= 0 && (A[f] == e || B[f] == e);
    }

    public boolean jugable(int f) {
        return encaja(f, -1) || encaja(f, 1);
    }

    public boolean puedeJugar(Jugador j) {
        for (int f : j.mano) if (jugable(f)) return true;
        return false;
    }

    // ------------------------------------------------------------------ preparar
    private int asientoLibre() {
        int[] orden = {0, 2, 1, 3};
        for (int s : orden) {
            boolean usado = false;
            for (Jugador j : js) if (j.asiento == s) usado = true;
            if (!usado) return s;
        }
        return 0;
    }

    public boolean unir(UUID u, String nombre) {
        if (fase != LOBBY || js.size() >= 4) return false;
        for (Jugador j : js) if (u.equals(j.uuid)) return false;
        Jugador j = new Jugador();
        j.uuid = u;
        j.nombre = nombre;
        j.asiento = asientoLibre();
        js.add(j);
        anotar("unido|" + nombre);
        return true;
    }

    /** Otra partida con los mismos jugadores en los mismos asientos (revancha). */
    public DominoPartida revancha(Random r, long ahora) {
        DominoPartida n = new DominoPartida();
        for (Jugador j : js) {
            Jugador c = new Jugador();
            c.uuid = j.uuid;
            c.nombre = j.nombre;
            c.asiento = j.asiento;
            n.js.add(c);
        }
        n.empezar(r, ahora);
        return n;
    }

    public boolean anadirCpu() {
        if (fase != LOBBY || js.size() >= 4) return false;
        Jugador j = new Jugador();
        j.asiento = asientoLibre();
        j.nombre = "CPU " + (js.size() + 1);
        js.add(j);
        anotar("unido|" + j.nombre);
        return true;
    }

    public boolean empezar(Random r, long ahora) {
        if (fase != LOBBY || js.size() < 2) return false;
        js.sort((a, b) -> Integer.compare(orden(a.asiento), orden(b.asiento)));
        List<Integer> todas = new ArrayList<>();
        for (int f = 0; f < 28; f++) todas.add(f);
        java.util.Collections.shuffle(todas, r);
        for (Jugador j : js) {
            j.mano.clear();
            for (int k = 0; k < 7; k++) j.mano.add(todas.remove(todas.size() - 1));
        }
        pozo.clear();
        pozo.addAll(todas);
        // sale el doble más alto (o la ficha más alta si nadie tiene dobles)
        int quien = 0, mejor = -1;
        for (int i = 0; i < js.size(); i++)
            for (int f : js.get(i).mano) {
                int v = (doble(f) ? 100 : 0) + A[f] + B[f];
                if (v > mejor) {
                    mejor = v;
                    quien = i;
                }
            }
        turno = quien;
        fase = JUGAR;
        int f0 = -1;
        mejor = -1;
        for (int f : actual().mano) {
            int v = (doble(f) ? 100 : 0) + A[f] + B[f];
            if (v > mejor) {
                mejor = v;
                f0 = f;
            }
        }
        actual().mano.remove((Integer) f0);
        centro = f0;
        ultLado = 0;
        tUlt = ahora;
        anotar("sale|" + actual().nombre + "|" + A[f0] + "|" + B[f0]);
        siguiente();
        return true;
    }

    /** Orden de juego alrededor de la mesa: abajo, derecha, arriba, izquierda. */
    private static int orden(int asiento) {
        return switch (asiento) {
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
    }

    // ------------------------------------------------------------------ turno
    public boolean jugar(int f, int lado, long ahora) {
        if (fase != JUGAR) return false;
        Jugador j = actual();
        if (!j.mano.contains(f) || !encaja(f, lado)) return false;
        int e = extremo(lado);
        Pieza p = new Pieza(f, e, A[f] == e ? B[f] : A[f]);
        (lado < 0 ? izq : der).add(p);
        j.mano.remove((Integer) f);
        sel = -1;
        pasadas = 0;
        ultLado = lado;
        tUlt = ahora;
        anotar("juega|" + j.nombre + "|" + A[f] + "|" + B[f]);
        if (j.mano.isEmpty()) {
            ganador = turno;
            fase = FIN;
            anotar("domino|" + j.nombre);
            return true;
        }
        siguiente();
        return true;
    }

    public boolean robar() {
        if (fase != JUGAR) return false;
        Jugador j = actual();
        if (puedeJugar(j) || pozo.isEmpty()) return false;
        j.mano.add(pozo.remove(pozo.size() - 1));
        anotar("roba|" + j.nombre);
        return true;
    }

    /** Pasa si no puede jugar ni robar. Devuelve true si pasó. */
    public boolean pasarSiHaceFalta() {
        if (fase != JUGAR) return false;
        Jugador j = actual();
        if (puedeJugar(j) || !pozo.isEmpty()) return false;
        anotar("pasa|" + j.nombre);
        if (++pasadas >= js.size()) {
            // cierre: gana quien menos puntos tenga; si empatan los que menos tienen, la partida queda en tablas
            int mejor = 0;
            boolean empate = false;
            for (int i = 1; i < js.size(); i++) {
                int pi = js.get(i).puntos(), pm = js.get(mejor).puntos();
                if (pi < pm) {
                    mejor = i;
                    empate = false;
                } else if (pi == pm) empate = true;
            }
            fase = FIN;
            if (empate) {
                ganador = -1;
                anotar("empate|" + js.get(mejor).puntos());
            } else {
                ganador = mejor;
                anotar("cierre|" + js.get(mejor).nombre);
            }
            return true;
        }
        siguiente();
        return true;
    }

    private void siguiente() {
        turno = (turno + 1) % js.size();
        sel = -1;
    }

    public List<UUID> humanos() {
        List<UUID> l = new ArrayList<>();
        for (Jugador j : js) if (j.uuid != null) l.add(j.uuid);
        return l;
    }

    public void terminar() {
        if (fase != JUGAR) return;
        fase = FIN;
        ganador = -1;
        anotar("terminada");
    }

    /** Un paso de la CPU: juega la ficha que más puntos se quita (dobles primero); si no puede, roba o pasa. */
    public boolean cpu(long ahora) {
        if (fase != JUGAR) return false;
        int[] m = jugadaCpu();
        if (m != null) return jugar(m[0], m[1], ahora);
        return robar() || pasarSiHaceFalta();
    }

    /** Jugada elegida por la CPU (ficha, lado) o null. */
    public int[] jugadaCpu() {
        Jugador j = actual();
        int mejor = -1, lado = 0, mv = -1;
        for (int f : j.mano) for (int l = -1; l <= 1; l += 2) if (encaja(f, l)) {
            int v = (doble(f) ? 20 : 0) + A[f] + B[f];
            if (v > mv) {
                mv = v;
                mejor = f;
                lado = l;
            }
        }
        return mejor < 0 ? null : new int[]{mejor, lado};
    }

    // ------------------------------------------------------------------ guardar
    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Jugador j : js) {
            CompoundTag c = new CompoundTag();
            c.putString("n", j.nombre);
            if (j.uuid != null) c.putUUID("u", j.uuid);
            c.putInt("a", j.asiento);
            c.putIntArray("m", j.mano.stream().mapToInt(Integer::intValue).toArray());
            l.add(c);
        }
        t.put("js", l);
        t.putIntArray("pozo", pozo.stream().mapToInt(Integer::intValue).toArray());
        t.putIntArray("izq", piezas(izq));
        t.putIntArray("der", piezas(der));
        t.putIntArray("e", new int[]{centro, fase, turno, ganador, pasadas, sel, ultLado});
        t.putLong("tU", tUlt);
        ListTag lg = new ListTag();
        for (String s : log) lg.add(StringTag.valueOf(s));
        t.put("log", lg);
        return t;
    }

    /**
     * Lo que pueden ver todos: las manos ajenas y el pozo van como fichas ocultas (-1), solo se cuenta cuántas hay.
     * Al acabar la partida se enseñan todas las manos.
     */
    public CompoundTag guardarPublico() {
        CompoundTag t = guardar();
        ListTag l = t.getList("js", Tag.TAG_COMPOUND);
        if (fase != FIN) for (int i = 0; i < l.size(); i++) l.getCompound(i).putIntArray("m", ocultas(js.get(i).mano.size()));
        t.putIntArray("pozo", ocultas(pozo.size()));
        t.putIntArray("e", new int[]{centro, fase, turno, ganador, pasadas, -1, ultLado});
        return t;
    }

    private static int[] ocultas(int n) {
        int[] o = new int[n];
        java.util.Arrays.fill(o, -1);
        return o;
    }

    /** Lo que solo ve el jugador i: su mano y la ficha que tiene elegida. */
    public CompoundTag privado(int i) {
        CompoundTag t = new CompoundTag();
        Jugador j = js.get(i);
        if (j.uuid != null) t.putUUID("u", j.uuid);
        t.putInt("i", i);
        t.putIntArray("m", j.mano.stream().mapToInt(Integer::intValue).toArray());
        t.putInt("sel", turno == i ? sel : -1);
        return t;
    }

    /** En el cliente: pone la mano propia recibida aparte (si sigue correspondiendo a esta partida). */
    public void aplicarPrivado(CompoundTag t) {
        int i = t.getInt("i");
        if (i < 0 || i >= js.size() || !t.hasUUID("u")) return;
        Jugador j = js.get(i);
        if (j.uuid == null || !j.uuid.equals(t.getUUID("u"))) return;
        int[] m = t.getIntArray("m");
        if (m.length != j.mano.size()) return;
        j.mano.clear();
        for (int f : m) j.mano.add(f);
        if (turno == i && fase == JUGAR) sel = t.getInt("sel");
    }

    private static int[] piezas(List<Pieza> l) {
        int[] o = new int[l.size() * 3];
        for (int i = 0; i < l.size(); i++) {
            o[i * 3] = l.get(i).ficha();
            o[i * 3 + 1] = l.get(i).dentro();
            o[i * 3 + 2] = l.get(i).fuera();
        }
        return o;
    }

    private static void piezas(int[] o, List<Pieza> l) {
        for (int i = 0; i + 2 < o.length; i += 3) l.add(new Pieza(o[i], o[i + 1], o[i + 2]));
    }

    public static DominoPartida cargar(CompoundTag t) {
        DominoPartida p = new DominoPartida();
        ListTag lj = t.getList("js", Tag.TAG_COMPOUND);
        for (int i = 0; i < lj.size(); i++) {
            CompoundTag c = lj.getCompound(i);
            Jugador j = new Jugador();
            j.nombre = c.getString("n");
            if (c.hasUUID("u")) j.uuid = c.getUUID("u");
            j.asiento = c.getInt("a");
            for (int f : c.getIntArray("m")) j.mano.add(f);
            p.js.add(j);
        }
        for (int f : t.getIntArray("pozo")) p.pozo.add(f);
        piezas(t.getIntArray("izq"), p.izq);
        piezas(t.getIntArray("der"), p.der);
        int[] e = t.getIntArray("e");
        if (e.length >= 7) {
            p.centro = e[0]; p.fase = e[1]; p.turno = e[2]; p.ganador = e[3]; p.pasadas = e[4]; p.sel = e[5]; p.ultLado = e[6];
        }
        if (!p.js.isEmpty()) p.turno = Math.min(p.turno, p.js.size() - 1);
        p.tUlt = t.getLong("tU");
        ListTag lg = t.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < lg.size(); i++) p.log.add(lg.getString(i));
        if (p.js.size() < 2 && p.fase != LOBBY) p.fase = LOBBY;
        return p;
    }
}
