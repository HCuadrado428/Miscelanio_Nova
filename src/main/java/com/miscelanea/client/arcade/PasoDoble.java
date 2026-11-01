package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvent;

/**
 * Paso Doble: las flechas suben al ritmo de la canción y hay que pulsar la dirección justo cuando llegan arriba.
 * Tres canciones propias con tres dificultades; uno o dos jugadores a la vez (cada uno con su dificultad).
 */
public class PasoDoble extends JuegoArcade {
    static final int MENU = 0, BAILE = 1, RESULTADO = 2;
    static final int OBJ_Y = 30, LATENCIA = 5, VENTANA = 10;
    public static final int[] CARRIL_BOTON = {Botones.IZQ, Botones.ABAJO, Botones.ARRIBA, Botones.DER};
    static final int[] CARRIL_COLOR = {43, 63, 28, 11}, CARRIL_LUZ = {44, 40, 29, 12};
    static final float[] VELOCIDAD = {1.7F, 2.2F, 2.7F};
    public static final String[] FLECHA = {
            "......##......", ".....####.....", "....######....", "...########...", "..##########..", ".############.",
            "##############", "....######....", "....######....", "....######....", "....######....", "....######....", "....######....", "....######...."};
    static boolean[][][] mascaras;

    final class Bailarin {
        final int lado;
        int dif = 1, puntosB, combo, maxCombo, vida = 60, perfectos, geniales, bienes, fallos;
        boolean listo, fuera;
        int[] notas;
        boolean[] hecha;
        int juicio = -1, juicioT;
        final int[] pulsado = new int[4];

        Bailarin(int lado) {
            this.lado = lado;
        }

        void preparar(int cancion) {
            // las notas dobles se separan en dos flechas (carril y el opuesto)
            int[] base = RitmoCanciones.NOTAS[cancion][dif];
            java.util.List<Integer> l = new java.util.ArrayList<>();
            for (int n : base) {
                l.add(n & ~4);
                if ((n & 4) != 0) l.add(((n >> 3) << 3) | (((n & 3) + 2) % 4));
            }
            notas = l.stream().mapToInt(Integer::intValue).toArray();
            hecha = new boolean[notas.length];
            puntosB = combo = maxCombo = perfectos = geniales = bienes = fallos = 0;
            vida = 70;
            fuera = false;
        }

        void paso(Botones bb, int tiempo) {
            if (juicioT > 0) juicioT--;
            for (int c = 0; c < 4; c++) if (pulsado[c] > 0) pulsado[c]--;
            if (fuera) return;
            for (int c = 0; c < 4; c++) {
                if (!bb.pulsa(CARRIL_BOTON[c])) continue;
                pulsado[c] = 8;
                int mejor = -1, md = 99;
                for (int i = 0; i < notas.length; i++) {
                    if (hecha[i] || (notas[i] & 3) != c) continue;
                    int d = Math.abs((notas[i] >> 3) - tiempo);
                    if (d < md) {
                        md = d;
                        mejor = i;
                    }
                    if ((notas[i] >> 3) - tiempo > VENTANA) break;
                }
                if (mejor >= 0 && md <= VENTANA) {
                    hecha[mejor] = true;
                    int j = md <= 3 ? 0 : md <= 6 ? 1 : 2;
                    juzgar(j);
                }
            }
            // notas que se han pasado
            for (int i = 0; i < notas.length; i++) {
                if (hecha[i]) continue;
                int t = notas[i] >> 3;
                if (t > tiempo) break;
                if (tiempo - t > VENTANA) {
                    hecha[i] = true;
                    juzgar(3);
                }
            }
        }

