package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** El portapapeles: clic derecho a un jugador le pone número de sujeto ("Sujeto 7"), que luego sale en el cuaderno. */
public class PortapapelesItem extends Item {
    public PortapapelesItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack st, Player p, LivingEntity v, InteractionHand h) {
        if (!(v instanceof Player sujeto)) return InteractionResult.PASS;
        if (!p.level().isClientSide) {
            int n = asignar(st, sujeto);
            p.displayClientMessage(Component.translatable("laboratorio.miscelanea.portapapeles", n, sujeto.getGameProfile().getName())
                    .withStyle(ChatFormatting.AQUA), true);
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    /** El número de sujeto de ese jugador en este portapapeles (se le da el siguiente si aún no tiene). */
    public static int asignar(ItemStack portapapeles, Player sujeto) {
        CompoundTag t = portapapeles.getOrCreateTag();
        CompoundTag sujetos = t.getCompound("Sujetos");
        String id = sujeto.getStringUUID();
        if (!sujetos.contains(id)) {
            int n = t.getInt("Siguiente") + 1;
            sujetos.putInt(id, n);
            t.putInt("Siguiente", n);
            t.put("Sujetos", sujetos);
        }
        return sujetos.getInt(id);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        int n = st.getTag() == null ? 0 : st.getTag().getCompound("Sujetos").size();
        tip.add(Component.translatable("laboratorio.miscelanea.portapapeles.sujetos", n).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.miscelanea.portapapeles.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
