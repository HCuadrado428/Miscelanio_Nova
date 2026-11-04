package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;

/** Poderes de la rueda (se usan con clic derecho y la mano vacía). El orden es el de la rueda y el de la red. */
public enum Poder {
    NINGUNO("ninguno"), PARPADEO("parpadeo"), PANTALLA("pantalla"), IMITAR("imitar"), ARRASTRAR("arrastrar"),
    ATRAER("atraer"), LANZAR("lanzar"), ESTAMPAR("estampar");

    public static final Poder[] V = values();
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
            case ARRASTRAR -> ConfigServidor.estaticaArrastrar();
            case ATRAER -> ConfigServidor.estaticaAtraer();
            case LANZAR -> ConfigServidor.estaticaLanzar();
            case ESTAMPAR -> ConfigServidor.estaticaEstampar();
        };
    }

    /** Ticks de espera tras usarlo. */
    public int enfriamiento() {
        return switch (this) {
            case NINGUNO -> 0;
            case PARPADEO -> ConfigServidor.estaticaParpadeoEnfriamiento();
            case PANTALLA -> ConfigServidor.estaticaPantallasEnfriamiento();
            case IMITAR -> ConfigServidor.estaticaImitarEnfriamiento();
            case ARRASTRAR -> ConfigServidor.estaticaArrastrarEnfriamiento();
            case ATRAER -> ConfigServidor.estaticaAtraerEnfriamiento();
            case LANZAR -> ConfigServidor.estaticaLanzarEnfriamiento();
            case ESTAMPAR -> ConfigServidor.estaticaEstamparEnfriamiento();
        };
    }

    public String clave() {
        return "estatica.miscelanea.poder." + id;
    }

    public boolean tentaculo() {
        return this == ATRAER || this == LANZAR || this == ESTAMPAR;
    }
}
