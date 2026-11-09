package com.miscelanea.armasfuego.oeste;

import com.miscelanea.logros.LogrosMiscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Estuche de violín: guarda una Thompson (y nada más). Clic derecho con él en la mano: lo abre y la Thompson pasa a
 * la mano (el estuche vacío, al inventario). Con la Thompson en la otra mano, clic derecho la guarda. En el
 * inventario, como un saco: suelta la Thompson encima para guardarla, clic derecho con la mano vacía para sacarla.
 * Cada vez que alguien lo abre suena una nota de violín desafinada: nadie ha tocado ese violín en su vida.
 */
public class EstucheViolinItem extends Item {
    public static final String ARMA = "Arma";

    public EstucheViolinItem(Properties p) {
        super(p);
    }

    public static ItemStack dentro(ItemStack s) {
        return s.hasTag() && s.getTag().contains(ARMA) ? ItemStack.of(s.getTag().getCompound(ARMA)) : ItemStack.EMPTY;
    }

    public static void guardar(ItemStack estuche, ItemStack arma) {
        if (arma.isEmpty()) estuche.removeTagKey(ARMA);
        else estuche.getOrCreateTag().put(ARMA, arma.save(new net.minecraft.nbt.CompoundTag()));
    }

    /** Un estuche con una Thompson dentro (para el botín). */
    public static ItemStack con(ItemStack arma) {
        ItemStack e = new ItemStack(com.miscelanea.registry.ModItems.ESTUCHE_VIOLIN.get());
        guardar(e, arma);
        return e;
    }

    private static void nota(Player p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.VIOLIN_DESAFINADO.get(), SoundSource.PLAYERS, 0.9F,
                0.85F + p.getRandom().nextFloat() * 0.3F);
        if (p instanceof ServerPlayer sp) LogrosMiscelanea.dar(sp, "thompson_violin");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        ItemStack arma = dentro(s);
        if (!arma.isEmpty()) {
            if (!level.isClientSide) {
                nota(p);
                ItemStack vacio = s.copy();
                guardar(vacio, ItemStack.EMPTY);
                p.setItemInHand(hand, arma);
                if (!p.getInventory().add(vacio)) p.drop(vacio, false);
            }
            return InteractionResultHolder.sidedSuccess(p.getItemInHand(hand), level.isClientSide);
        }
        InteractionHand otra = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack o = p.getItemInHand(otra);
        if (o.getItem() instanceof ThompsonItem) {
            if (!level.isClientSide) {
                nota(p);
                guardar(s, o.copy());
                p.setItemInHand(otra, ItemStack.EMPTY);
            }
            return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
        }
        return InteractionResultHolder.pass(s);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, Slot slot, ClickAction a, Player p, SlotAccess cursor) {
        if (a != ClickAction.SECONDARY || !slot.allowModification(p)) return false;
        ItemStack arma = dentro(s);
        if (otro.isEmpty() && !arma.isEmpty()) {
            cursor.set(arma);
            guardar(s, ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        if (otro.getItem() instanceof ThompsonItem && arma.isEmpty()) {
            guardar(s, otro.copy());
            cursor.set(ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack s, Slot slot, ClickAction a, Player p) {
        if (a != ClickAction.SECONDARY) return false;
        ItemStack otro = slot.getItem();
        if (otro.getItem() instanceof ThompsonItem && dentro(s).isEmpty()) {
            guardar(s, otro.copy());
            slot.set(ItemStack.EMPTY);
            if (!p.level().isClientSide) nota(p);
            return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        ItemStack arma = dentro(s);
        l.add(arma.isEmpty() ? Component.translatable("item.miscelanea.estuche_violin.vacio").withStyle(ChatFormatting.GRAY)
                : Component.translatable("item.miscelanea.estuche_violin.lleva").withStyle(ChatFormatting.GOLD));
        l.add(Component.translatable("item.miscelanea.estuche_violin.ayuda").withStyle(ChatFormatting.DARK_GRAY));
    }
}
