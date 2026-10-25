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

    /** Puertas con trampa. */
    @SubscribeEvent
    public static void usarBloque(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Prueba.dentro(p)) return;
        if (Jugadores.caido(p)) {
            e.setCanceled(true);
            return;
        }
        var st = e.getLevel().getBlockState(e.getPos());
        if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) {
            Prueba.puertaUsada((net.minecraft.server.level.ServerLevel) e.getLevel(), e.getPos(), p);
        }
    }

    /** Reanimar a un compañero caído (mantener clic derecho, o la jeringuilla al instante). */
    @SubscribeEvent
    public static void usarEntidad(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof ServerPlayer caido)) return;
        if (!Prueba.dentro(p) || !Jugadores.caido(caido) || Jugadores.caido(p)) return;
        var mano = p.getItemInHand(e.getHand());
        boolean jeringa = mano.is(com.miscelanea.registry.ModItems.JERINGA_REANIMAR.get());
        if (jeringa) mano.shrink(1);
        Acciones.poner(p, Acciones.Accion.REANIMAR, 8);
        Jugadores.reanimar(p, caido, jeringa);
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
    }

    /** Solo 3 huecos: si no cabe, no lo coges (las llaves y el bidón siempre). */
    @SubscribeEvent
    public static void coger(net.minecraftforge.event.entity.player.EntityItemPickupEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Prueba.dentro(p) && !Jugadores.cabe(p, e.getItem().getItem())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void comandos(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("prueba")
                .then(Commands.literal("entrar").executes(c -> {
                    Prueba.entrar(c.getSource().getPlayerOrException());
                    return 1;
                }).then(Commands.argument("rig", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 3)).executes(c -> {
                    Prueba.entrar(c.getSource().getPlayerOrException(),
                            Rigs.Rig.de(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "rig")));
                    return 1;
                })))
                .then(Commands.literal("salir").executes(c -> {
                    Prueba.salir(c.getSource().getPlayerOrException());
                    return 1;
                })));
    }
}
