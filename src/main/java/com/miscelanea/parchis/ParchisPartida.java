package com.miscelanea.parchis;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Reglas del parchís (las de toda la vida): sale con 5, el 6 repite (y vale 7 sin fichas en casa), tres seises mandan a casa,
 * comer da 20, llegar a la meta da 10, barreras que no se pueden saltar y que hay que abrir con un 6, seguros y pasillo de color.
 * Progreso de cada ficha: -1 en casa, 0..63 por el recorrido (0 = su salida), 64..70 su pasillo y 71 la meta.
 */
public class ParchisPartida {
    public static final int LOBBY = 0, TIRAR = 1, ELEGIR = 2, MOVER = 3, FIN = 4;
    public static final int CASA = -1, PASILLO = 64, META = 71;
    public static final int[] ORDEN_UNION = {0, 2, 1, 3};
    public static final int PASO = 4;            // ticks por casilla al animar (velocidad normal)
    private static final int[] PASO_V = {6, 4, 2}, CPU_V = {28, 16, 7}, DADO_V = {24, 18, 10};

    public static final class Jugador {
        public String nombre = "";
        public UUID uuid;
        public int color;

        public boolean cpu() {
            return uuid == null;
        }
    }

    public final List<Jugador> js = new ArrayList<>();
    public final int[][] prog = new int[4][4];
    public final long[][] llegada = new long[4][4];
    public int fase = LOBBY, turno, dado = 1, valor = 1, seises, ganador = -1;
    /** 0 lenta, 1 normal, 2 rápida. */
    public int velocidad = 1;

    public int paso() {
        return PASO_V[Math.floorMod(velocidad, 3)];
    }

    public int esperaCpu() {
        return CPU_V[Math.floorMod(velocidad, 3)];
    }

    /** Ticks que rueda el dado antes de poder mover. */
    public int dadoTicks() {
        return DADO_V[Math.floorMod(velocidad, 3)];
    }

    public void cambiarVelocidad() {
        velocidad = (velocidad + 1) % 3;
    }
    public boolean repetir, esBono;
    public final List<Integer> bonos = new ArrayList<>();
    public long tDado, tMover, tFin, contador;
    // animación del último movimiento
    public int movColor = -1, movFicha, movDesde, movHasta, movPaso = PASO;
    public int capColor = -1, capFicha, capDesde;
    public long tCap;
    public int ultColor = -1, ultFicha = -1;
    public final List<String> log = new ArrayList<>();

    public ParchisPartida() {
        for (int[] p : prog) java.util.Arrays.fill(p, CASA);
    }

    public Jugador actual() {
        return js.isEmpty() ? null : js.get(turno);
    }

    public int colorActual() {
        return js.isEmpty() ? -1 : js.get(turno).color;
    }

    public boolean enJuego(int color) {
        for (Jugador j : js) if (j.color == color) return true;
        return false;
    }

    public Jugador deColor(int color) {
        for (Jugador j : js) if (j.color == color) return j;
        return null;
    }

    void anotar(String s) {
        log.add(s);
        while (log.size() > 6) log.remove(0);
    }

    // ------------------------------------------------------------------ geometría lógica
    /** Casilla del recorrido común (0..67) para el progreso p (0..63) de un color. */
    public static int casilla(int color, int p) {
        return (17 * color + 5 + p) % 68;
    }

    public static boolean seguro(int g) {
        int m = g % 17;
        return m == 0 || m == 5 || m == 12;
    }

    /** Clave de ocupación: casillas del recorrido 0..67, pasillos 100+, meta 200+ y casa -1. */
    public static int clave(int color, int p) {
        if (p < 0) return -1;
        if (p < PASILLO) return casilla(color, p);
        if (p < META) return 100 + color * 10 + (p - PASILLO);
        return 200 + color;
    }

    /** Fichas (color*4+ficha) que hay en una clave, por orden de llegada. */
    public List<Integer> en(int clave) {
        List<Integer> l = new ArrayList<>();
        if (clave < 0) return l;
        for (int c = 0; c < 4; c++) {
            if (!enJuego(c)) continue;
            for (int f = 0; f < 4; f++) if (clave(c, prog[c][f]) == clave) l.add(c * 4 + f);
        }
        l.sort((a, b) -> Long.compare(llegada[a / 4][a % 4], llegada[b / 4][b % 4]));
        return l;
    }

