package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOLO = "10";

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
    }
}
