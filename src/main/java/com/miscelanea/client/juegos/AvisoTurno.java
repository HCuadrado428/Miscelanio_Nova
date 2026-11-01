package com.miscelanea.client.juegos;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.WeakHashMap;

/** Avisa con un mensaje y un sonido cuando en un juego de mesa cercano pasa a ser tu turno. */
public final class AvisoTurno {
    private static final Map<Object, Boolean> PREVIO = new WeakHashMap<>();

    private AvisoTurno() {}

    public static void revisar(Object tablero, boolean mio, String claveJuego) {
        Boolean antes = PREVIO.put(tablero, mio);
        if (!mio || antes == null || antes) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.translatable("msg.miscelanea.te_toca", Component.translatable(claveJuego)).withStyle(ChatFormatting.GOLD), true);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.get(), 1.5F, 0.7F));
    }
}
