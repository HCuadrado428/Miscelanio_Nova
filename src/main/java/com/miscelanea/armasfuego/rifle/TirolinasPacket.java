package com.miscelanea.armasfuego.rifle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Servidor → cliente: todas las tirolinas del rifle de la dimensión del jugador. */
public class TirolinasPacket {
    /** Lo que necesita el cliente de cada tirolina: puntas, altura del suelo bajo la punta a (NaN si no hay) y nivel. */
    public record Linea(int id, Vec3 a, Vec3 b, double sueloA, int nivel) {}

    private final List<Linea> lineas;

    public TirolinasPacket(List<Tirolinas.Linea> l) {
        lineas = new ArrayList<>();
        for (Tirolinas.Linea x : l)
            lineas.add(new Linea(x.id(), x.a(), x.b(), x.anclaA() == null ? Double.NaN : x.anclaA().getY() + 1, x.nivel()));
    }

    public TirolinasPacket(FriendlyByteBuf b) {
        int n = Math.min(b.readVarInt(), 4096);
        lineas = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            lineas.add(new Linea(b.readVarInt(), new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readDouble(), b.readByte()));
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(lineas.size());
        for (Linea x : lineas) {
            b.writeVarInt(x.id());
            b.writeDouble(x.a().x);
            b.writeDouble(x.a().y);
            b.writeDouble(x.a().z);
            b.writeDouble(x.b().x);
            b.writeDouble(x.b().y);
            b.writeDouble(x.b().z);
            b.writeDouble(x.sueloA());
            b.writeByte(x.nivel());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.armasfuego.rifle.TirolinasCliente.recibir(lineas));
    }
}
