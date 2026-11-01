package com.miscelanea.imperio;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Reglas del juego de propiedades. El servidor la ejecuta; el cliente recibe una copia para dibujar.
 * Fases: LOBBY, TIRAR, MOVER (animación), COMPRAR, GESTION (construir/hipotecar/terminar), DEUDA, FIN.
 */
public class Partida {
    public static final int LOBBY = 0, TIRAR = 1, MOVER = 2, COMPRAR = 3, GESTION = 4, DEUDA = 5, FIN = 6;
    public static final int INICIAL = 1500, SALARIO = 200, MULTA = 50;
    public static final int MAX_JUGADORES = 4;

    public static final class Jugador {
        public String nombre = "";
        public UUID uuid;          // null = CPU
        public int ficha, dinero = INICIAL, pos, carcel, libre;
        public boolean fuera;

        public boolean cpu() {
            return uuid == null;
        }
    }

    public final List<Jugador> js = new ArrayList<>();
    public final int[] dueno = new int[40], casas = new int[40];
    public final boolean[] hipo = new boolean[40];
    public static final int[] LIMITES = {20, 30, 50, 0};
    public int ronda, limite = 1, primero;
    /** 0 lenta, 1 normal, 2 rápida: espera de la CPU, retardo del dado y ritmo de las fichas. */
    public int velocidad = 1;
    private static final int[] PASO_V = {7, 5, 3}, DADO_V = {28, 22, 12}, CARTA_V = {36, 30, 18}, CPU_V = {28, 16, 7};

    public int paso() {
        return PASO_V[Math.floorMod(velocidad, 3)];
    }

    public int esperaCpu() {
        return CPU_V[Math.floorMod(velocidad, 3)];
    }

    /** Ticks que rueda el dado antes de que se mueva la ficha. */
    public int dadoTicks() {
        return DADO_V[Math.floorMod(velocidad, 3)] - 2;
    }

    public void cambiarVelocidad() {
        velocidad = (velocidad + 1) % 3;
    }
    public int fase = LOBBY, turno, d1 = 1, d2 = 1, dobles, sel = -1, acreedor = -1;
    public long tDados, tMover;
    public int movDesde, movPasos;
    public boolean movAtras, tiroOtraVez;
    public String carta = "";
    public long tCarta;
    public final List<String> log = new ArrayList<>();
    private final int[] mazoCofre = new int[12], mazoDestino = new int[12];
    private int iCofre, iDestino;

    public Partida() {
        java.util.Arrays.fill(dueno, -1);
        for (int i = 0; i < 12; i++) {
            mazoCofre[i] = i;
            mazoDestino[i] = i;
        }
    }

    public Jugador actual() {
        return js.isEmpty() ? null : js.get(turno);
    }

    public int activos() {
        int n = 0;
        for (Jugador j : js) if (!j.fuera) n++;
        return n;
    }

    void anotar(String s) {
        log.add(s);
        while (log.size() > 6) log.remove(0);
    }

    // ------------------------------------------------------------------ preparar
    public boolean unir(UUID u, String nombre) {
        if (fase != LOBBY || js.size() >= MAX_JUGADORES) return false;
        for (Jugador j : js) if (u.equals(j.uuid)) return false;
        Jugador j = new Jugador();
        j.uuid = u;
        j.nombre = nombre;
        j.ficha = fichaLibre();
        js.add(j);
        anotar("unido|" + nombre);
        return true;
    }

    public boolean anadirCpu() {
        if (fase != LOBBY || js.size() >= MAX_JUGADORES) return false;
        Jugador j = new Jugador();
        j.nombre = "CPU " + (js.size() + 1);
        j.ficha = fichaLibre();
        js.add(j);
        anotar("unido|" + j.nombre);
        return true;
    }

    private int fichaLibre() {
        for (int f = 0; f < 6; f++) {
            boolean usada = false;
            for (Jugador j : js) if (j.ficha == f) usada = true;
            if (!usada) return f;
        }
        return 0;
    }

