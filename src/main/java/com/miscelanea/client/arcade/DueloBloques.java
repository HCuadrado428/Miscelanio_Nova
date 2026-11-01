package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

import java.util.Random;

/**
 * Duelo de bloques: dos tableros de piezas cara a cara. Las líneas que haces le suben filas de basura al rival; pierde el
 * que se queda sin sitio. Un jugador contra la máquina (cada vez más rápida) o dos jugadores al mejor de tres.
 * Izq/der mover · abajo bajar · arriba caída rápida · A/B girar · C guardar pieza.
 */
public class DueloBloques extends JuegoArcade {
    static final int AN = 10, AL = 22, VISIBLE = 20, CELDA = 8;
    static final int[][][] FORMAS = {
            {{0, 1}, {1, 1}, {2, 1}, {3, 1}},          // I (4x4)
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}},          // O (2x2)
            {{1, 0}, {0, 1}, {1, 1}, {2, 1}},          // T
            {{1, 0}, {2, 0}, {0, 1}, {1, 1}},          // S
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}},          // Z
            {{0, 0}, {0, 1}, {1, 1}, {2, 1}},          // J
            {{2, 0}, {0, 1}, {1, 1}, {2, 1}}};         // L
    static final int[] TAM = {4, 2, 3, 3, 3, 3, 3};
    static final int[] COLOR = {63, 19, 43, 28, 11, 36, 15}, LUZ = {40, 20, 44, 29, 12, 37, 16}, SOMBRA = {31, 18, 42, 27, 10, 35, 14};
    static final int[] CAIDA = {48, 40, 32, 26, 20, 15, 11, 8, 6, 5, 4, 3, 2};
    static final int[][] PATADAS = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {-1, -1}, {1, -1}, {0, 1}, {-2, 0}, {2, 0}};

    final class Tablero {
        final int lado;
        final int[][] celdas = new int[AL][AN];     // 0 vacío, 1..7 pieza, 8 basura
        final Random bolsaRnd;
        final int[] bolsa = new int[7];
        int bolsaN = 7;
        int pieza, rot, px, py, siguiente, guardada = -1;
        boolean guardadoUsado, perdido, cpu;
        int caidaT, bloqueoT, bloqueoMov, dasT, dasDir, lineas, puntosT, pendiente, combo = -1, aviso, avisoT, sacudida;
        int[] lineasLimpias = new int[0];
        int limpiaT;
        // máquina
        int objRot = -1, objX, pensarT;
        final Mando man = new Mando();

        Tablero(int lado, long semilla) {
            this.lado = lado;
            bolsaRnd = new Random(semilla);
        }

        int sacar() {
            if (bolsaN >= 7) {
                for (int i = 0; i < 7; i++) bolsa[i] = i;
                for (int i = 6; i > 0; i--) {
                    int j = bolsaRnd.nextInt(i + 1), t = bolsa[i];
                    bolsa[i] = bolsa[j];
                    bolsa[j] = t;
                }
                bolsaN = 0;
            }
            return bolsa[bolsaN++];
        }

        void empezar() {
            for (int[] f : celdas) java.util.Arrays.fill(f, 0);
            perdido = false;
            guardada = -1;
            pendiente = 0;
            combo = -1;
            lineas = 0;
            siguiente = sacar();
            nueva();
        }

        void nueva() {
            pieza = siguiente;
            siguiente = sacar();
            rot = 0;
            px = pieza == 1 ? 4 : 3;
            py = 0;
            caidaT = 0;
            bloqueoT = 0;
            bloqueoMov = 0;
            guardadoUsado = false;
            objRot = -1;
            if (!cabe(pieza, rot, px, py)) {
                perdido = true;
                sonido(ModSounds.DB_PIERDE, 1F);
            }
        }

        boolean cabe(int p, int r, int x, int y) {
            for (int[] c : celdasPieza(p, r)) {
                int cx = x + c[0], cy = y + c[1];
                if (cx < 0 || cx >= AN || cy >= AL) return false;
                if (cy >= 0 && celdas[cy][cx] != 0) return false;
            }
            return true;
        }

        boolean mover(int dx, int dy) {
            if (!cabe(pieza, rot, px + dx, py + dy)) return false;
            px += dx;
            py += dy;
            if (dy == 0 && bloqueoMov < 15) bloqueoT = 0;
            if (dy == 0) bloqueoMov++;
            return true;
        }

        void girar(int dir) {
            int nr = (rot + dir + 4) % 4;
            for (int[] k : PATADAS)
                if (cabe(pieza, nr, px + k[0], py + k[1])) {
                    px += k[0];
                    py += k[1];
                    rot = nr;
                    if (bloqueoMov < 15) bloqueoT = 0;
                    bloqueoMov++;
                    sonido(ModSounds.DB_GIRO, 1F + lado * 0.05F);
                    return;
                }
        }

        int fondo() {
            int y = py;
            while (cabe(pieza, rot, px, y + 1)) y++;
            return y;
        }

        void fijar() {
            for (int[] c : celdasPieza(pieza, rot)) {
                int cx = px + c[0], cy = py + c[1];
                if (cy < 0) {
                    perdido = true;
                    sonido(ModSounds.DB_PIERDE, 1F);
                    return;
                }
                celdas[cy][cx] = pieza + 1;
            }
            sonido(ModSounds.DB_FIJA, 1F);
            // líneas
            int n = 0;
            int[] llenas = new int[4];
            for (int y = 0; y < AL; y++) {
                boolean llena = true;
                for (int x = 0; x < AN; x++) if (celdas[y][x] == 0) llena = false;
                if (llena) llenas[n++] = y;
            }
            if (n > 0) {
                combo++;
                for (int k = 0; k < n; k++) {
                    int y = llenas[k];
                    for (int yy = y; yy > 0; yy--) celdas[yy] = celdas[yy - 1].clone();
                    celdas[0] = new int[AN];
                }
                lineas += n;
                int ataque = (n == 4 ? 4 : n - 1) + Math.max(0, Math.min(3, combo - 1));
                puntosT += new int[]{0, 100, 300, 500, 800}[n] * (1 + nivel / 2) + combo * 50;
                // la basura que venía se cancela antes de atacar
                int cancel = Math.min(pendiente, ataque);
                pendiente -= cancel;
                ataque -= cancel;
                if (ataque > 0) {
                    rival(this).pendiente += ataque;
                    rival(this).sacudida = 6;
                }
                aviso = n == 4 ? 4 : n;
                avisoT = 50;
                sonido(n == 4 ? ModSounds.DB_CUATRO : ModSounds.DB_LINEA, 0.9F + n * 0.08F);
            } else {
                combo = -1;
                // entra la basura pendiente
                if (pendiente > 0) {
                    int hueco = rnd.nextInt(AN), filas = Math.min(pendiente, 8);
                    for (int y = 0; y < AL - filas; y++) celdas[y] = celdas[y + filas];
                    for (int y = AL - filas; y < AL; y++) {
                        celdas[y] = new int[AN];
                        for (int x = 0; x < AN; x++) celdas[y][x] = x == hueco ? 0 : 8;
                    }
                    pendiente -= filas;
                    sonido(ModSounds.DB_BASURA, 1F);
                }
            }
            nueva();
        }

        void paso() {
            if (perdido) return;
            if (avisoT > 0) avisoT--;
            if (sacudida > 0) sacudida--;
            Mando m = man;
            if (m.usar(Botones.C) && !guardadoUsado) {
                int g = guardada;
                guardada = pieza;
                if (g < 0) nueva();
                else {
                    pieza = g;
                    rot = 0;
                    px = pieza == 1 ? 4 : 3;
                    py = 0;
                }
                guardadoUsado = true;
                sonido(ModSounds.DB_GIRO, 0.7F);
            }
            if (m.usar(Botones.A)) girar(1);
            if (m.usar(Botones.B)) girar(-1);
            // movimiento lateral con autorrepetición
            int dir = (m.m[Botones.DER] ? 1 : 0) - (m.m[Botones.IZQ] ? 1 : 0);
            if (dir != 0) {
                if (dir != dasDir) {
                    dasDir = dir;
                    dasT = 0;
                    if (mover(dir, 0)) sonido(ModSounds.DB_MOVER, 1F);
                } else if (++dasT >= 10 && (dasT - 10) % 2 == 0) mover(dir, 0);
            } else dasDir = 0;
            if (m.usar(Botones.ARRIBA)) {
                int f = fondo();
                puntosT += (f - py) * 2;
                py = f;
                fijar();
                return;
            }
            int velocidad = CAIDA[Math.min(CAIDA.length - 1, nivelDe(this))];
            if (m.m[Botones.ABAJO]) velocidad = Math.min(velocidad, 2);
            if (cabe(pieza, rot, px, py + 1)) {
                bloqueoT = 0;
                if (++caidaT >= velocidad) {
                    caidaT = 0;
                    py++;
                    if (m.m[Botones.ABAJO]) puntosT++;
                }
            } else if (++bloqueoT >= 30) fijar();
        }
    }

    final Tablero[] t = new Tablero[2];
    int nivel, ronda, estado, estT;
    final int[] victorias = new int[2];
    static final int JUEGA = 0, FIN_RONDA = 1, PRESENTA = 2;

    public DueloBloques(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.DB_MUSICA.get();
    }

    static int[][] celdasPieza(int p, int r) {
        int n = TAM[p];
        int[][] out = new int[4][2];
        for (int i = 0; i < 4; i++) {
            int x = FORMAS[p][i][0], y = FORMAS[p][i][1];
            for (int k = 0; k < r; k++) {
                int nx = n - 1 - y, ny = x;
                x = nx;
                y = ny;
            }
            out[i][0] = x;
            out[i][1] = y;
        }
        return out;
    }

    Tablero rival(Tablero a) {
        return t[1 - a.lado];
    }

    int nivelDe(Tablero a) {
        return jugadores < 2 ? Math.min(12, nivel + a.lineas / 12) : Math.min(12, (t[0].lineas + t[1].lineas) / 16);
    }

    @Override
    public void reiniciar() {
        nivel = 0;
        ronda = 0;
        puntos = 0;
        fin = false;
        victorias[0] = victorias[1] = 0;
        empezarRonda();
    }

    void empezarRonda() {
        long s = rnd.nextLong();
        for (int i = 0; i < 2; i++) {
            t[i] = new Tablero(i, s);     // la misma serie de piezas para los dos
            t[i].cpu = i == 1 && jugadores < 2;
            t[i].empezar();
        }
        estado = PRESENTA;
        estT = 0;
        ronda++;
    }

    @Override
    public void paso() {
        estT++;
        if (estado == PRESENTA) {
            if (estT > 90) {
                estado = JUEGA;
                estT = 0;
            }
            return;
        }
        if (estado == FIN_RONDA) {
            if (estT > 150) siguienteRonda();
            return;
        }
        for (int i = 0; i < 2; i++) {
            Tablero a = t[i];
            if (a.cpu) a.man.desde(cerebro(a));
            else a.man.desde(b[jugadores >= 2 ? i : 0]);
        }
        for (Tablero a : t) a.paso();
        if (t[0].perdido || t[1].perdido) {
            int gana = t[0].perdido ? 1 : 0;
            victorias[gana]++;
            estado = FIN_RONDA;
            estT = 0;
            sonido(ModSounds.DB_GANA, gana == 0 ? 1F : 0.8F);
            if (jugadores < 2) puntos += t[0].puntosT;
        }
    }

    void siguienteRonda() {
        if (jugadores < 2) {
            if (victorias[1] > 0) {
                fin = true;
                mensajeFin = I18n.get("bloques.miscelanea.pierdes", nivel + 1);
                return;
            }
            nivel++;
            victorias[0] = 0;
            empezarRonda();
            return;
        }
        if (victorias[0] >= 2 || victorias[1] >= 2) {
            fin = true;
            puntos = t[0].puntosT;
            mensajeFin = I18n.get("bloques.miscelanea.gana_j", victorias[0] >= 2 ? 1 : 2);
            return;
        }
        empezarRonda();
    }

    // ------------------------------------------------------------------ la máquina
    int cerebro(Tablero a) {
        if (a.perdido) return 0;
        if (a.objRot < 0) planear(a);
        int lento = Math.max(1, 9 - nivel * 2);
        boolean listo = a.rot == a.objRot && a.px == a.objX;
        if (++a.pensarT < lento) return listo && nivel < 2 ? (1 << Botones.ABAJO) : 0;
        a.pensarT = 0;
        if (a.rot != a.objRot) return 1 << Botones.A;
        if (a.px < a.objX) return 1 << Botones.DER;
        if (a.px > a.objX) return 1 << Botones.IZQ;
        return nivel >= 2 ? 1 << Botones.ARRIBA : 1 << Botones.ABAJO;
    }

    void planear(Tablero a) {
        double mejor = -1e18;
        a.objRot = 0;
        a.objX = a.px;
        for (int r = 0; r < 4; r++)
            for (int x = -2; x < AN; x++) {
                if (!a.cabe(a.pieza, r, x, 0)) continue;
                int y = 0;
                while (a.cabe(a.pieza, r, x, y + 1)) y++;
                double nota = evaluar(a, r, x, y) + rnd.nextGaussian() * Math.max(0, 1.6 - nivel * 0.35);
                if (nota > mejor) {
                    mejor = nota;
                    a.objRot = r;
                    a.objX = x;
                }
            }
    }

    double evaluar(Tablero a, int r, int x, int y) {
        int[][] c = new int[AL][];
        for (int i = 0; i < AL; i++) c[i] = a.celdas[i].clone();
        for (int[] p : celdasPieza(a.pieza, r)) if (y + p[1] >= 0) c[y + p[1]][x + p[0]] = 1;
        int lineas = 0;
        for (int i = 0; i < AL; i++) {
            boolean llena = true;
            for (int k = 0; k < AN; k++) if (c[i][k] == 0) llena = false;
            if (llena) {
                lineas++;
                java.util.Arrays.fill(c[i], -1);
            }
        }
        int altura = 0, agujeros = 0, irregular = 0, previa = -1;
        for (int k = 0; k < AN; k++) {
            int h = 0;
            boolean techo = false;
            for (int i = 0; i < AL; i++) {
                if (c[i][k] > 0) {
                    if (!techo) h = AL - i;
                    techo = true;
                } else if (techo && c[i][k] == 0) agujeros++;
            }
            altura += h;
            if (previa >= 0) irregular += Math.abs(h - previa);
            previa = h;
        }
        return -0.51 * altura + 0.76 * lineas - 0.36 * agujeros * 2 - 0.18 * irregular;
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        p.limpiar(0);
        for (int y = 0; y < Pantalla.H; y += 2) p.rect(0, y, Pantalla.W, 1, 1);
        for (int i = 0; i < 2; i++) tablero(p, t[i], i == 0 ? 8 : 138);
        p.textoC(I18n.get("bloques.miscelanea.titulo"), 128, 2, 20);
        if (jugadores < 2) p.textoC(I18n.get("bloques.miscelanea.nivel", nivel + 1), 128, 12, 39);
        else p.textoC(victorias[0] + " - " + victorias[1], 128, 12, 7);
        if (estado == PRESENTA) {
            p.rect(60, 84, 136, 22, 0);
            p.marco(60, 84, 136, 22, 45);
            p.textoC(jugadores < 2 ? I18n.get("bloques.miscelanea.contra_cpu", nivel + 1) : I18n.get("bloques.miscelanea.ronda", ronda), 128, 91, 20);
        } else if (estado == FIN_RONDA) {
            for (int i = 0; i < 2; i++) {
                int x0 = i == 0 ? 8 : 138;
                p.rect(x0, 92, 80, 16, 0);
                p.textoC(I18n.get(t[i].perdido ? "bloques.miscelanea.ko" : "bloques.miscelanea.gana"), x0 + 40, 96, t[i].perdido ? 11 : 29);
            }
        }
    }

    void tablero(Pantalla p, Tablero a, int x0) {
        int y0 = 26 + (a.sacudida > 0 ? (a.sacudida % 2) * 2 : 0);
        p.rect(x0 - 2, y0 - 2, AN * CELDA + 4, VISIBLE * CELDA + 4, a.lado == 0 ? 11 : 36);
        p.rect(x0, y0, AN * CELDA, VISIBLE * CELDA, 33);
        for (int y = 0; y < VISIBLE; y++)
            for (int x = 0; x < AN; x++) {
                int v = a.celdas[y + AL - VISIBLE][x];
                if (v == 0) {
                    if ((x + y) % 2 == 0) p.px(x0 + x * CELDA + 4, y0 + y * CELDA + 4, 34);
                } else celda(p, x0 + x * CELDA, y0 + y * CELDA, v == 8 ? -1 : v - 1);
            }
        if (!a.perdido && estado == JUEGA) {
            int f = a.fondo();
            for (int[] c : celdasPieza(a.pieza, a.rot)) {
                int vy = f + c[1] - (AL - VISIBLE);
                if (vy >= 0) p.marco(x0 + (a.px + c[0]) * CELDA, y0 + vy * CELDA, CELDA, CELDA, COLOR[a.pieza]);
            }
            for (int[] c : celdasPieza(a.pieza, a.rot)) {
                int vy = a.py + c[1] - (AL - VISIBLE);
                if (vy >= 0) celda(p, x0 + (a.px + c[0]) * CELDA, y0 + vy * CELDA, a.pieza);
            }
        }
        // basura que viene
        int pend = Math.min(VISIBLE, a.pendiente);
        if (pend > 0) p.rect(x0 + AN * CELDA + 2, y0 + (VISIBLE - pend) * CELDA, 3, pend * CELDA, (estT / 6) % 2 == 0 ? 11 : 12);
        // siguiente y guardada (pequeñas)
        int sx = a.lado == 0 ? x0 + AN * CELDA + 4 : x0 - 23;
        {
            p.texto(I18n.get("bloques.miscelanea.sig"), sx, y0, 6);
            mini(p, a.siguiente, sx, y0 + 12);
            p.texto(I18n.get("bloques.miscelanea.guarda"), sx, y0 + 40, 6);
            if (a.guardada >= 0) mini(p, a.guardada, sx, y0 + 52);
            p.texto(Integer.toString(a.lineas), sx, y0 + 80, 7);
        }
        if (a.avisoT > 0 && a.aviso >= 2) p.textoC(I18n.get("bloques.miscelanea.lineas" + a.aviso), x0 + 40, y0 + 60, a.aviso == 4 ? 20 : 63);
        p.texto(a.cpu ? "CPU" : (a.lado == 0 ? "1P" : "2P"), x0, 16, a.lado == 0 ? 12 : 39);
        if (jugadores < 2 && a.lado == 0) p.textoD(Integer.toString(a.puntosT + puntos), x0 + 80, 16, 7);
    }

    void mini(Pantalla p, int pieza, int x, int y) {
        for (int[] c : celdasPieza(pieza, 0)) {
            p.rect(x + c[0] * 5, y + c[1] * 5, 5, 5, COLOR[pieza]);
            p.rect(x + c[0] * 5, y + c[1] * 5, 5, 1, LUZ[pieza]);
        }
    }

    /** Celda con relieve; pieza -1 es basura gris. */
    void celda(Pantalla p, int x, int y, int pieza) {
        int c = pieza < 0 ? 4 : COLOR[pieza], l = pieza < 0 ? 5 : LUZ[pieza], s = pieza < 0 ? 2 : SOMBRA[pieza];
        p.rect(x, y, CELDA, CELDA, c);
        p.rect(x, y, CELDA, 1, l);
        p.rect(x, y, 1, CELDA, l);
        p.rect(x, y + CELDA - 1, CELDA, 1, s);
        p.rect(x + CELDA - 1, y, 1, CELDA, s);
        if (pieza >= 0) p.px(x + 2, y + 2, 7);
    }

    /** Mandos de un tablero (jugador o máquina). */
    static final class Mando {
        final boolean[] m = new boolean[Botones.N];
        final int[] buffer = new int[Botones.N];
        final boolean[] antes = new boolean[Botones.N];

        void desde(Botones b) {
            for (int i = 0; i < Botones.N; i++) {
                m[i] = b.mantiene(i);
                if (b.pulsa(i)) buffer[i] = 4;
                else if (buffer[i] > 0) buffer[i]--;
            }
        }

        void desde(int mascara) {
            for (int i = 0; i < Botones.N; i++) {
                boolean ahora = (mascara >> i & 1) != 0;
                if (ahora && !antes[i]) buffer[i] = 4;
                else if (buffer[i] > 0) buffer[i]--;
                antes[i] = ahora;
                m[i] = ahora;
            }
        }

        boolean usar(int b) {
            if (buffer[b] > 0) {
                buffer[b] = 0;
                return true;
            }
            return false;
        }
    }
}
