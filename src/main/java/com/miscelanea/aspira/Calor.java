package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Sistema de sobrecalentamiento. Cada uso suma calor; al llegar al máximo la aspiradora
 * se bloquea echando humo hasta que se enfría del todo.
 */
public final class Calor {
    public static final float MAX = 200.0F;            // ~10 s aspirando sin parar
    private static final float ENFRIAR = 2.0F;         // por tick cuando no se usa
    private static final float ENFRIAR_BLOQUEADA = 1.5F; // más lento si se ha sobrecalentado (~6,5 s)

    private static final String HEAT = "Heat";
    private static final String OVERHEATED = "Overheated";

    private Calor() {}

    public static float get(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getFloat(HEAT) : 0.0F;
    }

    public static float fraccion(ItemStack stack) {
        return Mth.clamp(get(stack) / MAX, 0.0F, 1.0F);
    }

    public static boolean sobrecalentada(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(OVERHEATED);
    }

    /** Suma calor. Devuelve true si con esto se ha sobrecalentado. */
    public static boolean sumar(ItemStack stack, float cantidad) {
        float nuevo = Math.min(MAX, get(stack) + cantidad);
        stack.getOrCreateTag().putFloat(HEAT, nuevo);
        return nuevo >= MAX;
    }

    /** Se llama cada tick cuando la boquilla NO se está usando. */
    public static void enfriar(ItemStack stack) {
        float heat = get(stack);
        if (heat <= 0.0F) return;
        boolean bloqueada = sobrecalentada(stack);
        heat -= bloqueada ? ENFRIAR_BLOQUEADA : ENFRIAR;
        if (heat <= 0.0F) {
            stack.getTag().remove(HEAT);
            stack.getTag().remove(OVERHEATED);
        } else {
            stack.getTag().putFloat(HEAT, heat);
        }
    }

    public static void sobrecalentar(Player player, ItemStack stack) {
        stack.getOrCreateTag().putFloat(HEAT, MAX);
        stack.getTag().putBoolean(OVERHEATED, true);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.6F);
        if (player.level() instanceof ServerLevel level) {
            Vec3 boca = AspiraUtil.boca(player);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, boca.x, boca.y, boca.z, 15, 0.2, 0.2, 0.2, 0.02);
        }
    }

    /** Color de la barra: verde → amarillo → rojo; parpadea si está bloqueada. */
    public static int colorBarra(ItemStack stack, long gameTime) {
        if (sobrecalentada(stack)) {
            return (gameTime / 5) % 2 == 0 ? 0xFF2020 : 0xFF9020;
        }
        float hue = (1.0F - fraccion(stack)) * 0.33F;
        return Mth.hsvToRgb(hue, 1.0F, 1.0F);
    }
}
