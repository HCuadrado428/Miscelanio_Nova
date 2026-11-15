package com.miscelanea.armasfuego.comun;

import com.miscelanea.armasfuego.pistola.RevolverItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: el gatillo (clic izquierdo) del revólver y de las armas de la segunda tanda. {@code apretado}
 * falso es soltarlo (acaba la ráfaga de la Thompson).
 */
public class GatilloPacket {
    private final boolean apretado;

    public GatilloPacket(boolean apretado) {
        this.apretado = apretado;
    }

    public GatilloPacket(FriendlyByteBuf b) {
        apretado = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeBoolean(apretado);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        var p = ctx.get().getSender();
        if (p == null || p.isSpectator()) return;
        if (!apretado) {
            ArmaFuegoItem.soltarGatillo(p);
            return;
        }
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof ArmaFuegoItem a) a.gatillo(p.level(), p, s);
        else if (s.getItem() instanceof RevolverItem r) r.gatillo(p.level(), p, s);
    }
}
