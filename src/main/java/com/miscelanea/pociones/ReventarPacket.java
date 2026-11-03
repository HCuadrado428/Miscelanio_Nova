package com.miscelanea.pociones;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Servidor → cliente: un mob ha reventado de un puñetazo de la sed de sangre. Posición y tamaño de la víctima y hasta
 * ocho de sus botines (para las partículas de "trozos").
 */
public class ReventarPacket {
    public final double x, y, z;
    public final float ancho, alto;
    public final List<ItemStack> trozos;

    public ReventarPacket(double x, double y, double z, float ancho, float alto, List<ItemStack> trozos) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.ancho = ancho;
        this.alto = alto;
        this.trozos = trozos;
    }

    public ReventarPacket(FriendlyByteBuf b) {
        x = b.readDouble();
        y = b.readDouble();
        z = b.readDouble();
        ancho = b.readFloat();
        alto = b.readFloat();
        int n = b.readByte();
        trozos = new ArrayList<>(n);
        for (int i = 0; i < n; i++) trozos.add(b.readItem());
    }

    public void encode(FriendlyByteBuf b) {
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeFloat(ancho);
        b.writeFloat(alto);
        int n = Math.min(8, trozos.size());
        b.writeByte(n);
        for (int i = 0; i < n; i++) b.writeItem(trozos.get(i));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.pociones.SangreCliente.reventar(this));
    }
}
