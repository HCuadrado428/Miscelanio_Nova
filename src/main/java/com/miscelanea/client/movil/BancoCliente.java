package com.miscelanea.client.movil;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Estado del Banco que manda el servidor: saldo, dinero en el inventario, límites e historial. */
public final class BancoCliente {
    private BancoCliente() {}

    public record Mov(long t, int tipo, UUID otro, String nombre, long cantidad, String texto) {}

    public static long saldo, enInventario, limite;
    /** enCajero: el servidor lo ve junto a un cajero (sin límite); omega: el dinero son chips de OmegaChips (si no, esmeraldas). */
    public static boolean enCajero, omega;
    public static int precioFicha = 9;
    public static int maxTransferencia = 100000, maxConcepto = 40;
    public static boolean objetos, cargado;
    public static final List<Mov> HISTORIAL = new ArrayList<>();

    static void recibir(FriendlyByteBuf b) {
        saldo = b.readVarLong();
        enInventario = b.readVarLong();
        enCajero = b.readBoolean();
        omega = b.readBoolean();
        limite = b.readVarLong();
        precioFicha = b.readVarInt();
        maxTransferencia = b.readVarInt();
        maxConcepto = b.readVarInt();
        objetos = b.readBoolean();
        HISTORIAL.clear();
        int n = b.readVarInt();
        for (int i = 0; i < n; i++) {
            long t = b.readLong();
            int tipo = b.readByte();
            UUID o = b.readBoolean() ? b.readUUID() : null;
            HISTORIAL.add(new Mov(t, tipo, o, b.readUtf(), b.readLong(), MovilCliente.traducir(b.readUtf())));
        }
        cargado = true;
    }

    static void limpiar() {
        saldo = 0;
        enInventario = 0;
        cargado = false;
        HISTORIAL.clear();
    }
}
