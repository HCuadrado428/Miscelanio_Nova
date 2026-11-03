package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente → servidor (silla de interrogatorio y gancho). HERRAMIENTA: el interrogador ha elegido una herramienta en la
 * rueda del maletín (valor = ordinal de {@link Herramienta}). AGUANTAR: la víctima de la silla pulsa SALTAR para
 * calmarse. ESCAPAR: la víctima colgada pulsa SALTAR para intentar soltarse. MANIVELA: rueda del ratón mirando el gancho
 * (valor = +1 sube / -1 baja). Todo se comprueba en el servidor (distancia, que sea su aparato).
 */
public class InterrogaAccionPacket {
    public static final int HERRAMIENTA = 1, AGUANTAR = 2, ESCAPAR = 3, MANIVELA = 4;
    public final int accion;
    public final BlockPos pos;
    public final int valor;

    public InterrogaAccionPacket(int accion, BlockPos pos, int valor) {
        this.accion = accion;
        this.pos = pos;
        this.valor = valor;
    }

    public InterrogaAccionPacket(FriendlyByteBuf b) {
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
        if (p == null || !p.isAlive() || !p.level().isLoaded(pos)) return;
        if (!(p.level().getBlockEntity(pos) instanceof AparatoBlockEntity be)) return;
        switch (accion) {
            case HERRAMIENTA -> {
                if (be instanceof SillaInterrogatorioBlockEntity s && !be.esVictima(p) && !Aparatos.en(p) && p.distanceToSqr(be.centro()) < 36)
                    s.usarHerramienta(p, Herramienta.de(valor));
            }
            case AGUANTAR -> {
                if (be instanceof SillaInterrogatorioBlockEntity s && be.esVictima(p)) s.aguantar(p);
            }
            case ESCAPAR -> {
                if (be instanceof GanchoBlockEntity g && be.esVictima(p)) g.escapar(p);
            }
            case MANIVELA -> {
                if (be instanceof GanchoBlockEntity g && !be.esVictima(p) && !Aparatos.en(p) && p.distanceToSqr(be.centro()) < 100)
                    g.manivela(p, valor > 0 ? 1 : -1);
            }
            default -> {
            }
        }
    }
}
