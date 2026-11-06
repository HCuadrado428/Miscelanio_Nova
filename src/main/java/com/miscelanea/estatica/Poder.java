package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;

import java.util.List;

/**
 * Poderes de la rueda (se usan con clic derecho y la mano vacía). El orden de la enumeración es el de la red; el de la
 * rueda es {@link #RUEDA}. ATRAER, LANZAR y ESTAMPAR ya no salen en la rueda: son lo que hace TENTACULOS después de
 * agarrar (clic izquierdo estampa, clic derecho lanza, sin hacer nada atrae).
 */
public enum Poder {
    NINGUNO("ninguno"), PARPADEO("parpadeo"), PANTALLA("pantalla"), IMITAR("imitar"), CANAL("canal"),
    ATRAER("atraer"), LANZAR("lanzar"), ESTAMPAR("estampar"), SOBRECARGA("sobrecarga"),
    TENTACULOS("tentaculos"), PAUSA("pausa"), REBOBINAR("rebobinar"), SIN_SENAL("sin_senal"), GRABACION("grabacion");

    public static final Poder[] V = values();
    public static final List<Poder> RUEDA = List.of(PARPADEO, TENTACULOS, PAUSA, CANAL, PANTALLA, IMITAR, REBOBINAR, SIN_SENAL, GRABACION, SOBRECARGA);
    public final String id;

    Poder(String id) {
        this.id = id;
    }

    public static Poder de(int i) {
        return i >= 0 && i < V.length ? V[i] : NINGUNO;
    }

    /** Si está activado en la configuración (Imitar además necesita el voicechat: eso lo mira el cliente). */
    public boolean activo() {
        return switch (this) {
            case NINGUNO -> true;
            case PARPADEO -> ConfigServidor.estaticaParpadeo();
            case PANTALLA -> ConfigServidor.estaticaPantallas();
            case IMITAR -> ConfigServidor.estaticaImitar();
            case CANAL -> ConfigServidor.estaticaArrastrar();
            case ATRAER -> ConfigServidor.estaticaAtraer();
            case LANZAR -> ConfigServidor.estaticaLanzar();
            case ESTAMPAR -> ConfigServidor.estaticaEstampar();
            case TENTACULOS -> ConfigServidor.estaticaAtraer() || ConfigServidor.estaticaLanzar() || ConfigServidor.estaticaEstampar();
            case SOBRECARGA -> ConfigServidor.estaticaSobrecarga();
            case PAUSA -> ConfigServidor.estaticaPausa();
            case REBOBINAR -> ConfigServidor.estaticaRebobinar();
            case SIN_SENAL -> ConfigServidor.estaticaSinSenal();
            case GRABACION -> ConfigServidor.estaticaGrabacion();
        };
    }

    /** Ticks de espera tras usarlo. */
    public int enfriamiento() {
        return switch (this) {
            case NINGUNO, ATRAER, LANZAR, ESTAMPAR -> 0;
            case PARPADEO -> 5;   // entre cargas seguidas; cada carga se recarga aparte (Poderes.cargas)
            case PANTALLA -> ConfigServidor.estaticaPantallasEnfriamiento();
            case IMITAR -> ConfigServidor.estaticaImitarEnfriamiento();
            case CANAL -> ConfigServidor.estaticaArrastrarEnfriamiento();
            case TENTACULOS -> ConfigServidor.estaticaAtraerEnfriamiento();
            case SOBRECARGA -> ConfigServidor.estaticaSobrecargaEnfriamiento();
            case PAUSA -> ConfigServidor.estaticaPausaEnfriamiento();
            case REBOBINAR -> ConfigServidor.estaticaRebobinarEnfriamiento();
            case SIN_SENAL -> ConfigServidor.estaticaSinSenalEnfriamiento();
            case GRABACION -> ConfigServidor.estaticaGrabacionEnfriamiento();
        };
    }

    /** Señal que gasta (0-100). La Sobrecarga necesita la barra llena y la vacía entera. */
    public int coste() {
        return switch (this) {
            case PAUSA -> ConfigServidor.estaticaCostePausa();
            case CANAL -> ConfigServidor.estaticaCosteCanal();
            case REBOBINAR -> ConfigServidor.estaticaCosteRebobinar();
            case SIN_SENAL -> ConfigServidor.estaticaCosteSinSenal();
            case GRABACION -> ConfigServidor.estaticaCosteGrabacion();
            case SOBRECARGA -> Senal.MAX;
            default -> 0;
        };
    }

    public String clave() {
        return "estatica.miscelanea.poder." + id;
    }

    public boolean tentaculo() {
        return this == ATRAER || this == LANZAR || this == ESTAMPAR || this == TENTACULOS;
    }
}
