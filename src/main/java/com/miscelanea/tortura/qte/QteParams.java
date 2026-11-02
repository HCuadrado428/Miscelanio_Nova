package com.miscelanea.tortura.qte;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Parámetros de un minijuego. Se construye encadenando:
 * <pre>{@code
 * QteParams.de(0.6F).rondas(2).titulo(Component.translatable("...")).ancla(Vec3.atCenterOf(pos), 4.5)
 * }</pre>
 * <ul>
 *   <li>dificultad: 0 (muy fácil) .. 1 (muy difícil). Cambia velocidad, tamaño de zona, longitud...</li>
 *   <li>rondas: cuántas veces seguidas hay que superar CIRCULO (las demás lo ignoran).</li>
 *   <li>semilla: 0 = aleatoria. Con la misma semilla sale lo mismo (GANZUA: el mismo punto dulce en todas las ganzúas de una cerradura).</li>
 *   <li>titulo: texto encima del minijuego (opcional).</li>
 *   <li>ancla + radio: si el jugador se aleja más de 'radio' bloques de ese punto, se cancela (solo en el servidor).</li>
 * </ul>
 */
public final class QteParams {
    float dificultad = 0.5F;
    int rondas = 1;
    long semilla;
    @Nullable Component titulo;
    @Nullable Vec3 ancla;
    double radio = 6;

    private QteParams() {}

    public static QteParams de(float dificultad) {
        QteParams p = new QteParams();
        p.dificultad = Math.max(0, Math.min(1, dificultad));
        return p;
    }

    public QteParams rondas(int n) {
        this.rondas = Math.max(1, Math.min(8, n));
        return this;
    }

    public QteParams semilla(long s) {
        this.semilla = s;
        return this;
    }

    public QteParams titulo(@Nullable Component c) {
        this.titulo = c;
        return this;
    }

    public QteParams ancla(Vec3 punto, double radio) {
        this.ancla = punto;
        this.radio = radio;
        return this;
    }

    public float dificultad() {
        return dificultad;
    }

    public int rondas() {
        return rondas;
    }

    public long semilla() {
        return semilla;
    }
}
