package com.miscelanea.flota;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Hundir la flota: dos jugadores (o uno contra la CPU), mares de 10x10 y cinco barcos (5, 4, 3, 3, 2).
 * Al tocar se repite el disparo; gana quien hunda toda la flota rival.
 */
public class FlotaPartida {
    public static final int LOBBY = 0, COLOCAR = 1, JUGAR = 2, FIN = 3;
    public static final int[] TAM = {5, 4, 3, 3, 2};
    public static final int AGUA = 1, TOCADO = 2;
    public static final int VUELO = 14;         // ticks que tarda el proyectil en caer

    public static final class Jugador {
        public String nombre = "";
        public UUID uuid;
        public boolean listo;
        /** Barcos: x, y, horizontal(1/0) por barco. */
        public final int[][] barcos = new int[TAM.length][3];
        /** Disparos recibidos en su mar: 0 nada, 1 agua, 2 tocado. */
        public final int[] recibidos = new int[100];
        public int sel = -1;

        public boolean cpu() {
            return uuid == null;
        }
    }

    public final List<Jugador> js = new ArrayList<>();
    public int fase = LOBBY, turno, ganador = -1;
    public long tDisparo, tLibre;
    public int ultTirador = -1, ultCelda = -1, ultResultado, ultHundido = -1;
    public final List<String> log = new ArrayList<>();

    public Jugador actual() {
        return js.isEmpty() ? null : js.get(turno);
    }

    void anotar(String s) {
        log.add(s);
        while (log.size() > 6) log.remove(0);
    }

    // ------------------------------------------------------------------ barcos
    public static int[] celdas(int[] b, int tam) {
        int[] o = new int[tam];
        for (int k = 0; k < tam; k++) o[k] = b[2] == 1 ? b[1] * 10 + b[0] + k : (b[1] + k) * 10 + b[0];
        return o;
    }

    /** Índice del barco que ocupa una celda del mar del jugador, o -1. */
    public static int barcoEn(Jugador j, int celda) {
        for (int i = 0; i < TAM.length; i++) for (int c : celdas(j.barcos[i], TAM[i])) if (c == celda) return i;
        return -1;
    }

    private static boolean cabe(Jugador j, int i, int x, int y, int h) {
        int tam = TAM[i];
        if (x < 0 || y < 0 || (h == 1 ? x + tam > 10 || y > 9 : y + tam > 10 || x > 9)) return false;
        int[] mio = celdas(new int[]{x, y, h}, tam);
        for (int k = 0; k < TAM.length; k++) {
            if (k == i) continue;
            for (int c : celdas(j.barcos[k], TAM[k]))
                for (int m : mio) {
                    // sin tocarse ni en diagonal
                    if (Math.abs(c % 10 - m % 10) <= 1 && Math.abs(c / 10 - m / 10) <= 1) return false;
                }
        }
        return true;
    }

    public static void aleatorio(Jugador j, Random r) {
        for (int intento = 0; intento < 200; intento++) {
            for (int[] b : j.barcos) {
                b[0] = -20;
                b[1] = -20;
            }
            boolean ok = true;
            for (int i = 0; i < TAM.length && ok; i++) {
                ok = false;
                for (int k = 0; k < 300 && !ok; k++) {
                    int h = r.nextInt(2), x = r.nextInt(10), y = r.nextInt(10);
                    if (cabe(j, i, x, y, h)) {
                        j.barcos[i][0] = x;
                        j.barcos[i][1] = y;
                        j.barcos[i][2] = h;
                        ok = true;
                    }
                }
            }
            if (ok) return;
        }
    }

    public boolean hundido(Jugador j, int i) {
        // en el cliente los barcos ajenos que siguen a flote llegan fuera del mar (ocultos)
        for (int c : celdas(j.barcos[i], TAM[i])) if (c < 0 || c >= 100 || j.recibidos[c] != TOCADO) return false;
        return true;
    }

    public boolean flotaHundida(Jugador j) {
        for (int i = 0; i < TAM.length; i++) if (!hundido(j, i)) return false;
        return true;
    }

    // ------------------------------------------------------------------ preparar
    public boolean unir(UUID u, String nombre, Random r) {
        if (fase != LOBBY || js.size() >= 2) return false;
        for (Jugador j : js) if (u.equals(j.uuid)) return false;
        Jugador j = new Jugador();
        j.uuid = u;
        j.nombre = nombre;
        aleatorio(j, r);
        js.add(j);
        anotar("unido|" + nombre);
        return true;
    }

    public boolean anadirCpu(Random r) {
        if (fase != LOBBY || js.size() >= 2) return false;
        Jugador j = new Jugador();
        j.nombre = "CPU";
        aleatorio(j, r);
        j.listo = true;
        js.add(j);
        anotar("unido|CPU");
        return true;
    }

    public boolean empezar() {
        if (fase != LOBBY || js.size() < 2) return false;
        fase = COLOCAR;
        return true;
    }

