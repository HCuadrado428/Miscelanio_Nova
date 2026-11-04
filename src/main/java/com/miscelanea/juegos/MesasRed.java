package com.miscelanea.juegos;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Canal propio de los juegos de mesa (gestos de los jugadores y reloj de turno), aparte del canal principal del mod.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MesasRed {
    private static final String PROTOCOLO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(Miscelanea.MOD_ID, "mesas"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private MesasRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(GestoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(GestoPacket::encode).decoder(GestoPacket::new).consumerMainThread(GestoPacket::handle).add();
            CANAL.messageBuilder(RelojPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(RelojPacket::encode).decoder(RelojPacket::new).consumerMainThread(RelojPacket::handle).add();
        });
    }

    /** Servidor → clientes: un jugador hace un gesto en una mesa (lo ven todos los que lo tienen cerca, y él mismo). */
    public static final class GestoPacket {
        public final int entidad, tipo;
        public final float x, y, z;

        public GestoPacket(int entidad, int tipo, Vec3 objetivo) {
            this.entidad = entidad;
            this.tipo = tipo;
            this.x = (float) objetivo.x;
            this.y = (float) objetivo.y;
            this.z = (float) objetivo.z;
        }

        public GestoPacket(FriendlyByteBuf b) {
            entidad = b.readVarInt();
            tipo = b.readByte();
            x = b.readFloat();
            y = b.readFloat();
            z = b.readFloat();
        }

        public void encode(FriendlyByteBuf b) {
            b.writeVarInt(entidad);
            b.writeByte(tipo);
            b.writeFloat(x);
            b.writeFloat(y);
            b.writeFloat(z);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            if (ctx.get().getSender() != null) return;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.juegos.GestosCliente.recibir(this));
        }

        static void enviar(ServerPlayer p, int tipo, Vec3 objetivo) {
            CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new GestoPacket(p.getId(), tipo, objetivo));
        }
    }

    /**
     * Servidor → clientes cercanos: a quién espera la mesa y cuánto le queda de turno. Se repite cada medio segundo
     * mientras se espera a un humano; si deja de llegar, el cliente quita el reloj solo.
     */
    public static final class RelojPacket {
        public final BlockPos pos;
        @Nullable
        public final UUID esperado;
        public final String nombre;
        /** Ticks que le quedan (-1 sin límite) y ticks totales del turno. */
        public final int restante, total;
        /** La CPU está jugando por él (se fue o se le acabó el tiempo). */
        public final boolean dormido;

        public RelojPacket(BlockPos pos, @Nullable UUID esperado, String nombre, int restante, int total, boolean dormido) {
            this.pos = pos;
            this.esperado = esperado;
            this.nombre = nombre == null ? "" : nombre;
            this.restante = restante;
            this.total = total;
            this.dormido = dormido;
        }

        public RelojPacket(FriendlyByteBuf b) {
            pos = b.readBlockPos();
            esperado = b.readBoolean() ? b.readUUID() : null;
            nombre = b.readUtf(64);
            restante = b.readVarInt() - 1;
            total = b.readVarInt();
            dormido = b.readBoolean();
        }

        public void encode(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeBoolean(esperado != null);
            if (esperado != null) b.writeUUID(esperado);
            b.writeUtf(nombre.length() > 64 ? nombre.substring(0, 64) : nombre, 64);
            b.writeVarInt(Math.max(-1, restante) + 1);
            b.writeVarInt(Math.max(0, total));
            b.writeBoolean(dormido);
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            if (ctx.get().getSender() != null) return;
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.juegos.RelojCliente.recibir(this));
        }

        public static void enviar(Level l, BlockPos pos, RelojPacket p) {
            if (l == null || l.isClientSide) return;
            CANAL.send(PacketDistributor.TRACKING_CHUNK.with(() -> l.getChunkAt(pos)), p);
        }
    }
}
