package com.miscelanea.tortura.heridas;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Todas las heridas de un jugador más las vendas puestas (ticks que le quedan a la venda de cada parte). */
public class HeridasDatos {
    /** Umbral de "lisiado" (como las extremidades tullidas de Fallout). */
    public static final float LISIADO = 0.5F;
    /** Lo que dura una venda puesta. */
    public static final int DURACION_VENDA = 2400;
    public static final int MAX_TOTAL = 24;

    public final List<Herida> lista = new ArrayList<>();
    public final int[] vendas = new int[Parte.V.length];

    public boolean vacio() {
        if (!lista.isEmpty()) return false;
        for (int v : vendas) if (v > 0) return false;
        return true;
    }

    public boolean vendada(Parte p) {
        return vendas[p.ordinal()] > 0;
    }

    public int cuantas(Parte p) {
        int n = 0;
        for (Herida h : lista) if (h.parte == p) n++;
        return n;
    }

    /** Lo tocada que está la parte: 0 = sana, 1 = destrozada. Las partes vendadas no cuentan (la venda las sujeta). */
    public float nivel(Parte p) {
        if (vendada(p)) return 0F;
        return dano(p);
    }

    /** Igual que nivel() pero sin tener en cuenta la venda (para el HUD). */
    public float dano(Parte p) {
        float s = 0;
        for (Herida h : lista) if (h.parte == p) s += h.peso();
        return Math.min(1F, s / 6F);
    }

    public boolean lisiado(Parte p) {
        return nivel(p) >= LISIADO;
    }

    public Herida peor() {
        Herida peor = null;
        for (Herida h : lista) if (peor == null || h.peso() > peor.peso()) peor = h;
        return peor;
    }

    public CompoundTag guardar() {
        CompoundTag t = new CompoundTag();
        ListTag l = new ListTag();
        for (Herida h : lista) l.add(h.guardar());
        t.put("l", l);
        t.putIntArray("v", vendas.clone());
        return t;
    }

    public static HeridasDatos cargar(CompoundTag t) {
        HeridasDatos d = new HeridasDatos();
        ListTag l = t.getList("l", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && i < MAX_TOTAL; i++) d.lista.add(Herida.cargar(l.getCompound(i)));
        int[] v = t.getIntArray("v");
        for (int i = 0; i < Math.min(v.length, d.vendas.length); i++) d.vendas[i] = v[i];
        return d;
    }

    public void escribir(FriendlyByteBuf b) {
        int n = Math.min(MAX_TOTAL, lista.size());
        b.writeByte(n);
        for (int i = 0; i < n; i++) lista.get(i).escribir(b);
        for (int v : vendas) b.writeVarInt(Math.max(0, v));
    }

    public static HeridasDatos leer(FriendlyByteBuf b) {
        HeridasDatos d = new HeridasDatos();
        int n = Math.min(MAX_TOTAL, b.readUnsignedByte());
        for (int i = 0; i < n; i++) d.lista.add(Herida.leer(b));
        for (int i = 0; i < d.vendas.length; i++) d.vendas[i] = b.readVarInt();
        return d;
    }
}
