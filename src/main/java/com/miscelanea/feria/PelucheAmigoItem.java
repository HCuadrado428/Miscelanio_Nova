package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.List;

/**
 * El peluche en la mano. Clic derecho lo lanza (rebota y chilla); agachado te lo pones en la cabeza.
 */
public class PelucheAmigoItem extends BlockItem implements Equipable {

    /** Cuál de los peluches es, para que cada uno suene distinto. */
    private final int indice;

    public PelucheAmigoItem(Block b, Properties p, int indice) {
        super(b, p);
        this.indice = indice;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    public SoundEvent getEquipSound() {
        return SoundEvents.WOOL_PLACE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
        ItemStack st = j.getItemInHand(m);
        // agachado: se lo pone en la cabeza
        if (j.isShiftKeyDown()) return swapWithEquipmentSlot(this, l, j, m);
        // de pie: lo lanza
        l.playSound(null, j.getX(), j.getY(), j.getZ(), ModSounds.FE_CUAC.get(), SoundSource.NEUTRAL,
                0.6F, 1.4F + (indice % 4 - 1.5F) * 0.06F);
        if (!l.isClientSide) {
            PelucheVueloEntity e = new PelucheVueloEntity(l, j, indice);
            e.setItem(st.copyWithCount(1));
            e.shootFromRotation(j, j.getXRot(), j.getYRot(), 0.0F, 0.7F, 1.0F);
            l.addFreshEntity(e);
        }
        j.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        if (!j.getAbilities().instabuild) st.shrink(1);
        return InteractionResultHolder.sidedSuccess(st, l.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> t, TooltipFlag f) {
        for (int i = 1; i <= 3; i++)
            t.add(Component.translatable(getDescriptionId() + ".ayuda" + (i == 1 ? "" : String.valueOf(i)))
                    .withStyle(ChatFormatting.GRAY));
    }
}
