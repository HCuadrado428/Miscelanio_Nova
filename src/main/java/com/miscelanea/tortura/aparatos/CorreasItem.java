package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Correas de cuero: el objeto de control de los aparatos. Clic derecho a un jugador o mob cercano: lo amarra en el
 * aparato libre más cercano (8 bloques). Shift + clic derecho a alguien metido en un aparato (o al aparato): lo suelta
 * (si eres su captor o estás en creativo).
 */
public class CorreasItem extends ObjetoTortura {
    public static final double RADIO = 8;

    public CorreasItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity t, InteractionHand mano) {
        if (t == p) return InteractionResult.PASS;
        if (!(p.level() instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        AparatoBlockEntity actual = Aparatos.de(t);
        if (actual != null) {
            if (p.isShiftKeyDown() && actual.puedeManejar(p)) {
                actual.sacar(true);
                p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.sacado", t.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
            } else {
                p.displayClientMessage(Component.translatable("msg.miscelanea.correas.ya_dentro").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.SUCCESS;
        }
        if (p.distanceTo(t) > 5) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.lejos").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }
        AparatoBlockEntity be = Aparatos.libreCerca(sl, t.blockPosition(), RADIO);
        if (be == null) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.correas.sin_aparato", (int) RADIO).withStyle(ChatFormatting.RED), true);
            return InteractionResult.SUCCESS;
        }
        be.meter(t, p);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Player p = c.getPlayer();
        BlockState s = c.getLevel().getBlockState(c.getClickedPos());
        if (p == null || !p.isShiftKeyDown() || !(s.getBlock() instanceof AparatoBlock)) return InteractionResult.PASS;
        if (c.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (c.getLevel().getBlockEntity(AparatoBlock.principal(s, c.getClickedPos())) instanceof AparatoBlockEntity be && be.ocupado()) {
            if (be.puedeManejar(p)) be.sacar(true);
            else p.displayClientMessage(Component.translatable("msg.miscelanea.aparato.no_tuyo").withStyle(ChatFormatting.RED), true);
        }
        return InteractionResult.SUCCESS;
    }
}