    private boolean barrera(int clave) {
        if (clave < 0 || clave >= 200) return false;
        List<Integer> l = en(clave);
        return l.size() >= 2 && l.get(0) / 4 == l.get(1) / 4;
    }

    public int enCasa(int color) {
        int n = 0;
        for (int f = 0; f < 4; f++) if (prog[color][f] == CASA) n++;
        return n;
    }

    /** Destino de mover la ficha d casillas, o -2 si no puede. */
    public int destino(int c, int f, int d) {
        int p = prog[c][f];
        if (p == META) return -2;
        if (p == CASA) {
            if (d != 5) return -2;
            List<Integer> l = en(clave(c, 0));
            if (l.size() >= 2 && l.get(0) / 4 == c && l.get(1) / 4 == c) return -2;
            return 0;
        }
        int n = p + d;
        if (n > META) return -2;
        for (int s = p + 1; s < n; s++) if (barrera(clave(c, s))) return -2;
        if (n == META) return n;
        if (en(clave(c, n)).size() >= 2) return -2;
        return n;
    }

    /** Ficha que se comería al llegar (color*4+ficha) o -1. */
    public int victima(int c, int desde, int hasta) {
        if (hasta >= PASILLO) return -1;
        List<Integer> l = en(clave(c, hasta));
        int g = casilla(c, hasta);
        if (desde == CASA) {
            if (l.size() < 2) return -1;
            for (int k = l.size() - 1; k >= 0; k--) if (l.get(k) / 4 != c) return l.get(k);
            return -1;
        }
        if (l.size() == 1 && l.get(0) / 4 != c && !seguro(g)) return l.get(0);
        return -1;
    }

    /** Fichas que puede mover el color con el valor actual (aplicando las obligaciones). */
    public List<Integer> opciones(int c, int d, boolean bono) {
        List<Integer> o = new ArrayList<>();
        if (!bono && d == 5 && enCasa(c) > 0) {
            for (int f = 0; f < 4; f++) if (prog[c][f] == CASA && destino(c, f, 5) == 0) {
                o.add(f);
                return o;   // salir es obligatorio
            }
        }
        for (int f = 0; f < 4; f++) {
            if (prog[c][f] == CASA) continue;
            if (destino(c, f, d) >= 0) o.add(f);
        }
        if (!bono && dado == 6) {
            // con un seis hay que abrir barrera si se puede
            List<Integer> abre = new ArrayList<>();
            for (int f : o) {
                int p = prog[c][f];
                if (p >= 0 && p < META && barrera(clave(c, p))) abre.add(f);
            }
            if (!abre.isEmpty()) return abre;
        }
        return o;
    }

    // ------------------------------------------------------------------ preparar
    public boolean unir(UUID u, String nombre) {
        if (fase != LOBBY || js.size() >= 4) return false;
        for (Jugador j : js) if (u.equals(j.uuid)) return false;
        Jugador j = new Jugador();
        j.uuid = u;
        j.nombre = nombre;
        j.color = colorLibre();
        js.add(j);
        anotar("unido|" + nombre);
        return true;
    }

    /**
     * Sentarse en un color concreto (clic en su casa durante la preparación). Si ya estaba sentado, cambia de color;
     * si el color lo tenía una CPU, la CPU se va.
     */
    public boolean unirColor(UUID u, String nombre, int color) {
        if (fase != LOBBY || color < 0 || color > 3) return false;
        Jugador ocupa = deColor(color);
        if (ocupa != null && !ocupa.cpu()) return false;
        if (ocupa != null) js.remove(ocupa);
        for (Jugador j : js) if (u.equals(j.uuid)) {
            j.color = color;
            return true;
        }
        if (js.size() >= 4) return false;
        Jugador j = new Jugador();
        j.uuid = u;
        j.nombre = nombre;
        j.color = color;
        js.add(j);
        anotar("unido|" + nombre);
        return true;
    }

