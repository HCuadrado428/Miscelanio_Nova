package com.miscelanea.llave;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: el jugador hace una acción con la Llave Espada, mirando hacia 'yaw' (y apuntando a 'objetivo'). */
public class AccionPacket {
    private final int accion, objetivo;
    private final float yaw;

    public AccionPacket(AccionLlave a, float yaw, int objetivo) {
        this.accion = a.ordinal();
        this.yaw = yaw;
        this.objetivo = objetivo;
    }

    public AccionPacket(FriendlyByteBuf b) {
        this.accion = b.readByte();
        this.yaw = b.readFloat();
        this.objetivo = b.readInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(accion);
        b.writeFloat(yaw);
        b.writeInt(objetivo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) CombateLlave.accion(p, AccionLlave.de(accion), yaw, objetivo);
    }
}
