package com.miscelanea.tortura.saw;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Utilidades comunes de los juegos de Jigsaw: el estado guardado en la entidad y el saludo de Jigsaw.
 * <pre>
 * miscelanea_saw: {
 *   Caja: {Tanque (pos), Activa, Nivel, Sangre, Empujes, Espera (tick), Aire, AX/AY/AZ (ancla), Captor (UUID), Abierta (ticks)},
 * }
 * </pre>
 * Se guarda con la entidad (sobrevive a desconectarse); al morir no se copia al cuerpo nuevo. Las claves de juegos
 * que ya no existen (las del pozo de agujas de partidas viejas) se borran con {@link #limpiarViejo(Entity)}.
 */
public final class Saw {
    private Saw() {}

    public static final String RAIZ = "miscelanea_saw";

    public static CompoundTag leer(Entity e) {
        return e.getPersistentData().getCompound(RAIZ);
    }

    public static void escribir(Entity e, CompoundTag d) {
        if (d.isEmpty()) e.getPersistentData().remove(RAIZ);
        else e.getPersistentData().put(RAIZ, d);
    }

    public static boolean tieneAlgo(Entity e) {
        return e.getPersistentData().contains(RAIZ);
    }

    /** Quita del estado lo que no sea de la caja de agua (restos de juegos quitados en mundos viejos). */
    public static void limpiarViejo(Entity e) {
        if (!tieneAlgo(e)) return;
        CompoundTag d = leer(e);
        if (d.size() == 1 && d.contains("Caja")) return;
        CompoundTag n = new CompoundTag();
        if (d.contains("Caja")) n.put("Caja", d.get("Caja"));
        escribir(e, n);
    }

    /**
     * "Hola, &lt;nombre&gt;. Quiero jugar a un juego..." como título, con las reglas del juego de subtítulo (y en el chat,
     * para poder releerlas) y la sintonía.
     * @param juego sufijo de las claves "msg.miscelanea.saw.&lt;juego&gt;.reglas"
     */
    public static void saludo(ServerPlayer p, String juego) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("msg.miscelanea.saw.hola", p.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED)));
        p.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("msg.miscelanea.saw.juego")
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
        p.sendSystemMessage(Component.translatable("msg.miscelanea.saw.hola_chat", p.getDisplayName())
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
        p.sendSystemMessage(Component.translatable("msg.miscelanea.saw." + juego + ".reglas").withStyle(ChatFormatting.RED));
        p.playNotifySound(SawRegistro.JUEGO.get(), SoundSource.MASTER, 1F, 1F);
    }

    /** Título corto (sin el saludo). */
    public static void titulo(ServerPlayer p, Component titulo, Component sub, int quieto) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, quieto, 15));
        p.connection.send(new ClientboundSetTitleTextPacket(titulo));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
    }
}
