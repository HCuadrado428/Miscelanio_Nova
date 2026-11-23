package com.miscelanea.rave;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

/** Canal propio de la rave ({@code miscelanea:rave}): las órdenes de la pantalla de la mesa de DJ (todo se valida aquí). */
public final class RaveRed {
    private RaveRed() {}

    private static final String PROTOCOLO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Miscelanea.MOD_ID, "rave"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    public static final int DJ_PONER = 0, DJ_PARAR = 1, DJ_VOLUMEN = 2, DJ_BUCLE = 3, DJ_LUCES_ON = 4, DJ_LUCES_OFF = 5;

    /** Una orden de la mesa de DJ: posición, acción, texto (la pista) y número (volumen, bucle). */
    public record Dj(BlockPos pos, int accion, String texto, int numero) {
        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeByte(accion);
            b.writeUtf(texto, 200);
            b.writeVarInt(numero);
        }

        static Dj decode(FriendlyByteBuf b) {
            return new Dj(b.readBlockPos(), b.readUnsignedByte(), b.readUtf(200), b.readVarInt());
        }

        static void handle(Dj m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer p = c.getSender();
            c.enqueueWork(() -> {
                if (p == null || p.distanceToSqr(m.pos.getX() + 0.5, m.pos.getY() + 0.5, m.pos.getZ() + 0.5) > 100) return;
                if (p.level().getBlockEntity(m.pos) instanceof MesaDjBlockEntity mesa) mesa.orden(p, m.accion, m.texto, m.numero);
            });
            c.setPacketHandled(true);
        }
    }

    static void registrar() {
        CANAL.messageBuilder(Dj.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Dj::encode).decoder(Dj::decode).consumerMainThread(Dj::handle).add();
    }

    /** Desde el cliente. */
    public static void dj(BlockPos pos, int accion, String texto, int numero) {
        CANAL.sendToServer(new Dj(pos, accion, texto, numero));
    }
}
