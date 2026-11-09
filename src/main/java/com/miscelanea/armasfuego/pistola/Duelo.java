package com.miscelanea.armasfuego.pistola;

import com.miscelanea.registry.ModSounds;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Duelo del oeste. Agachado + clic derecho a otro jugador con el revólver en la mano: le retas (le sale en el chat con
 * [Aceptar] / [Rechazar], o te devuelve el reto igual: agachado + clic derecho). Si acepta: cuenta atrás con campana
 * (3, 2, 1) y "¡YA!" en el centro de la pantalla. Antes del "¡YA!" el revólver de los dos no dispara. El primero que da
 * al otro (bala, dardo o bala imán; el fogueo no cuenta) gana, y se dice en cuánto tiempo desenfundó. Todo en memoria:
 * se anula si uno se va, se muere, cambia de dimensión o se alejan mucho, y a los 60 s del "¡YA!" queda en tablas.
 */
public final class Duelo {
    private Duelo() {}

    /** Ticks: cuánto dura un reto sin contestar, cada número de la cuenta atrás y lo que dura el duelo tras el ¡YA!. */
    static final int RETO = 30 * 20, PASO = 20, CUENTA = 3 * PASO, MAXIMO = 60 * 20;
    private static final double LEJOS = 96;

    private record Reto(ServerPlayer retador, ServerPlayer retado, int hasta) {}

    static final class Activo {
        final ServerPlayer a, b;
        final int inicio;
        boolean acabado;

        Activo(ServerPlayer a, ServerPlayer b, int inicio) {
            this.a = a;
            this.b = b;
            this.inicio = inicio;
        }

        boolean tiene(ServerPlayer p) {
            return p == a || p == b;
        }

        ServerPlayer otro(ServerPlayer p) {
            return p == a ? b : a;
        }
    }

    /** Retos pendientes, por el retado. */
    private static final Map<ServerPlayer, Reto> RETOS = new HashMap<>();
    private static final List<Activo> DUELOS = new ArrayList<>();
    private static int reloj;

    public static Activo de(ServerPlayer p) {
        for (Activo d : DUELOS) if (!d.acabado && d.tiene(p)) return d;
        return null;
    }

    /** En la cuenta atrás no se puede disparar. */
    public static boolean bloqueado(ServerPlayer p) {
        Activo d = de(p);
        return d != null && reloj - d.inicio < CUENTA;
    }

