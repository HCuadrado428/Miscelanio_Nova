package com.miscelanea.tortura.efectos;

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
 * Canal de red propio de los efectos ("miscelanea:efectos", como el de las heridas y el de los aparatos): así no se
 * mueve la numeración de ModNetwork ni la de TorturaRed.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EfectosRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "efectos"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private EfectosRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(CollarPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(CollarPacket::encode).decoder(CollarPacket::new).consumerMainThread(CollarPacket::handle).add();
            CANAL.messageBuilder(SustoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(SustoPacket::encode).decoder(SustoPacket::new).consumerMainThread(SustoPacket::handle).add();
            CANAL.messageBuilder(FantasmaPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(FantasmaPacket::encode).decoder(FantasmaPacket::new).consumerMainThread(FantasmaPacket::handle).add();
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
