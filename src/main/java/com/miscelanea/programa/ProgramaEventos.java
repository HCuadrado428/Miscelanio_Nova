package com.miscelanea.programa;

import com.miscelanea.Miscelanea;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class ProgramaEventos {
    private ProgramaEventos() {}

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) Prueba.tick(e.getServer());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void muerte(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Prueba.muerte(p)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void entra(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Prueba.alConectar(p);
    }

    @SubscribeEvent
    public static void sale(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Prueba.alDesconectar(p);
    }

    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("prueba")
                .then(Commands.literal("entrar").executes(c -> {
                    Prueba.entrar(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("salir").executes(c -> {
                    Prueba.salir(c.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
