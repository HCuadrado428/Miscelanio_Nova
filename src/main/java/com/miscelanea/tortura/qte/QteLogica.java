package com.miscelanea.tortura.qte;

import java.util.Random;

/**
 * Reglas de cada minijuego, deterministas a partir de (dificultad, rondas, semilla). Las usa el cliente para jugar y el
 * servidor para comprobar que el resultado es posible (tiempo mínimo, ritmo de pulsaciones, ángulo de la ganzúa...).
 * Todos los tiempos en milisegundos reales; los "por tick" son pasos de 50 ms del cliente.
 */
public final class QteLogica {
    private QteLogica() {}

    /** Margen a favor del jugador al comprobar tiempos mínimos. */
    public static final int HOLGURA_MS = 150;

    static float lerp(float d, float a, float b) {
        return a + (b - a) * d;
    }

    // ------------------------------------------------------------------ CIRCULO
    public static final int CIRC_PREVIO_MS = 550, CIRC_PAUSA_MS = 380;

    /** Grados por milisegundo de la aguja. */
    public static float circVel(float d) {
        return 360F / lerp(d, 1500F, 850F);
    }

    public static float circAncho(float d) {
        return lerp(d, 56F, 26F);
    }

    public static float circPerfecto(float d) {
        return circAncho(d) * 0.26F;
    }

    /** Ángulo (grados, 0 = arriba, sentido horario) donde empieza la zona de la ronda. */
    public static float circZona(long semilla, int ronda) {
        Random r = new Random(semilla * 31L + ronda * 7919L + 17);
        return 105F + r.nextFloat() * 180F;
    }

    /** Duración de una ronda completa (si no se pulsa, la aguja da una vuelta y se falla). */
    public static int circRondaMs(float d) {
        return CIRC_PREVIO_MS + (int) (360F / circVel(d));
    }

    // ------------------------------------------------------------------ SECUENCIA
    public static final int SEC_PREVIO_MS = 450;

    public static int secLargo(float d) {
        return Math.round(lerp(d, 4, 8));
    }

    /** 0 = arriba, 1 = abajo, 2 = izquierda, 3 = derecha. */
    public static int[] secTeclas(long semilla, float d) {
        Random r = new Random(semilla ^ 0x5EC0L);
        int[] t = new int[secLargo(d)];
        for (int i = 0; i < t.length; i++) {
            int k;
            do k = r.nextInt(4); while (i > 0 && k == t[i - 1] && r.nextBoolean());
            t[i] = k;
        }
        return t;
    }

    public static int secTiempoMs(float d) {
        return secLargo(d) * Math.round(lerp(d, 950, 520)) + 800;
    }

    // ------------------------------------------------------------------ MACHACAR / ALTERNAR
    public static final int MACH_TIEMPO_MS = 7000, MACH_PREVIO_MS = 350;
    /** Nadie pulsa más de ~26 veces por segundo de forma sostenida. */
    public static final int MACH_MIN_ENTRE_MS = 38;

    public static float machGanancia(float d, boolean alternar) {
        return alternar ? lerp(d, 0.085F, 0.05F) : lerp(d, 0.10F, 0.055F);
    }

    public static float machCaidaTick(float d) {
        return lerp(d, 0.008F, 0.02F);
    }

    public static int machNecesarias(float d, boolean alternar) {
        return (int) Math.ceil(1F / machGanancia(d, alternar));
    }

    // ------------------------------------------------------------------ GANZUA
    public static final int GAN_TIEMPO_MS = 90000, GAN_PREVIO_MS = 300;
    /** Grados que gira la cerradura por tick mientras se mantiene, y lo que vuelve al soltar. */
    public static final float GAN_GIRO_TICK = 4.5F, GAN_VUELTA_TICK = 9F;

    /** Punto dulce de la ganzúa en grados (-80..80). */
    public static float ganDulce(long semilla) {
        return -80F + new Random(semilla ^ 0x6A7A0AL).nextFloat() * 160F;
    }

    public static float ganTolerancia(float d) {
        return lerp(d, 15F, 4F);
    }

    public static float ganCaida(float d) {
        return lerp(d, 75F, 38F);
    }

    /** Lo máximo que puede girar la cerradura con la ganzúa en este ángulo (90 = se abre). */
    public static float ganMaxGiro(float pick, long semilla, float d) {
        float dist = Math.abs(pick - ganDulce(semilla));
        float tol = ganTolerancia(d);
        if (dist <= tol) return 90F;
        return 90F * Math.max(0F, 1F - (dist - tol) / ganCaida(d));
    }

