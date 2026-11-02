package com.miscelanea.tortura;

import com.miscelanea.tortura.qte.QteFinPacket;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteResultadoPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.simple.SimpleChannel;

/** Paquetes de los minijuegos y de los cautivos. Se registran al final del canal principal (ModNetwork). */
public final class TorturaRed {
    private TorturaRed() {}

    /** Registra los paquetes a partir del id dado y devuelve el siguiente id libre. */
    public static int registrar(SimpleChannel ch, int id) {
        ch.messageBuilder(QteInicioPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QteInicioPacket::encode).decoder(QteInicioPacket::new).consumerMainThread(QteInicioPacket::handle).add();
        ch.messageBuilder(QteFinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(QteFinPacket::encode).decoder(QteFinPacket::new).consumerMainThread(QteFinPacket::handle).add();
        ch.messageBuilder(QteResultadoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(QteResultadoPacket::encode).decoder(QteResultadoPacket::new).consumerMainThread(QteResultadoPacket::handle).add();
        ch.messageBuilder(EstadoCautivoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EstadoCautivoPacket::encode).decoder(EstadoCautivoPacket::new).consumerMainThread(EstadoCautivoPacket::handle).add();
        // ejecución de la trampa de oso (va el último para no mover los ids de los demás)
        ch.messageBuilder(EjecucionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EjecucionPacket::encode).decoder(EjecucionPacket::new).consumerMainThread(EjecucionPacket::handle).add();
        return id;
    }
}
