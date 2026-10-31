package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOLO = "16";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "main"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(FlashbangPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(FlashbangPacket::encode)
                .decoder(FlashbangPacket::new)
                .consumerMainThread(FlashbangPacket::handle)
                .add();
        CHANNEL.messageBuilder(SoltarCapturaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SoltarCapturaPacket::encode)
                .decoder(SoltarCapturaPacket::new)
                .consumerMainThread(SoltarCapturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(AgarrePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(AgarrePacket::encode)
                .decoder(AgarrePacket::new)
                .consumerMainThread(AgarrePacket::handle)
                .add();
        CHANNEL.messageBuilder(ForcejeoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ForcejeoPacket::encode)
                .decoder(ForcejeoPacket::new)
                .consumerMainThread(ForcejeoPacket::handle)
                .add();
        CHANNEL.messageBuilder(MedigunPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(MedigunPacket::encode)
                .decoder(MedigunPacket::new)
                .consumerMainThread(MedigunPacket::handle)
                .add();
        CHANNEL.messageBuilder(MochilaAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MochilaAccionPacket::encode)
                .decoder(MochilaAccionPacket::new)
                .consumerMainThread(MochilaAccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(CapturaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(CapturaPacket::encode)
                .decoder(CapturaPacket::new)
                .consumerMainThread(CapturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(LazoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(LazoPacket::encode)
                .decoder(LazoPacket::new)
                .consumerMainThread(LazoPacket::handle)
                .add();
        CHANNEL.messageBuilder(NucleoActivarPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(NucleoActivarPacket::encode)
                .decoder(NucleoActivarPacket::new)
                .consumerMainThread(NucleoActivarPacket::handle)
                .add();
        CHANNEL.messageBuilder(NucleoEstadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(NucleoEstadoPacket::encode)
                .decoder(NucleoEstadoPacket::new)
                .consumerMainThread(NucleoEstadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.tinta.TintaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.tinta.TintaPacket::encode)
                .decoder(com.miscelanea.tinta.TintaPacket::new)
                .consumerMainThread(com.miscelanea.tinta.TintaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.tinta.DepositoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.tinta.DepositoPacket::encode)
                .decoder(com.miscelanea.tinta.DepositoPacket::new)
                .consumerMainThread(com.miscelanea.tinta.DepositoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.gravedad.GravedadPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.gravedad.GravedadPacket::encode)
                .decoder(com.miscelanea.gravedad.GravedadPacket::new)
                .consumerMainThread(com.miscelanea.gravedad.GravedadPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.armas.GanchoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.armas.GanchoPacket::encode)
                .decoder(com.miscelanea.armas.GanchoPacket::new)
                .consumerMainThread(com.miscelanea.armas.GanchoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.AccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.llave.AccionPacket::encode)
                .decoder(com.miscelanea.llave.AccionPacket::new)
                .consumerMainThread(com.miscelanea.llave.AccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.AnimPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.llave.AnimPacket::encode)
                .decoder(com.miscelanea.llave.AnimPacket::new)
                .consumerMainThread(com.miscelanea.llave.AnimPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.llave.PmPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.llave.PmPacket::encode)
                .decoder(com.miscelanea.llave.PmPacket::new)
                .consumerMainThread(com.miscelanea.llave.PmPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.HachaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.hacha.HachaPacket::encode)
                .decoder(com.miscelanea.hacha.HachaPacket::new)
                .consumerMainThread(com.miscelanea.hacha.HachaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.PosturaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.hacha.PosturaPacket::encode)
                .decoder(com.miscelanea.hacha.PosturaPacket::new)
                .consumerMainThread(com.miscelanea.hacha.PosturaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.hacha.PosturaOtroPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.hacha.PosturaOtroPacket::encode)
                .decoder(com.miscelanea.hacha.PosturaOtroPacket::new)
                .consumerMainThread(com.miscelanea.hacha.PosturaOtroPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.pipboy.PipBoyEstadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.pipboy.PipBoyEstadoPacket::encode)
                .decoder(com.miscelanea.pipboy.PipBoyEstadoPacket::new)
                .consumerMainThread(com.miscelanea.pipboy.PipBoyEstadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.pipboy.PipBoyDatosPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.pipboy.PipBoyDatosPacket::encode)
                .decoder(com.miscelanea.pipboy.PipBoyDatosPacket::new)
                .consumerMainThread(com.miscelanea.pipboy.PipBoyDatosPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.pipboy.PipBoyAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.pipboy.PipBoyAccionPacket::encode)
                .decoder(com.miscelanea.pipboy.PipBoyAccionPacket::new)
                .consumerMainThread(com.miscelanea.pipboy.PipBoyAccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.ColeccionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.bichos.ColeccionPacket::encode)
                .decoder(com.miscelanea.bichos.ColeccionPacket::new)
                .consumerMainThread(com.miscelanea.bichos.ColeccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.PescaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.bichos.PescaPacket::encode)
                .decoder(com.miscelanea.bichos.PescaPacket::new)
                .consumerMainThread(com.miscelanea.bichos.PescaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.bichos.PescaResultadoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.bichos.PescaResultadoPacket::encode)
                .decoder(com.miscelanea.bichos.PescaResultadoPacket::new)
                .consumerMainThread(com.miscelanea.bichos.PescaResultadoPacket::handle)
                .add();
    }
}
