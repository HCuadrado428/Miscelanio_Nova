package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOLO = "3";

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
        CHANNEL.messageBuilder(EsconditePacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(EsconditePacket::encode)
                .decoder(EsconditePacket::new)
                .consumerMainThread(EsconditePacket::handle)
                .add();
        CHANNEL.messageBuilder(SalirEsconditePacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SalirEsconditePacket::encode)
                .decoder(SalirEsconditePacket::new)
                .consumerMainThread(SalirEsconditePacket::handle)
                .add();
        CHANNEL.messageBuilder(GafasPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(GafasPacket::encode)
                .decoder(GafasPacket::new)
                .consumerMainThread(GafasPacket::handle)
                .add();
    }
}
