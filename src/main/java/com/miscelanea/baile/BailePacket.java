package com.miscelanea.baile;

import com.miscelanea.arcade.ArcadeRecords;
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
 * FIN (puntos), SALIR. Servidor → cliente: ABRIR (menú), EMPIEZA (suena y empieza), PASO (pose de otro), PARA.
 */
public class BailePacket {
    public static final int ABRIR = 0, EMPIEZA = 1, PASO = 2, FIN = 3, SALIR = 4, PARA = 5;
    public final int tipo, entidad, a, b;
    public final BlockPos pos;

    public BailePacket(int tipo, BlockPos pos, int entidad, int a, int b) {
        this.tipo = tipo;
        this.pos = pos;
        this.entidad = entidad;
        this.a = a;
        this.b = b;
    }

    public BailePacket(FriendlyByteBuf buf) {
        this(buf.readByte(), buf.readBlockPos(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeByte(tipo);
        buf.writeBlockPos(pos);
        buf.writeVarInt(entidad);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
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
            case EMPIEZA -> be.empezar(p, a);
            case PASO -> ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> p), new BailePacket(PASO, pos, p.getId(), a & 7, b & 7));
            case FIN -> {
                be.parar();
                be.cambio();
                if (a > 0) {
                    ArcadeRecords.registrar(p, "paso", Math.min(a, 999_999));
                    p.serverLevel().players().stream().filter(o -> o.distanceToSqr(p) < 32 * 32).forEach(o ->
                            o.displayClientMessage(Component.translatable("baile.miscelanea.resultado", p.getDisplayName(), a,
                                    "SABCDE".substring(Math.max(0, Math.min(5, b)), Math.max(0, Math.min(5, b)) + 1)).withStyle(ChatFormatting.LIGHT_PURPLE), false));
                }
            }
            case SALIR -> be.salir();
            default -> {}
        }
    }
}
