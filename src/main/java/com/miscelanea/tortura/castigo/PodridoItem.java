package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.ObjetoTortura;
import com.miscelanea.tortura.TorturaRegistro;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Tomate podrido / huevo podrido: se lanzan como una bola de nieve (algo más lentos, para que se puedan esquivar). */
public class PodridoItem extends ObjetoTortura {
    public static final float VELOCIDAD = 1.05F;
    private final boolean huevo;

    public PodridoItem(Properties p, boolean huevo) {
        super(p, 2);
        this.huevo = huevo;
    }

    public boolean huevo() {
        return huevo;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), TorturaRegistro.TOMATE_LANZAR.get(), SoundSource.PLAYERS, 0.5F,
                0.8F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide) {
            ProyectilPodrido t = new ProyectilPodrido(level, p, huevo);
            t.setItem(st);
            t.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, VELOCIDAD, 0.8F);
            level.addFreshEntity(t);
        }
        p.getCooldowns().addCooldown(this, 5);
        p.awardStat(Stats.ITEM_USED.get(this));
        if (!p.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide());
    }
}
