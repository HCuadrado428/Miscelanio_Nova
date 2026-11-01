package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

/**
 * Comecocos (diseño propio): Tragón recorre el laberinto comiéndose los cocos mientras le persiguen cuatro robots de
 * chatarra, cada uno con su manera de cazar. Las pilas los dejan en cortocircuito un rato y entonces se les puede comer.
 * Uno o dos jugadores a la vez (Tragón verde y Tragona rosa) en el mismo laberinto. Cruceta para moverse.
 */
public class Comecocos extends JuegoArcade {
    static final int T = 8, COLS = 27, FILAS = 23, OX = 4, OY = 8;
    static final String[][] LABERINTOS = {
            {"###########################", "#............#............#", "#.####.#####.#.#####.####.#", "#o####.#####.#.#####.####o#", "#.........................#", "#.####.##.#######.##.####.#", "#......##....#....##......#", "######.#####.#.#####.######", "XXXXX#.##.........##.#XXXXX", "XXXXX#.##.###-###.##.#XXXXX", "######.##.#HHHHH#.##.######", "T.........#HHHHH#.........T", "######.##.#######.##.######", "XXXXX#.##.........##.#XXXXX", "######.##.#######.##.######", "#............#............#", "#.####.#####.#.#####.####.#", "#o..##...............##..o#", "###.##.##.#######.##.##.###", "#......##....#....##......#", "#.##########.#.##########.#", "#.........................#", "###########################"},
            {"###########################", "#............#............#", "#o###.##.###.#.###.##.###o#", "#.###.##.###...###.##.###.#", "#.........................#", "##.##.####.#####.####.##.##", "##.##....#...#...#....##.##", "##.#####.#.#.#.#.#.#####.##", "XX.......#.#...#.#.......XX", "XX.####.##.##-##.##.####.XX", "##.#XX#.##.#HHH#.##.#XX#.##", "T..#XX#....#HHH#....#XX#..T", "##.####.##.#####.##.####.##", "XX......##.......##......XX", "##.####.#####.#####.####.##", "#.....#.............#.....#", "#.###.#.####.#.####.#.###.#", "#o..#........#........#..o#", "###.#.####.#.#.#.####.#.###", "#.....#....#...#....#.....#", "#.#####.####.#.####.#####.#", "#.........................#", "###########################"},
            {"###########################", "#.....#......#......#.....#", "#.###.#.####.#.####.#.###.#", "#o###...####...####...###o#", "#.###.#......#......#.###.#", "#.....######.#.######.....#", "###.########.#.########.###", "###.....####...####.....###", "XX#.###.#.........#.###.#XX", "XX#.###.#.###-###.#.###.#XX", "###.....#.#HHHHH#.#.....###", "T...###...#HHHHH#...###...T", "###.###.#.#######.#.###.###", "XX#.....#.........#.....#XX", "XX#.###.#.#######.#.###.#XX", "###.###.............###.###", "#......##.##.#.##.##......#", "#o####.##.##...##.##.####o#", "#.####.......#.......####.#", "#......####.###.####......#", "#.####......###......####.#", "#......###.......###......#", "###########################"}};
    static final int[][] COLORES_MURO = {{33, 36, 39}, {41, 43, 44}, {30, 31, 32}};
    static final int[] DX = {1, 0, -1, 0}, DY = {0, 1, 0, -1};
    static final int[] PUNTOS_PREMIO = {100, 300, 500, 700, 1000, 2000, 3000, 5000};
    static final int[][] ESQUINAS = {{25, -3}, {1, -3}, {26, 25}, {0, 25}};
    static final int PREPARA = 0, JUEGA = 1, MUERE = 2, NIVEL_OK = 3, COME = 4;

    static final class Bicho {
        float x, y;            // centro en píxeles dentro del laberinto
        int dir, quiere = -1, anim, vidas = 3, robot;
        boolean fuera, muerto;
        // robots
        int modo;              // 0 en casa, 1 saliendo, 2 fuera, 3 ojos volviendo, 4 entrando
        int esperaCasa;
        boolean asustado;
    }

    final Bicho[] jug = {new Bicho(), new Bicho()};
    final Bicho[] robots = {new Bicho(), new Bicho(), new Bicho(), new Bicho()};
    char[][] mapa;
    byte[] muros;
    int nivel, lab, estado, t, cocos, comidos, sustoT, cadena, fase, faseT, premioT, premioTipo, muerto = -1, comeT, comePts, comeX, comeY, extraDada;
    boolean dosPremios1, dosPremios2;
    Sprite[] spT1, spT2, spR, spP;

