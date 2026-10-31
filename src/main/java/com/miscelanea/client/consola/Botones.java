package com.miscelanea.client.consola;

import org.lwjgl.glfw.GLFW;

/** Estado de los botones de la consola (cruceta, A, B, Start, Select) a 60 pasos por segundo. */
public class Botones {
    public static final int ARRIBA = 0, ABAJO = 1, IZQ = 2, DER = 3, A = 4, B = 5, START = 6, SELECT = 7;
    final boolean[] abajo = new boolean[8];
    final boolean[] nuevo = new boolean[8];
    final int[] tiempo = new int[8];

    public static int boton(int tecla) {
        return switch (tecla) {
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> ARRIBA;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> ABAJO;
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> IZQ;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> DER;
            case GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_J -> A;
            case GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT, GLFW.GLFW_KEY_K -> B;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> START;
            case GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_BACKSPACE -> SELECT;
            default -> -1;
        };
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
        for (int i = 0; i < 8; i++) {
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
}