    /** Otra partida con los mismos jugadores y colores (revancha). */
    public ParchisPartida revancha(Random r) {
        ParchisPartida n = new ParchisPartida();
        n.velocidad = velocidad;
        for (Jugador j : js) {
            Jugador c = new Jugador();
            c.uuid = j.uuid;
            c.nombre = j.nombre;
            c.color = j.color;
            n.js.add(c);
        }
        n.empezar(r);
        return n;
    }

    /** Deja la partida mientras se prepara. Si ya no queda ningún humano, se quitan también las CPU. */
    public boolean salir(UUID u) {
        if (fase != LOBBY) return false;
        Jugador fuera = null;
        for (Jugador j : js) if (u.equals(j.uuid)) fuera = j;
        if (fuera == null) return false;
        js.remove(fuera);
        anotar("sale|" + fuera.nombre);
        boolean humanos = false;
        for (Jugador j : js) if (!j.cpu()) humanos = true;
        if (!humanos) js.clear();
        turno = 0;
        return true;
    }

    /** Humanos de la partida (para votar terminar). */
    public List<UUID> humanos() {
        List<UUID> l = new ArrayList<>();
        for (Jugador j : js) if (j.uuid != null) l.add(j.uuid);
        return l;
    }

    public boolean anadirCpu() {
        if (fase != LOBBY || js.size() >= 4) return false;
        Jugador j = new Jugador();
        j.color = colorLibre();
        j.nombre = "CPU " + (js.size() + 1);
        js.add(j);
        anotar("unido|" + j.nombre);
        return true;
    }

    private int colorLibre() {
        for (int c : ORDEN_UNION) if (!enJuego(c)) return c;
        return 0;
    }

    public boolean empezar(Random r) {
        if (fase != LOBBY || js.size() < 2) return false;
        js.sort((a, b) -> Integer.compare(a.color, b.color));
        turno = r.nextInt(js.size());
        fase = TIRAR;
        anotar("empieza|" + actual().nombre);
        return true;
    }

    // ------------------------------------------------------------------ turno
    public void tirar(Random r, long ahora) {
        if (fase != TIRAR) return;
        int c = colorActual();
        dado = 1 + r.nextInt(6);
        tDado = ahora;
        esBono = false;
        repetir = false;
        if (dado == 6) {
            if (++seises == 3) {
                seises = 0;
                anotar("tres_seises|" + actual().nombre);
                if (ultColor == c && ultFicha >= 0 && prog[c][ultFicha] >= 0 && prog[c][ultFicha] < PASILLO) {
                    capColor = c;
                    capFicha = ultFicha;
                    capDesde = prog[c][ultFicha];
                    tCap = ahora + dadoTicks();
                    prog[c][ultFicha] = CASA;
                    movColor = -1;
                }
                fase = MOVER;
                tFin = ahora + 40;
                return;
            }
            repetir = true;
        } else seises = 0;
        valor = dado == 6 && enCasa(c) == 0 ? 7 : dado;
        prepararEleccion(ahora);
    }

    private void prepararEleccion(long ahora) {
        int c = colorActual();
        if (opciones(c, valor, esBono).isEmpty()) {
            anotar((esBono ? "pierde_bono|" : "no_puede|") + actual().nombre + "|" + valor);
            movColor = -1;
            fase = MOVER;
            tFin = ahora + (esBono ? 20 : 30) * paso() / PASO;
        } else fase = ELEGIR;
    }

    public boolean mover(int f, long ahora) {
        if (fase != ELEGIR) return false;
        int c = colorActual();
        if (!opciones(c, valor, esBono).contains(f)) return false;
        int desde = prog[c][f], hasta = destino(c, f, valor);
        int v = victima(c, desde, hasta);
        prog[c][f] = hasta;
        llegada[c][f] = ++contador;
        movColor = c;
        movFicha = f;
        movDesde = desde;
        movHasta = hasta;
        tMover = ahora;
        int pasos = desde == CASA ? 2 : hasta - desde;
        movPaso = pasos > 10 ? Math.max(1, paso() / 2) : paso();
        tFin = ahora + (long) pasos * movPaso + 4;
        ultColor = c;
        ultFicha = f;
        capColor = -1;
        if (v >= 0) {
            capColor = v / 4;
            capFicha = v % 4;
            capDesde = prog[capColor][capFicha];
            prog[capColor][capFicha] = CASA;
            tCap = tFin - 2;
            tFin += 18;
            bonos.add(20);
            anotar("come|" + actual().nombre + "|" + deColor(capColor).nombre);
            com.miscelanea.logros.LogrosMiscelanea.dar(actual().uuid, "parchis_come");
        }
        if (hasta == META) {
            bonos.add(10);
            anotar("meta|" + actual().nombre);
        }
        fase = MOVER;
        return true;
    }

