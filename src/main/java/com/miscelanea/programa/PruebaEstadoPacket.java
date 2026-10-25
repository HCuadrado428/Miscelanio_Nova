package com.miscelanea.programa;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor → cliente: estado de la prueba y del jugador para la interfaz. */
public class PruebaEstadoPacket {
    public final boolean activo, intro, perseguido;
    public final int etapa, a, b, manivela, muertes, vidas, cordura, psicosis, caido, rig, rigCd, rigMax, rigOff;
    public final long inicio;

    public PruebaEstadoPacket(boolean activo, boolean intro, int etapa, int a, int b, int manivela, long inicio, int muertes,
                              int vidas, int cordura, int psicosis, int caido, int rig, int rigCd, int rigMax, int rigOff, boolean perseguido) {
        this.activo = activo;
        this.intro = intro;
        this.etapa = etapa;
        this.a = a;
        this.b = b;
        this.manivela = manivela;
        this.inicio = inicio;
        this.muertes = muertes;
        this.vidas = vidas;
        this.cordura = cordura;
        this.psicosis = psicosis;
        this.caido = caido;
        this.rig = rig;
        this.rigCd = rigCd;
        this.rigMax = rigMax;
        this.rigOff = rigOff;
        this.perseguido = perseguido;
    }

    public static PruebaEstadoPacket inactivo() {
        return new PruebaEstadoPacket(false, false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, false);
    }

    public PruebaEstadoPacket(FriendlyByteBuf f) {
        this(f.readBoolean(), f.readBoolean(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readLong(), f.readVarInt(),
                f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readVarInt(), f.readBoolean());
    }

    public void encode(FriendlyByteBuf f) {
        f.writeBoolean(activo);
        f.writeBoolean(intro);
        f.writeVarInt(etapa);
        f.writeVarInt(a);
        f.writeVarInt(b);
        f.writeVarInt(manivela);
        f.writeLong(inicio);
        f.writeVarInt(muertes);
        f.writeVarInt(vidas);
        f.writeVarInt(cordura);
        f.writeVarInt(psicosis);
        f.writeVarInt(caido);
        f.writeVarInt(rig);
        f.writeVarInt(rigCd);
        f.writeVarInt(rigMax);
        f.writeVarInt(rigOff);
        f.writeBoolean(perseguido);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.programa.ProgramaHud.estado(this));
    }
}
