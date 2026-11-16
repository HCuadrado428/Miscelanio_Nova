package com.miscelanea.armasfuego.granada;

import com.miscelanea.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Una granada de cobre: clic derecho la lanza (como una bola de nieve, pero rebota y estalla a los 3 segundos). */
public class GranadaItem extends Item {
    public final TipoGranada tipo;

    public GranadaItem(TipoGranada tipo, Properties p) {
        super(p);
        this.tipo = tipo;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand mano) {
        ItemStack s = p.getItemInHand(mano);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 0.5F, 1.6F);
        if (!l.isClientSide) {
            GranadaEntity g = new GranadaEntity(ModEntities.GRANADA.get(), p, l);
            g.setItem(s.copyWithCount(1));
            g.shootFromRotation(p, p.getXRot(), p.getYRot(), 0F, 1.0F, 1.2F);
            l.addFreshEntity(g);
        }
        p.awardStat(Stats.ITEM_USED.get(this));
        p.getCooldowns().addCooldown(this, 20);
        if (!p.getAbilities().instabuild) s.shrink(1);
        return InteractionResultHolder.sidedSuccess(s, l.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
        t.add(Component.translatable("item.miscelanea.granada_" + tipo.id + ".desc").withStyle(ChatFormatting.GRAY));
    }
}
