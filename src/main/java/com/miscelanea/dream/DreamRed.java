package com.miscelanea.dream;

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

/** Canal de red propio de lo de Dream ("miscelanea:dream"): no toca la numeración de ModNetwork ni la de otros canales. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DreamRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "dream"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    private DreamRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            int id = 0;
            CANAL.messageBuilder(MordiscoPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(MordiscoPacket::encode).decoder(MordiscoPacket::new).consumerMainThread(MordiscoPacket::handle).add();
            CANAL.messageBuilder(AnilloPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(AnilloPacket::encode).decoder(AnilloPacket::new).consumerMainThread(AnilloPacket::handle).add();
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