    public Comecocos(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public SoundEvent musica() {
        return ModSounds.CC_MUSICA.get();
    }

    @Override
    public void reiniciar() {
        spT1 = Sprite.hoja("comecocos/tragon1", 16, 16);
        spT2 = Sprite.hoja("comecocos/tragon2", 16, 16);
        spR = Sprite.hoja("comecocos/robots", 16, 16);
        spP = Sprite.hoja("comecocos/premios", 16, 16);
        puntos = 0;
        fin = false;
        nivel = 0;
        for (Bicho j : jug) {
            j.vidas = 3;
            j.fuera = false;
        }
        if (jugadores < 2) jug[1].fuera = true;
        extraDada = 0;
        cargarNivel();
        sonido(ModSounds.CC_INICIO, 1F);
        t = -120;
    }

    void cargarNivel() {
        lab = (nivel / 2) % 3;
        String[] L = LABERINTOS[lab];
        mapa = new char[FILAS][COLS];
        cocos = 0;
        for (int y = 0; y < FILAS; y++)
            for (int x = 0; x < COLS; x++) {
                mapa[y][x] = L[y].charAt(x);
                if (mapa[y][x] == '.' || mapa[y][x] == 'o') cocos++;
            }
        comidos = 0;
        dosPremios1 = dosPremios2 = false;
        premioT = 0;
        premioTipo = Math.min(7, nivel);
        pintarMuros();
        colocar();
    }

    void colocar() {
        for (int i = 0; i < 2; i++) {
            Bicho j = jug[i];
            j.x = (i == 0 ? 12 : 14) * T + 4;
            j.y = 17 * T + 4;
            j.dir = i == 0 ? 2 : 0;
            j.quiere = -1;
            j.muerto = false;
            j.anim = 0;
        }
        for (int i = 0; i < 4; i++) {
            Bicho r = robots[i];
            r.robot = i;
            r.asustado = false;
            if (i == 0) {
                r.x = 13 * T + 4;
                r.y = 8 * T + 4;
                r.modo = 2;
                r.dir = 2;
            } else {
                r.x = (11 + i) * T + 4;
                r.y = 10 * T + 4;
                r.modo = 0;
                r.dir = 3;
            }
            r.esperaCasa = i == 1 ? 90 : i == 2 ? 480 : 840;
        }
        sustoT = 0;
        fase = 0;
        faseT = 0;
        estado = PREPARA;
        t = 0;
    }

    // ------------------------------------------------------------------ laberinto
    boolean pared(int tx, int ty) {
        if (ty < 0 || ty >= FILAS) return true;
        tx = Math.floorMod(tx, COLS);
        char c = mapa[ty][tx];
        return c == '#' || c == 'X';
    }

    /** ¿Puede entrar este bicho en la casilla? Los robots cruzan la puerta solo al salir o volver. */
    boolean libre(Bicho b, int tx, int ty, boolean robot) {
        if (ty < 0 || ty >= FILAS) return false;
        char c = mapa[ty][Math.floorMod(tx, COLS)];
        if (c == '#' || c == 'X') return false;
        if (c == '-' || c == 'H') return robot && (b.modo == 1 || b.modo == 3 || b.modo == 4 || b.modo == 0);
        return true;
    }

    void pintarMuros() {
        int w = COLS * T, h = FILAS * T;
        muros = new byte[w * h];
        java.util.Arrays.fill(muros, (byte) 0);
        int[] col = COLORES_MURO[lab];
        for (int ty = 0; ty < FILAS; ty++)
            for (int tx = 0; tx < COLS; tx++) {
                char c = mapa[ty][tx];
                if (c == '-') {
                    for (int x = 0; x < T; x++) {
                        muros[(ty * T + 5) * w + tx * T + x] = (byte) 46;
                        muros[(ty * T + 6) * w + tx * T + x] = (byte) 46;
                    }
                    continue;
                }
                if (c != '#') continue;
                boolean u = abierta(tx, ty - 1), d = abierta(tx, ty + 1), l = abierta(tx - 1, ty), r = abierta(tx + 1, ty);
                int x0 = l ? 2 : 0, x1 = r ? 5 : 7, y0 = u ? 2 : 0, y1 = d ? 5 : 7;
                for (int y = y0; y <= y1; y++)
                    for (int x = x0; x <= x1; x++) {
                        boolean borde = (l && x == x0) || (r && x == x1) || (u && y == y0) || (d && y == y1);
                        // esquinas redondeadas
                        if ((u && l && x == x0 && y == y0) || (u && r && x == x1 && y == y0) || (d && l && x == x0 && y == y1) || (d && r && x == x1 && y == y1))
                            continue;
                        muros[(ty * T + y) * w + tx * T + x] = (byte) (borde ? col[2] : col[0]);
                    }
                // borde interior suave en las esquinas cóncavas
                if (!u && !l && abierta(tx - 1, ty - 1)) muros[(ty * T) * w + tx * T] = (byte) col[1];
                if (!u && !r && abierta(tx + 1, ty - 1)) muros[(ty * T) * w + tx * T + 7] = (byte) col[1];
                if (!d && !l && abierta(tx - 1, ty + 1)) muros[(ty * T + 7) * w + tx * T] = (byte) col[1];
                if (!d && !r && abierta(tx + 1, ty + 1)) muros[(ty * T + 7) * w + tx * T + 7] = (byte) col[1];
            }
    }

    boolean abierta(int tx, int ty) {
        if (ty < 0 || ty >= FILAS || tx < 0 || tx >= COLS) return false;
        char c = mapa[ty][tx];
        return c != '#' && c != 'X';
    }

    // ------------------------------------------------------------------ paso
    @Override
    public void paso() {
        t++;
        if (comeT > 0) {
            comeT--;
            return;
        }
        switch (estado) {
            case PREPARA -> {
                if (t > 120) {
                    estado = JUEGA;
                    t = 0;
                }
            }
            case JUEGA -> jugar();
            case MUERE -> {
                Bicho j = jug[muerto];
                j.anim++;
                if (t == 1) sonido(ModSounds.CC_MUERTE, 1F);
                if (t > 110) {
                    j.vidas--;
                    if (j.vidas <= 0) j.fuera = true;
                    if (jug[0].fuera && jug[1].fuera) {
                        fin = true;
                        mensajeFin = I18n.get("comecocos.miscelanea.fin");
                        return;
                    }
                    muerto = -1;
                    colocar();
                }
            }
            case NIVEL_OK -> {
                if (t > 150) {
                    nivel++;
                    cargarNivel();
                }
            }
            default -> {}
        }
    }

    float velocidad(Bicho r) {
        float base = Math.min(1.15F, 0.9F + nivel * 0.035F);
        int tx = (int) (r.x / T), ty = (int) (r.y / T);
        if (r.modo == 3) return 2F;
        if (mapa[ty][Math.floorMod(tx, COLS)] == 'T' || (ty == 11 && (tx < 5 || tx > 21))) return base * 0.5F;
        if (r.asustado) return base * 0.6F;
        if (r.modo == 0 || r.modo == 1 || r.modo == 4) return 0.6F;
        // el rojo acelera cuando quedan pocos cocos
        if (r.robot == 0 && cocos < 30) return base * 1.08F;
        return base;
    }

    void jugar() {
        // modos dispersarse / perseguir
        if (sustoT > 0) {
            sustoT--;
            if (sustoT == 0) for (Bicho r : robots) r.asustado = false;
        } else {
            faseT++;
            int[] dur = {9, 20, 9, 20, 7, 20, 5, 99999};
            if (fase < 7 && faseT >= dur[fase] * 60) {
                fase++;
                faseT = 0;
                for (Bicho r : robots) if (r.modo == 2) r.dir = (r.dir + 2) % 4;     // al cambiar de modo dan media vuelta
            }
        }
        for (int i = 0; i < 2; i++) if (!jug[i].fuera) moverJugador(jug[i], b[jugadores >= 2 ? i : 0], i);
        for (Bicho r : robots) moverRobot(r);
        // premio
        if (premioT > 0) premioT--;
        if (!dosPremios1 && comidos >= 70) {
            dosPremios1 = true;
            premioT = 600;
        }
        if (!dosPremios2 && comidos >= 170) {
            dosPremios2 = true;
            premioT = 600;
        }
        choques();
        if (extraDada == 0 && puntos >= 10000) {
            extraDada = 1;
            for (Bicho j : jug) if (!j.fuera) j.vidas++;
            sonido(ModSounds.CC_EXTRA, 1F);
        }
        if (cocos == 0) {
            estado = NIVEL_OK;
            t = 0;
        }
    }

    void moverJugador(Bicho j, Botones bb, int indice) {
        if (bb.mantiene(Botones.DER)) j.quiere = 0;
        else if (bb.mantiene(Botones.ABAJO)) j.quiere = 1;
        else if (bb.mantiene(Botones.IZQ)) j.quiere = 2;
        else if (bb.mantiene(Botones.ARRIBA)) j.quiere = 3;
        // dar la vuelta se puede en cualquier momento
        if (j.quiere >= 0 && j.quiere == (j.dir + 2) % 4) j.dir = j.quiere;
        float v = Math.min(1.25F, 1.1F + nivel * 0.02F);
        boolean movido = avanzar(j, v, false);
        if (movido) j.anim++;
        // comer
        int tx = Math.floorMod((int) (j.x / T), COLS), ty = (int) (j.y / T);
        char c = mapa[ty][tx];
        if ((c == '.' || c == 'o') && Math.abs(j.x - (tx * T + 4)) < 3 && Math.abs(j.y - (ty * T + 4)) < 3) {
            mapa[ty][tx] = ' ';
            cocos--;
            comidos++;
            if (c == '.') {
                puntos += 10;
                sonido(ModSounds.CC_PUNTO, comidos % 2 == 0 ? 1F : 1.15F);
            } else {
                puntos += 50;
                sustoT = Math.max(90, 420 - nivel * 36);
                cadena = 0;
                for (Bicho r : robots)
                    if (r.modo != 3) {
                        r.asustado = true;
                        if (r.modo == 2) r.dir = (r.dir + 2) % 4;
                    }
                sonido(ModSounds.CC_PILA, 1F);
            }
            for (Bicho r : robots) if (r.modo == 0 && r.esperaCasa > 0) r.esperaCasa -= 5;
        }
        if (premioT > 0 && Math.abs(j.x - (13 * T + 4)) < 6 && Math.abs(j.y - (13 * T + 4)) < 6) {
            premioT = 0;
            int pts = PUNTOS_PREMIO[premioTipo];
            puntos += pts;
            mostrarPuntos(pts, 13 * T + 4, 13 * T + 4, 40);
            sonido(ModSounds.CC_FRUTA, 1F);
        }
    }

    void mostrarPuntos(int pts, int x, int y, int dur) {
        comePts = pts;
        comeX = x;
        comeY = y;
        comeT = dur;
    }

    /** Avanza por la cuadrícula; en el centro de cada casilla decide giro. Devuelve si se ha movido. */
    boolean avanzar(Bicho b, float dist, boolean robot) {
        boolean movido = false;
        int vueltas = 0;
        while (dist > 0.0001F && vueltas++ < 4) {
            int tx = (int) Math.floor(b.x / T), ty = (int) Math.floor(b.y / T);
            float cx = tx * T + 4, cy = ty * T + 4;
            // distancia hasta el centro en la dirección de marcha (si ya lo ha pasado, hasta el siguiente)
            float hasta = b.dir == 0 ? cx - b.x : b.dir == 2 ? b.x - cx : b.dir == 1 ? cy - b.y : b.y - cy;
            if (hasta < 0) hasta += T;
            if (hasta < 0.0001F) {
                // en el centro: girar si se puede
                b.x = cx;
                b.y = cy;
                if (robot) decidir(b, tx, ty);
                else {
                    if (b.quiere >= 0 && libre(b, tx + DX[b.quiere], ty + DY[b.quiere], false)) b.dir = b.quiere;
                    if (!libre(b, tx + DX[b.dir], ty + DY[b.dir], false)) return movido;
                }
                hasta = T;
            }
            float paso = Math.min(dist, hasta);
            b.x += DX[b.dir] * paso;
            b.y += DY[b.dir] * paso;
            // túnel
            if (b.x < -4) b.x += COLS * T;
            if (b.x >= COLS * T + 4) b.x -= COLS * T;
            dist -= paso;
            movido = true;
        }
        return movido;
    }

    // ------------------------------------------------------------------ robots
    void moverRobot(Bicho r) {
        r.anim++;
        switch (r.modo) {
            case 0 -> {    // en casa: botan arriba y abajo hasta que les toca salir
                r.y += (r.anim / 16) % 2 == 0 ? 0.3F : -0.3F;
                if (--r.esperaCasa <= 0) r.modo = 1;
            }
            case 1 -> {    // salir: al centro de la puerta y hacia arriba
                float cx = 13 * T + 4;
                if (Math.abs(r.x - cx) > 0.6F) r.x += Math.signum(cx - r.x) * 0.6F;
                else {
                    r.x = cx;
                    r.y -= 0.6F;
                    if (r.y <= 8 * T + 4) {
                        r.y = 8 * T + 4;
                        r.modo = 2;
                        r.dir = 2;
                    }
                }
            }
            case 4 -> {    // entrar en casa con los ojos y revivir
                r.y += 1.2F;
                if (r.y >= 10 * T + 4) {
                    r.y = 10 * T + 4;
                    r.modo = 1;
                    r.asustado = false;
                }
            }
            default -> {
                if (r.modo == 3 && Math.abs(r.x - (13 * T + 4)) < 1.5F && Math.abs(r.y - (8 * T + 4)) < 1.5F) {
                    r.x = 13 * T + 4;
                    r.y = 8 * T + 4;
                    r.modo = 4;
                    break;
                }
                avanzar(r, velocidad(r), true);
            }
        }
    }

    Bicho cercano(Bicho r) {
        Bicho mejor = null;
        float d = 1e9F;
        for (Bicho j : jug) {
            if (j.fuera) continue;
            float dd = Math.abs(j.x - r.x) + Math.abs(j.y - r.y);
            if (dd < d) {
                d = dd;
                mejor = j;
            }
        }
        return mejor == null ? jug[0] : mejor;
    }

    void decidir(Bicho r, int tx, int ty) {
        int objX, objY;
        Bicho j = cercano(r);
        int jx = (int) (j.x / T), jy = (int) (j.y / T);
        if (r.modo == 3) {
            objX = 13;
            objY = 8;
        } else if (fase % 2 == 0 && fase < 7) {
            objX = ESQUINAS[r.robot][0];
            objY = ESQUINAS[r.robot][1];
        } else switch (r.robot) {
            case 0 -> {
                objX = jx;
                objY = jy;
            }
            case 1 -> {
                objX = jx + DX[j.dir] * 4;
                objY = jy + DY[j.dir] * 4;
            }
            case 2 -> {
                Bicho rojo = robots[0];
                int px = jx + DX[j.dir] * 2, py = jy + DY[j.dir] * 2;
                objX = 2 * px - (int) (rojo.x / T);
                objY = 2 * py - (int) (rojo.y / T);
            }
            default -> {
                float dd = (jx - tx) * (jx - tx) + (jy - ty) * (jy - ty);
                if (dd > 64) {
                    objX = jx;
                    objY = jy;
                } else {
                    objX = ESQUINAS[3][0];
                    objY = ESQUINAS[3][1];
                }
            }
        }
        int mejor = -1;
        float md = 1e9F;
        int atras = (r.dir + 2) % 4;
        // asustados: dirección al azar entre las posibles
        if (r.asustado && r.modo == 2) {
            int[] op = new int[4];
            int n = 0;
            for (int d = 0; d < 4; d++) if (d != atras && libre(r, tx + DX[d], ty + DY[d], true) && !casa(tx + DX[d], ty + DY[d])) op[n++] = d;
            if (n > 0) {
                r.dir = op[rnd.nextInt(n)];
                return;
            }
        }
        for (int d : new int[]{3, 2, 1, 0}) {     // preferencia arriba, izquierda, abajo, derecha
            if (d == atras) continue;
            int nx = tx + DX[d], ny = ty + DY[d];
            if (!libre(r, nx, ny, true)) continue;
            if (r.modo != 3 && casa(nx, ny)) continue;
            float dist = (nx - objX) * (nx - objX) + (ny - objY) * (ny - objY);
            if (dist < md) {
                md = dist;
                mejor = d;
            }
        }
        if (mejor < 0) mejor = atras;
        r.dir = mejor;
    }

    boolean casa(int tx, int ty) {
        if (ty < 0 || ty >= FILAS) return false;
        char c = mapa[ty][Math.floorMod(tx, COLS)];
        return c == '-' || c == 'H';
    }

    void choques() {
        for (int i = 0; i < 2; i++) {
            Bicho j = jug[i];
            if (j.fuera) continue;
            for (Bicho r : robots) {
                if (r.modo == 3 || r.modo == 4) continue;
                if (Math.abs(j.x - r.x) < 6 && Math.abs(j.y - r.y) < 6) {
                    if (r.asustado) {
                        r.asustado = false;
                        r.modo = 3;
                        cadena++;
                        int pts = 100 << cadena;
                        puntos += pts;
                        mostrarPuntos(pts, (int) r.x, (int) r.y, 40);
                        sonido(ModSounds.CC_ROBOT, 1F);
                    } else if (r.modo == 2) {
                        muerto = i;
                        j.anim = 0;
                        estado = MUERE;
                        t = 0;
                        return;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        p.limpiar(0);
        int w = COLS * T;
        boolean parpadeo = estado == NIVEL_OK && (t / 12) % 2 == 1;
        for (int y = 0; y < FILAS * T; y++) {
            int base = (y + OY) * Pantalla.W + OX;
            for (int x = 0; x < w; x++) {
                byte c = muros[y * w + x];
                if (c != 0) p.buf[base + x] = parpadeo ? (byte) 7 : c;
            }
        }
        // cocos y pilas
        for (int ty = 0; ty < FILAS; ty++)
            for (int tx = 0; tx < COLS; tx++) {
                char c = mapa[ty][tx];
                int px = OX + tx * T, py = OY + ty * T;
                if (c == '.') p.rect(px + 3, py + 3, 2, 2, 62);
                else if (c == 'o' && (t / 12) % 2 == 0) {
                    p.rect(px + 2, py + 1, 4, 6, 36);
                    p.rect(px + 2, py + 1, 4, 2, 19);
                    p.rect(px + 3, py, 2, 1, 6);
                }
            }
        if (premioT > 0 && (premioT > 120 || (premioT / 6) % 2 == 0)) p.spr(spP[premioTipo], OX + 13 * T + 4 - 8, OY + 13 * T + 4 - 8, false);
        // robots
        if (estado != NIVEL_OK && !(estado == MUERE && t > 30))
            for (Bicho r : robots) {
                int f;
                int k = (r.anim / 8) % 2;
                if (r.modo == 3 || r.modo == 4) f = 36 + (r.modo == 4 ? 1 : r.dir);
                else if (r.asustado) f = (sustoT < 120 && (sustoT / 10) % 2 == 0) ? 34 + k : 32 + k;
                else f = r.robot * 8 + r.dir * 2 + k;
                p.spr(spR[f], OX + Math.round(r.x) - 8, OY + Math.round(r.y) - 8, false);
            }
        for (int i = 0; i < 2; i++) {
            Bicho j = jug[i];
            if (j.fuera) continue;
            Sprite[] s = i == 0 ? spT1 : spT2;
            int f;
            if (estado == MUERE && muerto == i) f = 12 + Math.min(9, Math.max(0, (t - 30) / 8));
            else {
                int[] ciclo = {0, 1, 2, 1};
                f = j.dir * 3 + ciclo[(j.anim / 3) % 4];
            }
            p.spr(s[f], OX + Math.round(j.x) - 8, OY + Math.round(j.y) - 8, false);
        }
        if (comeT > 0) p.textoC(Integer.toString(comePts), OX + comeX, OY + comeY - 4, 63);
        // marcador
        p.texto(I18n.get("comecocos.miscelanea.puntos") + " " + puntos, 4, 0, 7);
        p.textoD(I18n.get("comecocos.miscelanea.nivel", nivel + 1), 252, 0, 19);
        int y = 24;
        for (int i = 0; i < 2; i++) {
            Bicho j = jug[i];
            if (i == 1 && jugadores < 2) break;
            Sprite[] s = i == 0 ? spT1 : spT2;
            for (int k = 0; k < Math.min(4, j.vidas - (j.fuera ? 0 : 0)); k++) p.spr(s[0], 230 + (k % 2) * 12, y + (k / 2) * 14, false);
            y += 34;
        }
        for (int k = 0; k <= Math.min(7, nivel); k++) p.spr(spP[k], 236, 186 - (k + 1) * 14, false);
        if (estado == PREPARA) p.textoC(I18n.get("comecocos.miscelanea.preparados"), OX + 13 * T + 4, OY + 13 * T, 19);
        if (estado == NIVEL_OK && t > 40) p.textoC(I18n.get("comecocos.miscelanea.nivel_ok"), OX + 13 * T + 4, OY + 13 * T, 29);
    }
}
