package com.miscelanea.tesoros;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import javax.annotation.Nullable;
import java.util.List;

/** Cápsula del tiempo: al abrirla salen recuerdos de alguien del pasado y una nota. */
public class CapsulaItem extends Item {
    public static final int NOTAS = 10;

    public CapsulaItem(Properties p) {
        super(p);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.capsula_tiempo.ayuda").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack st = p.getItemInHand(hand);
        if (level instanceof ServerLevel l) {
            var tabla = l.getServer().getLootData().getLootTable(new ResourceLocation(Miscelanea.MOD_ID, "tesoros/capsula_contenido"));
            LootParams lp = new LootParams.Builder(l).withParameter(LootContextParams.ORIGIN, p.position()).withParameter(LootContextParams.THIS_ENTITY, p)
                    .withLuck(p.getLuck()).create(LootContextParamSets.CHEST);
            for (ItemStack s : tabla.getRandomItems(lp)) if (!p.addItem(s)) p.drop(s, false);
            int n = l.random.nextInt(NOTAS);
            p.sendSystemMessage(Component.translatable("msg.miscelanea.capsula.nota").withStyle(ChatFormatting.GOLD)
                    .append(Component.translatable("nota.miscelanea.capsula." + n).withStyle(ChatFormatting.ITALIC, ChatFormatting.YELLOW)));
            l.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.CAPSULA_ABRIR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!p.getAbilities().instabuild) st.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(st, level.isClientSide);
    }
}
