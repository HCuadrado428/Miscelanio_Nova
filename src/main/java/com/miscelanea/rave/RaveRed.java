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

    public static final int DJ_PONER = 0, DJ_PARAR = 1, DJ_VOLUMEN = 2, DJ_BUCLE = 3, DJ_LUCES_ON = 4, DJ_LUCES_OFF = 5, DJ_EMITIR = 6;
    /** Tamaño máximo de un trozo de audio Opus del DJ (20 ms suelen ser 60 a 300 bytes). */
    public static final int MAX_AUDIO = 1500;

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

    /** 20 ms del audio del ordenador del DJ, en Opus, para la mesa que está emitiendo. */
    public record Audio(BlockPos pos, byte[] opus) {
        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeByteArray(opus);
        }

        static Audio decode(FriendlyByteBuf b) {
            return new Audio(b.readBlockPos(), b.readByteArray(MAX_AUDIO));
        }

        static void handle(Audio m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer p = c.getSender();
            c.enqueueWork(() -> {
                if (p == null || m.opus.length == 0) return;
                if (p.level().getBlockEntity(m.pos) instanceof MesaDjBlockEntity mesa && p.getUUID().equals(mesa.emisor())) AudioDj.recibir(p, mesa, m.opus);
            });
            c.setPacketHandled(true);
        }
    }

    /** Pantalla de vídeo: poner un vídeo de mi galería (id; -1 = quitarlo) en la pantalla cuyo origen es pos. */
    public record PonerVideo(BlockPos pos, int clip) {
        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeVarInt(clip);
        }

        static PonerVideo decode(FriendlyByteBuf b) {
            return new PonerVideo(b.readBlockPos(), b.readVarInt());
        }

        static void handle(PonerVideo m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer p = c.getSender();
            c.enqueueWork(() -> {
                if (p == null || p.distanceToSqr(m.pos.getX() + 0.5, m.pos.getY() + 0.5, m.pos.getZ() + 0.5) > 32 * 32) return;
                if (!(p.level().getBlockEntity(m.pos) instanceof PantallaVideoBlockEntity be)) return;
                if (m.clip < 0) be.poner(null, -1);
                else if (com.miscelanea.movil.Clips.clipDe(p.server, p.getUUID(), m.clip) != null) be.poner(p.getUUID(), m.clip);
            });
            c.setPacketHandled(true);
        }
    }

    /** El cliente pide los datos del vídeo que enseña la pantalla de pos. */
    public record PedirVideo(BlockPos pos) {
        void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
        }

        static PedirVideo decode(FriendlyByteBuf b) {
            return new PedirVideo(b.readBlockPos());
        }

        static void handle(PedirVideo m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer p = c.getSender();
            c.enqueueWork(() -> {
                if (p == null || !(p.level().getBlockEntity(m.pos) instanceof PantallaVideoBlockEntity be) || be.dueno() == null) return;
                if (p.distanceToSqr(m.pos.getX() + 0.5, m.pos.getY() + 0.5, m.pos.getZ() + 0.5) > 160 * 160) return;
                byte[] d = com.miscelanea.movil.Clips.clipDe(p.server, be.dueno(), be.clip());
                CANAL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> p), new Video(be.dueno(), be.clip(), d == null ? new byte[0] : d));
            });
            c.setPacketHandled(true);
        }
    }

    /** Servidor → cliente: los datos de un vídeo (de quién, cuál y el archivo; vacío si ya no existe). */
    public record Video(java.util.UUID dueno, int clip, byte[] datos) {
        void encode(FriendlyByteBuf b) {
            b.writeUUID(dueno);
            b.writeVarInt(clip);
            b.writeByteArray(datos);
        }

        static Video decode(FriendlyByteBuf b) {
            return new Video(b.readUUID(), b.readVarInt(), b.readByteArray(1_000_000));
        }

        static void handle(Video m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            c.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () ->
                    com.miscelanea.client.rave.VideosPantalla.recibir(m.dueno, m.clip, m.datos)));
            c.setPacketHandled(true);
        }
    }

    /** Orden del panel o del mando de luces. */
    public record Luces(boolean mando, BlockPos pos, int accion, int valor) {
        void encode(FriendlyByteBuf b) {
            b.writeBoolean(mando);
            b.writeBlockPos(pos);
            b.writeByte(accion);
            b.writeVarInt(valor);
        }

        static Luces decode(FriendlyByteBuf b) {
            return new Luces(b.readBoolean(), b.readBlockPos(), b.readUnsignedByte(), b.readVarInt());
        }

        static void handle(Luces m, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context c = ctx.get();
            ServerPlayer p = c.getSender();
            c.enqueueWork(() -> {
                if (p != null && m.valor >= 0 && m.valor < 16) PanelLuces.orden(p, m.mando, m.pos, m.accion, m.valor);
            });
            c.setPacketHandled(true);
        }
    }

    static void registrar() {
        CANAL.messageBuilder(Dj.class, 0, NetworkDirection.PLAY_TO_SERVER).encoder(Dj::encode).decoder(Dj::decode).consumerMainThread(Dj::handle).add();
        CANAL.messageBuilder(Audio.class, 1, NetworkDirection.PLAY_TO_SERVER).encoder(Audio::encode).decoder(Audio::decode).consumerMainThread(Audio::handle).add();
        CANAL.messageBuilder(PonerVideo.class, 2, NetworkDirection.PLAY_TO_SERVER).encoder(PonerVideo::encode).decoder(PonerVideo::decode).consumerMainThread(PonerVideo::handle).add();
        CANAL.messageBuilder(PedirVideo.class, 3, NetworkDirection.PLAY_TO_SERVER).encoder(PedirVideo::encode).decoder(PedirVideo::decode).consumerMainThread(PedirVideo::handle).add();
        CANAL.messageBuilder(Luces.class, 5, NetworkDirection.PLAY_TO_SERVER).encoder(Luces::encode).decoder(Luces::decode).consumerMainThread(Luces::handle).add();
        CANAL.messageBuilder(Video.class, 4, NetworkDirection.PLAY_TO_CLIENT).encoder(Video::encode).decoder(Video::decode).consumerMainThread(Video::handle).add();
    }

    /** Desde el cliente: un trozo de audio del ordenador del DJ. */
    public static void audio(BlockPos pos, byte[] opus) {
        CANAL.sendToServer(new Audio(pos, opus));
    }

    /** Desde el cliente. */
    public static void dj(BlockPos pos, int accion, String texto, int numero) {
        CANAL.sendToServer(new Dj(pos, accion, texto, numero));
    }
}
