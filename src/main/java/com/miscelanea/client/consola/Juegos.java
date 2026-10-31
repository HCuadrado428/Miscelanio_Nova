package com.miscelanea.client.consola;

import com.miscelanea.consola.Juego;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

/** Los seis juegos de la consola. */
public final class Juegos {
    private Juegos() {}

    public static JuegoGB crear(Juego j, Botones b) {
        return switch (j) {
            case TETROMINOS -> new Tetrominos(b);
            case SERPIENTE -> new Serpiente(b);
            case LADRILLOS -> new Ladrillos(b);
            case PONG -> new Pong(b);
            case PAJARO -> new Pajaro(b);
            case INVASORES -> new Invasores(b);
        };
    }

    private static String t(String k) {
        return I18n.get("consola.miscelanea." + k);
    }

    // =================================================================== TETROMINÓS
    static final class Tetrominos extends JuegoGB {
        static final int W = 10, H = 18, C = 7, BX = 12, BY = 9;
        static final int[][][] BASE = {
                {{0, 1}, {1, 1}, {2, 1}, {3, 1}},   // I
                {{0, 0}, {1, 0}, {0, 1}, {1, 1}},   // O
                {{1, 0}, {0, 1}, {1, 1}, {2, 1}},   // T
                {{1, 0}, {2, 0}, {0, 1}, {1, 1}},   // S
                {{0, 0}, {1, 0}, {1, 1}, {2, 1}},   // Z
                {{0, 0}, {0, 1}, {1, 1}, {2, 1}},   // J
                {{2, 0}, {0, 1}, {1, 1}, {2, 1}}};  // L
        static final int[] TAM = {4, 2, 3, 3, 3, 3, 3};
        static final int[] GRAV = {48, 42, 36, 30, 25, 20, 16, 13, 10, 8, 6, 5, 4, 3, 3, 2};
        static final int[][][][] ROT = new int[7][4][4][2];

        static {
            for (int p = 0; p < 7; p++) {
                int n = TAM[p];
                ROT[p][0] = BASE[p];
                for (int r = 1; r < 4; r++)
                    for (int c = 0; c < 4; c++) {
                        int[] a = ROT[p][r - 1][c];
                        ROT[p][r][c] = new int[]{n - 1 - a[1], a[0]};
                    }
            }
        }

        int[][] tab;
        int pieza, rot, px, py, sig, nivel, lineas, caida, bloqueo, reinicios;
        final List<Integer> bolsa = new ArrayList<>();
        final List<Integer> llenas = new ArrayList<>();
        int limpiando, finT;

        Tetrominos(Botones b) {
            super(b);
        }

        int sacar() {
            if (bolsa.isEmpty()) {
                for (int i = 0; i < 7; i++) bolsa.add(i);
                Collections.shuffle(bolsa, rnd);
            }
            return bolsa.remove(0);
        }

        @Override
        public void reiniciar() {
            tab = new int[H][W];
            bolsa.clear();
            llenas.clear();
            puntos = nivel = lineas = 0;
            fin = false;
            limpiando = 0;
            finT = 0;
            sig = sacar();
            nueva();
        }

        void nueva() {
            pieza = sig;
            sig = sacar();
            rot = 0;
            px = (W - TAM[pieza]) / 2;
            py = pieza == 0 ? -1 : 0;
            caida = 0;
            bloqueo = 0;
            reinicios = 0;
            if (choca(px, py, rot)) finT = 1;
        }

        boolean choca(int x, int y, int r) {
            for (int[] c : ROT[pieza][r]) {
                int cx = x + c[0], cy = y + c[1];
                if (cx < 0 || cx >= W || cy >= H) return true;
                if (cy >= 0 && tab[cy][cx] != 0) return true;
            }
            return false;
        }

        void girar(int d) {
            int r = (rot + d + 4) % 4;
            int[][] pruebas = {{0, 0}, {-1, 0}, {1, 0}, {-2, 0}, {2, 0}, {0, -1}};
            for (int[] k : pruebas)
                if (!choca(px + k[0], py + k[1], r)) {
                    px += k[0];
                    py += k[1];
                    rot = r;
                    if (reinicios++ < 12) bloqueo = 0;
                    sonido(ModSounds.GB_GIRAR);
                    return;
                }
        }

        void fijar() {
            for (int[] c : ROT[pieza][rot]) {
                int cy = py + c[1];
                if (cy < 0) {
                    finT = 1;
                    return;
                }
                tab[cy][px + c[0]] = pieza + 1;
            }
            llenas.clear();
            for (int y = 0; y < H; y++) {
                boolean ll = true;
                for (int x = 0; x < W; x++) if (tab[y][x] == 0) ll = false;
                if (ll) llenas.add(y);
            }
            if (!llenas.isEmpty()) {
                limpiando = 30;
                sonido(ModSounds.GB_LINEA, llenas.size() == 4 ? 1.25F : 1.0F);
            } else {
                sonido(ModSounds.GB_MOVER, 0.6F);
                nueva();
            }
        }

        @Override
        public void paso() {
            if (finT > 0) {
                // el tablero se va llenando de abajo arriba antes del "fin del juego"
                if (++finT > H * 3 + 10) fin = true;
                return;
            }
            if (limpiando > 0) {
                if (--limpiando == 0) {
                    for (int y : llenas) {
                        for (int k = y; k > 0; k--) tab[k] = tab[k - 1];
                        tab[0] = new int[W];
                    }
                    int n = llenas.size();
                    puntos += new int[]{0, 40, 100, 300, 1200}[n] * (nivel + 1);
                    lineas += n;
                    if (lineas / 10 > nivel) {
                        nivel = lineas / 10;
                        sonido(ModSounds.GB_INICIO, 1.2F);
                    }
                    llenas.clear();
                    nueva();
                }
                return;
            }
            if (b.repite(Botones.IZQ, 12, 4) && !choca(px - 1, py, rot)) {
                px--;
                if (reinicios++ < 12) bloqueo = 0;
                sonido(ModSounds.GB_MOVER);
            }
            if (b.repite(Botones.DER, 12, 4) && !choca(px + 1, py, rot)) {
                px++;
                if (reinicios++ < 12) bloqueo = 0;
                sonido(ModSounds.GB_MOVER);
            }
            if (b.pulsa(Botones.A)) girar(1);
            if (b.pulsa(Botones.B)) girar(-1);
            if (b.pulsa(Botones.ARRIBA)) {
                int n = 0;
                while (!choca(px, py + 1, rot)) {
                    py++;
                    n++;
                }
                puntos += n * 2;
                fijar();
                return;
            }
            int g = GRAV[Math.min(nivel, GRAV.length - 1)];
            if (b.mantiene(Botones.ABAJO)) g = Math.min(g, 2);
            if (++caida >= g) {
                caida = 0;
                if (!choca(px, py + 1, rot)) {
                    py++;
                    bloqueo = 0;
                    if (b.mantiene(Botones.ABAJO)) puntos++;
                }
            }
            if (choca(px, py + 1, rot)) {
                if (++bloqueo >= (b.mantiene(Botones.ABAJO) ? 8 : 30)) fijar();
            }
        }

