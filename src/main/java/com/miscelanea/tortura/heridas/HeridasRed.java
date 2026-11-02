package com.miscelanea.tortura.heridas;

import com.miscelanea.Miscelanea;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Arranque de las heridas: canal de red propio (así no se toca la numeración de ModNetwork) y la regla de juego
 * "miscelanea_heridas". Todo se registra en el common setup, en el hilo principal.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HeridasRed {
    private static final String PROTOCOLO = "1";

    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Miscelanea.MOD_ID, "heridas"),
            () -> PROTOCOLO, PROTOCOLO::equals, PROTOCOLO::equals);

    /** /gamerule miscelanea_heridas false lo apaga todo (y borra las heridas que hubiera). */
    public static GameRules.Key<GameRules.BooleanValue> REGLA;

    private HeridasRed() {}

    @SubscribeEvent
    public static void setup(FMLCommonSetupEvent e) {
        e.enqueueWork(() -> {
            REGLA = GameRules.register("miscelanea_heridas", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true));
            int id = 0;
            CANAL.messageBuilder(HeridasPacket.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                    .encoder(HeridasPacket::encode)
                    .decoder(HeridasPacket::new)
                    .consumerMainThread(HeridasPacket::handle)
                    .add();
        });
    }
}
