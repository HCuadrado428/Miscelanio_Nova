package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;

/** Piezas comunes de los juegos de lucha (Duelo Final y Puños de Barrio): cuadros de animación, hojas, mandos y dibujo. */
final class Lucha {
    private Lucha() {}

    static final String[] PJ = {"kai", "luna", "toro", "chispa", "chupa", "chandal", "grandullon", "jefe"};
    static final int KAI = 0, LUNA = 1, TORO = 2, CHISPA = 3, CHUPA = 4, CHANDAL = 5, GRANDULLON = 6, JEFE = 7;
    static final int TAM = 88, PIES = 85, CADERA = 40;

    // cuadros de la hoja de cada luchador
    static final int QUIETO = 0, ANDAR = 4, AGACHADO = 10, SALTO = 11, SALTO2 = 12, PUNO_P = 13, PUNO = 14, PATADA_P = 15, PATADA = 16,
            BARRIDO = 17, AEREA = 18, BLOQUEO = 19, BLOQ_AGACH = 20, DANO = 21, DANO_AGACH = 22, CAIDA1 = 23, CAIDA2 = 24, TUMBADO = 25,
            LEVANTA = 26, ESP1 = 27, ESP2 = 28, VICTORIA = 29, PUNO2 = 30, GANCHO = 31;

    // efectos (hoja de 32x32)
    static final int EF_GOLPE = 0, EF_BLOQUEO = 3, EF_POLVO = 5, EF_ONDA = 8, EF_LLAVE = 11, EF_POLLO = 15, EF_BOLSA = 16, EF_CUBO = 17, EF_CUBO_ROTO = 18;

    static Sprite[][] hojas;
    static Sprite[] efectos, caras;

    static void cargar() {
        if (hojas == null) {
            hojas = new Sprite[PJ.length][];
            for (int i = 0; i < PJ.length; i++) hojas[i] = Sprite.hoja("lucha/" + PJ[i], TAM, TAM);
        }
        efectos = Sprite.hoja("lucha/efectos", 32, 32);
        caras = Sprite.hoja("lucha/caras", 32, 32);
    }

    static Sprite cuadro(int pj, int f) {
        Sprite[] h = hojas[pj];
        return h[Math.max(0, Math.min(h.length - 1, f))];
    }

    /** Dibuja un luchador con la cadera en (sx) y los pies en (sy) de pantalla. */
    static void dibujar(Pantalla p, int pj, int f, int sx, int sy, int dir, int destello) {
        Sprite s = cuadro(pj, f);
        boolean esp = dir < 0;
        int x = esp ? sx - (TAM - 1 - CADERA) : sx - CADERA, y = sy - PIES;
        if (destello > 0) p.sprColor(s, x, y, esp, destello % 4 < 2 ? 7 : 12);
        else p.spr(s, x, y, esp);
    }

    /** Sombra elíptica tramada bajo un luchador. */
    static void sombra(Pantalla p, int cx, int cy, int rx) {
        int ry = Math.max(2, rx / 4);
        for (int y = -ry; y <= ry; y++) {
            int w = (int) (rx * Math.sqrt(1 - (y * y) / (double) (ry * ry)));
            int yy = cy + y;
            if (yy < 0 || yy >= Pantalla.H) continue;
            for (int x = cx - w; x <= cx + w; x++) {
                if (x < 0 || x >= Pantalla.W || ((x + yy) & 1) == 0) continue;
                p.buf[yy * Pantalla.W + x] = 1;
            }
        }
    }

    /** Copia una franja de un fondo grande (sin transparencias) desplazada por la cámara. */
    static void fondo(Pantalla p, Sprite s, int camX, int y0) {
        if (s == null) return;
        int w = Math.min(Pantalla.W, s.w - camX);
        for (int y = 0; y < s.h && y + y0 < Pantalla.H; y++) {
            if (y + y0 < 0) continue;
            System.arraycopy(s.px, y * s.w + camX, p.buf, (y + y0) * Pantalla.W, Math.max(0, w));
        }
    }

    /** Barra de vida con rastro rojo de lo perdido. */
    static void barra(Pantalla p, int x, int y, int w, float vida, float rastro, boolean derecha, int color) {
        p.rect(x - 1, y - 1, w + 2, 8, 0);
        p.rect(x, y, w, 6, 2);
        int wr = Math.round(w * Math.max(0, rastro)), wv = Math.round(w * Math.max(0, vida));
        if (derecha) {
            p.rect(x + w - wr, y, wr, 6, 11);
            p.rect(x + w - wv, y, wv, 6, color);
            p.rect(x + w - wv, y, wv, 2, color + 1);
        } else {
            p.rect(x, y, wr, 6, 11);
            p.rect(x, y, wv, 6, color);
            p.rect(x, y, wv, 2, color + 1);
        }
    }

    /** Mandos de un luchador: los de un jugador o los que decide la máquina. */
    static final class Mando {
        final boolean[] m = new boolean[Botones.N], p = new boolean[Botones.N];
        final int[] buffer = new int[Botones.N];

        void desde(Botones b) {
            for (int i = 0; i < Botones.N; i++) {
                m[i] = b.mantiene(i);
                p[i] = b.pulsa(i);
                if (p[i]) buffer[i] = 7;
                else if (buffer[i] > 0) buffer[i]--;
            }
        }

        void desde(int mascara) {
            for (int i = 0; i < Botones.N; i++) {
                boolean ahora = (mascara >> i & 1) != 0;
                p[i] = ahora && !m[i];
                m[i] = ahora;
                if (p[i]) buffer[i] = 7;
                else if (buffer[i] > 0) buffer[i]--;
            }
        }

        /** Pulsación reciente (con memoria de unos pasos), que se gasta al usarla. */
        boolean usar(int b) {
            if (buffer[b] > 0) {
                buffer[b] = 0;
                return true;
            }
            return false;
        }

        void vaciar() {
            java.util.Arrays.fill(m, false);
            java.util.Arrays.fill(p, false);
            java.util.Arrays.fill(buffer, 0);
        }
    }

    /** Efecto visual suelto. */
    static final class Efecto {
        float x, y, vx;
        int tipo, t, dur;
        boolean esp;

        Efecto(int tipo, float x, float y, int dur) {
            this.tipo = tipo;
            this.x = x;
            this.y = y;
            this.dur = dur;
        }
    }
}