    public boolean empezar(Random r) {
        if (fase != LOBBY || js.size() < 2) return false;
        barajar(mazoCofre, r);
        barajar(mazoDestino, r);
        turno = r.nextInt(js.size());
        primero = turno;
        ronda = 1;
        fase = TIRAR;
        anotar("empieza|" + actual().nombre);
        return true;
    }

    private static void barajar(int[] m, Random r) {
        for (int i = m.length - 1; i > 0; i--) {
            int k = r.nextInt(i + 1), t = m[i];
            m[i] = m[k];
            m[k] = t;
        }
    }

    // ------------------------------------------------------------------ turno
    public void tirar(Random r, long ahora) {
        if (fase != TIRAR) return;
        Jugador j = actual();
        d1 = 1 + r.nextInt(6);
        d2 = 1 + r.nextInt(6);
        tDados = ahora;
        boolean doble = d1 == d2;
        tiroOtraVez = false;
        if (j.carcel > 0) {
            if (doble) {
                j.carcel = 0;
                anotar("sale_dobles|" + j.nombre);
            } else if (++j.carcel > 3) {
                j.carcel = 0;
                pagar(turno, MULTA, -1);
                anotar("sale_paga|" + j.nombre);
            } else {
                anotar("sigue_carcel|" + j.nombre);
                fase = GESTION;
                return;
            }
        } else if (doble) {
            if (++dobles >= 3) {
                aCarcel(j);
                anotar("tres_dobles|" + j.nombre);
                fase = GESTION;
                return;
            }
            tiroOtraVez = true;
        }
        mover(j.pos, d1 + d2, false, ahora + DADO_V[Math.floorMod(velocidad, 3)]);
    }

    private void mover(int desde, int pasos, boolean atras, long inicio) {
        movDesde = desde;
        movPasos = pasos;
        movAtras = atras;
        tMover = inicio;
        fase = MOVER;
    }

    /** Llamar cada tick en el servidor: termina el movimiento cuando acaba la animación. */
    public boolean tick(long ahora, Random r) {
        if (fase == MOVER && ahora >= tMover + (long) movPasos * paso() + 4) {
            Jugador j = actual();
            int dest = movAtras ? Math.floorMod(movDesde - movPasos, 40) : (movDesde + movPasos) % 40;
            if (!movAtras && movDesde + movPasos >= 40) {
                j.dinero += SALARIO;
                anotar("salida|" + j.nombre);
            }
            j.pos = dest;
            fase = GESTION;
            aterrizar(r, ahora);
            if (fase != MOVER && j.dinero < 0) fase = DEUDA;
            return true;
        }
        return false;
    }

    private void aterrizar(Random r, long ahora) {
        Jugador j = actual();
        int c = j.pos;
        switch (Casillas.tipo(c)) {
            case Casillas.PROP, Casillas.ESTACION, Casillas.UTIL -> {
                int d = dueno[c];
                if (d < 0) {
                    if (j.dinero >= Casillas.precio(c)) fase = COMPRAR;
                    else anotar("no_llega|" + j.nombre);
                } else if (d != turno && !hipo[c] && !js.get(d).fuera) {
                    int a = alquiler(c);
                    anotar("alquiler|" + j.nombre + "|" + a + "|" + js.get(d).nombre);
                    pagar(turno, a, d);
                }
            }
            case Casillas.IMPUESTO -> {
                anotar("impuesto|" + j.nombre + "|" + Casillas.precio(c));
                pagar(turno, Casillas.precio(c), -1);
            }
            case Casillas.COFRE -> carta(true, r, ahora);
            case Casillas.DESTINO -> carta(false, r, ahora);
            case Casillas.IR_CARCEL -> {
                aCarcel(j);
                anotar("a_carcel|" + j.nombre);
            }
            default -> {}
        }
    }

