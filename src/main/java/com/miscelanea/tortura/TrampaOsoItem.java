package com.miscelanea.tortura;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Trampa de oso invertida. Clic derecho a un jugador o mob a 3 bloques o menos: se la pones en la cabeza.
 * Shift + clic derecho al aire: te la pones tú (para probar el juego en solitario); en creativo, si ya la llevas, te la quitas.
 * (El uso sobre entidades lo gestiona {@link TorturaEventos} para adelantarse a los aldeanos y otros mobs con menú.)
 */
public class TrampaOsoItem extends ObjetoTortura {
    public static final double ALCANCE = 3.0;

    public TrampaOsoItem(Properties p) {
        super(p, 3);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (!p.isShiftKeyDown()) return InteractionResultHolder.pass(s);
        if (!level.isClientSide) {
            if (Cautivos.conTrampa(p)) {
                if (p.getAbilities().instabuild) TrampaOso.quitar(p, false);
                else p.displayClientMessage(Component.translatable("msg.miscelanea.trampa.ya_la_llevas").withStyle(ChatFormatting.RED), true);
            } else if (TrampaOso.poner(p, p) && !p.getAbilities().instabuild) s.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    /** Desde el evento de interacción (servidor). */
    public static void usarSobre(Player p, ItemStack s, LivingEntity t) {
        if (Cautivos.conTrampa(t)) {
            if (p.getAbilities().instabuild && p.isShiftKeyDown()) TrampaOso.quitar(t, false);
            else p.displayClientMessage(Component.translatable("msg.miscelanea.trampa.ya_la_lleva").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (p.distanceTo(t) > ALCANCE) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.trampa.lejos").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        if (TrampaOso.poner(t, p) && !p.getAbilities().instabuild) s.shrink(1);
    }
}
