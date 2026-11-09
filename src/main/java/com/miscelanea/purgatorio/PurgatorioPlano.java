package com.miscelanea.purgatorio;

import com.miscelanea.purgatorio.PurgatorioGenerador.Zona;

/**
 * El plano del Purgatorio: qué hay en cada sitio, sin poner un solo bloque (por eso no usa nada de Minecraft y se puede
 * probar y dibujar fuera del juego). Todo sale de un hash de las coordenadas: siempre igual, en todos los mundos.
 * <ul>
 *   <li><b>Pisos</b>: tres, uno debajo de otro, cada {@link #ALTO} bloques (el de arriba, a la altura de siempre). Al
 *       aire libre (la feria y el Mundo sin Texturas) solo se está en el de arriba.</li>
 *   <li><b>Zonas</b>: celdas de Voronoi con peso (unas grandes y otras pequeñas) sobre una rejilla de {@link #CELDA}
 *       chunks, con la raya mordida (el sitio que se mira se mueve un poco cada 2 x 2 chunks): formas raras, nada de
 *       cuadrados. Cada piso tiene su plano.</li>
 *   <li><b>Salas grandes</b>: de vez en cuando, en cada cuadro de {@link #SUPER} x {@link #SUPER} chunks, una sala de
 *       varios chunks según la zona (el pabellón de la feria, el vestíbulo del hotel, la sala de espera enorme...) o un
 *       pasillo que vuelve al mismo sitio.</li>
 *   <li><b>Escaleras</b> que bajan un piso (a otra zona) y <b>ascensores</b> del hotel que te dejan en otro piso.</li>
 * </ul>
 * Lo que siempre se cumple (así las salidas siempre se pueden alcanzar): en cada chunk normal, la calle de en medio de
 * este a oeste (z = 7..8) se puede andar de punta a punta y en todas las rayas entre chunks hay paso en 7..8; solo las
 * escaleras, los pasillos sin salida y las salas grandes cortan algún paso, y siempre de pocos en pocos.
 */
public final class PurgatorioPlano {
    private PurgatorioPlano() {}

    /** Primer bloque de aire sobre el suelo del piso de arriba. */
    public static final int SUELO = 64;
    /** Bloques entre un piso y el siguiente (de suelo a suelo). */
    public static final int ALTO = 16;
    public static final int NIVELES = 3;
    /** Lado (en chunks) de la rejilla de las zonas: cada celda es una zona (de tamaño y forma distintos). */
    public static final int CELDA = 5;
    /** Lado (en chunks) de los cuadros en los que cabe, como mucho, una sala grande. */
    public static final int SUPER = 7;

    /** Suelo (primer bloque de aire) del piso n (0 = arriba). */
    public static int base(int n) {
        return SUELO - ALTO * n;
    }

    /** El piso en el que está algo a esa altura (cada piso va de su suelo - 2 a su suelo + 13). */
    public static int nivel(double y) {
        int n = (int) Math.floor((SUELO + 14 - y - 1e-6) / ALTO);
        return Math.max(0, Math.min(NIVELES - 1, n));
    }

    // ------------------------------------------------------------------ azar fijo
    public static long h(int a, int b, int c) {
        long x = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        x ^= x >>> 31;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 29;
        return x & Long.MAX_VALUE;
    }

    /** Lo mismo, pero distinto en cada piso (el de arriba es el de siempre). */
    public static long hn(int n, int a, int b, int c) {
        return h(a, b, n == 0 ? c : c + 1000 * n);
    }

    // ------------------------------------------------------------------ zonas
    /** Las zonas de cada piso y cuántas celdas de cada (de 100). Al aire libre, solo arriba; abajo, sobre todo garaje. */
    private static final Zona[][] TABLA_Z = {
            {Zona.FERIA, Zona.SALA, Zona.RECREATIVA, Zona.HOTEL, Zona.SIN_TEXTURA, Zona.PISCINA, Zona.COLEGIO, Zona.CENTRO},
            {Zona.SALA, Zona.RECREATIVA, Zona.HOTEL, Zona.PISCINA, Zona.APARCAMIENTO, Zona.COLEGIO, Zona.CENTRO},
            {Zona.SALA, Zona.RECREATIVA, Zona.HOTEL, Zona.PISCINA, Zona.APARCAMIENTO, Zona.COLEGIO, Zona.CENTRO}};
    private static final int[][] TABLA_P = {
            {22, 14, 12, 14, 10, 9, 9, 10},
            {14, 10, 17, 14, 21, 12, 12},
            {12, 9, 13, 12, 34, 10, 10}};
    /** Mientras no estén hechas, las zonas nuevas se cambian por estas (ver {@link #hecha}). */
    static boolean nuevas = false;

    static boolean hecha(Zona z) {
        return nuevas || z.ordinal() <= Zona.SIN_TEXTURA.ordinal();
    }

