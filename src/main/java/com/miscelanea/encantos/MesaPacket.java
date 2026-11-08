package com.miscelanea.encantos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: abre (o refresca) la mesa de elegir con sus opciones. */
public class MesaPacket {
    public record Op(String id, int niveles, int lapis) {}

    public final BlockPos mesa;
    public final List<Op> ops;
    public final int libros;

    public MesaPacket(BlockPos mesa, List<MesaEncantar.Opcion> o, int libros) {
        this.mesa = mesa;
        this.libros = libros;
        ops = new ArrayList<>();
        for (var x : o) ops.add(new Op(String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(x.e())), x.niveles(), x.lapis()));
    }

    public MesaPacket(FriendlyByteBuf b) {
        mesa = b.readBlockPos();
        libros = b.readVarInt();
        int n = b.readVarInt();
        ops = new ArrayList<>();
        for (int i = 0; i < n; i++) ops.add(new Op(b.readUtf(), b.readVarInt(), b.readVarInt()));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBlockPos(mesa);
        b.writeVarInt(libros);
        b.writeVarInt(ops.size());
        for (Op o : ops) {
            b.writeUtf(o.id());
            b.writeVarInt(o.niveles());
            b.writeVarInt(o.lapis());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> c) {
        c.get().setPacketHandled(true);
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.encantos.MesaEncantarScreen.abrir(this));
    }
}
