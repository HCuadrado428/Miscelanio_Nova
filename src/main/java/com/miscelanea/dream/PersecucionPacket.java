package com.miscelanea.dream;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → un jugador: la persecución.
 * <ul>
 *   <li>CAZADOR (a Dream): a quién persigue (id de entidad), dónde está ahora, cuánto le queda a la marca y a cuántos
 *       bloques se oye el latido. Va cada medio segundo.</li>
 *   <li>PRESA (a la víctima marcada): que la están cazando (para el ojo del HUD).</li>
 *   <li>FIN: se acaba la marca (para el rol que diga).</li>
 * </ul>
 */
public class PersecucionPacket {
    public enum Tipo { CAZADOR, PRESA, FIN_CAZADOR, FIN_PRESA }

    public final Tipo tipo;
    public final int id, restante, latido;
    public final boolean rastro;
    public final double x, y, z;

    public PersecucionPacket(Tipo tipo, int id, double x, double y, double z, int restante, int latido, boolean rastro) {
        this.tipo = tipo;
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.restante = restante;
        this.latido = latido;
        this.rastro = rastro;
    }

    public static PersecucionPacket fin(Tipo tipo) {
        return new PersecucionPacket(tipo, -1, 0, 0, 0, 0, 0, false);
    }

    public PersecucionPacket(FriendlyByteBuf b) {
        this.tipo = b.readEnum(Tipo.class);
        this.id = b.readVarInt();
        this.x = b.readDouble();
        this.y = b.readDouble();
        this.z = b.readDouble();
        this.restante = b.readVarInt();
        this.latido = b.readVarInt();
        this.rastro = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeEnum(tipo);
        b.writeVarInt(id);
        b.writeDouble(x);
        b.writeDouble(y);
        b.writeDouble(z);
        b.writeVarInt(restante);
        b.writeVarInt(latido);
        b.writeBoolean(rastro);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.dream.PersecucionCliente.recibir(this));
    }
}
