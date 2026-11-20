package com.miscelanea.fiesta;

import com.miscelanea.Miscelanea;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** {@code /fiesta entrar} y {@code /fiesta salir}: para cualquier jugador. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class FiestaComando {
    private FiestaComando() {}

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("fiesta")
                .then(Commands.literal("entrar").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    if (!FiestaConfig.activada()) {
                        c.getSource().sendFailure(Component.translatable("fiesta.miscelanea.desactivada"));
                        return 0;
                    }
                    if (Fiesta.en(p.level())) {
                        c.getSource().sendFailure(Component.translatable("fiesta.miscelanea.ya_dentro"));
                        return 0;
                    }
                    if (!Fiesta.entrar(p)) {
                        c.getSource().sendFailure(Component.translatable("fiesta.miscelanea.sin_dimension"));
                        return 0;
                    }
                    return 1;
                }))
                .then(Commands.literal("salir").executes(c -> {
                    var p = c.getSource().getPlayerOrException();
                    if (!Fiesta.salir(p)) {
                        c.getSource().sendFailure(Component.translatable("fiesta.miscelanea.no_dentro"));
                        return 0;
                    }
                    return 1;
                })));
    }
}
