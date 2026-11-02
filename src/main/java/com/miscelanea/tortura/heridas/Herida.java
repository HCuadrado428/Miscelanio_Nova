package com.miscelanea.tortura.heridas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Una herida concreta. Lleva dónde está (parte, cara del cubo y posición dentro de esa cara, 0..255),
 * qué es (tipo, gravedad 1..3, estilo especial) y cuánto le queda para curarse (en ticks).
 */
public class Herida {
    /** Caras del cubo de la pieza (convención de las skins: "derecha" = lado derecho del personaje). */
    public static final int FRENTE = 0, ESPALDA = 1, DERECHA = 2, IZQUIERDA = 3;

    public Parte parte;
    public Tipo tipo;
    public int gravedad;
    public int cara;
    public int fx, fy;
    public int semilla;
    /** Calcomanía especial: cruz (corte), zarpazo (rasguño), nudillos (moratón), Lichtenberg (quemadura). */
    public boolean especial;
    public int total, restante;

    public Herida(Parte parte, Tipo tipo, int gravedad, int cara, int fx, int fy, int semilla, boolean especial, int total) {
        this.parte = parte;
        this.tipo = tipo;
        this.gravedad = Math.max(1, Math.min(3, gravedad));
        this.cara = cara & 3;
        this.fx = fx & 255;
        this.fy = fy & 255;
        this.semilla = semilla;
        this.especial = especial;
        this.total = Math.max(20, total);
        this.restante = this.total;
    }

    private Herida() {}

    /** 0 = recién hecha, 1 = curada del todo. */
    public float progreso() {
        return 1F - Math.max(0, Math.min(total, restante)) / (float) total;
    }

    /** Lo que "pesa" para los efectos tipo Fallout (va bajando a medida que se cura). */
    public float peso() {
        float base = tipo == Tipo.RASGUNO ? 0.3F : gravedad == 1 ? 1F : gravedad == 2 ? 2F : 3.5F;
        return base * (0.4F + 0.6F * (1F - progreso()));
    }

    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        t.putByte("p", (byte) parte.ordinal());
        t.putByte("t", (byte) tipo.ordinal());
        t.putByte("g", (byte) gravedad);
        t.putByte("c", (byte) cara);
        t.putByte("x", (byte) fx);
        t.putByte("y", (byte) fy);
        t.putInt("s", semilla);
        t.putBoolean("e", especial);
        t.putInt("T", total);
        t.putInt("r", restante);
        return t;
    }

    public static Herida cargar(CompoundTag t) {
        Herida h = new Herida();
        h.parte = Parte.de(t.getByte("p"));
        h.tipo = Tipo.de(t.getByte("t"));
        h.gravedad = Math.max(1, Math.min(3, t.getByte("g")));
        h.cara = t.getByte("c") & 3;
        h.fx = t.getByte("x") & 255;
        h.fy = t.getByte("y") & 255;
        h.semilla = t.getInt("s");
        h.especial = t.getBoolean("e");
        h.total = Math.max(20, t.getInt("T"));
        h.restante = t.getInt("r");
        return h;
    }

    public void escribir(FriendlyByteBuf b) {
        b.writeByte(parte.ordinal());
        b.writeByte(tipo.ordinal() | (especial ? 0x80 : 0));
        b.writeByte(gravedad | (cara << 4));
        b.writeByte(fx);
        b.writeByte(fy);
        b.writeInt(semilla);
        b.writeVarInt(total);
        b.writeVarInt(Math.max(0, restante));
    }

    public static Herida leer(FriendlyByteBuf b) {
        Herida h = new Herida();
        h.parte = Parte.de(b.readByte());
        int t = b.readUnsignedByte();
        h.tipo = Tipo.de(t & 0x7F);
        h.especial = (t & 0x80) != 0;
        int g = b.readUnsignedByte();
        h.gravedad = Math.max(1, Math.min(3, g & 15));
        h.cara = (g >> 4) & 3;
        h.fx = b.readUnsignedByte();
        h.fy = b.readUnsignedByte();
        h.semilla = b.readInt();
        h.total = Math.max(20, b.readVarInt());
        h.restante = b.readVarInt();
        return h;
    }
}
