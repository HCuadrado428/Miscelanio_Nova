package com.miscelanea.cartas;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor: JUGAR (carta de la mano, por su valor 0..39), CPU (contra la máquina), NUEVA, LEVANTARSE.
 * Servidor → cliente: ESTADO de la partida vista por ese jugador (con su mano), y si hay que abrir la pantalla.
 */
public class CartasPacket {
    public static final int ESTADO = 0, JUGAR = 1, CPU = 2, NUEVA = 3, LEVANTARSE = 4, ROBAR = 5, DESCARTAR = 6, CERRAR = 7, JUEGO = 8;
    public final int tipo, arg;
    public final BlockPos pos;
    // estado
    public boolean abrir;
    public int miAsiento = -1, triunfo = -1, mazo, turno, estado, ultimaGana = -1;
    public int[] mano = new int[0], mesa = {-1, -1}, puntos = new int[2], bazas = new int[2], enMano = new int[2];
    public String[] nombres = {"", ""};
    public int juego, fase, descarte = -1;
    public int[] acumulado = new int[2], ultSueltos = {-1, -1}, manoRival = new int[0];

    public CartasPacket(int tipo, BlockPos pos, int arg) {
        this.tipo = tipo;
        this.pos = pos;
        this.arg = arg;
    }

    static CartasPacket estado(MesaCartasBlockEntity be, int asiento, boolean abrir) {
        CartasPacket p = new CartasPacket(ESTADO, be.getBlockPos(), 0);
        p.abrir = abrir;
        p.miAsiento = asiento;
        p.triunfo = be.triunfo;
        p.mazo = be.mazo.size();
        p.turno = be.turno;
        p.estado = be.estado;
        p.ultimaGana = be.ultimaGana;
        p.mano = asiento >= 0 ? be.mano[asiento].stream().mapToInt(Integer::intValue).toArray() : new int[0];
        p.mesa = be.mesa.clone();
        p.puntos = be.puntos.clone();
        p.bazas = be.bazas.clone();
        p.enMano = new int[]{be.mano[0].size(), be.mano[1].size()};
        p.nombres = be.nombre.clone();
        p.juego = be.juego;
        p.fase = be.fase;
        p.descarte = be.descarte.isEmpty() ? -1 : be.descarte.get(be.descarte.size() - 1);
        p.acumulado = be.acumulado.clone();
        p.ultSueltos = be.ultSueltos.clone();
        if (be.juego == MesaCartasBlockEntity.CHINCHON && be.fase == MesaCartasBlockEntity.FIN_RONDA && asiento >= 0)
            p.manoRival = be.mano[1 - asiento].stream().mapToInt(Integer::intValue).toArray();
        return p;
    }

    public CartasPacket(FriendlyByteBuf b) {
        this(b.readByte(), b.readBlockPos(), b.readVarInt());
        if (tipo != ESTADO) return;
        abrir = b.readBoolean();
        miAsiento = b.readByte();
        triunfo = b.readByte();
        mazo = b.readByte();
        turno = b.readByte();
        estado = b.readByte();
        ultimaGana = b.readByte();
        mano = b.readVarIntArray();
        mesa = b.readVarIntArray();
        puntos = b.readVarIntArray();
        bazas = b.readVarIntArray();
        enMano = b.readVarIntArray();
        nombres = new String[]{b.readUtf(64), b.readUtf(64)};
        juego = b.readByte();
        fase = b.readByte();
        descarte = b.readByte();
        acumulado = b.readVarIntArray();
        ultSueltos = b.readVarIntArray();
        manoRival = b.readVarIntArray();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(tipo);
        b.writeBlockPos(pos);
        b.writeVarInt(arg);
        if (tipo != ESTADO) return;
        b.writeBoolean(abrir);
        b.writeByte(miAsiento);
        b.writeByte(triunfo);
        b.writeByte(mazo);
        b.writeByte(turno);
        b.writeByte(estado);
        b.writeByte(ultimaGana);
        b.writeVarIntArray(mano);
        b.writeVarIntArray(mesa);
        b.writeVarIntArray(puntos);
        b.writeVarIntArray(bazas);
        b.writeVarIntArray(enMano);
        b.writeUtf(nombres[0], 64);
        b.writeUtf(nombres[1], 64);
        b.writeByte(juego);
        b.writeByte(fase);
        b.writeByte(descarte);
        b.writeVarIntArray(acumulado);
        b.writeVarIntArray(ultSueltos);
        b.writeVarIntArray(manoRival);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.cartas.CartasCliente.recibir(this));
            return;
        }
        if (p.distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) > 64) return;
        if (!(p.level().getBlockEntity(pos) instanceof MesaCartasBlockEntity be)) return;
        be.despertar(p);
        // JUGAR, DESCARTAR y CERRAR llevan la carta (0..39), no su posición en la mano
        switch (tipo) {
            case JUGAR -> be.jugar(p, be.indiceCarta(be.asiento(p), arg));
            case CPU -> be.contraCpu(p);
            case NUEVA -> be.nueva(p);
            case LEVANTARSE -> be.levantarse(p);
            case ROBAR -> be.robar(be.asiento(p), arg == 1);
            case DESCARTAR -> be.descartar(be.asiento(p), be.indiceCarta(be.asiento(p), arg), false);
            case CERRAR -> be.descartar(be.asiento(p), be.indiceCarta(be.asiento(p), arg), true);
            case JUEGO -> be.cambiarJuego(p);
            default -> {}
        }
    }
}
