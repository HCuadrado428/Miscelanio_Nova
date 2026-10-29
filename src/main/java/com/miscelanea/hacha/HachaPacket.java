package com.miscelanea.hacha;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente → servidor: acción con el Hacha Leviatán, mirando hacia 'yaw'/'pitch' (y a 'objetivo'). */
public class HachaPacket {
    private final int accion, objetivo;
    private final float yaw, pitch;

    public HachaPacket(AccionHacha a, float yaw, float pitch, int objetivo) {
        this.accion = a.ordinal();
        this.yaw = yaw;
        this.pitch = pitch;
        this.objetivo = objetivo;
    }

    public HachaPacket(FriendlyByteBuf b) {
        this.accion = b.readByte();
        this.yaw = b.readFloat();
        this.pitch = b.readFloat();
        this.objetivo = b.readInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(accion);
        b.writeFloat(yaw);
        b.writeFloat(pitch);
        b.writeInt(objetivo);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p != null) Leviatan.accion(p, AccionHacha.de(accion), yaw, pitch, objetivo);
    }
}
