package com.miscelanea.torneo;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Acciones en el tablón de torneos (cliente → servidor). */
public class TorneoPacket {
    public static final int APUNTAR = 0, BORRAR = 1, EMPEZAR = 2, GANA = 3, REINICIAR = 4, TITULO = 5;
    public final int tipo, a, b;
    public final BlockPos pos;
    public final String texto;

    public TorneoPacket(int tipo, BlockPos pos, int a, int b, String texto) {
        this.tipo = tipo;
        this.pos = pos;
        this.a = a;
        this.b = b;
        this.texto = texto;
    }

    public TorneoPacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(32));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(tipo);
        buf.writeBlockPos(pos);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeUtf(texto, 32);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 100) return;
        if (!(p.level().getBlockEntity(pos) instanceof TorneoBlockEntity be)) return;
        switch (tipo) {
            case APUNTAR -> be.apuntar(p);
            case BORRAR -> be.borrar(p);
            case EMPEZAR -> be.empezar(p);
            case GANA -> be.gana(p, a, b);
            case REINICIAR -> be.reiniciar(p);
            case TITULO -> be.titulo(p, texto);
            default -> {}
        }
    }
}
