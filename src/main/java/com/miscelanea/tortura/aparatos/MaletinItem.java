package com.miscelanea.tortura.aparatos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Maletín de herramientas del interrogador. Con él en la mano, mantener el clic derecho mirando a alguien atado a la
 * silla de interrogatorio (o a la silla) abre la RUEDA DE HERRAMIENTAS (eso lo hace el cliente, InterrogaCliente);
 * se suelta sobre una herramienta para usarla. Aquí solo queda la ayuda para cuando no se mira a nadie.
 */
public class MaletinItem extends ObjetoTortura {
    public MaletinItem(Properties p) {
        super(p, 3);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        if (!level.isClientSide)
            p.displayClientMessage(Component.translatable("msg.miscelanea.maletin.mira").withStyle(ChatFormatting.GRAY), true);
        return InteractionResultHolder.sidedSuccess(p.getItemInHand(mano), level.isClientSide);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity t, InteractionHand mano) {
        if (!p.level().isClientSide && !(Aparatos.de(t) instanceof SillaInterrogatorioBlockEntity))
            p.displayClientMessage(Component.translatable("msg.miscelanea.maletin.mira").withStyle(ChatFormatting.GRAY), true);
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }
}
