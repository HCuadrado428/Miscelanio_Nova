package com.miscelanea.ranking;

import com.miscelanea.Miscelanea;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * {@code /miscelanea ranking <categoría>}: los 10 primeros de esa categoría en el chat (cualquiera puede usarlo).
 * {@code /miscelanea ranking} a secas: las categorías que tienen marcas (se puede hacer clic en ellas).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class RankingComando {
    private RankingComando() {}

    @SubscribeEvent
    public static void registrar(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("miscelanea")
                .then(Commands.literal("ranking")
                        .executes(RankingComando::lista)
                        .then(Commands.argument("categoria", StringArgumentType.greedyString())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Ranking.de(c.getSource().getServer()).categorias(), b))
                                .executes(RankingComando::top))));
    }

    private static int lista(CommandContext<CommandSourceStack> c) {
        Ranking r = Ranking.de(c.getSource().getServer());
        List<String> l = r.categorias(true);
        MutableComponent m = Component.translatable("ranking.miscelanea.comando.categorias").withStyle(ChatFormatting.GOLD);
        for (String cat : l) {
            m.append(Component.literal("\n · ").withStyle(ChatFormatting.DARK_GRAY));
            m.append(Ranking.nombre(cat).withStyle(s -> s.withColor(Ranking.esGlobal(cat) ? ChatFormatting.AQUA : ChatFormatting.WHITE)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/miscelanea ranking " + cat))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(cat)))));
        }
        c.getSource().sendSuccess(() -> m, false);
        return l.size();
    }

    private static int top(CommandContext<CommandSourceStack> c) {
        String cat = StringArgumentType.getString(c, "categoria").trim();
        Ranking r = Ranking.de(c.getSource().getServer());
        if (!r.categorias().contains(cat)) {
            c.getSource().sendFailure(Component.translatable("ranking.miscelanea.comando.no_existe", cat));
            return 0;
        }
        List<Ranking.Entrada> l = r.top(cat);
        MutableComponent m = Component.literal("━━ ").withStyle(ChatFormatting.GOLD)
                .append(Ranking.nombre(cat).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD))
                .append(Component.literal(" ━━").withStyle(ChatFormatting.GOLD));
        if (l.isEmpty()) m.append(Component.literal("\n").append(Component.translatable("ranking.miscelanea.vacio").withStyle(ChatFormatting.GRAY)));
        ChatFormatting[] col = {ChatFormatting.GOLD, ChatFormatting.WHITE, ChatFormatting.RED};
        String unidad = unidad(cat);
        for (int i = 0; i < l.size(); i++) {
            Ranking.Entrada e = l.get(i);
            ChatFormatting f = i < 3 ? col[i] : ChatFormatting.GRAY;
            m.append(Component.literal("\n" + (i + 1) + ". ").withStyle(f))
                    .append(Component.literal(e.nombre()).withStyle(i < 3 ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(unidad, e.puntos()).withStyle(f));
        }
        c.getSource().sendSuccess(() -> m, false);
        return l.size();
    }

    /** Clave de idioma para "N puntos" / "N victorias" / "N récords" según la categoría. */
    public static String unidad(String cat) {
        return switch (cat) {
            case "global/campeones" -> "ranking.miscelanea.unidad.records";
            case "global/victorias" -> "ranking.miscelanea.unidad.victorias";
            default -> cat.startsWith(Ranking.MESA) ? "ranking.miscelanea.unidad.victorias" : "ranking.miscelanea.unidad.puntos";
        };
    }
}
