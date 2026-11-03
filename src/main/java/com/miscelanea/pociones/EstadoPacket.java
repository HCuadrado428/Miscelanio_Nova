package com.miscelanea.pociones;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servidor → cliente: qué pociones del cuerpo lleva una entidad que ves (vanilla solo sincroniza los efectos de uno
 * mismo). Se reenvía cada segundo mientras dure algo (el cliente lo olvida si deja de llegar) y al quitarse, con todo
 * a cero. {@link #pasos}: +n gigante, -n enano, 0 nada. {@link #espejo}: a quién imita (espejismo), o null.
 */
public class EstadoPacket {
    public static final int SOMBRA = 1, NIEBLA = 2, ESPEJISMO = 4;
    public final int id, banderas, pasos;
    public final UUID espejo;
    public final String nombre;

    public EstadoPacket(int id, int banderas, int pasos, UUID espejo, String nombre) {
        this.id = id;
        this.banderas = banderas;
        this.pasos = pasos;
        this.espejo = espejo;
        this.nombre = nombre == null ? "" : nombre;
    }

    public EstadoPacket(FriendlyByteBuf b) {
        id = b.readVarInt();
        banderas = b.readByte();
        pasos = b.readByte();
        if (b.readBoolean()) {
            espejo = b.readUUID();
            nombre = b.readUtf(64);
        } else {
            espejo = null;
            nombre = "";
        }
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(id);
        b.writeByte(banderas);
        b.writeByte(pasos);
        b.writeBoolean(espejo != null);
        if (espejo != null) {
            b.writeUUID(espejo);
            b.writeUtf(nombre, 64);
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.pociones.Estados.recibir(this));
    }
}
