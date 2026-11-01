package com.miscelanea.tirolina;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Carrete de cable: clic derecho en un poste y luego en otro (hasta 64 bloques) para tender la tirolina. */
public class CableTirolinaItem extends Item {
    public static final int MAX = 64;

    public CableTirolinaItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.cable_tirolina.ayuda").withStyle(ChatFormatting.GRAY));
        if (s.hasTag() && s.getTag().contains("desde")) {
            BlockPos p = NbtUtils.readBlockPos(s.getTag().getCompound("desde"));
            lista.add(Component.translatable("item.miscelanea.cable_tirolina.desde", p.getX(), p.getY(), p.getZ()).withStyle(ChatFormatting.YELLOW));
        }
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return s.hasTag() && s.getTag().contains("desde");
    }

    @Override
    public InteractionResult useOn(UseOnContext c) {
        Level l = c.getLevel();
        BlockPos pos = c.getClickedPos();
        Player p = c.getPlayer();
        ItemStack st = c.getItemInHand();
        if (!(l.getBlockEntity(pos) instanceof PosteTirolinaBlockEntity be)) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (be.otro() != null) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.ocupado").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!st.hasTag() || !st.getTag().contains("desde")) {
            st.getOrCreateTag().put("desde", NbtUtils.writeBlockPos(pos));
            if (p != null) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.primero").withStyle(ChatFormatting.YELLOW), true);
            l.playSound(null, pos, ModSounds.TIROLINA_ENGANCHAR.get(), SoundSource.BLOCKS, 0.6F, 1.4F);
            return InteractionResult.CONSUME;
        }
        BlockPos a = NbtUtils.readBlockPos(st.getTag().getCompound("desde"));
        st.removeTagKey("desde");
        if (a.equals(pos)) return InteractionResult.CONSUME;
        if (!(l.getBlockEntity(a) instanceof PosteTirolinaBlockEntity ba) || ba.otro() != null) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.perdido").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (Math.sqrt(a.distSqr(pos)) > MAX) {
            if (p != null) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.lejos", MAX).withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        ba.destino = pos;
        be.origen = a;
        ba.cambiado();
        be.cambiado();
        if (p == null || !p.getAbilities().instabuild) st.shrink(1);
        if (p != null) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.lista").withStyle(ChatFormatting.GREEN), true);
        l.playSound(null, pos, ModSounds.TIROLINA_ENGANCHAR.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        return InteractionResult.CONSUME;
    }
}
