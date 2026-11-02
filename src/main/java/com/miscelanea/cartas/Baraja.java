package com.miscelanea.cartas;

import java.util.List;

/**
 * Baraja española de 40 cartas: carta = palo * 10 + índice; palos oros, copas, espadas, bastos; índices 0..9 para
 * 1, 2, 3, 4, 5, 6, 7, sota, caballo y rey.
 */
public final class Baraja {
    public static final int[] NUMERO = {1, 2, 3, 4, 5, 6, 7, 10, 11, 12};
    /** Puntos en la brisca: as 11, tres 10, rey 4, caballo 3, sota 2. */
    public static final int[] PUNTOS = {11, 0, 10, 0, 0, 0, 0, 2, 3, 4};
    /** Fuerza en la brisca: as > tres > rey > caballo > sota > 7 > 6 > 5 > 4 > 2. */
    public static final int[] FUERZA = {9, 0, 8, 1, 2, 3, 4, 5, 6, 7};

    private Baraja() {}

    public static int palo(int c) {
        return c / 10;
    }

    public static int indice(int c) {
        return c % 10;
    }

    public static int puntos(int c) {
        return PUNTOS[indice(c)];
    }

    public static int fuerza(int c) {
        return FUERZA[indice(c)];
    }

    /** ¿Gana la segunda carta a la primera (la que salió) con este palo de triunfo? */
    public static boolean ganaSegunda(int primera, int segunda, int paloTriunfo) {
        if (palo(primera) == palo(segunda)) return fuerza(segunda) > fuerza(primera);
        return palo(segunda) == paloTriunfo;
    }

    /** Jugada de la máquina: qué carta de su mano echar. */
    public static int elegirCpu(List<Integer> mano, int enMesa, int paloTriunfo) {
        int mejor = 0;
        if (enMesa < 0) {
            // sale: la carta que menos valga, mejor si no es triunfo
            double peor = 1e9;
            for (int i = 0; i < mano.size(); i++) {
                int c = mano.get(i);
                double v = puntos(c) * 10 + fuerza(c) + (palo(c) == paloTriunfo ? 60 : 0);
                if (v < peor) {
                    peor = v;
                    mejor = i;
                }
            }
            return mejor;
        }
        int valorMesa = puntos(enMesa);
        // ganar con lo más barato posible si merece la pena
        int gana = -1;
        double coste = 1e9;
        for (int i = 0; i < mano.size(); i++) {
            int c = mano.get(i);
            if (!ganaSegunda(enMesa, c, paloTriunfo)) continue;
            double v = puntos(c) * 3 + fuerza(c) + (palo(c) == paloTriunfo ? 25 : 0) - puntos(c) * (palo(c) == palo(enMesa) ? 4 : 0);
            if (v < coste) {
                coste = v;
                gana = i;
            }
        }
        if (gana >= 0) {
            int c = mano.get(gana);
            boolean barata = palo(c) != paloTriunfo || valorMesa >= 3;
            if (valorMesa > 0 || palo(c) == palo(enMesa)) if (barata) return gana;
        }
        double peor = 1e9;
        for (int i = 0; i < mano.size(); i++) {
            int c = mano.get(i);
            double v = puntos(c) * 10 + fuerza(c) + (palo(c) == paloTriunfo ? 60 : 0);
            if (v < peor) {
                peor = v;
                mejor = i;
            }
        }
        return mejor;
    }
}
