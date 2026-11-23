package com.miscelanea.laboratorio;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Un frasco de suero: se bebe (mantener clic derecho) o se inyecta a otro (clic derecho a alguien). Da un efecto durante un minuto. */
public class SueroItem extends Item {
    public SueroItem(Properties p) {
        super(p);
    }

    @Override
    public Component getName(ItemStack s) {
        return Sueros.conocido(s) ? Component.translatable("item.miscelanea.suero", Component.translatable("laboratorio.miscelanea.suero." + Sueros.tipo(s)))
                : Component.translatable("item.miscelanea.suero_sin_etiquetar");
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 32;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player p, InteractionHand h) {
        return ItemUtils.startUsingInstantly(l, p, h);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack s, Level l, LivingEntity e) {
        if (!l.isClientSide) Sueros.aplicar(e, Sueros.tipo(s));
        return vaciar(s, e);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack s, Player p, LivingEntity v, InteractionHand h) {
        if (!p.level().isClientSide) {
            Sueros.aplicar(v, Sueros.tipo(s));
            p.setItemInHand(h, vaciar(s, p));
        }
        return InteractionResult.sidedSuccess(p.level().isClientSide);
    }

    /** Al gastarlo queda el tubo de ensayo vacío. */
    private static ItemStack vaciar(ItemStack s, LivingEntity quien) {
        if (quien instanceof Player p && p.getAbilities().instabuild) return s;
        s.shrink(1);
        ItemStack tubo = new ItemStack(LabRegistro.TUBO_ENSAYO.get());
        if (s.isEmpty()) return tubo;
        if (quien instanceof Player p && !p.getInventory().add(tubo)) p.drop(tubo, false);
        return s;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        if (Sueros.conocido(s)) tip.add(Component.translatable("laboratorio.miscelanea.suero.identificado").withStyle(ChatFormatting.GREEN));
        else tip.add(Component.translatable("laboratorio.miscelanea.suero.desconocido").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.miscelanea.suero.desc").withStyle(ChatFormatting.DARK_GRAY));
    }
}
