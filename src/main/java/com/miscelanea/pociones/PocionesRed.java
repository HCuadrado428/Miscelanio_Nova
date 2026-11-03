package com.miscelanea.pociones;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de red propio de las pociones del cuerpo ("miscelanea:pociones", como el de los efectos de la tortura): así no
 * se toca la numeración de ModNetwork ni la de los demás canales.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class PocionesRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "pociones"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private PocionesRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(EstadoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(EstadoPacket::encode).decoder(EstadoPacket::new).consumerMainThread(EstadoPacket::handle).add();
            CANAL.messageBuilder(ReventarPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(ReventarPacket::encode).decoder(ReventarPacket::new).consumerMainThread(ReventarPacket::handle).add();
            CANAL.messageBuilder(PalmadaPacket.class, id++, NetworkDirection.PLAY_TO_SERVER)
                    .encoder(PalmadaPacket::encode).decoder(PalmadaPacket::new).consumerMainThread(PalmadaPacket::handle).add();
        });
    }

    public static void a(ServerPlayer p, Object msg) {
        if (p.connection != null) CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }

    /** A quien ve a la entidad y a ella misma. */
    public static void alrededor(Entity e, Object msg) {
        CANAL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), msg);
    }
}
