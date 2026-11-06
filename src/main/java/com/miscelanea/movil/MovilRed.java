package com.miscelanea.movil;

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
 * Canal propio del móvil ({@code miscelanea:movil}). Dos paquetes genéricos (uno por sentido) con un tipo y una carga
 * ya serializada; cada tipo se lee en {@link MovilServidor#recibir} o en el cliente
 * ({@code com.miscelanea.client.movil.MovilRedCliente}).
 */
public final class MovilRed {
    private MovilRed() {}

    private static final String PROTOCOLO = "2";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Miscelanea.MOD_ID, "movil"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    // ---------------------------------------------------------------- cliente → servidor
    public static final int C_POSE = 0, C_SYNC = 1, C_CHAT_ABRIR = 2, C_CHAT_NUEVO = 3, C_MENSAJE = 4, C_FOTO_SUBIR = 5, C_FOTOS = 6,
            C_FOTO_PEDIR = 7, C_FOTO_BORRAR = 8, C_LLAMAR = 9, C_LLAMAR_NUMERO = 10, C_CONTESTAR = 11, C_COLGAR = 12, C_VIDEO = 13,
            C_AJUSTES = 14, C_CHAT_LEIDO = 15, C_CHAT_SALIR = 16,
            // notas de voz
            C_VOZ_TROZO = 17, C_VOZ_PEDIR = 18,
            // ubicación en tiempo real
            C_UBI_COMPARTIR = 19, C_UBI_PARAR = 20,
            // Minegram
            C_MG_FEED = 21, C_MG_PUBLICAR = 22, C_MG_LIKE = 23, C_MG_POST = 24, C_MG_COMENTAR = 25, C_MG_BORRAR = 26, C_MG_PERFIL = 27,
            C_MG_SEGUIR = 28, C_MG_NOTIS = 29,
            // banco
            C_BANCO = 30, C_BANCO_INGRESAR = 31, C_BANCO_RETIRAR = 32, C_BIZUM = 33, C_ENVIAR_OBJETO = 34,
            // linterna
            C_LINTERNA = 35,
            // fichas de recreativa en el cajero (cuántas)
            C_FICHAS = 38;
    // ---------------------------------------------------------------- servidor → cliente
    public static final int S_SYNC = 0, S_CHAT = 1, S_MENSAJE = 2, S_FOTOS = 3, S_FOTO = 4, S_LLAMADA = 5, S_VIDEO = 6, S_POSE = 7,
            S_AVISO = 8, S_CHAT_ID = 9, S_VOZ = 10, S_UBI = 11, S_NOTI = 12, S_MG_FEED = 13, S_MG_POST = 14, S_MG_PERFIL = 15,
            S_MG_NOTIS = 16, S_BANCO = 17, S_LINTERNA = 18;

    /** Estados de llamada que manda {@link #S_LLAMADA}. */
    public static final int LL_FIN = 0, LL_SALIENTE = 1, LL_ENTRANTE = 2, LL_ACTIVA = 3;
    /** Poses del jugador con el móvil (para los demás). */
    public static final int POSE_NADA = 0, POSE_MIRAR = 1, POSE_SELFIE = 2;
    /** Tipos de foto que se piden: de tu galería o adjunta a un mensaje. */
    public static final int FOTO_PROPIA = 0, FOTO_ADJUNTA = 1, FOTO_MINEGRAM = 2;
    /** Icono (app) de las notificaciones genéricas {@link #S_NOTI}: mismos números que las apps del cliente. */
    public static final int APP_CHATS = 0, APP_MINEGRAM = 8, APP_MAPA = 9, APP_BANCO = 10;
    /** Trozo máximo de los paquetes que el cliente sube por partes (el límite de vanilla es 32767 bytes). */
    public static final int TROZO = 28000;

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
                if (sp != null) MovilServidor.recibir(sp, m.p.tipo, new FriendlyByteBuf(Unpooled.wrappedBuffer(m.p.datos)));
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
                    com.miscelanea.client.movil.MovilRedCliente.recibir(m.p.tipo, new FriendlyByteBuf(Unpooled.wrappedBuffer(m.p.datos)))));
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

    public static void aQuienLoVe(ServerPlayer p, int tipo, Consumer<FriendlyByteBuf> w) {
        CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new AlCliente(new Paquete(tipo, bytes(w))));
    }

    /** Desde el cliente. */
    public static void alServidor(int tipo, Consumer<FriendlyByteBuf> w) {
        CANAL.sendToServer(new AlServidor(new Paquete(tipo, bytes(w))));
    }
}
