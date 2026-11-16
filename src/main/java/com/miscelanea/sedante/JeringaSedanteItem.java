package com.miscelanea.sedante;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.List;

/** Jeringuilla con sedante de suavis: clic derecho a alguien para pincharle (o al aire, agachado, para pincharte tú). */
public class JeringaSedanteItem extends Item {
    public JeringaSedanteItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity v, InteractionHand h) {
        if (p.getCooldowns().isOnCooldown(this)) return InteractionResult.PASS;
        if (!p.level().isClientSide) {
            // por la espalda: la víctima no mira hacia quien pincha
            Vec3 mira = v.getViewVector(1F).multiply(1, 0, 1);
            Vec3 hacia = p.position().subtract(v.position()).multiply(1, 0, 1);
            boolean espalda = mira.lengthSqr() > 1e-4 && hacia.lengthSqr() > 1e-4 && mira.normalize().dot(hacia.normalize()) < -0.2;
            Sedante.pinchar(v, p, espalda);
            gastar(p, h, st);
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        ItemStack st = p.getItemInHand(h);
        if (!p.isShiftKeyDown() || p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(st);
        if (!l.isClientSide) {
            Sedante.pinchar(p, p, false);
            gastar(p, h, st);
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }

    private void gastar(Player p, InteractionHand h, ItemStack st) {
        p.getCooldowns().addCooldown(this, 60);
        if (p.getAbilities().instabuild) return;
        st.shrink(1);
        ItemStack vacia = new ItemStack(com.miscelanea.registry.ModItems.JERINGUILLA.get());
        if (st.isEmpty()) p.setItemInHand(h, vacia);
        else if (!p.getInventory().add(vacia)) p.drop(vacia, false);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        tip.add(Component.translatable("item.miscelanea.jeringuilla_sedante.desc").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
