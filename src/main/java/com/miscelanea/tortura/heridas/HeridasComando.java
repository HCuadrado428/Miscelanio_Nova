package com.miscelanea.tortura.heridas;

import com.miscelanea.Miscelanea;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Arrays;

/**
 * Comandos de prueba:
 * /miscelanea heridas poner &lt;jugador&gt; [parte] [tipo] [gravedad]
 * /miscelanea heridas curar &lt;jugador&gt;
 * (Brigadier junta los nodos "miscelanea" si otro sistema registra más subcomandos con la misma raíz.)
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class HeridasComando {
    private static final SuggestionProvider<CommandSourceStack> PARTES =
            (c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Parte.V).map(Parte::id), b);
    private static final SuggestionProvider<CommandSourceStack> TIPOS =
            (c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Tipo.V).map(Tipo::id), b);
    private static final DynamicCommandExceptionType PARTE_MALA =
            new DynamicCommandExceptionType(o -> Component.translatable("miscelanea.heridas.cmd.parte_mala", o));
    private static final DynamicCommandExceptionType TIPO_MALO =
            new DynamicCommandExceptionType(o -> Component.translatable("miscelanea.heridas.cmd.tipo_malo", o));

    private HeridasComando() {}

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("miscelanea")
                .then(Commands.literal("heridas").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("poner")
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .executes(c -> poner(c, false, false, false))
                                        .then(Commands.argument("parte", StringArgumentType.word()).suggests(PARTES)
                                                .executes(c -> poner(c, true, false, false))
                                                .then(Commands.argument("tipo", StringArgumentType.word()).suggests(TIPOS)
                                                        .executes(c -> poner(c, true, true, false))
                                                        .then(Commands.argument("gravedad", IntegerArgumentType.integer(1, 3))
                                                                .executes(c -> poner(c, true, true, true)))))))
                        .then(Commands.literal("curar")
                                .then(Commands.argument("jugador", EntityArgument.player())
                                        .executes(HeridasComando::curar)))));
    }

    private static int poner(CommandContext<CommandSourceStack> c, boolean conParte, boolean conTipo, boolean conGravedad) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        if (!Heridas.activo(p.level())) {
            c.getSource().sendFailure(Component.translatable("miscelanea.heridas.desactivadas"));
            return 0;
        }
        RandomSource r = p.getRandom();
        Parte parte = Parte.V[r.nextInt(Parte.V.length)];
        if (conParte) {
            String s = StringArgumentType.getString(c, "parte");
            parte = Parte.porId(s);
            if (parte == null) throw PARTE_MALA.create(s);
        }
        Tipo tipo = Tipo.V[r.nextInt(Tipo.V.length)];
        if (conTipo) {
            String s = StringArgumentType.getString(c, "tipo");
            tipo = Tipo.porId(s);
            if (tipo == null) throw TIPO_MALO.create(s);
        }
        int g = conGravedad ? IntegerArgumentType.getInteger(c, "gravedad") : 1 + r.nextInt(3);
        HeridasDatos d = Heridas.leer(p);
        Heridas.agregar(d, Heridas.alAzar(p, parte, tipo, g, r.nextInt(4) == 0));
        Heridas.actualizar(p, d);
        final Parte fp = parte;
        final Tipo ft = tipo;
        c.getSource().sendSuccess(() -> Component.translatable("miscelanea.heridas.cmd.puesta",
                Component.translatable(ft.clave()), g, Component.translatable(fp.clave()), p.getDisplayName()), true);
        return 1;
    }

    private static int curar(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "jugador");
        Heridas.curarTodo(p);
        c.getSource().sendSuccess(() -> Component.translatable("miscelanea.heridas.cmd.curado", p.getDisplayName()), true);
        return 1;
    }
}
