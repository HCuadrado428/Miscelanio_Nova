package com.miscelanea.oca;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Juego de la Oca (reglas tradicionales, un dado): de oca a oca y tiro porque me toca, puentes, dados, posada, pozo,
 * laberinto, cárcel y la muerte. Hay que entrar en el jardín (63) con la cuenta exacta; lo que sobra se retrocede.
 */
public class OcaPartida {
    public static final int LOBBY = 0, TIRAR = 1, MOVER = 2, FIN = 3;
    public static final int META = 63;
    public static final int[] OCAS = {5, 9, 14, 18, 23, 27, 32, 36, 41, 45, 50, 54, 59};
    public static final int PUENTE_A = 6, PUENTE_B = 12, POSADA = 19, DADOS_A = 26, DADOS_B = 53, POZO = 31, LABERINTO = 42, CARCEL = 52, MUERTE = 58;
    private static final int[] PASO_V = {7, 5, 3}, CPU_V = {28, 16, 7}, DADO_V = {24, 18, 10};

    public static final class Jugador {
        public String nombre = "";
        public UUID uuid;
        public int color, pos, castigo;

        public boolean cpu() {
            return uuid == null;
        }
    }

    public final List<Jugador> js = new ArrayList<>();
    public int fase = LOBBY, turno, dado = 1, ganador = -1, velocidad = 1;
    public boolean otraVez;
    public long tDado, tMover, tSalto, tFin;
    /** Animación: quién se mueve, camino casilla a casilla (con rebote) y salto final (-1 si no hay). */
    public int movJ = -1, saltoDesde = -1, saltoA = -1;
    public int[] camino = new int[0];
    public String casillaEspecial = "";
    public final List<String> log = new ArrayList<>();

    public Jugador actual() {
        return js.isEmpty() ? null : js.get(turno);
    }

    public int paso() {
        return PASO_V[Math.floorMod(velocidad, 3)];
    }

    public int esperaCpu() {
        return CPU_V[Math.floorMod(velocidad, 3)];
    }

    public int dadoTicks() {
        return DADO_V[Math.floorMod(velocidad, 3)];
    }

    public void cambiarVelocidad() {
        velocidad = (velocidad + 1) % 3;
    }

    void anotar(String s) {
        log.add(s);
        while (log.size() > 6) log.remove(0);
    }

    public static boolean esOca(int c) {
        for (int o : OCAS) if (o == c) return true;
        return false;
    }

    /** Nombre de la casilla especial ("" si es normal). */
    public static String tipo(int c) {
        if (c == META) return "jardin";
        if (c == 0) return "salida";
        if (esOca(c)) return "oca";
        return switch (c) {
            case PUENTE_A, PUENTE_B -> "puente";
            case POSADA -> "posada";
            case DADOS_A, DADOS_B -> "dados";
            case POZO -> "pozo";
            case LABERINTO -> "laberinto";
            case CARCEL -> "carcel";
            case MUERTE -> "muerte";
            default -> "";
        };
    }

    // ------------------------------------------------------------------ preparar
    private int colorLibre() {
        for (int c = 0; c < 4; c++) {
            boolean usado = false;
            for (Jugador j : js) if (j.color == c) usado = true;
            if (!usado) return c;
        }
        return 0;
    }

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

    /** Deja la partida mientras se prepara (si no queda ningún humano, se van también las CPU). */
    public boolean salir(UUID u) {
        if (fase != LOBBY) return false;
        Jugador fuera = null;
        for (Jugador j : js) if (u.equals(j.uuid)) fuera = j;
        if (fuera == null) return false;
        js.remove(fuera);
        anotar("sale|" + fuera.nombre);
        if (js.stream().allMatch(Jugador::cpu)) js.clear();
        turno = 0;
        return true;
    }