    public int alquiler(int c) {
        int d = dueno[c];
        if (d < 0) return 0;
        switch (Casillas.tipo(c)) {
            case Casillas.ESTACION -> {
                int n = 0;
                for (int i = 0; i < 40; i++) if (Casillas.tipo(i) == Casillas.ESTACION && dueno[i] == d) n++;
                return 25 << (n - 1);
            }
            case Casillas.UTIL -> {
                int n = 0;
                for (int i = 0; i < 40; i++) if (Casillas.tipo(i) == Casillas.UTIL && dueno[i] == d) n++;
                return (d1 + d2) * (n == 2 ? 10 : 4);
            }
            default -> {
                int a = Casillas.alquilerCalle(c, casas[c]);
                if (casas[c] == 0 && grupoCompleto(d, Casillas.grupo(c))) a *= 2;
                return a;
            }
        }
    }

    public boolean grupoCompleto(int jugador, int grupo) {
        if (grupo < 0) return false;
        for (int i = 0; i < 40; i++) if (Casillas.grupo(i) == grupo && dueno[i] != jugador) return false;
        return true;
    }

    private void aCarcel(Jugador j) {
        j.pos = 10;
        j.carcel = 1;
        dobles = 0;
        tiroOtraVez = false;
    }

    private void pagar(int quien, int cant, int a) {
        Jugador j = js.get(quien);
        j.dinero -= cant;
        if (a >= 0) js.get(a).dinero += cant;
        if (j.dinero < 0) {
            fase = DEUDA;
            acreedor = a;
        }
    }

    // ------------------------------------------------------------------ cartas
    private void carta(boolean cofre, Random r, long ahora) {
        int[] mazo = cofre ? mazoCofre : mazoDestino;
        int k = mazo[cofre ? iCofre : iDestino];
        if (cofre) iCofre = (iCofre + 1) % 12;
        else iDestino = (iDestino + 1) % 12;
        carta = (cofre ? "cofre." : "destino.") + k;
        tCarta = ahora;
        Jugador j = actual();
        int casasT = 0, hoteles = 0;
        for (int i = 0; i < 40; i++)
            if (dueno[i] == turno) {
                if (casas[i] == 5) hoteles++;
                else casasT += casas[i];
            }
        if (cofre) switch (k) {
            case 0 -> j.dinero += 200;
            case 1 -> j.dinero += 50;
            case 2 -> pagar(turno, 100, -1);
            case 3 -> aCarcel(j);
            case 4 -> j.libre++;
            case 5 -> avanzarA(0, ahora);
            case 6 -> {
                for (int i = 0; i < js.size(); i++)
                    if (i != turno && !js.get(i).fuera) {
                        int q = Math.max(0, Math.min(20, js.get(i).dinero));
                        js.get(i).dinero -= q;
                        j.dinero += q;
                    }
            }
            case 7 -> pagar(turno, 50, -1);
            case 8 -> j.dinero += 120;
            case 9 -> pagar(turno, casasT * 25 + hoteles * 100, -1);
            case 10 -> j.dinero += 75;
            default -> pagar(turno, 60, -1);
        }
        else switch (k) {
            case 0 -> avanzarA(39, ahora);
            case 1 -> avanzarA(0, ahora);
            case 2 -> {
                int e = j.pos;
                do e = (e + 1) % 40; while (Casillas.tipo(e) != Casillas.ESTACION);
                avanzarA(e, ahora);
            }
            case 3 -> mover(j.pos, 3, true, ahora + CARTA_V[Math.floorMod(velocidad, 3)]);
            case 4 -> aCarcel(j);
            case 5 -> j.libre++;
            case 6 -> pagar(turno, 150, -1);
            case 7 -> j.dinero += 150;
            case 8 -> avanzarA(16, ahora);
            case 9 -> {
                int n = 0;
                for (int i = 0; i < js.size(); i++)
                    if (i != turno && !js.get(i).fuera) {
                        js.get(i).dinero += 25;
                        n++;
                    }
                pagar(turno, n * 25, -1);
            }
            case 10 -> avanzarA(24, ahora);
            default -> pagar(turno, casasT * 40 + hoteles * 115, -1);
        }
    }

