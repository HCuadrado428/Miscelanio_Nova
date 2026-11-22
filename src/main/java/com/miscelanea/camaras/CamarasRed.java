package com.miscelanea.camaras;

import com.miscelanea.Miscelanea;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Canal propio de las cámaras ({@code miscelanea:camaras}): un paquete genérico por sentido, con un tipo y la carga ya
 * serializada (como el del móvil). El servidor solo reenvía fotogramas: no los decodifica.
 */
public final class CamarasRed {
    private CamarasRed() {}

    private static final String PROTOCOLO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Miscelanea.MOD_ID, "camaras"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    /** Cliente → servidor: canales que estoy mirando; fotograma de un canal que me han mandado dibujar. */
    public static final int C_VER = 0, C_FRAME = 1;
    /** Servidor → cliente: dibuja esta cámara; para; fotograma de un canal; lista de canales que existen. */
    public static final int S_ORDEN = 0, S_PARAR = 1, S_FRAME = 2, S_LISTA = 3;
    /** Tamaño máximo de un fotograma JPEG (los de 128x72 salen de 2 a 6 KB). */
    public static final int MAX_FRAME = 14000;

    public static final class Paquete {
        final int tipo;
        final byte[] datos;

        Paquete(int tipo, byte[] datos) {
            this.tipo = tipo;
            this.datos = datos;
        }

        Paquete(FriendlyByteBuf b) {
            tipo = b.readUnsignedByte();
            datos = new byte[b.readableBytes()];
            b.readBytes(datos);
        }

        void encode(FriendlyByteBuf b) {
            b.writeByte(tipo);
            b.writeBytes(datos);
        }
    }

    public static final class AlServidor {
        final Paquete p;

        AlServidor(Paquete p) {
            this.p = p;
        }

        static void handle(AlServidor m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer sp = c.getSender();
            c.enqueueWork(() -> {
                if (sp != null) CamarasServidor.recibir(sp, m.p.tipo, new FriendlyByteBuf(Unpooled.wrappedBuffer(m.p.datos)));
            });
            c.setPacketHandled(true);
        }
    }

    public static final class AlCliente {
        final Paquete p;

        AlCliente(Paquete p) {
            this.p = p;
        }

        static void handle(AlCliente m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    com.miscelanea.client.camaras.CamarasCliente.recibir(m.p.tipo, new FriendlyByteBuf(Unpooled.wrappedBuffer(m.p.datos)))));
            c.setPacketHandled(true);
        }
    }

    static void registrar() {
        int id = 0;
        CANAL.messageBuilder(AlServidor.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, b) -> m.p.encode(b)).decoder(b -> new AlServidor(new Paquete(b))).consumerMainThread(AlServidor::handle).add();
        CANAL.messageBuilder(AlCliente.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, b) -> m.p.encode(b)).decoder(b -> new AlCliente(new Paquete(b))).consumerMainThread(AlCliente::handle).add();
    }

    private static byte[] bytes(Consumer<FriendlyByteBuf> w) {
        FriendlyByteBuf b = new FriendlyByteBuf(Unpooled.buffer());
        if (w != null) w.accept(b);
        byte[] out = new byte[b.readableBytes()];
        b.readBytes(out);
        return out;
    }

    public static void aJugador(ServerPlayer p, int tipo, Consumer<FriendlyByteBuf> w) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), new AlCliente(new Paquete(tipo, bytes(w))));
    }

    /** Desde el cliente. */
    public static void alServidor(int tipo, Consumer<FriendlyByteBuf> w) {
        CANAL.sendToServer(new AlServidor(new Paquete(tipo, bytes(w))));
    }
}
