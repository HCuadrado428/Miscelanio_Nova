package com.miscelanea.network;

import com.miscelanea.aspira.Deposito;
import com.miscelanea.aspira.Modos;
import com.miscelanea.aspira.Reserva;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: botones de la ventana de la mochila. */
public class MochilaAccionPacket {
    public static final int SOLTAR = 0, ROCIAR = 1, VACIAR = 2, PAUSA = 3, USAR = 4, QUITAR = 5;

    private final int accion, indice;

    public MochilaAccionPacket(int accion, int indice) {
        this.accion = accion;
        this.indice = indice;
    }

    public MochilaAccionPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(accion);
        buf.writeVarInt(indice);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) return;
        ItemStack mochila = Deposito.mochila(p);
        if (mochila.isEmpty()) return;
        switch (accion) {
            case SOLTAR -> {
                Vec3 mira = p.getViewVector(1.0F);
                Vec3 pos = p.position().add(mira.x * 1.6, 0.2, mira.z * 1.6);
                Deposito.soltar(p, indice, pos, new Vec3(mira.x * 0.15, 0.15, mira.z * 0.15));
            }
            case ROCIAR -> Modos.rociar(p);
            case VACIAR -> {
                if (!Deposito.tanque(mochila).isEmpty()) {
                    Deposito.vaciarTanque(mochila);
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8F, 1.3F);
                }
            }
            case PAUSA -> Reserva.alternarPausa(mochila, indice);
            case USAR -> Reserva.usarAhora(p, mochila, indice);
            case QUITAR -> {
                Reserva.quitar(mochila, indice);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.7F, 1.0F);
            }
            default -> {}
        }
    }
}
