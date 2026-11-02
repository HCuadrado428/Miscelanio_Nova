package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.heridas.Parte;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Mancha de tomate o de huevo sobre la piel de un jugador (no es una herida: no hace nada, solo se ve). Lleva dónde está
 * (pieza, cara del cubo y posición 0..255 dentro de la cara, como las heridas), qué es y cuánto le queda (se seca y se va
 * a los ~2 minutos; el agua o la lluvia la quitan al momento).
 */
public class Mancha {
    public static final int TOMATE = 0, HUEVO = 1;
    public Parte parte;
    public int cara, fx, fy, tipo, semilla, total, restante;

    public Mancha(Parte parte, int cara, int fx, int fy, int tipo, int semilla, int total) {
        this.parte = parte;
        this.cara = cara & 3;
        this.fx = fx & 255;
        this.fy = fy & 255;
        this.tipo = tipo;
        this.semilla = semilla;
        this.total = Math.max(20, total);
        this.restante = this.total;
    }

    private Mancha() {}

    /** 0 = recién puesta, 1 = desaparecida. */
    public float progreso() {
        return 1F - Math.max(0, Math.min(total, restante)) / (float) total;
    }

    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        t.putByte("p", (byte) parte.ordinal());
        t.putByte("c", (byte) cara);
        t.putByte("x", (byte) fx);
        t.putByte("y", (byte) fy);
        t.putByte("t", (byte) tipo);
        t.putInt("s", semilla);
        t.putInt("T", total);
        t.putInt("R", restante);
        return t;
    }

    public static Mancha cargar(CompoundTag t) {
        Mancha m = new Mancha();
        m.parte = Parte.de(t.getByte("p"));
        m.cara = t.getByte("c") & 3;
        m.fx = t.getByte("x") & 255;
        m.fy = t.getByte("y") & 255;
        m.tipo = t.getByte("t") & 1;
        m.semilla = t.getInt("s");
        m.total = Math.max(20, t.getInt("T"));
        m.restante = Math.max(0, Math.min(m.total, t.getInt("R")));
        return m;
    }

    public void escribir(FriendlyByteBuf b) {
        b.writeByte(parte.ordinal());
        b.writeByte(cara | (tipo << 2));
        b.writeByte(fx);
        b.writeByte(fy);
        b.writeInt(semilla);
        b.writeVarInt(total);
        b.writeVarInt(restante);
    }

    public static Mancha leer(FriendlyByteBuf b) {
        Mancha m = new Mancha();
        m.parte = Parte.de(b.readByte());
        int k = b.readByte();
        m.cara = k & 3;
        m.tipo = (k >> 2) & 1;
        m.fx = b.readByte() & 255;
        m.fy = b.readByte() & 255;
        m.semilla = b.readInt();
        m.total = Math.max(20, b.readVarInt());
        m.restante = b.readVarInt();
        return m;
    }
}