        void juzgar(int j) {
            juicio = j;
            juicioT = 30;
            switch (j) {
                case 0 -> {
                    perfectos++;
                    puntosB += 300 + combo * 2;
                    vida = Math.min(100, vida + 2);
                }
                case 1 -> {
                    geniales++;
                    puntosB += 200 + combo;
                    vida = Math.min(100, vida + 1);
                }
                case 2 -> {
                    bienes++;
                    puntosB += 100;
                }
                default -> {
                    fallos++;
                    vida -= 6;
                }
            }
            if (j < 3) combo++;
            else combo = 0;
            maxCombo = Math.max(maxCombo, combo);
            if (vida <= 0) {
                vida = 0;
                fuera = true;
                sonido(ModSounds.GB_FIN, 0.8F);
            }
        }

        String nota() {
            int total = notas.length * 300;
            float p = total == 0 ? 0 : (perfectos * 300 + geniales * 200 + bienes * 100) / (float) total;
            if (fuera) return "E";
            return p >= 0.93F ? "S" : p >= 0.8F ? "A" : p >= 0.65F ? "B" : p >= 0.5F ? "C" : "D";
        }
    }

    final Bailarin[] bs = {new Bailarin(0), new Bailarin(1)};
    int estado, cancion, sel, t, tiempoCancion;

    public PasoDoble(Botones[] b, long semilla, int jugadores) {
        super(b, semilla, jugadores);
    }

    @Override
    public void reiniciar() {
        Lucha.cargar();
        if (mascaras == null) {
            mascaras = new boolean[4][14][14];
            for (int y = 0; y < 14; y++)
                for (int x = 0; x < 14; x++) {
                    boolean v = FLECHA[y].charAt(x) == '#';
                    mascaras[2][y][x] = v;                 // arriba
                    mascaras[1][13 - y][x] = v;            // abajo
                    mascaras[0][x][y] = v;                 // izquierda
                    mascaras[3][x][13 - y] = v;            // derecha
                }
        }
        estado = MENU;
        t = 0;
        puntos = 0;
        fin = false;
    }

    SoundEvent musicaDe(int c) {
        return switch (c) {
            case 1 -> ModSounds.RI_NEON.get();
            case 2 -> ModSounds.RI_TORMENTA.get();
            default -> ModSounds.RI_VERBENA.get();
        };
    }

