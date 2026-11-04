package com.miscelanea.tortura.muertes;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor → cliente: empieza (o se anula, tipo 0) la cinemática de muerte de una entidad. Va a la víctima y a quien la
 * ve. Lleva los ticks que ya han pasado (como el de la trampa de oso), el foco y el frente de la escena (el aparato,
 * fijo durante toda la cinemática) y un punto auxiliar (el monitor del paro cardíaco; si no hay, el foco).
 */
public class MuertePacket {
    public final int entidad, tipo, transcurrido;
    public final double fx, fy, fz, ax, ay, az;
    public final float yaw;

    public MuertePacket(int entidad, int tipo, int transcurrido, Vec3 foco, float yaw, Vec3 aux) {
        this.entidad = entidad;
        this.tipo = tipo;
        this.transcurrido = transcurrido;
        this.fx = foco.x;
        this.fy = foco.y;
        this.fz = foco.z;
        this.yaw = yaw;
        this.ax = aux.x;
        this.ay = aux.y;
        this.az = aux.z;
    }

    public static MuertePacket anular(int entidad) {
        return new MuertePacket(entidad, 0, 0, Vec3.ZERO, 0F, Vec3.ZERO);
    }

    public MuertePacket(FriendlyByteBuf b) {
        this.entidad = b.readVarInt();
        this.tipo = b.readByte();
        if (tipo == 0) {
            transcurrido = 0;
            fx = fy = fz = ax = ay = az = 0;
            yaw = 0;
            return;
        }
        this.transcurrido = b.readVarInt();
        this.fx = b.readDouble();
        this.fy = b.readDouble();
        this.fz = b.readDouble();
        this.yaw = b.readFloat();
        this.ax = b.readDouble();
        this.ay = b.readDouble();
        this.az = b.readDouble();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeVarInt(entidad);
        b.writeByte(tipo);
        if (tipo == 0) return;
        b.writeVarInt(Math.max(0, transcurrido));
        b.writeDouble(fx);
        b.writeDouble(fy);
        b.writeDouble(fz);
        b.writeFloat(yaw);
        b.writeDouble(ax);
        b.writeDouble(ay);
        b.writeDouble(az);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.muertes.MuertesCliente.recibir(this));
    }
}