    /** Daño por tick a la ganzúa mientras se fuerza contra el tope (1 = rota). */
    public static float ganDanoTick(float d) {
        return lerp(d, 0.028F, 0.075F);
    }

    // ------------------------------------------------------------------ BARRA_ZONA
    public static final int BARRA_TIEMPO_MS = 30000, BARRA_PREVIO_MS = 600;
    public static final float BARRA_GANA = 0.0125F, BARRA_INICIO = 0.3F;

    public static float barraZona(float d) {
        return lerp(d, 0.32F, 0.17F);
    }

    public static float barraPierde(float d) {
        return lerp(d, 0.006F, 0.012F);
    }

    // ------------------------------------------------------------------ RITMO
    public static final int RITMO_PREVIO_MS = 1800, RITMO_PERFECTO_MS = 55;

    /** Momento de cada nota (ms desde que empieza la canción, tras el previo). */
    public static int[] ritmoNotas(long semilla, float d) {
        Random r = new Random(semilla ^ 0x317A0L);
        int n = Math.round(lerp(d, 8, 16));
        float intervalo = 60000F / lerp(d, 92F, 140F);
        int[] t = new int[n];
        float pos = 0;
        for (int i = 0; i < n; i++) {
            t[i] = Math.round(pos);
            float paso = intervalo;
            float x = r.nextFloat();
            if (x < 0.12F + d * 0.25F) paso = intervalo * 0.5F;     // corchea
            else if (x > 0.88F) paso = intervalo * 2F;              // silencio
            pos += paso;
        }
        return t;
    }

    public static int ritmoVentana(float d) {
        return Math.round(lerp(d, 150, 95));
    }

    public static int ritmoNecesarias(int n, float d) {
        return (int) Math.ceil(n * lerp(d, 0.6F, 0.8F));
    }

    // ------------------------------------------------------------------ SERRAR
    public static final int SERRAR_PREVIO_MS = 500, SERRAR_TIEMPO_MS = 30000;

    /** Lo que tarda la marca en ir y volver (ms). */
    public static int serrarPeriodo(float d) {
        return Math.round(lerp(d, 1500F, 900F));
    }

    /** Distancia máxima (en la barra de -1 a 1) entre la sierra y la marca para que el corte avance. */
    public static float serrarTolerancia(float d) {
        return lerp(d, 0.42F, 0.22F);
    }

    /** Lo que avanza el corte por tick bueno (1 = cortado). */
    public static float serrarGanancia(float d) {
        return lerp(d, 0.0125F, 0.0075F);
    }

    public static float serrarPierde(float d) {
        return lerp(d, 0.003F, 0.006F);
    }

    public static int serrarNecesarios(float d) {
        return (int) Math.ceil(1F / serrarGanancia(d));
    }

    // ------------------------------------------------------------------ ESQUIVA
    public static final int ESQUIVA_REACCION_MIN_MS = 90;

    /** Tiempo para reaccionar (ms) según la dificultad. */
    public static int esquivaVentana(float d) {
        return Math.round(lerp(d, 1100F, 330F));
    }

    /** Dificultad que da una ventana de 'ms' milisegundos (inversa de {@link #esquivaVentana}). */
    public static float esquivaDificultad(int ms) {
        return Math.max(0F, Math.min(1F, (1100F - ms) / (1100F - 330F)));
    }

    /** true si viene por la izquierda (hay que pulsar DERECHA). */
    public static boolean esquivaPorIzquierda(long semilla) {
        return (semilla & 2L) != 0;
    }

    /** Dirección que hay que pulsar: 2 = izquierda, 3 = derecha (como en SECUENCIA). */
    public static int esquivaTecla(long semilla) {
        return esquivaPorIzquierda(semilla) ? 3 : 2;
    }

    // ------------------------------------------------------------------ validación
    /** Lo mínimo que tiene que durar una partida GANADA (el servidor mide siempre más que el cliente). */
    public static long minimoMs(QteTipo t, float d, int rondas, long semilla) {
        switch (t) {
            case CIRCULO: {
                long ms = 0;
                for (int i = 0; i < rondas; i++) ms += CIRC_PREVIO_MS + (long) (circZona(semilla, i) / circVel(d));
                return ms + (long) (rondas - 1) * CIRC_PAUSA_MS;
            }
            case SECUENCIA:
                return SEC_PREVIO_MS + secLargo(d) * 60L;
            case MACHACAR:
                return MACH_PREVIO_MS + (long) machNecesarias(d, false) * MACH_MIN_ENTRE_MS;
            case ALTERNAR:
                return MACH_PREVIO_MS + (long) machNecesarias(d, true) * MACH_MIN_ENTRE_MS;
            case GANZUA:
                return GAN_PREVIO_MS + (long) (90F / GAN_GIRO_TICK * 50F * 0.9F);
            case BARRA_ZONA:
                return BARRA_PREVIO_MS + (long) ((1F - BARRA_INICIO) / BARRA_GANA * 50F);
            case RITMO: {
                int[] notas = ritmoNotas(semilla, d);
                int k = Math.max(0, Math.min(notas.length - 1, ritmoNecesarias(notas.length, d) - 1));
                return RITMO_PREVIO_MS + notas[k] - ritmoVentana(d);
            }
            case SERRAR:
                return SERRAR_PREVIO_MS + (long) serrarNecesarios(d) * 50L;
            case ESQUIVA:
                return ESQUIVA_REACCION_MIN_MS;
        }
        return 0;
    }