    private static Zona deTabla(int n, long k) {
        Zona[] zs = TABLA_Z[n];
        int[] ps = TABLA_P[n];
        int t = 0;
        for (int p : ps) t += p;
        int r = (int) (k % t);
        for (int i = 0; i < zs.length; i++) {
            if (r < ps[i]) {
                if (hecha(zs[i])) return zs[i];
                break;
            }
            r -= ps[i];
        }
        Zona[] viejas = n == 0 ? new Zona[]{Zona.FERIA, Zona.SALA, Zona.RECREATIVA, Zona.HOTEL, Zona.SIN_TEXTURA} : new Zona[]{Zona.SALA, Zona.RECREATIVA, Zona.HOTEL};
        return viejas[(int) ((k >> 20) % viejas.length)];
    }

    /** La zona de una celda de la rejilla (la del origen, en el piso de arriba, siempre es la feria). */
    static Zona zonaCelda(int n, int gx, int gz) {
        if (n == 0 && gx == 0 && gz == 0) return Zona.FERIA;
        return deTabla(n, hn(n, gx, gz, 5) >> 3);
    }

    /** El "Voronoi" de las zonas: el punto (con peso) de la celda más cercana, mirando desde un sitio un poco movido. */
    static Zona voronoi(int n, int cx, int cz) {
        long w = hn(n, Math.floorDiv(cx, 2), Math.floorDiv(cz, 2), 11);
        double qx = cx + 0.5 + ((w & 255) / 255.0 - 0.5) * 1.7, qz = cz + 0.5 + (((w >> 8) & 255) / 255.0 - 0.5) * 1.7;
        int gx = Math.floorDiv(cx, CELDA), gz = Math.floorDiv(cz, CELDA);
        double mejor = Double.MAX_VALUE;
        int bx = gx, bz = gz;
        for (int i = -2; i <= 2; i++)
            for (int j = -2; j <= 2; j++) {
                int ax = gx + i, az = gz + j;
                double px, pz, peso;
                if (n == 0 && ax == 0 && az == 0) {
                    px = 0.5;
                    pz = 0.5;
                    peso = 16;
                } else {
                    long k = hn(n, ax, az, 3);
                    px = ax * CELDA + (k & 1023) / 1024.0 * CELDA;
                    pz = az * CELDA + ((k >> 10) & 1023) / 1024.0 * CELDA;
                    peso = ((k >> 20) & 255) / 255.0 * 11;
                }
                double d = (qx - px) * (qx - px) + (qz - pz) * (qz - pz) - peso;
                if (d < mejor) {
                    mejor = d;
                    bx = ax;
                    bz = az;
                }
            }
        return zonaCelda(n, bx, bz);
    }

    /** La zona de un chunk en un piso (dentro de una sala grande, la de la sala). */
    public static Zona zona(int n, int cx, int cz) {
        Grande g = grande(n, cx, cz);
        return g != null ? g.zona() : voronoi(n, cx, cz);
    }

    // ------------------------------------------------------------------ salas grandes
    /** Las salas de varios chunks: zona, ancho (x) y fondo (z) en chunks, y alto del techo. */
    public enum Tipo {
        PABELLON(Zona.FERIA, 3, 3, 9), SALA_ENORME(Zona.SALA, 3, 3, 7), VESTIBULO(Zona.HOTEL, 3, 2, 8),
        PISCINA_OLIMPICA(Zona.PISCINA, 3, 2, 8), GIMNASIO(Zona.COLEGIO, 2, 2, 8), ATRIO(Zona.CENTRO, 3, 3, 10),
        /** El pasillo que vuelve al mismo sitio: de la zona que le toque (hotel, colegio o sala), solo se entra por el norte. */
        BUCLE(null, 1, 3, 4);

        public final Zona zona;
        public final int w, d, alto;

        Tipo(Zona z, int w, int d, int alto) {
            this.zona = z;
            this.w = w;
            this.d = d;
            this.alto = alto;
        }
    }

    /** Una sala grande: su tipo, su zona y los chunks que ocupa (de cx0, cz0 a cx0 + w - 1, cz0 + d - 1). */
    public record Grande(Tipo tipo, Zona zona, int n, int cx0, int cz0, int w, int d) {
        public boolean dentro(int cx, int cz) {
            return cx >= cx0 && cx < cx0 + w && cz >= cz0 && cz < cz0 + d;
        }

        /** Primer bloque (x, z) de la sala. */
        public int x0() {
            return cx0 * 16;
        }

        public int z0() {
            return cz0 * 16;
        }
    }

