package com.miscelanea.client.estatica;

import com.miscelanea.estatica.EstaticaRed;

/** Lo que llega por el canal de la estática (siempre en el hilo del juego). */
public final class EstaticaRedCliente {
    private EstaticaRedCliente() {}

    public static void estado(EstaticaRed.Estado p) {
        EstaticaCliente.recibir(p);
    }

    public static void cine(EstaticaRed.Cine p) {
        EstaticaCine.empezar(p);
    }

    public static void fx(EstaticaRed.Fx p) {
        EfectosMundo.fx(p);
    }

    public static void nieve(EstaticaRed.Nieve p) {
        EstaticaHud.nieve(p.ticks, p.borde, p.corromper);
    }

    public static void pantallas(EstaticaRed.Pantallas p) {
        PantallaCliente.recibir(p);
    }

    public static void charco(EstaticaRed.Charco p) {
        EfectosMundo.charco(p);
    }

    public static void imitar(EstaticaRed.Imitar p) {
        ImitarVoz.reproducir(p);
    }

    public static void tentaculo(EstaticaRed.Tentaculo p) {
        EfectosMundo.tentaculo(p);
    }

    public static void cara(EstaticaRed.CaraPantalla p) {
        CaraPantalla.recibir(p);
    }
}
