package com.miscelanea.laboratorio;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/** El cuaderno de laboratorio: clic derecho para leer las fichas de los experimentos. */
public class CuadernoItem extends Item {
    public CuadernoItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (l.isClientSide) {
            net.minecraft.nbt.ListTag fichas = Experimentos.fichas(st).copy();
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.laboratorio.LabCliente.abrirCuaderno(fichas));
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("laboratorio.miscelanea.cuaderno.fichas", Experimentos.fichas(st).size())
                .withStyle(net.minecraft.ChatFormatting.GRAY));
        tip.add(Component.translatable("item.miscelanea.cuaderno_laboratorio.desc").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
