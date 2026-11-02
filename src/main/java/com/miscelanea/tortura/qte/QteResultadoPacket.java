package com.miscelanea.tortura.qte;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: así ha terminado el minijuego (el servidor lo valida). */
public class QteResultadoPacket {
    private final int id;
    private final QteResultado resultado;
    private final int[] datos;

    public QteResultadoPacket(int id, QteResultado resultado, int[] datos) {
        this.id = id;
        this.resultado = resultado;
        this.datos = datos;
    }

    public QteResultadoPacket(FriendlyByteBuf b) {
        this.id = b.readVarInt();
        this.resultado = QteResultado.de(b.readByte());
        int n = Math.min(8, b.readVarInt());
        this.datos = new int[n];
        for (int i = 0; i < n; i++) datos[i] = b.readInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeByte(resultado.ordinal());
        int n = Math.min(8, datos.length);
        b.writeVarInt(n);
        for (int i = 0; i < n; i++) b.writeInt(datos[i]);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) Qte.recibir(p, id, resultado, datos);
    }
}