    private void avanzarA(int destino, long ahora) {
        Jugador j = actual();
        mover(j.pos, Math.floorMod(destino - j.pos, 40), false, ahora + CARTA_V[Math.floorMod(velocidad, 3)]);
    }

    // ------------------------------------------------------------------ decisiones
    public void comprar() {
        if (fase != COMPRAR) return;
        Jugador j = actual();
        int c = j.pos;
        if (dueno[c] >= 0 || j.dinero < Casillas.precio(c)) return;
        j.dinero -= Casillas.precio(c);
        dueno[c] = turno;
        anotar("compra|" + j.nombre + "|" + Casillas.ID[c] + "|" + Casillas.precio(c));
        fase = GESTION;
    }

    public void noComprar() {
        if (fase == COMPRAR) fase = GESTION;
    }

    public boolean puedeConstruir(int quien, int c) {
        if (Casillas.tipo(c) != Casillas.PROP || dueno[c] != quien || casas[c] >= 5 || hipo[c]) return false;
        int g = Casillas.grupo(c);
        if (!grupoCompleto(quien, g)) return false;
        for (int i = 0; i < 40; i++) if (Casillas.grupo(i) == g && (hipo[i] || casas[i] < casas[c])) return false;
        return js.get(quien).dinero >= Casillas.casa(c);
    }

    public boolean puedeVender(int quien, int c) {
        if (dueno[c] != quien || casas[c] <= 0) return false;
        int g = Casillas.grupo(c);
        for (int i = 0; i < 40; i++) if (Casillas.grupo(i) == g && casas[i] > casas[c]) return false;
        return true;
    }

    public boolean puedeHipotecar(int quien, int c) {
        if (dueno[c] != quien || hipo[c] || casas[c] > 0) return false;
        int g = Casillas.grupo(c);
        if (g >= 0) for (int i = 0; i < 40; i++) if (Casillas.grupo(i) == g && casas[i] > 0) return false;
        return true;
    }

    public boolean puedeDeshipotecar(int quien, int c) {
        return dueno[c] == quien && hipo[c] && js.get(quien).dinero >= costeDeshipoteca(c);
    }

    public static int costeDeshipoteca(int c) {
        return Casillas.precio(c) / 2 * 11 / 10;
    }

    public void construir(int c) {
        if (puedeConstruir(turno, c)) {
            js.get(turno).dinero -= Casillas.casa(c);
            casas[c]++;
            anotar((casas[c] == 5 ? "hotel|" : "casa|") + actual().nombre + "|" + Casillas.ID[c]);
        }
    }

    public void vender(int quien, int c) {
        if (puedeVender(quien, c)) {
            casas[c]--;
            js.get(quien).dinero += Casillas.casa(c) / 2;
            salirDeuda();
        }
    }

    public void hipotecar(int quien, int c) {
        if (puedeHipotecar(quien, c)) {
            hipo[c] = true;
            js.get(quien).dinero += Casillas.precio(c) / 2;
            anotar("hipoteca|" + js.get(quien).nombre + "|" + Casillas.ID[c]);
            salirDeuda();
        }
    }

    public void deshipotecar(int quien, int c) {
        if (puedeDeshipotecar(quien, c)) {
            js.get(quien).dinero -= costeDeshipoteca(c);
            hipo[c] = false;
        }
    }

    private void salirDeuda() {
        if (fase == DEUDA && actual().dinero >= 0) fase = GESTION;
    }

    public void pagarCarcel() {
        Jugador j = actual();
        if (fase != TIRAR || j.carcel == 0 || j.dinero < MULTA) return;
        j.dinero -= MULTA;
        j.carcel = 0;
        anotar("sale_paga|" + j.nombre);
    }

    public void usarCarta() {
        Jugador j = actual();
        if (fase != TIRAR || j.carcel == 0 || j.libre == 0) return;
        j.libre--;
        j.carcel = 0;
        anotar("sale_carta|" + j.nombre);
    }

