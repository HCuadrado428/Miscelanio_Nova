package com.miscelanea.tortura;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Ganzúa: si llevas grilletes, clic derecho = forzar la cerradura (minijuego GANZUA). Se rompen al forzar mal y se
 * gastan (en creativo no). Si te queda alguna en la mano tras romperse, se sigue con la siguiente.
 */
public class GanzuaItem extends ObjetoTortura {
    public GanzuaItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (p instanceof ServerPlayer sp) Grilletes.forzar(sp);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }
}
