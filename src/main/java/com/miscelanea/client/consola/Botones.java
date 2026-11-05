package com.miscelanea.client.consola;

/** Estado de los botones de la consola (cruceta, A, B, Start, Select) a 60 pasos por segundo. */
public class Botones {
    public static final int ARRIBA = 0, ABAJO = 1, IZQ = 2, DER = 3, A = 4, B = 5, START = 6, SELECT = 7, C = 8, N = 9;
    final boolean[] abajo = new boolean[N];
    final boolean[] nuevo = new boolean[N];
    final int[] tiempo = new int[N];

    /** Botón que corresponde a una tecla, según las teclas configurables "Consola: ..." (principal y alternativa). */
    public static int boton(int tecla, int scan) {
        for (int i = 0; i < N; i++)
            for (net.minecraft.client.KeyMapping k : com.miscelanea.client.TeclasMod.CONSOLA[i]) if (k.matches(tecla, scan)) return i;
        return -1;
    }

    /** Botón asignado a un botón del ratón (si alguien pone uno en Controles), o -1. */
    public static int botonRaton(int boton) {
        for (int i = 0; i < N; i++)
            for (net.minecraft.client.KeyMapping k : com.miscelanea.client.TeclasMod.CONSOLA[i]) if (k.matchesMouse(boton)) return i;
        return -1;
    }

    void pulsar(int b) {
        if (!abajo[b]) {
            nuevo[b] = true;
            tiempo[b] = 0;
        }
        abajo[b] = true;
    }

    void soltar(int b) {
        abajo[b] = false;
    }

    /** Fin de un paso de lógica: avanza contadores y borra las pulsaciones nuevas. */
    void paso() {
        for (int i = 0; i < N; i++) {
            if (abajo[i]) tiempo[i]++;
            nuevo[i] = false;
        }
    }

    void soltarTodo() {
        java.util.Arrays.fill(abajo, false);
        java.util.Arrays.fill(nuevo, false);
    }

    public boolean pulsa(int b) {
        return nuevo[b];
    }

    public boolean mantiene(int b) {
        return abajo[b] || nuevo[b];
    }

    /** Pulsación con auto-repetición (como mantener la cruceta en una consola de verdad). */
    public boolean repite(int b, int retardo, int ritmo) {
        if (nuevo[b]) return true;
        return abajo[b] && tiempo[b] >= retardo && (tiempo[b] - retardo) % ritmo == 0;
    }

    /** Acceso desde otros paquetes (la recreativa). */
    public static final class Acceso {
        private Acceso() {}

        public static void pulsar(Botones b, int i) {
            b.pulsar(i);
        }

        public static void soltar(Botones b, int i) {
            b.soltar(i);
        }

        public static void paso(Botones b) {
            b.paso();
        }

        public static boolean[] abajo(Botones b) {
            return b.abajo;
        }

        /** Botones mantenidos como máscara de bits (para enviarlos por red). */
        public static int mascara(Botones b) {
            int m = 0;
            for (int i = 0; i < N; i++) if (b.abajo[i] || b.nuevo[i]) m |= 1 << i;
            return m;
        }

        /** Pone los botones tal como indica una máscara (pulsaciones nuevas incluidas). */
        public static void aplicar(Botones b, int m) {
            for (int i = 0; i < N; i++) {
                if ((m >> i & 1) != 0) b.pulsar(i);
                else b.soltar(i);
            }
        }
    }
}
