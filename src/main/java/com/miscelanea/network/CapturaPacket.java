package com.miscelanea.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: estado del tira y afloja de captura.
 * resistencia 0–1 (−1 = se acabó), dir = hacia dónde tira el mob (+1 derecha), tirando = el jugador va ganando.
 */
public class CapturaPacket {
    private final int mob;
    private final float resistencia;
    private final byte dir;
    private final boolean tirando;

    public CapturaPacket(int mob, float resistencia, int dir, boolean tirando) {
        this.mob = mob;
        this.resistencia = resistencia;
        this.dir = (byte) dir;
        this.tirando = tirando;
    }

    public CapturaPacket(FriendlyByteBuf buf) {
        this.mob = buf.readVarInt();
        this.resistencia = buf.readFloat();
        this.dir = buf.readByte();
        this.tirando = buf.readBoolean();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mob);
        buf.writeFloat(resistencia);
        buf.writeByte(dir);
        buf.writeBoolean(tirando);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.miscelanea.client.CapturaHud.recibir(mob, resistencia, dir, tirando));
    }
}
