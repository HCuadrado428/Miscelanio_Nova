package com.miscelanea.tortura.saw;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * La caja de agua (Saw V). Clic derecho a un jugador o mob cercano: se la encajas en la cabeza (hace falta un tanque de
 * agua cerca). Shift + clic derecho al aire: te la pones tú (para probar el juego en solitario); en creativo, si ya la
 * llevas, te la quitas. (El uso sobre entidades lo gestiona {@link SawEventos}.)
 */
public class CajaAguaItem extends ObjetoTortura {
    public CajaAguaItem(Properties p) {
        super(p, 3);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!p.isShiftKeyDown()) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            if (CajaAgua.tiene(p)) {
                if (p.getAbilities().instabuild) CajaAgua.quitar(p, false);
                else p.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.ya_la_llevas").withStyle(ChatFormatting.RED), true);
            } else if (CajaAgua.poner(p, p) && !p.getAbilities().instabuild) s.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** Desde el evento de interacción (servidor). */
    public static void usarSobre(Player p, ItemStack s, LivingEntity t) {
        if (CajaAgua.tiene(t)) {
            if (p.getAbilities().instabuild && p.isShiftKeyDown()) CajaAgua.quitar(t, false);
            else p.displayClientMessage(Component.translatable("msg.miscelanea.saw.caja.ya_la_lleva").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.distanceTo(t) > CajaAgua.ALCANCE) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.trampa.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (CajaAgua.poner(t, p) && !p.getAbilities().instabuild) s.shrink(1);
    }
}