    /** Lo máximo que puede durar la partida en el cliente (el servidor añade margen y luego la da por fallada). */
    public static long limiteMs(QteTipo t, float d, int rondas, long semilla) {
        switch (t) {
            case CIRCULO:
                return (long) circRondaMs(d) * rondas + (long) rondas * CIRC_PAUSA_MS;
            case SECUENCIA:
                return SEC_PREVIO_MS + secTiempoMs(d);
            case MACHACAR:
            case ALTERNAR:
                return MACH_PREVIO_MS + MACH_TIEMPO_MS;
            case GANZUA:
                return GAN_TIEMPO_MS;
            case BARRA_ZONA:
                return BARRA_PREVIO_MS + BARRA_TIEMPO_MS;
            case RITMO: {
                int[] notas = ritmoNotas(semilla, d);
                return RITMO_PREVIO_MS + notas[notas.length - 1] + 1500L;
            }
            case SERRAR:
                return SERRAR_PREVIO_MS + SERRAR_TIEMPO_MS;
            case ESQUIVA:
                return esquivaVentana(d) + 250L;
        }
        return 60000;
    }

    /**
     * ¿Es creíble este éxito? 'datos' depende del tipo:
     * CIRCULO [aciertos, perfectos] · SECUENCIA [pulsadas] · MACHACAR/ALTERNAR [pulsaciones] · GANZUA [ángulo×10]
     * BARRA_ZONA [] · RITMO [aciertos, perfectos] · SERRAR [vaivenes, ticks buenos] · ESQUIVA [tecla, ms de reacción].
     */
    public static boolean plausible(QteTipo t, float d, int rondas, long semilla, int[] datos, long ms) {
        if (ms + HOLGURA_MS < minimoMs(t, d, rondas, semilla)) return false;
        int a = datos.length > 0 ? datos[0] : -1, b = datos.length > 1 ? datos[1] : 0;
        switch (t) {
            case CIRCULO:
                return a == rondas && b >= 0 && b <= a;
            case SECUENCIA:
                return a == secLargo(d);
            case MACHACAR:
            case ALTERNAR: {
                boolean alt = t == QteTipo.ALTERNAR;
                return a >= machNecesarias(d, alt) && (long) a * MACH_MIN_ENTRE_MS <= ms + HOLGURA_MS;
            }
            case GANZUA:
                return datos.length > 0 && Math.abs(a / 10F - ganDulce(semilla)) <= ganTolerancia(d) + 1.5F;
            case BARRA_ZONA:
                return true;
            case RITMO: {
                int n = ritmoNotas(semilla, d).length;
                return a >= ritmoNecesarias(n, d) && a <= n && b >= 0 && b <= a;
            }
            case SERRAR: {
                // cada tick bueno dura 50 ms; y no se puede serrar sin ir y venir: al menos medio vaivén por cada medio periodo
                int nec = serrarNecesarios(d);
                long minVaivenes = (long) nec * 50L / Math.max(1, serrarPeriodo(d) / 2) / 2L - 1L;
                return b >= nec && (long) b * 50L <= ms + HOLGURA_MS && a >= minVaivenes;
            }
            case ESQUIVA:
                return a == esquivaTecla(semilla) && b >= ESQUIVA_REACCION_MIN_MS - 40 && b <= esquivaVentana(d) + 100;
        }
        return false;
    }

    /** ¿Ese éxito puede contarse como PERFECTO? */
    public static boolean perfecto(QteTipo t, float d, int rondas, long semilla, int[] datos) {
        int a = datos.length > 0 ? datos[0] : 0, b = datos.length > 1 ? datos[1] : 0;
        if (t == QteTipo.CIRCULO) return a == rondas && b == rondas;
        if (t == QteTipo.RITMO) {
            int n = ritmoNotas(semilla, d).length;
            return a == n && b >= Math.ceil(n * 0.9);
        }
        return false;
    }
}
