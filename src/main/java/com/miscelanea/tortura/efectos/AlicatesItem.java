package com.miscelanea.tortura.efectos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Alicates de electricista: clic derecho al aire con un collar puesto para intentar desactivarlo (minijuego de cortar
 * cables); clic derecho a otro que lo lleve para desactivárselo a él. El uso sobre entidades va en {@link EfectosEventos}.
 */
public class AlicatesItem extends ObjetoTortura {
    public AlicatesItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        if (!Collares.tiene(p)) return InteractionResultHolder.pass(st);
        if (p instanceof ServerPlayer sp) Collares.cortarCables(sp, sp);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
