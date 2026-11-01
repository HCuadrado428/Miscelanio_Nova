package com.miscelanea.dardos;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Dardo: clic derecho para lanzarlo. */
public class DardoItem extends Item {
    public DardoItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        p.getCooldowns().addCooldown(this, 8);
        if (!l.isClientSide) {
            Dardo d = new Dardo(ModEntities.DARDO.get(), l);
            d.setOwner(p);
            d.setPos(p.getX(), p.getEyeY() - 0.1, p.getZ());
            d.shootFromRotation(p, p.getXRot(), p.getYRot(), 0, 1.5F, 0.6F);
            d.setItem(new ItemStack(this));
            l.addFreshEntity(d);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.DA_LANZA.get(), SoundSource.PLAYERS, 0.6F, 1.2F);
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide);
    }
}
