package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: a quién cura cada medigun (-1 = nadie), ticks de ÜberCarga que quedan y color del rayo. */
public class MedigunPacket {
    private final int medico, paciente, uber, color;

    public MedigunPacket(int medico, int paciente, int uber, int color) {
        this.medico = medico;
        this.paciente = paciente;
        this.uber = uber;
        this.color = color;
    }

    public MedigunPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readInt(), buf.readVarInt(), buf.readInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(medico);
        buf.writeInt(paciente);
        buf.writeVarInt(uber);
        buf.writeInt(color);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.medigun.MedigunCliente.recibir(medico, paciente, uber, color));
    }
}
