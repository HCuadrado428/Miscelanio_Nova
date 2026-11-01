package com.miscelanea.client.arcade;

import com.miscelanea.client.consola.Botones;
import com.miscelanea.client.consola.JuegoGB;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Random;

/**
 * Juego exclusivo de la recreativa (256x192, 64 colores), para uno o dos jugadores.
 * La lógica tiene que ser determinista (solo el Random con semilla y los mandos) porque en la partida a dos
 * cada jugador la simula en su propio ordenador con los mismos mandos.
 */
public abstract class JuegoArcade {
    protected final Botones[] b;
    protected final Random rnd;
    public final int jugadores;
    public int puntos;
    public boolean fin;
    public String mensajeFin;
    /** El juego pide cambiar la música (la pantalla la pone y lo vuelve a null); pararMusica la quita. */
    public SoundEvent cambiarMusica;
    public boolean pararMusica;

    protected JuegoArcade(Botones[] b, long semilla, int jugadores) {
        this.b = b;
        this.rnd = new Random(semilla);
        this.jugadores = jugadores;
    }

    public abstract void reiniciar();

    public abstract void paso();

    public abstract void dibujar(Pantalla p);

    /** Música de la partida (o null). */
    public SoundEvent musica() {
        return null;
    }

    protected static void sonido(RegistryObject<SoundEvent> s, float tono) {
        JuegoGB.sonido(s, tono);
    }
}
