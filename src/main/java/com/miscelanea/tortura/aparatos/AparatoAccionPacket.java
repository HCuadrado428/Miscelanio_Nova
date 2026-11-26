package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor. VOLTAJE: rueda del ratón mirando el cuadro de la silla (valor = +1 / -1). INFORME_DONCELLA: la
 * víctima de la doncella cuenta cuántos de los últimos 10 ticks ha estado fuera de la zona segura (0..10).
 * DESMAYO_PULSO: la víctima de la silla ha perdido el pulso. VELOCIDAD_PENDULO: rueda del ratón mirando el péndulo
 * (+1 / -1). INFORME_INMERSION: la víctima de la silla de inmersión cuenta cuántos de los últimos 10 ticks ha estado
 * fuera de la zona de calma (0..10). Todo se comprueba en el servidor (distancia, que sea su aparato) y los valores
 * se recortan.
 */
public class AparatoAccionPacket {
    public static final int VOLTAJE = 1, INFORME_DONCELLA = 2, DESMAYO_PULSO = 3, VELOCIDAD_PENDULO = 4, INFORME_INMERSION = 5;
    /** El panel de la consola de una estación del laboratorio: poner el nivel o el dial (valor), arrancar, parar, sacar al sujeto. */
    public static final int LAB_AJUSTE = 6, LAB_ARRANCAR = 7, LAB_PARAR = 8, LAB_SACAR = 9;
    public final int accion;
    public final BlockPos pos;
    public final int valor;

    public AparatoAccionPacket(int accion, BlockPos pos, int valor) {
        this.accion = accion;
        this.pos = pos;
        this.valor = valor;
    }

    public AparatoAccionPacket(FriendlyByteBuf b) {
        this.accion = b.readByte();
        this.pos = b.readBlockPos();
        this.valor = b.readVarInt();
    }

    public void encode(FriendlyByteBuf b) {
        b.writeByte(accion);
        b.writeBlockPos(pos);
        b.writeVarInt(valor);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p == null || !p.level().isLoaded(pos)) return;
        if (!(p.level().getBlockEntity(pos) instanceof AparatoBlockEntity be)) return;
        switch (accion) {
            case VOLTAJE -> {
                if (be instanceof SillaElectricaBlockEntity s && !be.esVictima(p) && p.distanceToSqr(be.centro()) < 64)
                    s.cambiarVoltaje(p, valor > 0 ? 1 : -1);
            }
            case INFORME_DONCELLA -> {
                if (be instanceof DoncellaBlockEntity d && be.esVictima(p)) d.informe(Math.max(0, Math.min(10, valor)));
            }
            case DESMAYO_PULSO -> {
                if (be instanceof SillaElectricaBlockEntity s && be.esVictima(p)) s.desmayoPulso(p);
            }
            case VELOCIDAD_PENDULO -> {
                if (be instanceof PenduloBlockEntity pe && !be.esVictima(p) && p.distanceToSqr(be.centro()) < 64)
                    pe.cambiarVelocidad(p, valor > 0 ? 1 : -1);
            }
            case INFORME_INMERSION -> {
                if (be instanceof InmersionBlockEntity in && be.esVictima(p)) in.informe(Math.max(0, Math.min(10, valor)));
            }
            case LAB_AJUSTE, LAB_ARRANCAR, LAB_PARAR, LAB_SACAR -> consola(p, be);
            default -> {
            }
        }
    }

    /** Una orden del panel de la consola del laboratorio, con todas las comprobaciones (como un clic en el bloque). */
    private void consola(ServerPlayer p, AparatoBlockEntity be) {
        if (!(be instanceof com.miscelanea.laboratorio.ConsolaLab c) || be.esVictima(p) || p.isSpectator()
                || p.distanceToSqr(be.centro()) > 64 || Aparatos.en(p)) return;
        if (!com.miscelanea.laboratorio.LabConfig.estaciones()) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("laboratorio.miscelanea.estacion.apagada")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        if (be.ocupado() && !be.puedeManejar(p)) {
            p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.aparato.no_tuyo")
                    .withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }
        switch (accion) {
            case LAB_AJUSTE -> c.ponerAjuste(p, Math.max(-3, Math.min(3, valor)));
            case LAB_ARRANCAR -> c.arrancarDesdeConsola(p);
            case LAB_PARAR -> c.pararDesdeConsola(p);
            case LAB_SACAR -> {
                if (!be.ocupado()) return;
                net.minecraft.world.entity.LivingEntity v = be.victimaEntidad();
                be.sacar(true);
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.aparato.sacado",
                        v == null ? net.minecraft.network.chat.Component.literal("?") : v.getDisplayName()).withStyle(net.minecraft.ChatFormatting.GRAY), true);
            }
            default -> {
            }
        }
    }
}
