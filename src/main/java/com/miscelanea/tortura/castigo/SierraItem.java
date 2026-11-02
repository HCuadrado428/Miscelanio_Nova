package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Sierra de arco (para metal). Con una bola de preso en el tobillo, clic derecho: minijuego SERRAR para cortar la cadena.
 * Clic derecho a otro preso: le cortas tú la suya. Se gasta: un intento fallido la mella, cortar la desgasta más.
 */
public class SierraItem extends ObjetoTortura {
    public SierraItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (!level.isClientSide && p instanceof ServerPlayer sp) Bolas.serrar(sp, sp, hand);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}