    /** Para el HUD del cliente no hace falta nada: los números salen como títulos de vanilla. */
    public static void retar(ServerPlayer a, ServerPlayer b) {
        if (a == b) return;
        if (de(a) != null || de(b) != null) {
            a.displayClientMessage(Component.translatable("pistola.miscelanea.duelo.ocupado").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Reto suyo = RETOS.get(a);
        if (suyo != null && suyo.retador == b && reloj <= suyo.hasta) {
            // b ya me había retado: devolverle el reto es aceptarlo
            empezar(b, a);
            return;
        }
        Reto mio = RETOS.get(b);
        if (mio != null && mio.retador == a && reloj <= mio.hasta) return;   // ya está retado (no repetir el aviso)
        RETOS.put(b, new Reto(a, b, reloj + RETO));
        String nombre = a.getGameProfile().getName();
        MutableComponent si = Component.translatable("pistola.miscelanea.duelo.aceptar").withStyle(Style.EMPTY.withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duelo aceptar " + nombre))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.translatable("pistola.miscelanea.duelo.aceptar_ayuda"))));
        MutableComponent no = Component.translatable("pistola.miscelanea.duelo.rechazar").withStyle(Style.EMPTY.withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/duelo rechazar " + nombre)));
        b.sendSystemMessage(Component.translatable("pistola.miscelanea.duelo.reto", a.getDisplayName()).withStyle(ChatFormatting.GOLD)
                .append(" ").append(si).append(" ").append(no));
        a.displayClientMessage(Component.translatable("pistola.miscelanea.duelo.retado", b.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
        sonar(a, ModSounds.REVOLVER_DUELO.get(), 1.0F, 1.0F);
        sonar(b, ModSounds.REVOLVER_DUELO.get(), 1.0F, 1.0F);
    }

    /** {@code b} acepta el reto de {@code a}. */
    public static boolean aceptar(ServerPlayer b, ServerPlayer a) {
        Reto r = RETOS.get(b);
        if (r == null || r.retador != a || reloj > r.hasta) {
            b.sendSystemMessage(Component.translatable("pistola.miscelanea.duelo.sin_reto", a.getDisplayName()).withStyle(ChatFormatting.GRAY));
            return false;
        }
        if (de(a) != null || de(b) != null) {
            b.sendSystemMessage(Component.translatable("pistola.miscelanea.duelo.ocupado").withStyle(ChatFormatting.GRAY));
            return false;
        }
        empezar(a, b);
        return true;
    }

    public static void rechazar(ServerPlayer b, ServerPlayer a) {
        Reto r = RETOS.get(b);
        if (r == null || r.retador != a) return;
        RETOS.remove(b);
        a.sendSystemMessage(Component.translatable("pistola.miscelanea.duelo.rechazado", b.getDisplayName()).withStyle(ChatFormatting.GRAY));
        b.sendSystemMessage(Component.translatable("pistola.miscelanea.duelo.rechazas", a.getDisplayName()).withStyle(ChatFormatting.GRAY));
    }

    private static void empezar(ServerPlayer a, ServerPlayer b) {
        RETOS.remove(a);
        RETOS.remove(b);
        DUELOS.add(new Activo(a, b, reloj));
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 6));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("pistola.miscelanea.duelo.contra",
                    (p == a ? b : a).getDisplayName()).withStyle(ChatFormatting.GRAY)));
        }
        numero(a, b, 3);
    }

    private static void numero(ServerPlayer a, ServerPlayer b, int n) {
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(String.valueOf(n)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
            sonar(p, SoundEvents.BELL_BLOCK, 1.0F, 0.8F + (3 - n) * 0.1F);
        }
    }

    private static void sonar(ServerPlayer p, SoundEvent s, float vol, float tono) {
        p.playNotifySound(s, SoundSource.PLAYERS, vol, tono);
    }

    public static void impacto(ServerPlayer tirador, ServerPlayer victima) {
        Activo d = de(tirador);
        if (d == null || d.otro(tirador) != victima || reloj - d.inicio < CUENTA) return;
        d.acabado = true;
        int ticks = reloj - d.inicio - CUENTA;
        String seg = String.format(java.util.Locale.ROOT, "%.2f", ticks / 20.0);
        for (ServerPlayer p : new ServerPlayer[]{d.a, d.b}) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(4, 50, 15));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("pistola.miscelanea.duelo.tiempo", seg).withStyle(ChatFormatting.GRAY)));
            p.connection.send(new ClientboundSetTitleTextPacket(p == tirador
                    ? Component.translatable("pistola.miscelanea.duelo.ganas").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                    : Component.translatable("pistola.miscelanea.duelo.pierdes").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
        sonar(tirador, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.0F);
        Component aviso = Component.translatable("pistola.miscelanea.duelo.resultado", tirador.getDisplayName(), victima.getDisplayName(), seg)
                .withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : tirador.server.getPlayerList().getPlayers())
            if (p.level() == tirador.level() && p.distanceToSqr(tirador) < 64 * 64) p.sendSystemMessage(aviso);
        if (!tirador.server.getPlayerList().getPlayers().contains(victima)) victima.sendSystemMessage(aviso);
        ganadorPrueba = tirador.getGameProfile().getName();
    }

    /** Para la prueba automática: quién ganó el último duelo. */
    public static String ganadorPrueba;

    private static boolean valido(ServerPlayer p) {
        return !p.isRemoved() && !p.hasDisconnected() && p.isAlive();
    }

    private static void anular(Activo d, String motivo) {
        d.acabado = true;
        for (ServerPlayer p : new ServerPlayer[]{d.a, d.b})
            if (valido(p)) p.displayClientMessage(Component.translatable(motivo).withStyle(ChatFormatting.GRAY), true);
    }

    static void tick(MinecraftServer srv) {
        reloj++;
        if (!RETOS.isEmpty()) RETOS.values().removeIf(r -> reloj > r.hasta || !valido(r.retador) || !valido(r.retado));
        if (DUELOS.isEmpty()) return;
        for (Activo d : DUELOS) {
            if (d.acabado) continue;
            if (!valido(d.a) || !valido(d.b) || d.a.level() != d.b.level() || d.a.distanceToSqr(d.b) > LEJOS * LEJOS) {
                anular(d, "pistola.miscelanea.duelo.anulado");
                continue;
            }
            int t = reloj - d.inicio;
            if (t == PASO) numero(d.a, d.b, 2);
            else if (t == 2 * PASO) numero(d.a, d.b, 1);
            else if (t == CUENTA) {
                for (ServerPlayer p : new ServerPlayer[]{d.a, d.b}) {
                    p.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
                    p.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
                    p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("pistola.miscelanea.duelo.ya").withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
                    sonar(p, SoundEvents.BELL_BLOCK, 1.0F, 1.6F);
                    sonar(p, SoundEvents.BELL_RESONATE, 0.6F, 1.4F);
                }
            } else if (t > CUENTA + MAXIMO) {
                anular(d, "pistola.miscelanea.duelo.tablas");
            }
        }
        DUELOS.removeIf(d -> d.acabado);
    }

    static void salir(ServerPlayer p) {
        RETOS.remove(p);
        RETOS.values().removeIf(r -> r.retador == p);
        for (Activo d : DUELOS) if (!d.acabado && d.tiene(p)) anular(d, "pistola.miscelanea.duelo.anulado");
        DUELOS.removeIf(d -> d.acabado);
    }

    static void limpiar() {
        RETOS.clear();
        DUELOS.clear();
    }

    static void comando(CommandDispatcher<CommandSourceStack> disp) {
        disp.register(Commands.literal("duelo")
                .then(Commands.literal("aceptar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> {
                    aceptar(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "jugador"));
                    return 1;
                })))
                .then(Commands.literal("rechazar").then(Commands.argument("jugador", EntityArgument.player()).executes(c -> {
                    rechazar(c.getSource().getPlayerOrException(), EntityArgument.getPlayer(c, "jugador"));
                    return 1;
                }))));
    }

    /** Para la prueba automática: acepta por nombre (el jugador de mentira no sale en la lista de jugadores). */
    public static boolean aceptarPrueba(ServerPlayer b) {
        Reto r = RETOS.get(b);
        return r != null && aceptar(b, r.retador);
    }
}
