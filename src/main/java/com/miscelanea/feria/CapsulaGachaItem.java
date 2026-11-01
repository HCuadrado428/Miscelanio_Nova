package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Cápsula del gachapón: clic derecho para abrirla. Casi siempre una chapa; a veces un peluche; muy rara vez la chapa de oro. */
public class CapsulaGachaItem extends Item {
    public static final int[] COLORES = {0xE84848, 0x4A8CE8, 0x58C858, 0xF0C840, 0xC060E0, 0xF08CC0};

    public CapsulaGachaItem(Properties p) {
        super(p);
    }

    public static int color(ItemStack st) {
        int c = st.hasTag() ? st.getTag().getInt("color") : 0;
        return COLORES[Math.floorMod(c, COLORES.length)];
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.capsula_gacha.ayuda").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level instanceof ServerLevel l) {
            float r = l.random.nextFloat();
            ItemStack premio;
            if (r < 0.04F) {
                premio = ChapaItem.de(ChapaItem.ORO);
                p.displayClientMessage(Component.translatable("miscelanea.gacha.oro").withStyle(ChatFormatting.GOLD), false);
            } else if (r < 0.14F) {
                premio = new ItemStack(ModItems.PELUCHE.get());
                CompoundTag est = new CompoundTag();
                est.putString("color", String.valueOf(l.random.nextInt(4)));
                premio.getOrCreateTag().put("BlockStateTag", est);
            } else premio = ChapaItem.de(l.random.nextInt(ChapaItem.ORO));
            if (!p.addItem(premio)) p.drop(premio, false);
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.FE_CAPSULA_ABRE.get(), SoundSource.PLAYERS, 1F, 1F);
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