    @Override
    public void paso() {
        t++;
        switch (estado) {
            case MENU -> {
                Botones b0 = b[0];
                if (b0.pulsa(Botones.ARRIBA)) {
                    cancion = (cancion + 2) % 3;
                    sonido(ModSounds.GB_MOVER, 1F);
                }
                if (b0.pulsa(Botones.ABAJO)) {
                    cancion = (cancion + 1) % 3;
                    sonido(ModSounds.GB_MOVER, 1F);
                }
                for (int i = 0; i < jugadores; i++) {
                    Botones bb = b[i];
                    if (bb.pulsa(Botones.IZQ)) bs[i].dif = Math.max(0, bs[i].dif - 1);
                    if (bb.pulsa(Botones.DER)) bs[i].dif = Math.min(2, bs[i].dif + 1);
                    if (bb.pulsa(Botones.A) || bb.pulsa(Botones.START)) bs[i].listo = true;
                }
                boolean todos = bs[0].listo && (jugadores < 2 || bs[1].listo);
                if (todos || t > 60 * 30) {
                    for (int i = 0; i < 2; i++) bs[i].preparar(cancion);
                    bs[1].fuera = jugadores < 2;
                    estado = BAILE;
                    t = 0;
                    cambiarMusica = musicaDe(cancion);
                }
            }
            case BAILE -> {
                tiempoCancion = t - LATENCIA;
                for (int i = 0; i < jugadores; i++) bs[i].paso(b[i], tiempoCancion);
                boolean vivos = !bs[0].fuera || (jugadores >= 2 && !bs[1].fuera);
                if (tiempoCancion > RitmoCanciones.DURACION[cancion] + 30 || !vivos) {
                    estado = RESULTADO;
                    t = 0;
                    pararMusica = true;
                    puntos = Math.max(bs[0].puntosB, jugadores >= 2 ? bs[1].puntosB : 0);
                    sonido(vivos ? ModSounds.GB_INICIO : ModSounds.GB_FIN, 1F);
                }
            }
            case RESULTADO -> {
                if (t > 240 || (t > 60 && b[0].pulsa(Botones.A))) {
                    fin = true;
                    mensajeFin = I18n.get("paso.miscelanea.nota", bs[0].nota());
                }
            }
            default -> {}
        }
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void dibujar(Pantalla p) {
        int beat = 3600 / RitmoCanciones.BPM[cancion];       // pasos por pulso
        boolean golpe = estado == BAILE && tiempoCancion > 0 && (tiempoCancion % beat) < 5;
        p.limpiar(golpe ? 42 : 41);
        // ecualizador de fondo
        for (int k = 0; k < 16; k++) {
            int h = estado == BAILE ? 10 + (int) (Math.abs(Math.sin(k * 1.7 + t * 0.09)) * (golpe ? 50 : 30)) : 8;
            p.rect(k * 16 + 2, Pantalla.H - h, 12, h, k % 2 == 0 ? 43 : 45);
        }
        for (int y = 0; y < Pantalla.H; y += 3) p.rect(0, y, Pantalla.W, 1, 41);
        if (estado == MENU) {
            menu(p);
            return;
        }
        if (jugadores >= 2) {
            pista(p, bs[0], 20);
            pista(p, bs[1], 140);
        } else {
            pista(p, bs[0], 60);
            bailarin(p, golpe, beat);
        }
        if (estado == RESULTADO) resultado(p);
    }

    void menu(Pantalla p) {
        p.rect(0, 6, Pantalla.W, 18, 0);
        p.textoC(I18n.get("paso.miscelanea.titulo"), 128, 11, 20);
        for (int c = 0; c < 3; c++) {
            int y = 40 + c * 26;
            boolean s = c == cancion;
            p.rect(30, y - 4, 196, 20, s ? 45 : 1);
            if (s) p.marco(30, y - 4, 196, 20, 20);
            p.texto(I18n.get("paso.miscelanea.cancion." + c), 40, y + 2, s ? 7 : 5);
            p.textoD(RitmoCanciones.BPM[c] + " BPM", 218, y + 2, s ? 20 : 4);
        }
        String[] dif = {"paso.miscelanea.facil", "paso.miscelanea.normal", "paso.miscelanea.dificil"};
        for (int i = 0; i < jugadores; i++) {
            int x = jugadores >= 2 ? (i == 0 ? 70 : 186) : 128;
            p.textoC((i == 0 ? "1P" : "2P") + ": ◄ " + I18n.get(dif[bs[i].dif]) + " ►", x, 128, bs[i].listo ? 29 : 7);
        }
        p.textoC(I18n.get("paso.miscelanea.ayuda"), 128, 150, 6);
        if ((t / 20) % 2 == 0) p.textoC(I18n.get("paso.miscelanea.empezar"), 128, 166, 19);
    }

    void pista(Pantalla p, Bailarin bl, int x0) {
        int tiempo = tiempoCancion;
        float vel = VELOCIDAD[bl.dif];
        // receptores
        for (int c = 0; c < 4; c++) {
            int x = x0 + c * 24;
            flecha(p, x, OBJ_Y, c, bl.pulsado[c] > 0 ? 7 : 3, bl.pulsado[c] > 0 ? 6 : 2, true);
        }
        // flechas que suben
        if (bl.notas != null)
            for (int i = 0; i < bl.notas.length; i++) {
                if (bl.hecha[i]) continue;
                int nt = bl.notas[i] >> 3, c = bl.notas[i] & 3;
                int y = OBJ_Y + Math.round((nt - tiempo) * vel);
                if (y > Pantalla.H + 10) break;
                if (y < -16) continue;
                flecha(p, x0 + c * 24, y, c, CARRIL_COLOR[c], CARRIL_LUZ[c], false);
            }
        // vida, puntos, juicio y combo
        p.rect(x0, 6, 92, 6, 0);
        p.rect(x0 + 1, 7, Math.round(90 * bl.vida / 100F), 4, bl.vida < 25 ? 11 : 29);
        p.textoD(Integer.toString(bl.puntosB), x0 + 92, 14, 7);
        if (bl.juicioT > 0) {
            String[] j = {"paso.miscelanea.perfecto", "paso.miscelanea.genial", "paso.miscelanea.bien", "paso.miscelanea.fallo"};
            int[] col = {20, 63, 29, 11};
            p.textoC(I18n.get(j[bl.juicio]), x0 + 46, 70, col[bl.juicio]);
        }
        if (bl.combo >= 5) p.textoC(bl.combo + " COMBO", x0 + 46, 84, 19);
        if (bl.fuera && estado == BAILE) p.textoC(I18n.get("paso.miscelanea.fuera"), x0 + 46, 100, 11);
    }

    void flecha(Pantalla p, int x, int y, int c, int col, int luz, boolean receptor) {
        boolean[][] m = mascaras[c];
        for (int yy = 0; yy < 14; yy++)
            for (int xx = 0; xx < 14; xx++) {
                boolean v = m[yy][xx];
                if (v) {
                    if (receptor) {
                        boolean borde = yy == 0 || xx == 0 || yy == 13 || xx == 13 || !m[yy - 1][xx] || !m[yy + 1][xx] || !m[yy][xx - 1] || !m[yy][xx + 1];
                        if (borde) p.px(x + xx, y + yy, col);
                        else if (col == 7) p.px(x + xx, y + yy, luz);
                    } else p.px(x + xx, y + yy, yy + xx < 12 ? luz : col);
                } else {
                    boolean vecino = (yy > 0 && m[yy - 1][xx]) || (yy < 13 && m[yy + 1][xx]) || (xx > 0 && m[yy][xx - 1]) || (xx < 13 && m[yy][xx + 1]);
                    if (vecino && !receptor) p.px(x + xx, y + yy, 0);
                }
            }
    }

    void bailarin(Pantalla p, boolean golpe, int beat) {
        int[] pasos = {Lucha.QUIETO, Lucha.VICTORIA, Lucha.PUNO, Lucha.PATADA, Lucha.SALTO, Lucha.PUNO2, Lucha.AGACHADO, Lucha.VICTORIA};
        int k = estado == BAILE && tiempoCancion > 0 ? (tiempoCancion / beat) : t / 30;
        int f = bs[0].fuera ? Lucha.TUMBADO : bs[0].combo >= 10 || estado != BAILE ? pasos[k % pasos.length] : (k % 2 == 0 ? Lucha.QUIETO : Lucha.QUIETO + 2);
        Lucha.sombra(p, 205, 176, 16);
        Lucha.dibujar(p, Lucha.LUNA, f, 205, 176 - (golpe ? 2 : 0), k % 4 < 2 ? 1 : -1, 0);
    }

    void resultado(Pantalla p) {
        p.rect(28, 40, 200, 110, 0);
        p.marco(28, 40, 200, 110, 45);
        p.textoC(I18n.get("paso.miscelanea.resultado"), 128, 46, 20);
        for (int i = 0; i < jugadores; i++) {
            Bailarin bl = bs[i];
            int x = jugadores >= 2 ? (i == 0 ? 78 : 178) : 128;
            p.textoC(bl.nota(), x, 62, bl.nota().equals("S") ? 20 : 7);
            p.textoC(I18n.get("paso.miscelanea.perfecto") + " " + bl.perfectos, x, 78, 20);
            p.textoC(I18n.get("paso.miscelanea.genial") + " " + bl.geniales, x, 90, 63);
            p.textoC(I18n.get("paso.miscelanea.bien") + " " + bl.bienes, x, 102, 29);
            p.textoC(I18n.get("paso.miscelanea.fallo") + " " + bl.fallos, x, 114, 11);
            p.textoC("COMBO " + bl.maxCombo, x, 128, 7);
        }
    }
}
