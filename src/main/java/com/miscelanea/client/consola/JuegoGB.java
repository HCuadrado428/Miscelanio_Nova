package com.miscelanea.client.consola;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.RegistryObject;

import java.util.Random;

/** Un juego de la consola. La lógica avanza a 60 pasos por segundo y se dibuja sobre la LCD. */
public abstract class JuegoGB {
    protected final Botones b;
    protected final Random rnd = new Random();
    public int puntos;
    public boolean fin;
    /** Si es distinto de null, sustituye a "FIN DEL JUEGO" (p. ej. ganaste/perdiste). */
    public String mensajeFin;

    protected JuegoGB(Botones b) {
        this.b = b;
    }

    public abstract void reiniciar();

    public abstract void paso();

    public abstract void dibujar(Lcd l);

    public static void sonido(RegistryObject<SoundEvent> s) {
        sonido(s, 1.0F);
    }

    public static void sonido(RegistryObject<SoundEvent> s, float tono) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s.get(), tono, 0.55F));
    }

    protected static String cifras(int n, int c) {
        String s = Integer.toString(n);
        StringBuilder r = new StringBuilder();
        for (int i = s.length(); i < c; i++) r.append('0');
        return r.append(s).toString();
    }

    protected static boolean choca(float ax, float ay, float aw, float ah, float bx, float by, float bw, float bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }
}