    /** La sala grande de un cuadro (o null). Nunca toca la orilla del cuadro: entre dos salas grandes hay chunks normales. */
    public static Grande grandeDe(int n, int sx, int sz) {
        if (n == 0 && sx == 0 && sz == 0) return null;      // junto al origen, la feria de siempre
        long k = hn(n, sx, sz, 21);
        if (k % 100 >= 58) return null;
        Zona z = voronoi(n, sx * SUPER + SUPER / 2, sz * SUPER + SUPER / 2);
        Tipo t = null;
        if ((z == Zona.HOTEL || z == Zona.COLEGIO || z == Zona.SALA) && (k >> 8) % 4 == 0) t = Tipo.BUCLE;
        else for (Tipo q : Tipo.values()) if (q.zona == z) t = q;
        if (t == null || !hecha(t == Tipo.BUCLE ? z : t.zona)) return null;
        int ox = 1 + (int) ((k >> 16) % (SUPER - 1 - t.w)), oz = 1 + (int) ((k >> 24) % (SUPER - 1 - t.d));
        return new Grande(t, t.zona != null ? t.zona : z, n, sx * SUPER + ox, sz * SUPER + oz, t.w, t.d);
    }

    public static Grande grande(int n, int cx, int cz) {
        Grande g = grandeDe(n, Math.floorDiv(cx, SUPER), Math.floorDiv(cz, SUPER));
        return g != null && g.dentro(cx, cz) ? g : null;
    }

    // ------------------------------------------------------------------ escaleras, ascensores, agujeros, salidas
    /**
     * ¿Baja aquí una escalera del piso n al n + 1? Se entra por el norte del chunk en el piso n (la calle de en medio
     * baja) y se sale por el sur en el n + 1. En el garaje hay muchas más (las rampas de las plantas).
     */
    public static boolean escalera(int n, int cx, int cz) {
        if (n < 0 || n >= NIVELES - 1) return false;
        if (n > 0 && escalera(n - 1, cx, cz)) return false;
        Zona a = voronoi(n, cx, cz), b = voronoi(n + 1, cx, cz);
        int k = a == Zona.APARCAMIENTO || b == Zona.APARCAMIENTO ? 8 : 22;
        if (hn(n, cx, cz, 300) % k != 0) return false;
        return grande(n, cx, cz) == null && grande(n + 1, cx, cz) == null;
    }

    /** ¿Hay un ascensor en este chunk? (en todos los pisos, siempre en el mismo sitio, si en alguno hay hotel). */
    public static boolean ascensor(int cx, int cz) {
        if (h(cx, cz, 400) % 14 != 0) return false;
        boolean hotel = false;
        for (int n = 0; n < NIVELES; n++) {
            if (grande(n, cx, cz) != null || (n < NIVELES - 1 && escalera(n, cx, cz))) return false;
            if (voronoi(n, cx, cz) == Zona.HOTEL) hotel = true;
        }
        return hotel;
    }

    /** ¿Hay puerta de salida en este chunk de este piso? (más o menos una de cada 60). */
    public static boolean salida(int n, int cx, int cz) {
        return hn(n, cx, cz, 7) % 60 == 0 && grande(n, cx, cz) == null;
    }

    /** Las cuatro parcelas de un chunk normal (esquina de cada una): 0 noroeste, 1 noreste, 2 suroeste, 3 sureste. */
    public static final int[][] PARCELAS = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};

    /**
     * ¿Hay un agujero en la parcela k de este chunk del Mundo sin Texturas? Por él se cae fuera del Purgatorio,
     * atravesando los pisos de abajo (allí se ve como un pilar sin textura).
     */
    public static boolean agujero(int cx, int cz, int k) {
        if (h(cx, cz, 80 + k) % 10 != 8 || voronoi(0, cx, cz) != Zona.SIN_TEXTURA || grande(0, cx, cz) != null) return false;
        if (k == 1 && ascensor(cx, cz)) return false;
        if (k == 0) for (int n = 0; n < NIVELES; n++) if (salida(n, cx, cz)) return false;
        return true;
    }

    /** ¿Tapa un pilar (el hueco de un agujero de arriba) la parcela k de este chunk en el piso n? */
    public static boolean tapado(int n, int cx, int cz, int k) {
        return n > 0 && agujero(cx, cz, k);
    }

    // ------------------------------------------------------------------ el pasillo que vuelve al mismo sitio
    /** Desde dónde (bloques desde la entrada) se vuelve atrás, y cuánto. Cada 4 bloques el pasillo es igual. */
    public static final int BUCLE_DESDE = 28, BUCLE_SALTO = 20;

    /** Si en (x, y, z) se está en un pasillo-bucle pasada la raya, lo que hay que mover en z para volver (o 0). */
    public static int bucle(double x, double y, double z) {
        int n = nivel(y);
        int cx = (int) Math.floor(x) >> 4, cz = (int) Math.floor(z) >> 4;
        Grande g = grande(n, cx, cz);
        if (g == null || g.tipo() != Tipo.BUCLE) return 0;
        double lx = x - g.x0(), lz = z - g.z0();
        return lx > 6 && lx < 10 && lz >= BUCLE_DESDE && lz < BUCLE_DESDE + 6 ? -BUCLE_SALTO : 0;
    }
}
