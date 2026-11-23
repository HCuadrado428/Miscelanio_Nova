package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/** El pulsómetro: clic derecho a alguien para ver su pulso y su miedo. Si el científico lleva el cuaderno, lo apunta. */
public class PulsometroItem extends Item {
    public PulsometroItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity v, InteractionHand h) {
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResult.PASS;
        if (!p.level().isClientSide) medir(p, v);
        p.getCooldowns().addCooldown(this, 20);
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    /** Mide y avisa al científico. Devuelve las lecturas. */
    public static Map<String, String> medir(Player cientifico, LivingEntity v) {
        Map<String, String> lecturas = Experimentos.reconocimiento(v);
        Player sujeto = v instanceof Player pl ? pl : null;
        String nombre = sujeto != null ? Experimentos.nombreSujeto(cientifico, sujeto) : v.getName().getString();
        cientifico.displayClientMessage(Component.translatable("laboratorio.miscelanea.pulsometro", nombre, lecturas.get("pulso"), lecturas.get("miedo"))
                .withStyle(ChatFormatting.GREEN), true);
        if (sujeto != null) Experimentos.anotar(cientifico, "reconocimiento", sujeto, 0, lecturas);
        return lecturas;
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.pulsometro.desc").withStyle(ChatFormatting.GRAY));
    }
}