    public void bancarrota() {
        if (fase != DEUDA) return;
        Jugador j = actual();
        for (int i = 0; i < 40; i++)
            if (dueno[i] == turno) {
                casas[i] = 0;
                dueno[i] = acreedor;
                if (acreedor < 0) hipo[i] = false;
            }
        if (acreedor >= 0) js.get(acreedor).dinero += Math.max(0, j.dinero + 0);
        j.fuera = true;
        j.dinero = 0;
        anotar("quiebra|" + j.nombre);
        if (activos() <= 1) {
            fase = FIN;
            for (Jugador o : js) if (!o.fuera) anotar("gana|" + o.nombre);
            return;
        }
        fase = GESTION;
        tiroOtraVez = false;
        terminarTurno();
    }

    public void terminarTurno() {
        if (fase != GESTION) return;
        if (tiroOtraVez && !actual().fuera && actual().carcel == 0) {
            tiroOtraVez = false;
            fase = TIRAR;
            return;
        }
        dobles = 0;
        tiroOtraVez = false;
        sel = -1;
        do {
            turno = (turno + 1) % js.size();
            if (turno == primero) ronda++;
        } while (js.get(turno).fuera);
        fase = TIRAR;
        int lim = LIMITES[limite];
        if (lim > 0 && ronda > lim) terminarPorRondas();
    }

    public void cambiarLimite() {
        if (fase == LOBBY) limite = (limite + 1) % LIMITES.length;
    }

    /** Fin por límite de rondas o a petición: gana el de mayor patrimonio. */
    public void terminarPorRondas() {
        int mejor = -1;
        for (int i = 0; i < js.size(); i++) if (!js.get(i).fuera && (mejor < 0 || patrimonio(i) > patrimonio(mejor))) mejor = i;
        fase = FIN;
        if (mejor >= 0) anotar("gana_patrimonio|" + js.get(mejor).nombre + "|" + patrimonio(mejor));
    }

    /** Patrimonio (para decidir el ganador si se termina antes). */
    public int patrimonio(int q) {
        int v = js.get(q).dinero;
        for (int i = 0; i < 40; i++)
            if (dueno[i] == q) v += (hipo[i] ? Casillas.precio(i) / 2 : Casillas.precio(i)) + casas[i] * Casillas.casa(i);
        return v;
    }