    /** Colocación manual: tocar un barco lo selecciona, tocarlo otra vez lo gira, tocar agua lo mueve allí. */
    public boolean tocarPropio(int yo, int celda) {
        if (fase != COLOCAR) return false;
        Jugador j = js.get(yo);
        if (j.listo) return false;
        int b = barcoEn(j, celda);
        if (b >= 0) {
            if (j.sel == b) {
                int[] q = j.barcos[b];
                int nh = 1 - q[2];
                if (cabe(j, b, q[0], q[1], nh)) q[2] = nh;
                else return false;
            } else j.sel = b;
            return true;
        }
        if (j.sel >= 0) {
            int[] q = j.barcos[j.sel];
            if (cabe(j, j.sel, celda % 10, celda / 10, q[2])) {
                q[0] = celda % 10;
                q[1] = celda / 10;
                return true;
            }
        }
        return false;
    }

    public void listo(int yo, Random r, long ahora) {
        if (fase != COLOCAR) return;
        Jugador j = js.get(yo);
        j.listo = true;
        j.sel = -1;
        anotar("listo|" + j.nombre);
        if (js.get(0).listo && js.get(1).listo) {
            fase = JUGAR;
            turno = r.nextInt(2);
            tLibre = ahora;
            anotar("empieza|" + actual().nombre);
        }
    }

    // ------------------------------------------------------------------ disparar
    public boolean puedeDisparar(long ahora) {
        return fase == JUGAR && ahora >= tLibre;
    }

    /** El jugador de turno dispara a la celda del mar rival. */
    public boolean disparar(int celda, long ahora) {
        if (!puedeDisparar(ahora) || celda < 0 || celda >= 100) return false;
        Jugador rival = js.get(1 - turno);
        if (rival.recibidos[celda] != 0) return false;
        int b = barcoEn(rival, celda);
        rival.recibidos[celda] = b >= 0 ? TOCADO : AGUA;
        ultTirador = turno;
        ultCelda = celda;
        ultResultado = rival.recibidos[celda];
        ultHundido = b >= 0 && hundido(rival, b) ? b : -1;
        tDisparo = ahora;
        tLibre = ahora + VUELO + 12;
        String coord = (char) ('A' + celda % 10) + "" + (celda / 10 + 1);
        if (b < 0) {
            anotar("agua|" + actual().nombre + "|" + coord);
            turno = 1 - turno;
        } else if (ultHundido >= 0) {
            anotar("hundido|" + actual().nombre + "|" + coord + "|" + TAM[b]);
            if (flotaHundida(rival)) {
                ganador = ultTirador;
                fase = FIN;
                anotar("gana|" + actual().nombre);
            }
        } else anotar("tocado|" + actual().nombre + "|" + coord);
        return true;
    }

    public void terminar() {
        if (fase == LOBBY || fase == FIN) return;
        fase = FIN;
        ganador = -1;
        anotar("terminada");
    }

