package com.miscelanea.tortura.qte;

/**
 * Tipos de minijuego rápido (QTE) disponibles. El orden importa: viaja por red como ordinal.
 * <ul>
 *   <li>{@link #CIRCULO}: prueba de habilidad (Dead by Daylight). La aguja gira; pulsa SALTAR (espacio) dentro de la zona.
 *       La franja pequeña del principio de la zona es "perfecta".</li>
 *   <li>{@link #SECUENCIA}: pulsa en orden las flechas que salen (WASD o cursores) antes de que se acabe el tiempo.</li>
 *   <li>{@link #MACHACAR}: aporrea SALTAR para llenar la barra, que se vacía sola.</li>
 *   <li>{@link #ALTERNAR}: alterna IZQUIERDA / DERECHA (A / D) lo más rápido posible.</li>
 *   <li>{@link #GANZUA}: forzar una cerradura (Skyrim). El ratón mueve la ganzúa; mantén IZQUIERDA (A) para girar la cerradura.
 *       Cuanto más cerca del punto dulce, más gira; forzar en mal ángulo daña la ganzúa y puede romperla.</li>
 *   <li>{@link #BARRA_ZONA}: mantén el objetivo dentro de la zona que sube con SALTAR y cae sola (pesca de Stardew Valley).</li>
 *   <li>{@link #RITMO}: pulsa SALTAR cuando cada nota llegue al círculo, al compás.</li>
 * </ul>
 */
public enum QteTipo {
    CIRCULO, SECUENCIA, MACHACAR, ALTERNAR, GANZUA, BARRA_ZONA, RITMO;

    public static QteTipo de(int i) {
        QteTipo[] v = values();
        return v[Math.max(0, Math.min(v.length - 1, i))];
    }
}