    /** Avanza el tiempo: termina animaciones y decide lo siguiente. Devuelve true si cambió algo. */
    public boolean tick(long ahora) {
        if (fase != MOVER || ahora < tFin) return false;
        int c = colorActual();
        boolean todas = true;
        for (int f = 0; f < 4; f++) if (prog[c][f] != META) todas = false;
        if (todas) {
            ganador = c;
            fase = FIN;
            anotar("gana|" + actual().nombre);
            return true;
        }
        while (!bonos.isEmpty()) {
            valor = bonos.remove(0);
            esBono = true;
            if (!opciones(c, valor, true).isEmpty()) {
                fase = ELEGIR;
                tDado = ahora;
                return true;
            }
            anotar("pierde_bono|" + actual().nombre + "|" + valor);
        }
        esBono = false;
        if (repetir) {
            repetir = false;
            fase = TIRAR;
            return true;
        }
        siguiente();
        return true;
    }

    private void siguiente() {
        seises = 0;
        turno = (turno + 1) % js.size();
        fase = TIRAR;
    }

    public void terminar() {
        if (fase == LOBBY || fase == FIN) return;
        fase = FIN;
        ganador = -1;
        anotar("terminada");
    }

    // ------------------------------------------------------------------ CPU
    private boolean amenazada(int c, int p) {
        if (p < 0 || p >= PASILLO) return false;
        int g = casilla(c, p);
        if (seguro(g)) return false;
        for (int o = 0; o < 4; o++) {
            if (o == c || !enJuego(o)) continue;
            for (int f = 0; f < 4; f++) {
                int q = prog[o][f];
                if (q < 0 || q >= PASILLO) continue;
                int go = casilla(o, q);
                int dist = Math.floorMod(g - go, 68);
                // solo puede alcanzarla si no entra antes en su pasillo
                if (dist >= 1 && dist <= 7 && q + dist < PASILLO) return true;
            }
        }
        return false;
    }

    public int elegirCpu(Random r) {
        int c = colorActual();
        List<Integer> o = opciones(c, valor, esBono);
        if (o.isEmpty()) return -1;
        int mejor = o.get(0);
        double mp = -1e9;
        for (int f : o) {
            int desde = prog[c][f], hasta = destino(c, f, valor);
            double s = r.nextDouble() * 3;
            if (victima(c, desde, hasta) >= 0) s += 120;
            if (hasta == META) s += 90;
            if (desde == CASA) s += 60;
            if (hasta >= PASILLO && desde < PASILLO) s += 45;
            if (amenazada(c, desde)) s += 35;
            int prev = prog[c][f];
            prog[c][f] = hasta;
            if (amenazada(c, hasta)) s -= 40;
            prog[c][f] = prev;
            if (hasta < PASILLO && seguro(casilla(c, hasta))) s += 18;
            if (en(clave(c, hasta)).size() == 1 && hasta < META && en(clave(c, hasta)).get(0) / 4 == c) s += 10;
            s += hasta * 0.4;
            if (s > mp) {
                mp = s;
                mejor = f;
            }
        }
        return mejor;
    }

    /** Un paso de la CPU (o del jugador cuando solo hay una opción). */
    public boolean cpu(Random r, long ahora) {
        if (fase == TIRAR) {
            tirar(r, ahora);
            return true;
        }
        if (fase == ELEGIR && ahora - tDado >= dadoTicks()) {
            int f = elegirCpu(r);
            return f >= 0 && mover(f, ahora);
        }
        return false;
    }

    /** Si el jugador humano solo tiene una ficha que mover, se mueve sola. */
    public boolean automatico(long ahora) {
        if (fase != ELEGIR || ahora - tDado < dadoTicks() + 2) return false;
        List<Integer> o = opciones(colorActual(), valor, esBono);
        if (o.size() == 1) return mover(o.get(0), ahora);
        // todas las opciones iguales (p. ej. varias fichas en la misma casilla)
        if (o.size() > 1) {
            int p0 = prog[colorActual()][o.get(0)];
            for (int f : o) if (prog[colorActual()][f] != p0) return false;
            return mover(o.get(0), ahora);
        }
        return false;
    }

