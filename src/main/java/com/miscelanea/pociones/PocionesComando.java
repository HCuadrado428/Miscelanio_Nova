package com.miscelanea.pociones;

import com.miscelanea.Miscelanea;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /miscelanea espejismo &lt;jugador&gt; &lt;objetivo&gt;: el jugador pasa a verse como el objetivo (si no llevaba el
 * efecto, se le pone 3 minutos). Brigadier junta la raíz "miscelanea" con la de los demás subcomandos del mod.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PocionesComando {
    private PocionesComando() {}

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("miscelanea")
                .then(Commands.literal("espejismo").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("jugador", EntityArgument.player())
                                .then(Commands.argument("objetivo", EntityArgument.player())
                                        .executes(PocionesComando::espejismo)))));
    }

    private static int espejismo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        ServerPlayer o = EntityArgument.getPlayer(c, "objetivo");
        if (p == o) {
            c.getSource().sendFailure(Component.translatable("miscelanea.pociones.cmd.mismo"));
            return 0;
        }
        Espejismos.fijar(p, o);
        if (!p.hasEffect(PocionesRegistro.ESPEJISMO.get())) p.addEffect(new MobEffectInstance(PocionesRegistro.ESPEJISMO.get(), 3600));
        Espejismos.enviarATodos(p, null);
        c.getSource().sendSuccess(() -> Component.translatable("miscelanea.pociones.cmd.espejismo", p.getDisplayName(), o.getDisplayName()), true);
        return 1;
    }
}
