package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: entras (true, con la orientación de la taquilla) o sales de un escondite. */
public class EsconditePacket {
    private final boolean dentro;
    private final float yaw;

    public EsconditePacket(boolean dentro, float yaw) {
        this.dentro = dentro;
        this.yaw = yaw;
    }

    public EsconditePacket(FriendlyByteBuf buf) {
        this.dentro = buf.readBoolean();
        this.yaw = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(dentro);
        buf.writeFloat(yaw);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.carcel.TaquillaCliente.cambiar(dentro, yaw));
    }
}