    // ------------------------------------------------------------------ CPU
    /** Un paso de la CPU si es su turno (devuelve true si ha hecho algo). */
    public boolean cpu(Random r, long ahora) {
        Jugador j = actual();
        if (j == null || !j.cpu() || fase == LOBBY || fase == FIN || fase == MOVER) return false;
        int me = turno;
        switch (fase) {
            case TIRAR -> {
                if (j.carcel > 0 && j.libre > 0) usarCarta();
                else if (j.carcel > 0 && j.dinero > 500) pagarCarcel();
                tirar(r, ahora);
            }
            case COMPRAR -> {
                int c = j.pos;
                if (j.dinero - Casillas.precio(c) >= 120 || Casillas.grupo(c) >= 0 && casiCompleto(me, Casillas.grupo(c))) comprar();
                else noComprar();
            }
            case GESTION -> {
                // construir en la calle más barata posible si va sobrado
                for (int c = 0; c < 40; c++)
                    if (puedeConstruir(me, c) && j.dinero - Casillas.casa(c) > 250) {
                        construir(c);
                        return true;
                    }
                for (int c = 0; c < 40; c++)
                    if (puedeDeshipotecar(me, c) && j.dinero - costeDeshipoteca(c) > 500) {
                        deshipotecar(me, c);
                        return true;
                    }
                terminarTurno();
            }
            case DEUDA -> {
                for (int c = 39; c >= 0; c--)
                    if (puedeVender(me, c)) {
                        vender(me, c);
                        return true;
                    }
                for (int c = 0; c < 40; c++)
                    if (puedeHipotecar(me, c)) {
                        hipotecar(me, c);
                        return true;
                    }
                bancarrota();
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean casiCompleto(int q, int g) {
        int mios = 0, total = 0, otros = 0;
        for (int i = 0; i < 40; i++)
            if (Casillas.grupo(i) == g) {
                total++;
                if (dueno[i] == q) mios++;
                else if (dueno[i] >= 0) otros++;
            }
        return otros == 0 && mios >= total - 2 && actual().dinero - Casillas.precio(actual().pos) >= 0;
    }

    // ------------------------------------------------------------------ guardar
    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Jugador j : js) {
            CompoundTag c = new CompoundTag();
            c.putString("n", j.nombre);
            if (j.uuid != null) c.putUUID("u", j.uuid);
            c.putInt("f", j.ficha);
            c.putInt("d", j.dinero);
            c.putInt("p", j.pos);
            c.putInt("c", j.carcel);
            c.putInt("l", j.libre);
            c.putBoolean("x", j.fuera);
            l.add(c);
        }
        t.put("js", l);
        t.putIntArray("dueno", dueno);
        t.putIntArray("casas", casas);
        int[] h = new int[40];
        for (int i = 0; i < 40; i++) h[i] = hipo[i] ? 1 : 0;
        t.putIntArray("hipo", h);
        t.putIntArray("e", new int[]{fase, turno, d1, d2, dobles, sel, acreedor, movDesde, movPasos, movAtras ? 1 : 0, tiroOtraVez ? 1 : 0, iCofre, iDestino, ronda, limite, primero, velocidad});
        t.putLong("tDados", tDados);
        t.putLong("tMover", tMover);
        t.putLong("tCarta", tCarta);
        t.putString("carta", carta);
        t.putIntArray("mc", mazoCofre);
        t.putIntArray("md", mazoDestino);
        ListTag lg = new ListTag();
        for (String s : log) lg.add(StringTag.valueOf(s));
        t.put("log", lg);
        return t;
    }

    public static Partida cargar(CompoundTag t) {
        Partida p = new Partida();
        ListTag l = t.getList("js", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            CompoundTag c = l.getCompound(i);
            Jugador j = new Jugador();
            j.nombre = c.getString("n");
            j.uuid = c.hasUUID("u") ? c.getUUID("u") : null;
            j.ficha = c.getInt("f");
            j.dinero = c.getInt("d");
            j.pos = c.getInt("p");
            j.carcel = c.getInt("c");
            j.libre = c.getInt("l");
            j.fuera = c.getBoolean("x");
            p.js.add(j);
        }
        int[] a = t.getIntArray("dueno");
        if (a.length == 40) System.arraycopy(a, 0, p.dueno, 0, 40);
        a = t.getIntArray("casas");
        if (a.length == 40) System.arraycopy(a, 0, p.casas, 0, 40);
        a = t.getIntArray("hipo");
        if (a.length == 40) for (int i = 0; i < 40; i++) p.hipo[i] = a[i] == 1;
        a = t.getIntArray("e");
        if (a.length >= 13) {
            p.fase = a[0];
            p.turno = a[1];
            p.d1 = a[2];
            p.d2 = a[3];
            p.dobles = a[4];
            p.sel = a[5];
            p.acreedor = a[6];
            p.movDesde = a[7];
            p.movPasos = a[8];
            p.movAtras = a[9] == 1;
            p.tiroOtraVez = a[10] == 1;
            p.iCofre = a[11];
            p.iDestino = a[12];
            if (a.length >= 16) {
                p.ronda = a[13];
                p.limite = a[14];
                p.primero = a[15];
            }
            if (a.length >= 17) p.velocidad = a[16];
        }
        p.tDados = t.getLong("tDados");
        p.tMover = t.getLong("tMover");
        p.tCarta = t.getLong("tCarta");
        p.carta = t.getString("carta");
        a = t.getIntArray("mc");
        if (a.length == 12) System.arraycopy(a, 0, p.mazoCofre, 0, 12);
        a = t.getIntArray("md");
        if (a.length == 12) System.arraycopy(a, 0, p.mazoDestino, 0, 12);
        ListTag lg = t.getList("log", Tag.TAG_STRING);
        for (int i = 0; i < lg.size(); i++) p.log.add(lg.getString(i));
        return p;
    }
}
