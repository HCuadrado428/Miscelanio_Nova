package com.miscelanea.network;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String PROTOCOLO = "8";

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
        CHANNEL.messageBuilder(com.miscelanea.programa.PruebaEstadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.programa.PruebaEstadoPacket::encode)
                .decoder(com.miscelanea.programa.PruebaEstadoPacket::new)
                .consumerMainThread(com.miscelanea.programa.PruebaEstadoPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.programa.PruebaFinPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.programa.PruebaFinPacket::encode)
                .decoder(com.miscelanea.programa.PruebaFinPacket::new)
                .consumerMainThread(com.miscelanea.programa.PruebaFinPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.programa.AccionPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(com.miscelanea.programa.AccionPacket::encode)
                .decoder(com.miscelanea.programa.AccionPacket::new)
                .consumerMainThread(com.miscelanea.programa.AccionPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.programa.UsarRigPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.programa.UsarRigPacket::encode)
                .decoder(com.miscelanea.programa.UsarRigPacket::new)
                .consumerMainThread(com.miscelanea.programa.UsarRigPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.programa.EntrarPruebaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.programa.EntrarPruebaPacket::encode)
                .decoder(com.miscelanea.programa.EntrarPruebaPacket::new)
                .consumerMainThread(com.miscelanea.programa.EntrarPruebaPacket::handle)
                .add();
        CHANNEL.messageBuilder(com.miscelanea.programa.GanzuaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(com.miscelanea.programa.GanzuaPacket::encode)
                .decoder(com.miscelanea.programa.GanzuaPacket::new)
                .consumerMainThread(com.miscelanea.programa.GanzuaPacket::handle)
                .add();
    }
}
