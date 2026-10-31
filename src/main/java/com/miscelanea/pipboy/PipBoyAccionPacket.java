package com.miscelanea.pipboy;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Cliente → servidor: acciones del Pip-Boy (mirarlo, quitárselo, notas, marcadores). */
public class PipBoyAccionPacket {
    public static final byte MIRAR = 0, DEJAR_MIRAR = 1, QUITAR = 2, NOTAS = 3, MARCA = 4, BORRAR_MARCA = 5;
    private final byte tipo;
    private final int x, z;
    private final List<String> textos;

    public PipBoyAccionPacket(byte tipo, int x, int z, List<String> textos) {
        this.tipo = tipo;
        this.x = x;
        this.z = z;
        this.textos = textos;
    }

    public static PipBoyAccionPacket simple(byte tipo) {
        return new PipBoyAccionPacket(tipo, 0, 0, List.of());
    }

    public PipBoyAccionPacket(FriendlyByteBuf b) {
        this.tipo = b.readByte();
        this.x = b.readInt();
        this.z = b.readInt();
        int n = Math.min(16, b.readVarInt());
        this.textos = new ArrayList<>();
        for (int i = 0; i < n; i++) textos.add(b.readUtf(128));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(tipo);
        b.writeInt(x);
        b.writeInt(z);
        b.writeVarInt(textos.size());
        for (String s : textos) b.writeUtf(s, 128);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null || !PipBoy.lleva(p)) return;
        switch (tipo) {
            case MIRAR -> {
                PipBoy.enviarEstado(p, true);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.PIPBOY_ENCENDER.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            }
            case DEJAR_MIRAR -> PipBoy.enviarEstado(p, false);
            case QUITAR -> {
                PipBoy.poner(p, false);
                ItemStack s = new ItemStack(ModItems.PIPBOY.get());
                if (!p.getInventory().add(s)) p.drop(s, false);
            }
            case NOTAS -> PipBoy.notas(p, textos);
            case MARCA -> PipBoy.marca(p, x, z, textos.isEmpty() ? "?" : textos.get(0));
            case BORRAR_MARCA -> PipBoy.borrarMarca(p, x);
            default -> {}
        }
    }
}