        static void bloque(Lcd l, int x, int y, int t) {
            l.rect(x, y, C, C, 3);
            switch (t) {
                case 1 -> {
                    l.rect(x + 1, y + 1, 5, 5, 1);
                    l.rect(x + 1, y + 1, 5, 1, 0);
                }
                case 2 -> {
                    l.rect(x + 1, y + 1, 5, 5, 0);
                    l.rect(x + 2, y + 2, 3, 3, 2);
                }
                case 3 -> {
                    l.rect(x + 1, y + 1, 5, 5, 2);
                    l.px(x + 1, y + 1, 0);
                    l.px(x + 2, y + 1, 1);
                    l.px(x + 1, y + 2, 1);
                }
                case 4 -> {
                    l.rect(x + 1, y + 1, 5, 5, 1);
                    for (int i = 0; i < 5; i++) l.px(x + 1 + i, y + 1 + (i % 2) * 2, 2);
                }
                case 5 -> {
                    l.rect(x + 1, y + 1, 5, 5, 2);
                    l.rect(x + 2, y + 2, 3, 3, 1);
                }
                case 6 -> {
                    l.rect(x + 2, y + 2, 3, 3, 1);
                    l.px(x + 1, y + 1, 0);
                }
                default -> {
                    l.rect(x + 1, y + 1, 5, 5, 0);
                    l.rect(x + 3, y + 3, 1, 1, 3);
                }
            }
        }

