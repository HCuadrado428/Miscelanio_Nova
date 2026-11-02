package com.miscelanea.tortura.efectos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Collar explosivo (Dead Money): clic derecho a un cautivo (a cualquier mob, o a cualquier jugador en creativo) para
 * cerrárselo al cuello; quien lo pone recibe el detonador enlazado. Shift + clic derecho al aire: te lo pones tú (para
 * probar: la "radio" es una baliza fija donde estás). El uso sobre entidades lo gestiona {@link EfectosEventos}.
 */
public class CollarItem extends ObjetoTortura {
    public CollarItem(Properties p) {
        super(p, 3);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (!p.isShiftKeyDown()) return InteractionResultHolder.pass(st);
        if (!level.isClientSide) Collares.poner(p, p, st);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