    /** Otra partida con los mismos jugadores (revancha). */
    public OcaPartida revancha(Random r) {
        OcaPartida n = new OcaPartida();
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

    public boolean anadirCpu() {
        if (fase != LOBBY || js.size() >= 4) return false;
        Jugador j = new Jugador();
        j.color = colorLibre();
        j.nombre = "CPU " + (js.size() + 1);
        js.add(j);
        anotar("unido|" + j.nombre);
        return true;
    }

    public boolean empezar(Random r) {
        if (fase != LOBBY || js.size() < 2) return false;
        turno = r.nextInt(js.size());
        fase = TIRAR;
        anotar("empieza|" + actual().nombre);
        return true;
    }

    // ------------------------------------------------------------------ turno
    public void tirar(Random r, long ahora) {
        if (fase != TIRAR) return;
        Jugador j = actual();
        dado = 1 + r.nextInt(6);
        tDado = ahora;
        int desde = j.pos;
        List<Integer> cam = new ArrayList<>();
        cam.add(desde);
        int p = desde, dir = 1;
        for (int k = 0; k < dado; k++) {
            if (p == META) dir = -1;
            p += dir;
            cam.add(p);
        }
        camino = cam.stream().mapToInt(Integer::intValue).toArray();
        movJ = turno;
        tMover = ahora + dadoTicks();
        j.pos = p;
        otraVez = false;
        saltoDesde = saltoA = -1;
        casillaEspecial = tipo(p);
        int salto = -1;
        boolean rebote = desde + dado > META;
        switch (casillaEspecial) {
            case "oca" -> {
                if (rebote) {
                    // rebotando desde el jardín, la oca manda hacia atrás a la oca anterior (y se vuelve a tirar)
                    salto = OCAS[0];
                    for (int o : OCAS) if (o < p) salto = o;
                    otraVez = true;
                    anotar("oca_atras|" + j.nombre);
                    break;
                }
                salto = META;
                for (int o : OCAS) if (o > p) {
                    salto = o;
                    break;
                }
                otraVez = salto != META;
                anotar("oca|" + j.nombre);
            }
            case "puente" -> {
                salto = p == PUENTE_A ? PUENTE_B : PUENTE_A;
                otraVez = true;
                anotar("puente|" + j.nombre);
            }
            case "dados" -> {
                salto = p == DADOS_A ? DADOS_B : DADOS_A;
                otraVez = true;
                anotar("dados|" + j.nombre);
            }
            case "posada" -> {
                j.castigo = 1;
                anotar("posada|" + j.nombre);
            }
            case "pozo" -> {
                for (Jugador o : js) if (o != j && o.pos == POZO && o.castigo > 0) {
                    o.castigo = 0;
                    anotar("rescata|" + j.nombre + "|" + o.nombre);
                }
                j.castigo = 3;
                anotar("pozo|" + j.nombre);
            }
            case "laberinto" -> {
                salto = 30;
                anotar("laberinto|" + j.nombre);
            }
            case "carcel" -> {
                j.castigo = 3;
                anotar("carcel|" + j.nombre);
            }
            case "muerte" -> {
                salto = 1;
                anotar("muerte|" + j.nombre);
            }
            case "jardin" -> anotar("gana|" + j.nombre);
            default -> {
                if (rebote) anotar("rebota|" + j.nombre);
            }
        }
        if (salto == META) anotar("gana|" + j.nombre);
        long llegar = tMover + (long) dado * paso();
        tFin = llegar + 4;
        if (salto >= 0) {
            saltoDesde = p;
            saltoA = salto;
            j.pos = salto;
            tSalto = llegar + 3;
            tFin = tSalto + 14L * paso() / 5 + 6;
        }
        fase = MOVER;
    }

    public boolean tick(long ahora) {
        if (fase != MOVER || ahora < tFin) return false;
        Jugador j = actual();
        if (j.pos == META) {
            ganador = turno;
            fase = FIN;
            return true;
        }
        if (otraVez) {
            fase = TIRAR;
            return true;
        }
        siguiente();
        return true;
    }

    private void siguiente() {
        for (int g = 0; g < js.size() * 4 + 1; g++) {
            turno = (turno + 1) % js.size();
            Jugador j = js.get(turno);
            if (j.castigo <= 0) break;
            j.castigo--;
            anotar("pierde|" + j.nombre);
        }
        fase = TIRAR;
    }

    public void terminar() {
        if (fase == LOBBY || fase == FIN) return;
        fase = FIN;
        ganador = -1;
        anotar("terminada");
    }

    /** Tira por el jugador de turno aunque sea humano (cuando se ha ido de la mesa). */
    public boolean jugarPor(Random r, long ahora) {
        if (fase != TIRAR) return false;
        tirar(r, ahora);
        return true;
    }

    public List<UUID> humanos() {
        List<UUID> l = new ArrayList<>();
        for (Jugador j : js) if (j.uuid != null) l.add(j.uuid);
        return l;
    }

    public boolean cpu(Random r, long ahora) {
        if (fase == TIRAR && actual().cpu()) {
            tirar(r, ahora);
            return true;
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
            c.putInt("p", j.pos);
            c.putInt("k", j.castigo);
            l.add(c);
        }
        t.put("js", l);
        t.putIntArray("e", new int[]{fase, turno, dado, ganador, velocidad, otraVez ? 1 : 0, movJ, saltoDesde, saltoA});
        t.putIntArray("cam", camino);
        t.putLong("tDado", tDado);
        t.putLong("tMover", tMover);
        t.putLong("tSalto", tSalto);
        t.putLong("tFin", tFin);
        t.putString("esp", casillaEspecial);
        ListTag lg = new ListTag();
        for (String s : log) lg.add(StringTag.valueOf(s));
        t.put("log", lg);
        return t;
    }

    public static OcaPartida cargar(CompoundTag t) {
        OcaPartida p = new OcaPartida();
        ListTag lj = t.getList("js", Tag.TAG_COMPOUND);
        for (int i = 0; i < lj.size(); i++) {
            CompoundTag c = lj.getCompound(i);
            Jugador j = new Jugador();
            j.nombre = c.getString("n");
            if (c.hasUUID("u")) j.uuid = c.getUUID("u");
            j.color = c.getInt("c");
            j.pos = c.getInt("p");
            j.castigo = c.getInt("k");
            p.js.add(j);
        }
        int[] e = t.getIntArray("e");
        if (e.length >= 9) {
            p.fase = e[0]; p.turno = e[1]; p.dado = Math.max(1, e[2]); p.ganador = e[3]; p.velocidad = e[4];
            p.otraVez = e[5] == 1; p.movJ = e[6]; p.saltoDesde = e[7]; p.saltoA = e[8];
        }
        if (!p.js.isEmpty()) p.turno = Math.min(p.turno, p.js.size() - 1);
        p.camino = t.getIntArray("cam");
        p.tDado = t.getLong("tDado");
        p.tMover = t.getLong("tMover");
        p.tSalto = t.getLong("tSalto");
        p.tFin = t.getLong("tFin");
        p.casillaEspecial = t.getString("esp");
        ListTag lg = t.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < lg.size(); i++) p.log.add(lg.getString(i));
        if (p.js.size() < 2 && p.fase != LOBBY) p.fase = LOBBY;
        return p;
    }
}
