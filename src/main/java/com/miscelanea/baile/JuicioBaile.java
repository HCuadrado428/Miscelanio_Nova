package com.miscelanea.baile;

/**
 * Reglas de juicio de las flechas, las de la pista de baile. El tiempo va en pasos de
 * 1/60 s desde que empieza la canción (como {@link CancionesBaile.Nota#t}).
 * <ul>
 *   <li>Pisar: la flecha pendiente más cercana del carril a menos de {@link #VENTANA} pasos; ≤3 perfecto, ≤6 genial, si no bien.</li>
 *   <li>Flecha que se mantiene: al pisarla se juzga la cabeza y queda "manteniendo"; si se aguanta hasta el final de la
 *       cola cuenta otro perfecto; si se suelta a menos de {@link #VENTANA} del final se juzga por lo que faltaba; si se
 *       suelta antes, la cola se rompe (fallo).</li>
 *   <li>Una flecha sin pisar {@link #VENTANA} pasos después de su momento es un fallo.</li>
 *   <li>Puntos: perfecto 300 + 2·combo, genial 200 + combo, bien 100; vida +2 / +1 / 0 y −6 por fallo (empieza en 70).</li>
 * </ul>
 */
public final class JuicioBaile {
    public static final int VENTANA = 10;
    /** Estado de cada flecha: sin tocar, manteniéndola, hecha, fallada (o soltada antes de tiempo). */
    public static final int PENDIENTE = 0, MANTENIENDO = 1, HECHA = 2, FALLADA = 3;
    /** Lo que se enseña: perfecto, genial, bien, fallo, mantenido, soltado. */
    public static final int PERFECTO = 0, GENIAL = 1, BIEN = 2, FALLO = 3, MANTENIDO = 4, SOLTADO = 5;

    /** Aviso de cada juicio: carril, qué enseñar y el paso a contar a los demás (luces y traspiés) o -1 si no hace falta. */
    public interface Oyente {
        void juicio(int carril, int mostrar, int paso);
    }

    public final CancionesBaile.Nota[] notas;
    public final int[] estado, tRoto;
    public int puntos, combo, maxCombo, vida = 70, perf, geni, bien, fall, mant, mantTotal;
    private final Oyente oyente;

    public JuicioBaile(CancionesBaile.Nota[] notas, Oyente oyente) {
        this.notas = notas == null ? new CancionesBaile.Nota[0] : notas;
        this.oyente = oyente;
        estado = new int[this.notas.length];
        tRoto = new int[this.notas.length];
        for (CancionesBaile.Nota n : this.notas) if (n.mantener()) mantTotal++;
    }

    private void avisar(int c, int mostrar, int paso) {
        if (oyente != null) oyente.juicio(c, mostrar, paso);
    }

    /** Se pisa el carril c en el paso t. */
    public void pulsar(int c, int t) {
        int mejor = -1, md = 99;
        for (int i = 0; i < notas.length; i++) {
            if (notas[i].t - t > VENTANA) break;
            if (estado[i] != PENDIENTE || notas[i].carril != c) continue;
            int d = Math.abs(notas[i].t - t);
            if (d < md) {
                md = d;
                mejor = i;
            }
        }
        if (mejor >= 0 && md <= VENTANA) {
            int j = md <= 3 ? 0 : md <= 6 ? 1 : 2;
            estado[mejor] = notas[mejor].mantener() ? MANTENIENDO : HECHA;
            juzgar(j);
            avisar(c, j, j);
        }
    }

    /** Se suelta el carril c en el paso t: si se estaba manteniendo una flecha, se juzga cuándo se ha soltado. */
    public void soltar(int c, int t) {
        for (int i = 0; i < notas.length; i++) {
            if (estado[i] != MANTENIENDO || notas[i].carril != c) continue;
            int falta = notas[i].fin - t;
            if (falta <= VENTANA) {
                // soltada a tiempo: cuanto más cerca del final de la cola, mejor
                int j = falta <= 3 ? 0 : falta <= 6 ? 1 : 2;
                estado[i] = HECHA;
                mant++;
                juzgar(j);
                avisar(c, j == 0 ? MANTENIDO : j, -1);
            } else {
                // soltada antes de tiempo: la cola se rompe
                estado[i] = FALLADA;
                tRoto[i] = t;
                juzgar(3);
                avisar(c, SOLTADO, 3);
            }
        }
    }

    /** Paso t: las flechas que se han pasado sin pisar y las colas mantenidas hasta el final. */
    public void avanzar(int t) {
        for (int i = 0; i < notas.length; i++) {
            CancionesBaile.Nota n = notas[i];
            if (n.t > t) break;
            if (estado[i] == PENDIENTE && t - n.t > VENTANA) {
                estado[i] = FALLADA;
                tRoto[i] = n.t;
                juzgar(3);
                avisar(n.carril, FALLO, 3);
            } else if (estado[i] == MANTENIENDO && t >= n.fin) {
                estado[i] = HECHA;
                mant++;
                juzgar(0);
                avisar(n.carril, MANTENIDO, -1);
            }
        }
    }

    private void juzgar(int j) {
        switch (j) {
            case 0 -> {
                perf++;
                puntos += 300 + combo * 2;
                vida = Math.min(100, vida + 2);
            }
            case 1 -> {
                geni++;
                puntos += 200 + combo;
                vida = Math.min(100, vida + 1);
            }
            case 2 -> {
                bien++;
                puntos += 100;
            }
            default -> {
                fall++;
                vida -= 6;
            }
        }
        combo = j < 3 ? combo + 1 : 0;
        maxCombo = Math.max(maxCombo, combo);
    }

    public boolean sinVida() {
        return vida <= 0;
    }

    /** Nota final: 0 S, 1 A, 2 B, 3 C, 4 D, 5 E (sin vida). */
    public int nota() {
        int total = (notas.length + mantTotal) * 300;
        float p = total == 0 ? 0 : (perf * 300 + geni * 200 + bien * 100) / (float) total;
        if (vida <= 0) return 5;
        return p >= 0.93F ? 0 : p >= 0.8F ? 1 : p >= 0.65F ? 2 : p >= 0.5F ? 3 : 4;
    }

    public static String letra(int nota) {
        return "SABCDE".substring(Math.max(0, Math.min(5, nota)), Math.max(0, Math.min(5, nota)) + 1);
    }
}
