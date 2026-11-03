package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de red propio de los aparatos (como el de las heridas): así no se mueve la numeración de ModNetwork ni la de
 * TorturaRed, que tocan otras ramas.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AparatosRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "aparatos"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private AparatosRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(AparatoJuegoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(AparatoJuegoPacket::encode).decoder(AparatoJuegoPacket::new).consumerMainThread(AparatoJuegoPacket::handle).add();
            CANAL.messageBuilder(AparatoEfectoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(AparatoEfectoPacket::encode).decoder(AparatoEfectoPacket::new).consumerMainThread(AparatoEfectoPacket::handle).add();
            CANAL.messageBuilder(AparatoAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(AparatoAccionPacket::encode).decoder(AparatoAccionPacket::new).consumerMainThread(AparatoAccionPacket::handle).add();
            CANAL.messageBuilder(MarcaDibujoPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(MarcaDibujoPacket::encode).decoder(MarcaDibujoPacket::new).consumerMainThread(MarcaDibujoPacket::handle).add();
            CANAL.messageBuilder(InterrogaAccionPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(InterrogaAccionPacket::encode).decoder(InterrogaAccionPacket::new).consumerMainThread(InterrogaAccionPacket::handle).add();
            CANAL.messageBuilder(InterrogaEfectoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(InterrogaEfectoPacket::encode).decoder(InterrogaEfectoPacket::new).consumerMainThread(InterrogaEfectoPacket::handle).add();
        });
    }

    public static void a(ServerPlayer p, Object msg) {
        if (p.connection != null) CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** A quien ve a la entidad y a ella misma. */
    public static void alrededor(LivingEntity e, Object msg) {
        CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }
}
