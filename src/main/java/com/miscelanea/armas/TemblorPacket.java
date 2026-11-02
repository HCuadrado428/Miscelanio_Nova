package com.miscelanea.armas;

import com.miscelanea.network.ModNetwork;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/** Servidor → cliente: sacudida de cámara (golpe sísmico, estallido de plasma...). */
public class TemblorPacket {
    final float fuerza;
    final int duracion;

    public TemblorPacket(float fuerza, int duracion) {
        this.fuerza = fuerza;
        this.duracion = duracion;
    }

    public TemblorPacket(FriendlyByteBuf b) {
        this(b.readFloat(), b.readVarInt());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeFloat(fuerza);
        b.writeVarInt(duracion);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.miscelanea.client.armas.TemblorCliente.empezar(fuerza, duracion));
    }

    /** Sacude la cámara de los jugadores cercanos, más cuanto más cerca. */
    public static void enviar(ServerLevel l, Vec3 c, double radio, float fuerza, int duracion) {
        for (ServerPlayer p : l.players()) {
            double d = p.position().distanceTo(c);
            if (d > radio) continue;
            float f = fuerza * (float) (1 - d / radio);
            if (f > 0.05F) ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new TemblorPacket(f, duracion));
        }
    }
}