    /** CPU: busca alrededor de los impactos sin hundir; si no hay, dispara en damero al azar. */
    public int objetivoCpu(Random r) {
        Jugador rival = js.get(1 - turno);
        int[] v = rival.recibidos;
        List<Integer> tocadas = new ArrayList<>();
        for (int c = 0; c < 100; c++) if (v[c] == TOCADO) {
            int b = barcoEn(rival, c);
            if (b >= 0 && !hundido(rival, b)) tocadas.add(c);
        }
        if (!tocadas.isEmpty()) {
            List<Integer> linea = new ArrayList<>(), cerca = new ArrayList<>();
            for (int c : tocadas) {
                int x = c % 10, y = c / 10;
                int[][] ds = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
                for (int[] d : ds) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || nx > 9 || ny < 0 || ny > 9 || v[ny * 10 + nx] != 0) continue;
                    int ox = x - d[0], oy = y - d[1];
                    boolean enLinea = ox >= 0 && ox <= 9 && oy >= 0 && oy <= 9 && tocadas.contains(oy * 10 + ox);
                    (enLinea ? linea : cerca).add(ny * 10 + nx);
                }
            }
            List<Integer> l = !linea.isEmpty() ? linea : cerca;
            if (!l.isEmpty()) return l.get(r.nextInt(l.size()));
        }
        List<Integer> libres = new ArrayList<>(), damero = new ArrayList<>();
        for (int c = 0; c < 100; c++) {
            if (v[c] != 0) continue;
            // no disparar junto a barcos ya hundidos (no pueden tocarse)
            boolean junto = false;
            for (int dy = -1; dy <= 1 && !junto; dy++)
                for (int dx = -1; dx <= 1; dx++) {
                    int x = c % 10 + dx, y = c / 10 + dy;
                    if (x < 0 || x > 9 || y < 0 || y > 9) continue;
                    int b = barcoEn(rival, y * 10 + x);
                    if (v[y * 10 + x] == TOCADO && b >= 0 && hundido(rival, b)) junto = true;
                }
            if (junto) continue;
            libres.add(c);
            if ((c % 10 + c / 10) % 2 == 0) damero.add(c);
        }
        List<Integer> l = !damero.isEmpty() ? damero : libres;
        if (l.isEmpty()) for (int c = 0; c < 100; c++) if (v[c] == 0) l.add(c);
        return l.isEmpty() ? -1 : l.get(r.nextInt(l.size()));
    }

    // ------------------------------------------------------------------ guardar
    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Jugador j : js) {
            CompoundTag c = new CompoundTag();
            c.putString("n", j.nombre);
            if (j.uuid != null) c.putUUID("u", j.uuid);
            c.putBoolean("l", j.listo);
            int[] b = new int[TAM.length * 3];
            for (int i = 0; i < TAM.length; i++) System.arraycopy(j.barcos[i], 0, b, i * 3, 3);
            c.putIntArray("b", b);
            c.putIntArray("r", j.recibidos);
            c.putInt("s", j.sel);
            l.add(c);
        }
        t.put("js", l);
        t.putIntArray("e", new int[]{fase, turno, ganador, ultTirador, ultCelda, ultResultado, ultHundido});
        t.putLong("tD", tDisparo);
        t.putLong("tL", tLibre);
        ListTag lg = new ListTag();
        for (String s : log) lg.add(StringTag.valueOf(s));
        t.put("log", lg);
        return t;
    }

    /** Barco escondido: fuera del mar, para que el cliente no sepa dónde está. */
    private static final int[] OCULTO = {-20, -20, 1};

    /**
     * Lo que pueden ver todos: solo los barcos ya hundidos (y todos al acabar). Los barcos propios de cada jugador le
     * llegan aparte con {@link #privado(int)}.
     */
    public CompoundTag guardarPublico() {
        CompoundTag t = guardar();
        if (fase == FIN) return t;
        ListTag l = t.getList("js", Tag.TAG_COMPOUND);
        for (int k = 0; k < l.size() && k < js.size(); k++) {
            Jugador j = js.get(k);
            int[] b = new int[TAM.length * 3];
            for (int i = 0; i < TAM.length; i++) System.arraycopy(hundido(j, i) ? j.barcos[i] : OCULTO, 0, b, i * 3, 3);
            l.getCompound(k).putIntArray("b", b);
        }
        return t;
    }

    /** Lo que solo ve el jugador i: dónde están sus barcos. */
    public CompoundTag privado(int i) {
        CompoundTag t = new CompoundTag();
        Jugador j = js.get(i);
        if (j.uuid != null) t.putUUID("u", j.uuid);
        t.putInt("i", i);
        int[] b = new int[TAM.length * 3];
        for (int k = 0; k < TAM.length; k++) System.arraycopy(j.barcos[k], 0, b, k * 3, 3);
        t.putIntArray("b", b);
        return t;
    }

    /** En el cliente: pone los barcos propios recibidos aparte (si siguen correspondiendo a esta partida). */
    public void aplicarPrivado(CompoundTag t) {
        int i = t.getInt("i");
        if (i < 0 || i >= js.size() || !t.hasUUID("u")) return;
        Jugador j = js.get(i);
        if (j.uuid == null || !j.uuid.equals(t.getUUID("u"))) return;
        int[] b = t.getIntArray("b");
        if (b.length == TAM.length * 3) for (int k = 0; k < TAM.length; k++) System.arraycopy(b, k * 3, j.barcos[k], 0, 3);
    }

    public List<UUID> humanos() {
        List<UUID> l = new ArrayList<>();
        for (Jugador j : js) if (j.uuid != null) l.add(j.uuid);
        return l;
    }

    public static FlotaPartida cargar(CompoundTag t) {
        FlotaPartida p = new FlotaPartida();
        ListTag lj = t.getList("js", Tag.TAG_COMPOUND);
        for (int i = 0; i < lj.size(); i++) {
            CompoundTag c = lj.getCompound(i);
            Jugador j = new Jugador();
            j.nombre = c.getString("n");
            if (c.hasUUID("u")) j.uuid = c.getUUID("u");
            j.listo = c.getBoolean("l");
            int[] b = c.getIntArray("b");
            if (b.length == TAM.length * 3) for (int k = 0; k < TAM.length; k++) System.arraycopy(b, k * 3, j.barcos[k], 0, 3);
            int[] r = c.getIntArray("r");
            if (r.length == 100) System.arraycopy(r, 0, j.recibidos, 0, 100);
            j.sel = c.getInt("s");
            p.js.add(j);
        }
        int[] e = t.getIntArray("e");
        if (e.length >= 7) {
            p.fase = e[0]; p.turno = e[1]; p.ganador = e[2]; p.ultTirador = e[3]; p.ultCelda = e[4]; p.ultResultado = e[5]; p.ultHundido = e[6];
        }
        p.tDisparo = t.getLong("tD");
        p.tLibre = t.getLong("tL");
        ListTag lg = t.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < lg.size(); i++) p.log.add(lg.getString(i));
        if (p.js.size() < 2 && p.fase != LOBBY) p.fase = LOBBY;
        return p;
    }
}
