package com.miscelanea.armasfuego.rifle;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: lo que hace el jugador con el rifle (disparar, accionar el cerrojo) y con las tirolinas
 * (colgarse, cortarlas con tijeras).
 */
public class RifleAccionPacket {
    public static final byte DISPARAR = 0, CERROJO = 1, COLGAR = 2, CORTAR = 3;

    private final byte accion;
    private final int id;
    private final float yRot, xRot;
    private final boolean firme;

    public RifleAccionPacket(byte accion, int id, float yRot, float xRot, boolean firme) {
        this.accion = accion;
        this.id = id;
        this.yRot = yRot;
        this.xRot = xRot;
        this.firme = firme;
    }

    public static RifleAccionPacket disparo(float yRot, float xRot, boolean firme) {
        return new RifleAccionPacket(DISPARAR, 0, yRot, xRot, firme);
    }

    public static RifleAccionPacket de(byte accion, int id) {
        return new RifleAccionPacket(accion, id, 0, 0, false);
    }

    public RifleAccionPacket(FriendlyByteBuf b) {
        accion = b.readByte();
        id = b.readVarInt();
        yRot = b.readFloat();
        xRot = b.readFloat();
        firme = b.readBoolean();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(accion);
        b.writeVarInt(id);
        b.writeFloat(yRot);
        b.writeFloat(xRot);
        b.writeBoolean(firme);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !p.isAlive()) return;
        var s = p.getMainHandItem();
        switch (accion) {
            case DISPARAR -> {
                if (s.getItem() instanceof RifleItem && Float.isFinite(yRot) && Float.isFinite(xRot))
                    Rifle.disparar(p, s, yRot, Math.max(-90, Math.min(90, xRot)), firme);
            }
            case CERROJO -> {
                if (s.getItem() instanceof RifleItem) Rifle.accionarCerrojo(p, s);
            }
            case COLGAR -> Tirolinas.colgar(p, id);
            case CORTAR -> Tirolinas.cortar(p, id);
            default -> {}
        }
    }
}
