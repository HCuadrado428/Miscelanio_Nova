package com.miscelanea.tortura.heridas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Marca a fuego del hierro de marcar: un símbolo de 8x8 quemado en la piel (pecho/espalda, o rodeando el brazo por
 * fuera y por delante). No se cura con el tiempo: solo se va al morir (las heridas se borran todas al morir).
 * bits: el bit (y*8 + x) es el píxel (x, y) del dibujo, con (0,0) arriba a la izquierda tal y como se dibujó.
 */
public final class Marca {
    public static final int MAX = 6;
    public final Parte parte;
    public final int cara;
    public final long bits;

    public Marca(Parte parte, int cara, long bits) {
        this.parte = parte;
        this.cara = cara;
        this.bits = bits;
    }

    public boolean pixel(int x, int y) {
        return x >= 0 && x < 8 && y >= 0 && y < 8 && ((bits >>> (y * 8 + x)) & 1L) != 0;
    }

    /** ¿Ocupa el mismo sitio que otra? (entonces la nueva la sustituye) */
    public boolean mismoSitio(Marca o) {
        return o.parte == parte && (parte.esBrazo() || o.cara == cara);
    }

    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        t.putByte("p", (byte) parte.ordinal());
        t.putByte("c", (byte) cara);
        t.putLong("b", bits);
        return t;
    }

    public static Marca cargar(CompoundTag t) {
        return new Marca(Parte.de(t.getByte("p")), t.getByte("c"), t.getLong("b"));
    }

    public void escribir(FriendlyByteBuf b) {
        b.writeByte(parte.ordinal());
        b.writeByte(cara);
        b.writeLong(bits);
    }

    public static Marca leer(FriendlyByteBuf b) {
        Parte p = Parte.de(b.readByte());
        int c = b.readByte();
        return new Marca(p, c, b.readLong());
    }
}
