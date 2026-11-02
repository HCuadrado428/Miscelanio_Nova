package com.miscelanea.tortura.efectos;

import com.miscelanea.tortura.ObjetoTortura;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Dardo de cerbatana con toxina del miedo: clic derecho lo sopla (rápido y casi recto); a quien le da, "miedo". */
public class DardoMiedoItem extends ObjetoTortura {
    public static final float VELOCIDAD = 2.7F;

    public DardoMiedoItem(Properties p) {
        super(p, 2);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand mano) {
        ItemStack st = p.getItemInHand(mano);
        level.playSound(null, p.getX(), p.getEyeY(), p.getZ(), EfectosRegistro.CERBATANA.get(), SoundSource.PLAYERS, 0.9F,
                0.9F + level.getRandom().nextFloat() * 0.2F);
        if (!level.isClientSide) {
            DardoMiedoEntity d = new DardoMiedoEntity(level, p);
            d.setItem(st);
            d.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, VELOCIDAD, 0.4F);
            level.addFreshEntity(d);
        }
        p.getCooldowns().addCooldown(this, 15);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}