    // ------------------------------------------------------------------ guardar
    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Jugador j : js) {
            CompoundTag c = new CompoundTag();
            c.putString("n", j.nombre);
            if (j.uuid != null) c.putUUID("u", j.uuid);
            c.putInt("c", j.color);
            l.add(c);
        }
        t.put("js", l);
        int[] pr = new int[16];
        for (int i = 0; i < 16; i++) pr[i] = prog[i / 4][i % 4];
        t.putIntArray("prog", pr);
        int[] ll = new int[16];
        for (int i = 0; i < 16; i++) ll[i] = (int) llegada[i / 4][i % 4];
        t.putIntArray("lleg", ll);
        t.putInt("fase", fase);
        t.putInt("turno", turno);
        t.putInt("dado", dado);
        t.putInt("valor", valor);
        t.putInt("seises", seises);
        t.putInt("ganador", ganador);
        t.putInt("vel", velocidad);
        t.putBoolean("rep", repetir);
        t.putBoolean("bono", esBono);
        t.putIntArray("bonos", bonos.stream().mapToInt(Integer::intValue).toArray());
        t.putLong("tDado", tDado);
        t.putLong("tMover", tMover);
        t.putLong("tFin", tFin);
        t.putLong("tCap", tCap);
        t.putLong("cont", contador);
        t.putIntArray("mov", new int[]{movColor, movFicha, movDesde, movHasta, movPaso, capColor, capFicha, capDesde, ultColor, ultFicha});
        ListTag lg = new ListTag();
        for (String s : log) lg.add(StringTag.valueOf(s));
        t.put("log", lg);
        return t;
    }

    public static ParchisPartida cargar(CompoundTag t) {
        ParchisPartida p = new ParchisPartida();
        ListTag lj = t.getList("js", Tag.TAG_COMPOUND);
        for (int i = 0; i < lj.size(); i++) {
            CompoundTag c = lj.getCompound(i);
            Jugador j = new Jugador();
            j.nombre = c.getString("n");
            if (c.hasUUID("u")) j.uuid = c.getUUID("u");
            j.color = c.getInt("c");
            p.js.add(j);
        }
        int[] pr = t.getIntArray("prog");
        if (pr.length == 16) for (int i = 0; i < 16; i++) p.prog[i / 4][i % 4] = pr[i];
        int[] ll = t.getIntArray("lleg");
        if (ll.length == 16) for (int i = 0; i < 16; i++) p.llegada[i / 4][i % 4] = ll[i];
        p.fase = t.getInt("fase");
        p.turno = p.js.isEmpty() ? 0 : Math.min(t.getInt("turno"), p.js.size() - 1);
        p.dado = Math.max(1, t.getInt("dado"));
        p.valor = Math.max(1, t.getInt("valor"));
        p.seises = t.getInt("seises");
        p.ganador = t.getInt("ganador");
        p.velocidad = t.contains("vel") ? t.getInt("vel") : 1;
        p.repetir = t.getBoolean("rep");
        p.esBono = t.getBoolean("bono");
        for (int b : t.getIntArray("bonos")) p.bonos.add(b);
        p.tDado = t.getLong("tDado");
        p.tMover = t.getLong("tMover");
        p.tFin = t.getLong("tFin");
        p.tCap = t.getLong("tCap");
        p.contador = t.getLong("cont");
        int[] m = t.getIntArray("mov");
        if (m.length == 10) {
            p.movColor = m[0]; p.movFicha = m[1]; p.movDesde = m[2]; p.movHasta = m[3]; p.movPaso = Math.max(1, m[4]);
            p.capColor = m[5]; p.capFicha = m[6]; p.capDesde = m[7]; p.ultColor = m[8]; p.ultFicha = m[9];
        }
        ListTag lg = t.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < lg.size(); i++) p.log.add(lg.getString(i));
        if (p.js.size() < 2 && p.fase != LOBBY) p.fase = LOBBY;
        return p;
    }
}
