package com.miscelanea.tortura.efectos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Granada de toxina del miedo: se lanza (clic derecho) y al chocar suelta una nube de gas verde que da "miedo". */
public class GranadaMiedoItem extends ObjetoTortura {
    public GranadaMiedoItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F,
                0.5F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            GranadaMiedoEntity g = new GranadaMiedoEntity(level, p);
            g.setItem(st);
            g.shootFromRotation(p, p.getXRot(), p.getYRot(), -6.0F, 0.95F, 0.6F);
            level.addFreshEntity(g);
        }
        p.getCooldowns().addCooldown(this, 20);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}