        @Override
        public void dibujar(Lcd l) {
            // paredes de ladrillo
            for (int[] w : new int[][]{{BX - 8, 8}, {BX + W * C, 8}}) {
                l.rect(w[0], 0, w[1], Lcd.H, 2);
                for (int y = 0; y < Lcd.H; y += 4) {
                    l.rect(w[0], y, w[1], 1, 3);
                    l.rect(w[0] + ((y / 4) % 2 == 0 ? 2 : 6), y, 1, 4, 3);
                }
            }
            l.rect(BX, BY - 9, W * C, 9, 0);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    int v = tab[y][x];
                    if (finT > 0 && (H - 1 - y) * 3 < finT) v = 8;
                    if (v != 0) {
                        if (limpiando > 0 && llenas.contains(y) && (limpiando / 4) % 2 == 0) l.rect(BX + x * C, BY + y * C, C, C, 1);
                        else bloque(l, BX + x * C, BY + y * C, v == 8 ? 0 : v);
                    }
                }
            if (finT == 0 && limpiando == 0) {
                int gy = py;
                while (!choca(px, gy + 1, rot)) gy++;
                for (int[] c : ROT[pieza][rot]) {
                    int x = BX + (px + c[0]) * C, y = BY + (gy + c[1]) * C;
                    if (gy + c[1] >= 0) l.marco(x + 1, y + 1, C - 2, C - 2, 1);
                }
                for (int[] c : ROT[pieza][rot])
                    if (py + c[1] >= 0) bloque(l, BX + (px + c[0]) * C, BY + (py + c[1]) * C, pieza + 1);
            }
            // panel
            int x0 = BX + W * C + 10;
            l.caja(x0, 3, 66, 26);
            l.textoC(t("puntos"), x0 + 33, 7, 2);
            l.textoC(Integer.toString(puntos), x0 + 33, 17, 3);
            l.caja(x0, 32, 66, 26);
            l.textoC(t("nivel"), x0 + 33, 36, 2);
            l.textoC(Integer.toString(nivel), x0 + 33, 46, 3);
            l.caja(x0, 61, 66, 26);
            l.textoC(t("lineas"), x0 + 33, 65, 2);
            l.textoC(Integer.toString(lineas), x0 + 33, 75, 3);
            l.caja(x0, 90, 66, 44);
            l.textoC(t("sig"), x0 + 33, 94, 2);
            int n = TAM[sig];
            int ox = x0 + 33 - n * C / 2, oy = 106 + (sig == 0 ? -3 : 2);
            for (int[] c : ROT[sig][0]) bloque(l, ox + c[0] * C, oy + c[1] * C, sig + 1);
        }
    }

    // =================================================================== SERPIENTE
    static final class Serpiente extends JuegoGB {
        static final int CW = 20, CHh = 16, C = 8, OY = 16;
        static final String[] MANZANA = {"...33...", "....3...", ".222322.", "22122222", "21222222", "22222222", ".222222.", "..2..2.."};
        static final String[] CEREZA = {".....3..", "....3.3.", "...3...3", "..3....3", ".222..22", "21222122", "22222222", ".22..22."};
        final Deque<int[]> cuerpo = new ArrayDeque<>();
        final Deque<int[]> cola = new ArrayDeque<>();
        int dx, dy, timer, comidas, mx, my, bx, by, bonusT, crecer, muerto, espera;

        Serpiente(Botones b) {
            super(b);
        }

        @Override
        public void reiniciar() {
            cuerpo.clear();
            cola.clear();
            for (int i = 0; i < 4; i++) cuerpo.addLast(new int[]{8 - i, 8});
            dx = 1;
            dy = 0;
            puntos = comidas = crecer = muerto = bonusT = 0;
            espera = 1;
            fin = false;
            bx = -1;
            colocar();
        }

        boolean ocupa(int x, int y) {
            for (int[] c : cuerpo) if (c[0] == x && c[1] == y) return true;
            return false;
        }

        void colocar() {
            do {
                mx = rnd.nextInt(CW);
                my = rnd.nextInt(CHh);
            } while (ocupa(mx, my) || (mx == bx && my == by));
        }

        @Override
        public void paso() {
            if (muerto > 0) {
                if (++muerto > 70) fin = true;
                return;
            }
            int[][] dirs = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
            for (int i = 0; i < 4; i++)
                if (b.pulsa(i)) {
                    int[] last = cola.isEmpty() ? new int[]{dx, dy} : cola.peekLast();
                    if (dirs[i][0] != -last[0] || dirs[i][1] != -last[1])
                        if (dirs[i][0] != last[0] || dirs[i][1] != last[1]) if (cola.size() < 3) cola.addLast(dirs[i]);
                    espera = 0;
                }
            if (b.pulsa(Botones.A)) espera = 0;
            if (espera > 0) return;
            if (bonusT > 0 && --bonusT == 0) bx = -1;
            int vel = Math.max(4, 9 - comidas / 5);
            if (++timer < vel) return;
            timer = 0;
            if (!cola.isEmpty()) {
                int[] d = cola.pollFirst();
                dx = d[0];
                dy = d[1];
            }
            int[] cab = cuerpo.peekFirst();
            int nx = cab[0] + dx, ny = cab[1] + dy;
            boolean crece = crecer > 0;
            if (nx < 0 || ny < 0 || nx >= CW || ny >= CHh || (ocupa(nx, ny) && !(nx == cuerpo.peekLast()[0] && ny == cuerpo.peekLast()[1] && !crece))) {
                muerto = 1;
                sonido(ModSounds.GB_EXPLOSION);
                return;
            }
            cuerpo.addFirst(new int[]{nx, ny});
            if (crece) crecer--;
            else cuerpo.pollLast();
            if (nx == mx && ny == my) {
                comidas++;
                crecer += 2;
                puntos += 10 + comidas / 5 * 2;
                sonido(ModSounds.GB_COMER, 1.0F + Math.min(0.5F, comidas * 0.02F));
                if (comidas % 6 == 0 && bx < 0) {
                    do {
                        bx = rnd.nextInt(CW);
                        by = rnd.nextInt(CHh);
                    } while (ocupa(bx, by) || (bx == mx && by == my));
                    bonusT = 60 * 7;
                }
                colocar();
            } else if (nx == bx && ny == by) {
                puntos += 50 + bonusT / 6;
                crecer += 3;
                bx = -1;
                bonusT = 0;
                sonido(ModSounds.GB_LINEA, 1.3F);
            }
        }

        @Override
        public void dibujar(Lcd l) {
            l.rect(0, 0, Lcd.W, OY - 1, 3);
            l.texto(t("puntos") + " " + puntos, 3, 4, 0);
            l.textoD(t("record") + " " + com.miscelanea.consola.RecordsPacket.cliente.getInt("serpiente"), 157, 4, 1);
            for (int y = 0; y < CHh; y++)
                for (int x = 0; x < CW; x++) if ((x + y) % 2 == 0) l.px(x * C + 4, OY + y * C + 4, 1);
            l.sprite(MANZANA, mx * C, OY + my * C);
            if (bx >= 0 && (bonusT > 120 || (bonusT / 6) % 2 == 0)) l.sprite(CEREZA, bx * C, OY + by * C);
            if (muerto > 0 && (muerto / 6) % 2 == 1) return;
            int[] prev = null;
            int i = 0;
            for (int[] c : cuerpo) {
                int x = c[0] * C, y = OY + c[1] * C;
                boolean cab = i == 0, colaF = i == cuerpo.size() - 1;
                int m = colaF ? 2 : 1;
                l.rect(x + m, y + m, C - 2 * m, C - 2 * m, 3);
                if (prev != null) {
                    int px2 = prev[0] * C, py2 = OY + prev[1] * C;
                    l.rect(Math.min(x, px2) + 2, Math.min(y, py2) + 2, Math.abs(x - px2) + C - 4, Math.abs(y - py2) + C - 4, 3);
                }
                if (!cab && !colaF) l.px(x + 3 + (i % 2), y + 3 + (i % 2), 2);
                if (cab) {
                    int ex = dx != 0 ? (dx > 0 ? 5 : 2) : 2, ey = dy != 0 ? (dy > 0 ? 5 : 2) : 2;
                    if (dx != 0) {
                        l.px(x + ex, y + 2, 0);
                        l.px(x + ex, y + 5, 0);
                    } else {
                        l.px(x + 2, y + ey, 0);
                        l.px(x + 5, y + ey, 0);
                    }
                    if (muerto == 0 && (timer / 2) % 6 == 0) l.px(x + 3 + dx * 4, y + 3 + dy * 4, 2);
                }
                prev = c;
                i++;
            }
            if (espera > 0) {
                l.caja(44, 100, 72, 20);
                l.textoC(t("pulsa_a"), 80, 106, 3);
            }
        }
    }

    // =================================================================== LADRILLOS
    static final class Ladrillos extends JuegoGB {
        static final int COLS = 8, FILAS = 6, BW = 18, BH = 6, X0 = 4, Y0 = 22, PY = 132;
        static final String[] CAP_L = {".33333.", "3022203", "3020003", "3022203", ".33333."};
        static final String[] CAP_V = {".33333.", "3020203", "3020203", "3002003", ".33333."};
        int[][] lad;
        float pxl, bxp, byp, vx, vy, vel;
        int ancho, vidas, nivel, pegada, anchoT, quedan, bajando;
        final List<float[]> caps = new ArrayList<>();

        Ladrillos(Botones b) {
            super(b);
        }

        @Override
        public void reiniciar() {
            puntos = 0;
            vidas = 3;
            nivel = 0;
            fin = false;
            montar();
        }

        void montar() {
            lad = new int[FILAS][COLS];
            quedan = 0;
            for (int f = 0; f < FILAS; f++)
                for (int c = 0; c < COLS; c++) {
                    boolean hay = switch (nivel % 4) {
                        case 1 -> (f + c) % 2 == 0 || f < 2;
                        case 2 -> Math.abs(c * 2 + 1 - COLS) <= (FILAS - f) * 2 - 1 + 2;
                        case 3 -> c != 3 && c != 4 || f % 2 == 0;
                        default -> true;
                    };
                    if (hay) {
                        lad[f][c] = f < 2 && nivel > 0 ? 2 : 1;
                        quedan++;
                    }
                }
            caps.clear();
            anchoT = 0;
            saque();
        }

        void saque() {
            ancho = anchoT > 0 ? 36 : 24;
            pxl = 80 - ancho / 2F;
            pegada = 1;
            vel = 1.5F + nivel * 0.15F;
        }

        @Override
        public void paso() {
            if (bajando > 0) {
                if (++bajando > 50) {
                    bajando = 0;
                    if (vidas <= 0) fin = true;
                    else saque();
                }
                return;
            }
            if (anchoT > 0 && --anchoT == 0) ancho = 24;
            float vp = 2.4F;
            if (b.mantiene(Botones.IZQ)) pxl -= vp;
            if (b.mantiene(Botones.DER)) pxl += vp;
            pxl = Math.max(X0, Math.min(156 - ancho, pxl));
            if (pegada > 0) {
                bxp = pxl + ancho / 2F - 1.5F;
                byp = PY - 4;
                if (b.pulsa(Botones.A) || b.pulsa(Botones.ARRIBA) || ++pegada > 180) {
                    pegada = 0;
                    float a = (float) Math.toRadians(-20 + rnd.nextInt(40));
                    vx = (float) Math.sin(a) * vel;
                    vy = -(float) Math.cos(a) * vel;
                    sonido(ModSounds.GB_REBOTE, 1.3F);
                }
            } else {
                for (int s = 0; s < 3 && bajando == 0 && pegada == 0; s++) mover(1 / 3F);
            }
            for (Iterator<float[]> it = caps.iterator(); it.hasNext(); ) {
                float[] c = it.next();
                c[1] += 0.8F;
                if (choca(c[0], c[1], 7, 5, pxl, PY, ancho, 4)) {
                    if (c[2] == 0) {
                        anchoT = 60 * 15;
                        float cx = pxl + ancho / 2F;
                        ancho = 36;
                        pxl = cx - 18;
                    } else vidas = Math.min(5, vidas + 1);
                    puntos += 50;
                    sonido(ModSounds.GB_LINEA, 1.4F);
                    it.remove();
                } else if (c[1] > Lcd.H) it.remove();
            }
        }

        void mover(float dt) {
            bxp += vx * dt;
            byp += vy * dt;
            if (bxp < X0) {
                bxp = X0;
                vx = Math.abs(vx);
                sonido(ModSounds.GB_REBOTE, 0.8F);
            }
            if (bxp > 156 - 3) {
                bxp = 153;
                vx = -Math.abs(vx);
                sonido(ModSounds.GB_REBOTE, 0.8F);
            }
            if (byp < 14) {
                byp = 14;
                vy = Math.abs(vy);
                sonido(ModSounds.GB_REBOTE, 0.8F);
            }
            if (vy > 0 && choca(bxp, byp, 3, 3, pxl, PY, ancho, 4)) {
                float off = ((bxp + 1.5F) - (pxl + ancho / 2F)) / (ancho / 2F);
                off = Math.max(-1, Math.min(1, off));
                float a = off * (float) Math.toRadians(60);
                vx = (float) Math.sin(a) * vel;
                vy = -(float) Math.cos(a) * vel;
                byp = PY - 3;
                sonido(ModSounds.GB_REBOTE);
            }
            if (byp > Lcd.H) {
                vidas--;
                bajando = 1;
                sonido(ModSounds.GB_EXPLOSION, 1.2F);
                return;
            }
            int c0 = (int) Math.floor((bxp - X0) / (BW + 1)), c1 = (int) Math.floor((bxp + 3 - X0) / (BW + 1));
            int f0 = (int) Math.floor((byp - Y0) / (BH + 2)), f1 = (int) Math.floor((byp + 3 - Y0) / (BH + 2));
            for (int f = f0; f <= f1; f++)
                for (int c = c0; c <= c1; c++) {
                    if (f < 0 || f >= FILAS || c < 0 || c >= COLS || lad[f][c] == 0) continue;
                    float lx = X0 + c * (BW + 1), ly = Y0 + f * (BH + 2);
                    if (!choca(bxp, byp, 3, 3, lx, ly, BW, BH)) continue;
                    float solX = Math.min(bxp + 3 - lx, lx + BW - bxp), solY = Math.min(byp + 3 - ly, ly + BH - byp);
                    if (solX < solY) vx = -vx;
                    else vy = -vy;
                    if (--lad[f][c] == 0) {
                        quedan--;
                        puntos += (FILAS - f) * 10;
                        vel = Math.min(3.2F, vel + 0.03F);
                        float s = vel / (float) Math.hypot(vx, vy);
                        vx *= s;
                        vy *= s;
                        if (rnd.nextFloat() < 0.1F) caps.add(new float[]{lx + BW / 2F - 3, ly, rnd.nextFloat() < 0.75F ? 0 : 1});
                        sonido(ModSounds.GB_PUNTO, 0.8F + (FILAS - f) * 0.1F);
                        if (quedan == 0) {
                            nivel++;
                            puntos += 500;
                            sonido(ModSounds.GB_INICIO, 1.1F);
                            montar();
                        }
                    } else sonido(ModSounds.GB_REBOTE, 1.5F);
                    return;
                }
        }

        @Override
        public void dibujar(Lcd l) {
            l.rect(0, 0, Lcd.W, 11, 3);
            l.texto(cifras(puntos, 6), 3, 2, 0);
            for (int i = 0; i < vidas; i++) l.sprite(new String[]{".0.0.", "00000", "00000", ".000.", "..0.."}, 153 - i * 7, 3);
            l.textoC(t("nivel") + " " + (nivel + 1), 88, 2, 1);
            l.rect(0, 11, X0, Lcd.H - 11, 2);
            l.rect(156, 11, 4, Lcd.H - 11, 2);
            l.rect(0, 11, Lcd.W, 3, 2);
            for (int f = 0; f < FILAS; f++)
                for (int c = 0; c < COLS; c++) {
                    int v = lad[f][c];
                    if (v == 0) continue;
                    int x = X0 + c * (BW + 1), y = Y0 + f * (BH + 2);
                    l.rect(x, y, BW, BH, 3);
                    int tono = v == 2 ? 3 : (f % 3 == 0 ? 2 : f % 3 == 1 ? 1 : 0);
                    l.rect(x + 1, y + 1, BW - 2, BH - 2, tono);
                    l.rect(x + 1, y + 1, BW - 2, 1, v == 2 ? 2 : 0);
                    if (v == 2) l.rect(x + 3, y + 2, 2, 2, 1);
                }
            for (float[] c : caps) l.sprite(c[2] == 0 ? CAP_L : CAP_V, (int) c[0], (int) c[1]);
            int px = Math.round(pxl);
            l.rect(px, PY, ancho, 4, 3);
            l.rect(px + 2, PY + 1, ancho - 4, 1, 1);
            if (bajando == 0 || (bajando / 5) % 2 == 0) {
                int x = Math.round(bxp), y = Math.round(byp);
                l.rect(x, y, 3, 3, 3);
                l.px(x, y, 2);
            }
            if (pegada > 0 && (pegada / 20) % 2 == 0) l.textoC(t("pulsa_a"), 80, 100, 3);
        }
    }

    // =================================================================== PONG
    static final class Pong extends JuegoGB {
        static final int PH = 22, TOP = 12, BOT = Lcd.H - 2, META = 7;
        float yo, cpu, bx, by, vx, vy, vel, objetivo;
        int mio, suyo, saque, golpes;

        Pong(Botones b) {
            super(b);
        }

        @Override
        public void reiniciar() {
            yo = cpu = (TOP + BOT) / 2F - PH / 2F;
            mio = suyo = 0;
            puntos = 0;
            fin = false;
            mensajeFin = null;
            servir(1);
        }

        void servir(int haciaQuien) {
            bx = 78.5F;
            by = (TOP + BOT) / 2F;
            vel = 1.6F;
            golpes = 0;
            float a = (float) Math.toRadians(-30 + rnd.nextInt(60));
            vx = (float) Math.cos(a) * vel * haciaQuien;
            vy = (float) Math.sin(a) * vel;
            saque = 50;
        }

        @Override
        public void paso() {
            if (b.mantiene(Botones.ARRIBA)) yo -= 2.3F;
            if (b.mantiene(Botones.ABAJO)) yo += 2.3F;
            yo = Math.max(TOP, Math.min(BOT - PH, yo));
            // CPU: predice dónde llegará la bola, con algo de error y velocidad limitada
            float vc = 1.35F + Math.min(0.9F, golpes * 0.06F) + mio * 0.05F;
            float meta = vx > 0 ? objetivo : (TOP + BOT) / 2F - PH / 2F;
            if (Math.abs(cpu - meta) > 1) cpu += Math.signum(meta - cpu) * Math.min(vc, Math.abs(meta - cpu));
            cpu = Math.max(TOP, Math.min(BOT - PH, cpu));
            if (saque > 0) {
                saque--;
                if (saque == 0) predecir();
                return;
            }
            for (int s = 0; s < 2; s++) mover(0.5F);
        }

        void predecir() {
            float x = bx, y = by, dvx = vx, dvy = vy;
            if (dvx <= 0) return;
            int n = 0;
            while (x < 150 && n++ < 400) {
                x += dvx;
                y += dvy;
                if (y < TOP || y > BOT - 3) dvy = -dvy;
            }
            objetivo = y - PH / 2F + (rnd.nextFloat() - 0.5F) * (14 - Math.min(10, golpes));
        }

        void mover(float f) {
            bx += vx * f;
            by += vy * f;
            if (by < TOP) {
                by = TOP;
                vy = Math.abs(vy);
                sonido(ModSounds.GB_REBOTE, 0.7F);
            }
            if (by > BOT - 3) {
                by = BOT - 3;
                vy = -Math.abs(vy);
                sonido(ModSounds.GB_REBOTE, 0.7F);
            }
            if (vx < 0 && choca(bx, by, 3, 3, 6, yo, 3, PH)) rebotar(yo, 1);
            else if (vx > 0 && choca(bx, by, 3, 3, 151, cpu, 3, PH)) rebotar(cpu, -1);
            if (bx < -4) punto(false);
            else if (bx > Lcd.W + 1) punto(true);
        }

        void rebotar(float pala, int dir) {
            golpes++;
            vel = Math.min(3.6F, vel + 0.1F);
            float off = ((by + 1.5F) - (pala + PH / 2F)) / (PH / 2F);
            float a = Math.max(-1, Math.min(1, off)) * (float) Math.toRadians(55);
            vx = (float) Math.cos(a) * vel * dir;
            vy = (float) Math.sin(a) * vel;
            bx = dir > 0 ? 9 : 148;
            if (dir > 0) {
                puntos += 10;
                predecir();
            }
            sonido(ModSounds.GB_REBOTE, dir > 0 ? 1.0F : 1.2F);
        }

        void punto(boolean mio) {
            if (mio) {
                this.mio++;
                puntos += 100;
                sonido(ModSounds.GB_PUNTO);
            } else {
                suyo++;
                sonido(ModSounds.GB_EXPLOSION, 1.5F);
            }
            if (this.mio >= META || suyo >= META) {
                boolean gano = this.mio >= META;
                if (gano) puntos += 500 + (META - suyo) * 100;
                mensajeFin = t(gano ? "ganas" : "pierdes");
                fin = true;
                return;
            }
            servir(mio ? -1 : 1);
        }

        static final String[][] DIG = {
                {"333", "3.3", "3.3", "3.3", "333"}, {".3.", "33.", ".3.", ".3.", "333"}, {"333", "..3", "333", "3..", "333"},
                {"333", "..3", "333", "..3", "333"}, {"3.3", "3.3", "333", "..3", "..3"}, {"333", "3..", "333", "..3", "333"},
                {"333", "3..", "333", "3.3", "333"}, {"333", "..3", "..3", "..3", "..3"}, {"333", "3.3", "333", "3.3", "333"},
                {"333", "3.3", "333", "..3", "333"}};

        static void digito(Lcd l, int d, int x, int y) {
            String[] s = DIG[d % 10];
            for (int j = 0; j < 5; j++)
                for (int i = 0; i < 3; i++) if (s[j].charAt(i) == '3') l.rect(x + i * 3, y + j * 3, 3, 3, 3);
        }

        @Override
        public void dibujar(Lcd l) {
            l.rect(0, TOP - 2, Lcd.W, 2, 3);
            l.rect(0, BOT, Lcd.W, 2, 3);
            for (int y = TOP + 2; y < BOT; y += 8) l.rect(79, y, 2, 4, 1);
            digito(l, mio, 52, TOP + 6);
            digito(l, suyo, 100, TOP + 6);
            l.texto(cifras(puntos, 5), 2, 1, 2);
            l.rect(6, Math.round(yo), 3, PH, 3);
            l.rect(151, Math.round(cpu), 3, PH, 3);
            if (saque == 0 || (saque / 6) % 2 == 0) l.rect(Math.round(bx), Math.round(by), 3, 3, 3);
        }
    }

    // =================================================================== PÁJARO SALTARÍN
    static final class Pajaro extends JuegoGB {
        static final int SUELO = 128, PW = 18, X = 40;
        static final String[][] PAJ = {
                {"..3333..", ".300033.", "3002003.3", "330000333", "3110002223", ".31100003.", "..33333.."},
                {"..3333..", ".300033.", "3002003.3", "300000333", "3000002223", ".33300003.", "..33333.."},
                {"..3333..", ".333033.", "3112003.3", "311000333", "3000002223", ".30000003.", "..33333.."}};
        float y, vy, vel, scroll, fondo;
        final List<float[]> tubos = new ArrayList<>();
        int estadoP, alas, muertoT;

        Pajaro(Botones b) {
            super(b);
        }

        @Override
        public void reiniciar() {
            y = 60;
            vy = 0;
            vel = 1.0F;
            puntos = 0;
            fin = false;
            tubos.clear();
            estadoP = 0;
            muertoT = 0;
        }

        @Override
        public void paso() {
            alas++;
            boolean salto = b.pulsa(Botones.A) || b.pulsa(Botones.ARRIBA);
            if (estadoP == 0) {
                y = 60 + (float) Math.sin(alas * 0.1) * 3;
                scroll += vel;
                if (salto) {
                    estadoP = 1;
                    vy = -2.7F;
                    sonido(ModSounds.GB_ALETEO);
                }
                return;
            }
            if (estadoP == 1) {
                if (salto) {
                    vy = -2.7F;
                    sonido(ModSounds.GB_ALETEO);
                }
                scroll += vel;
                fondo += vel * 0.3F;
                if (tubos.isEmpty() || tubos.get(tubos.size() - 1)[0] < Lcd.W - 74)
                    tubos.add(new float[]{Lcd.W + 4, 30 + rnd.nextInt(SUELO - 30 - 44 - 10), 0});
                for (Iterator<float[]> it = tubos.iterator(); it.hasNext(); ) {
                    float[] t = it.next();
                    t[0] -= vel;
                    if (t[2] == 0 && t[0] + PW < X) {
                        t[2] = 1;
                        puntos++;
                        vel = Math.min(1.8F, 1.0F + puntos * 0.02F);
                        sonido(ModSounds.GB_PUNTO);
                    }
                    if (t[0] < -PW - 4) it.remove();
                    else if (choca(X + 1, y + 1, 7, 5, t[0], -50, PW, t[1] + 50) || choca(X + 1, y + 1, 7, 5, t[0], t[1] + 44, PW, 200)) {
                        estadoP = 2;
                        vy = -1.5F;
                        sonido(ModSounds.GB_EXPLOSION);
                    }
                }
            }
            vy = Math.min(3.5F, vy + 0.16F);
            y += vy;
            if (y < -8) {
                y = -8;
                vy = 0;
            }
            if (y > SUELO - 7) {
                y = SUELO - 7;
                if (estadoP == 1) sonido(ModSounds.GB_EXPLOSION);
                estadoP = 2;
                if (++muertoT > 30) fin = true;
            }
        }

        @Override
        public void dibujar(Lcd l) {
            // fondo: nubes y ciudad en tono 1, con paralaje
            int f = (int) fondo;
            for (int i = 0; i < 6; i++) {
                int x = ((i * 37 - f / 2) % 222 + 222) % 222 - 30;
                l.rect(x, 20 + (i % 3) * 9, 18, 4, 1);
                l.rect(x + 3, 17 + (i % 3) * 9, 10, 3, 1);
            }
            for (int i = 0; i < 12; i++) {
                int x = ((i * 16 - f) % 192 + 192) % 192 - 16;
                int h = 10 + (i * 7 % 5) * 5;
                l.rect(x, SUELO - h, 14, h, 1);
                for (int k = 0; k < h - 4; k += 5) l.rect(x + 3, SUELO - h + 3 + k, 2, 2, 0);
            }
            for (float[] t : tubos) {
                int x = Math.round(t[0]), g = Math.round(t[1]);
                tubo(l, x, 0, g - 6);
                tubo(l, x, g + 44 + 6, SUELO - g - 50);
                l.rect(x - 2, g - 6, PW + 4, 6, 3);
                l.rect(x - 1, g - 5, PW + 2, 4, 2);
                l.rect(x, g - 5, 3, 4, 1);
                l.rect(x - 2, g + 44, PW + 4, 6, 3);
                l.rect(x - 1, g + 45, PW + 2, 4, 2);
                l.rect(x, g + 45, 3, 4, 1);
            }
            l.rect(0, SUELO, Lcd.W, Lcd.H - SUELO, 2);
            l.rect(0, SUELO, Lcd.W, 2, 3);
            int s = (int) scroll;
            for (int x = -(s % 8); x < Lcd.W; x += 8) l.rect(x, SUELO + 3, 4, 2, 1);
            int fr = estadoP == 2 ? 2 : vy < -1 ? (alas / 4) % 2 : 1;
            if (estadoP == 0) fr = (alas / 8) % 2;
            l.sprite(PAJ[fr], X, Math.round(y));
            l.textoC(Integer.toString(puntos), 80, 6, 3);
            if (estadoP == 0) {
                l.caja(40, 84, 80, 22);
                l.textoC(t("pulsa_a"), 80, 91, 3);
            }
        }

        static void tubo(Lcd l, int x, int y, int h) {
            if (h <= 0) return;
            l.rect(x, y, PW, h, 3);
            l.rect(x + 1, y, PW - 2, h, 2);
            l.rect(x + 2, y, 3, h, 1);
            l.rect(x + 6, y, 1, h, 0);
        }
    }

    // =================================================================== INVASORES
    static final class Invasores extends JuegoGB {
        static final int COLS = 8, FILAS = 5, SUELO = 134, CY = 126;
        static final String[][] ALIEN = {
                {"...33...", "..3333..", ".3.33.3.", "33333333", "..3..3..", ".3.33.3."},
                {"...33...", "..3333..", ".3.33.3.", "33333333", ".3.33.3.", "3......3"},
                {".3....3.", "..3333..", ".3.33.3.", "33333333", "3.3..3.3", "..3..3.."},
                {".3....3.", "3.3333.3", "33.33.33", "33333333", ".3....3.", "3......3"},
                {"..3333..", ".333333.", "33.33.33", "33333333", ".33..33.", "3......3"},
                {"..3333..", ".333333.", "33.33.33", "33333333", "..3..3..", ".3.33.3."}};
        static final String[] CANON = {"....3....", "...333...", ".3333333.", "333333333", "333333333"};
        static final String[] OVNI = {"...3333...", ".33333333.", "3.33.33.33", "3333333333", "..33..33.."};
        static final String[] BOOM = {"3...3...3", ".3.3.3.3.", "..3...3..", "33.....33", "..3...3..", ".3.3.3.3.", "3...3...3"};
        static final String[] ESCUDO = {"...3333333...", "..333333333..", ".33333333333.", "3333333333333", "3333333333333", "3333.....3333", "333.......333"};
        boolean[][] vivo;
        float fx, fy, cx;
        int dir, ritmo, marcha, frame, vidas, oleada, vivos, muerteT, ovniT;
        float[] bala;
        final List<float[]> balas = new ArrayList<>();
        final List<int[]> booms = new ArrayList<>();
        boolean[][][] escudos;
        float ovniX;
        int ovniDir;

        Invasores(Botones b) {
            super(b);
        }

        @Override
        public void reiniciar() {
            puntos = 0;
            vidas = 3;
            oleada = 0;
            fin = false;
            escudos = new boolean[4][ESCUDO.length][ESCUDO[0].length()];
            for (boolean[][] e : escudos)
                for (int j = 0; j < ESCUDO.length; j++)
                    for (int i = 0; i < ESCUDO[0].length(); i++) e[j][i] = ESCUDO[j].charAt(i) == '3';
            nuevaOleada();
        }

        void nuevaOleada() {
            vivo = new boolean[FILAS][COLS];
            for (boolean[] f : vivo) java.util.Arrays.fill(f, true);
            vivos = FILAS * COLS;
            fx = 14;
            fy = 22 + Math.min(3, oleada) * 4;
            dir = 1;
            cx = 76;
            bala = null;
            balas.clear();
            ovniT = 60 * 20;
            ovniDir = 0;
        }

        int puntosFila(int f) {
            return f == 0 ? 30 : f < 3 ? 20 : 10;
        }

        @Override
        public void paso() {
            for (Iterator<int[]> it = booms.iterator(); it.hasNext(); ) if (--it.next()[2] <= 0) it.remove();
            if (muerteT > 0) {
                if (++muerteT > 80) {
                    muerteT = 0;
                    balas.clear();
                    if (vidas <= 0) fin = true;
                }
                return;
            }
            if (b.mantiene(Botones.IZQ)) cx -= 1.3F;
            if (b.mantiene(Botones.DER)) cx += 1.3F;
            cx = Math.max(2, Math.min(Lcd.W - 11, cx));
            if (bala == null && (b.pulsa(Botones.A) || b.pulsa(Botones.ARRIBA))) {
                bala = new float[]{cx + 4, CY - 3};
                sonido(ModSounds.GB_DISPARO);
            }
            // la flota avanza a saltos; cuantos menos quedan, más rápido
            ritmo++;
            int cada = 2 + vivos * 3 / 4;
            if (ritmo >= cada) {
                ritmo = 0;
                frame ^= 1;
                float[] lim = limites();
                if ((dir > 0 && lim[1] + 2 > Lcd.W - 3) || (dir < 0 && lim[0] - 2 < 3)) {
                    dir = -dir;
                    fy += 5;
                } else fx += 2 * dir;
                sonido(ModSounds.GB_MOVER, new float[]{0.5F, 0.45F, 0.4F, 0.36F}[marcha++ % 4]);
                if (fy + lim[2] >= CY - 2) {
                    vidas = 0;
                    muerteT = 1;
                    booms.add(new int[]{(int) cx, CY - 1, 30});
                    sonido(ModSounds.GB_EXPLOSION);
                    return;
                }
            }
            // disparos enemigos
            int maxB = Math.min(4, 2 + oleada);
            if (balas.size() < maxB && rnd.nextInt(Math.max(12, 45 - oleada * 5)) == 0) {
                int c = rnd.nextInt(COLS);
                for (int f = FILAS - 1; f >= 0; f--)
                    if (vivo[f][c]) {
                        balas.add(new float[]{fx + c * 14 + 3, fy + f * 12 + 6});
                        break;
                    }
            }
            // ovni
            if (ovniDir == 0 && --ovniT <= 0) {
                ovniDir = rnd.nextBoolean() ? 1 : -1;
                ovniX = ovniDir > 0 ? -12 : Lcd.W + 2;
            }
            if (ovniDir != 0) {
                ovniX += 0.7F * ovniDir;
                if ((alas++ & 7) == 0) sonido(ModSounds.GB_GIRAR, 0.5F);
                if (ovniX < -14 || ovniX > Lcd.W + 4) {
                    ovniDir = 0;
                    ovniT = 60 * (18 + rnd.nextInt(12));
                }
            }
            // tu bala
            if (bala != null) {
                bala[1] -= 4;
                if (bala[1] < 10) bala = null;
                else if (escudo(bala[0], bala[1], 3)) bala = null;
                else if (ovniDir != 0 && choca(bala[0], bala[1], 1, 4, ovniX, 11, 10, 5)) {
                    int p = new int[]{50, 100, 150, 300}[rnd.nextInt(4)];
                    puntos += p;
                    booms.add(new int[]{(int) ovniX, 10, 25});
                    ovniDir = 0;
                    ovniT = 60 * 25;
                    bala = null;
                    sonido(ModSounds.GB_LINEA);
                } else {
                    outer:
                    for (int f = 0; f < FILAS; f++)
                        for (int c = 0; c < COLS; c++) {
                            if (!vivo[f][c]) continue;
                            float ax = fx + c * 14, ay = fy + f * 12;
                            if (choca(bala[0], bala[1], 1, 4, ax, ay, 8, 6)) {
                                vivo[f][c] = false;
                                vivos--;
                                puntos += puntosFila(f);
                                booms.add(new int[]{(int) ax, (int) ay, 12});
                                bala = null;
                                sonido(ModSounds.GB_EXPLOSION, 1.6F);
                                break outer;
                            }
                        }
                    if (vivos == 0) {
                        oleada++;
                        puntos += 200;
                        sonido(ModSounds.GB_INICIO);
                        nuevaOleada();
                        return;
                    }
                }
            }
            for (Iterator<float[]> it = balas.iterator(); it.hasNext(); ) {
                float[] e = it.next();
                e[1] += 1.4F + oleada * 0.1F;
                if (e[1] > SUELO) it.remove();
                else if (escudo(e[0], e[1] + 4, -3)) it.remove();
                else if (bala != null && Math.abs(bala[0] - e[0]) < 3 && Math.abs(bala[1] - e[1]) < 4) {
                    it.remove();
                    bala = null;
                } else if (choca(e[0], e[1], 3, 5, cx, CY, 9, 5)) {
                    it.remove();
                    vidas--;
                    muerteT = 1;
                    booms.add(new int[]{(int) cx, CY - 1, 40});
                    sonido(ModSounds.GB_EXPLOSION, 0.7F);
                    return;
                }
            }
        }

        int alas;

        float[] limites() {
            float min = 999, max = -999, abajo = 0;
            for (int f = 0; f < FILAS; f++)
                for (int c = 0; c < COLS; c++)
                    if (vivo[f][c]) {
                        min = Math.min(min, fx + c * 14);
                        max = Math.max(max, fx + c * 14 + 8);
                        abajo = Math.max(abajo, f * 12 + 6);
                    }
            return new float[]{min, max, abajo};
        }

        /** ¿La bala toca un escudo? Si es así, lo muerde. */
        boolean escudo(float x, float y, int empuje) {
            for (int e = 0; e < 4; e++) {
                int ex = 14 + e * 38, ey = 104;
                int i = (int) (x - ex), j = (int) (y - ey);
                if (i < 0 || j < 0 || j >= ESCUDO.length || i >= ESCUDO[0].length()) continue;
                if (!escudos[e][j][i]) continue;
                for (int dj = -2; dj <= 2; dj++)
                    for (int di = -2; di <= 2; di++) {
                        int a = i + di, c = j + dj + (empuje > 0 ? -1 : 1);
                        if (a >= 0 && c >= 0 && c < ESCUDO.length && a < ESCUDO[0].length() && (Math.abs(di) + Math.abs(dj) < 3 || rnd.nextBoolean()))
                            escudos[e][c][a] = false;
                    }
                return true;
            }
            return false;
        }

        @Override
        public void dibujar(Lcd l) {
            l.texto(cifras(puntos, 5), 2, 1, 3);
            l.textoD(t("oleada") + " " + (oleada + 1), 158, 1, 2);
            for (int f = 0; f < FILAS; f++)
                for (int c = 0; c < COLS; c++)
                    if (vivo[f][c]) l.sprite(ALIEN[(f == 0 ? 0 : f < 3 ? 2 : 4) + frame], Math.round(fx) + c * 14, Math.round(fy) + f * 12);
            if (ovniDir != 0) l.sprite(OVNI, Math.round(ovniX), 11);
            for (int e = 0; e < 4; e++)
                for (int j = 0; j < ESCUDO.length; j++)
                    for (int i = 0; i < ESCUDO[0].length(); i++) if (escudos[e][j][i]) l.px(14 + e * 38 + i, 104 + j, 2);
            if (bala != null) l.rect(Math.round(bala[0]), Math.round(bala[1]), 1, 4, 3);
            for (float[] e : balas) {
                int x = Math.round(e[0]), y = Math.round(e[1]);
                boolean z = ((int) (e[1] / 3)) % 2 == 0;
                l.px(x + (z ? 0 : 2), y, 3);
                l.px(x + 1, y + 1, 3);
                l.px(x + (z ? 2 : 0), y + 2, 3);
                l.px(x + 1, y + 3, 3);
                l.px(x + (z ? 0 : 2), y + 4, 3);
            }
            for (int[] bm : booms) l.sprite(BOOM, bm[0], bm[1]);
            if (muerteT == 0 || (muerteT / 6) % 2 == 0) l.sprite(CANON, Math.round(cx), CY);
            l.rect(0, SUELO, Lcd.W, 1, 3);
            for (int i = 0; i < Math.max(0, vidas - 1); i++) l.sprite(CANON, 3 + i * 12, SUELO + 3);
        }
    }
}
