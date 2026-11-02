package com.miscelanea.tortura.efectos;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: estado del collar explosivo de una entidad. A quien la ve le basta con saber si lo lleva, si está
 * en alarma (ticks que quedan y total), si está a punto de estallar y los errores al cortar cables (lo que cambia el
 * parpadeo y los pitidos). Al que lo lleva se le manda además, cada medio segundo, la distancia a la señal y el radio.
 * <ul>
 *   <li>senal: 0 = hay emisor (captor o detonador), 1 = sin emisor (dormido), 2 = baliza fija (puesto a sí mismo),
 *       3 = el emisor está en otra dimensión (fuera de alcance).</li>
 *   <li>dist: metros a la señal (-1 si no se sabe o no es para el que lo lleva).</li>
 * </ul>
 */
public class CollarPacket {
    public final int entidad;
    public final boolean tiene;
    public final int alarma, alarmaMax, detona, errores, senal, radio;
    public final float dist;

    public CollarPacket(int entidad, boolean tiene, int alarma, int alarmaMax, int detona, int errores, int senal, int radio, float dist) {
        this.entidad = entidad;
        this.tiene = tiene;
        this.alarma = alarma;
        this.alarmaMax = alarmaMax;
        this.detona = detona;
        this.errores = errores;
        this.senal = senal;
        this.radio = radio;
        this.dist = dist;
    }

    public static CollarPacket quitado(int entidad) {
        return new CollarPacket(entidad, false, -1, 0, -1, 0, 0, 0, -1F);
    }

    public CollarPacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.tiene = b.readBoolean();
        this.alarma = b.readVarInt() - 1;
        this.alarmaMax = b.readVarInt();
        this.detona = b.readVarInt() - 1;
        this.errores = b.readByte();
        this.senal = b.readByte();
        this.radio = b.readVarInt();
        this.dist = b.readFloat();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeBoolean(tiene);
        b.writeVarInt(Math.max(-1, alarma) + 1);
        b.writeVarInt(Math.max(0, alarmaMax));
        b.writeVarInt(Math.max(-1, detona) + 1);
        b.writeByte(errores);
        b.writeByte(senal);
        b.writeVarInt(Math.max(0, radio));
        b.writeFloat(dist);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.efectos.CollarCliente.recibir(this));
    }
}
