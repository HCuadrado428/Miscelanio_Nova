package com.miscelanea.baile;

import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

/**
 * Mensajes de la pista de baile en los dos sentidos. Cliente → servidor: EMPIEZA (canción), PASO (carril y juicio),
 * FIN (puntos), SALIR. Servidor → cliente: ABRIR (menú), EMPIEZA (suena y empieza), PASO (luces y traspiés de otro), PARA.
 */
public class BailePacket {
    public static final int ABRIR = 0, EMPIEZA = 1, PASO = 2, FIN = 3, SALIR = 4, PARA = 5;
    public final int tipo, entidad, a, b;
    public final BlockPos pos;
    /** Canción (EMPIEZA): por su id, para que no se líe si el servidor y el cliente no tienen las mismas canciones. */
    public final String cancion;

    public BailePacket(int tipo, BlockPos pos, int entidad, int a, int b) {
        this(tipo, pos, entidad, a, b, "");
    }

    public BailePacket(int tipo, BlockPos pos, int entidad, int a, int b, String cancion) {
        this.tipo = tipo;
        this.pos = pos;
        this.entidad = entidad;
        this.a = a;
        this.b = b;
        this.cancion = cancion == null ? "" : cancion;
    }

    public BailePacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(128));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(tipo);
        buf.writeBlockPos(pos);
        buf.writeVarInt(entidad);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeUtf(cancion, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.baile.BaileCliente.recibir(this));
            return;
        }
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 36) return;
        if (!(p.level().getBlockEntity(pos) instanceof PistaBaileBlockEntity be) || !p.getUUID().equals(be.bailarin)) return;
        switch (tipo) {
            case EMPIEZA -> be.empezar(p, cancion, b);
            case PASO -> ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new BailePacket(PASO, pos, p.getId(), a & 7, b & 7));
            case FIN -> {
                // los puntos los cuenta el cliente: el servidor solo acepta los de una canción que él empezó, y nunca más de lo
                // que da de sí el trozo de canción que ha sonado (todo perfecto); un FIN sin canción en marcha no cuenta
                int max = be.maxPuntos();
                String id = be.cancion;
                int dif = be.dificultad;
                be.parar();
                be.cambio();
                int puntos = Math.min(a, max);
                if (max > 0 && puntos > 0) {
                    int nota = Math.max(0, Math.min(5, b));
                    com.miscelanea.ranking.Ranking.mejor(p, com.miscelanea.ranking.Ranking.categoriaPista(id, dif), puntos);
                    p.serverLevel().players().stream().filter(o -> o.distanceToSqr(p) < 32 * 32).forEach(o ->
                            o.displayClientMessage(Component.translatable("baile.miscelanea.resultado", p.getDisplayName(), puntos,
                                    "SABCDE".substring(nota, nota + 1)).withStyle(ChatFormatting.LIGHT_PURPLE), false));
                }
            }
            case SALIR -> be.salir();
            default -> {}
        }
    }
}
