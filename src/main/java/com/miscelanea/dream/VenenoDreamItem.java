package com.miscelanea.dream;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Veneno de Dream: el veneno de sus colmillos en una botella (agachado + clic derecho con una botella vacía mientras se
 * tiene «Mordisco venenoso»). Beberlo MATA (ver {@link VenenoLetal}: cinemática de muerte TipoMuerte.VENENO); sus efectos
 * (los del mordisco, más cortos: lentitud, náuseas, desangrado y debilidad) son los que se llevan las mezclas del
 * caldero, que heredan la marca de veneno letal (los frascos arrojadizos solo salpican los efectos). Los efectos van en CustomPotionEffects,
 * como en los frascos de mezcla; el líquido es su propia textura (verde oscuro con vetas negras), sin teñir.
 */
public class VenenoDreamItem extends PotionItem {
    public VenenoDreamItem(Properties p) {
        super(p);
    }

    /** Lo que da al beberlo. */
    public static List<MobEffectInstance> efectos() {
        return List.of(
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12 * 20, 1),
                new MobEffectInstance(MobEffects.CONFUSION, 10 * 20, 0),
                new MobEffectInstance(DreamRegistro.DESANGRADO.get(), 8 * 20, 0),
                new MobEffectInstance(MobEffects.WEAKNESS, 12 * 20, 0));
    }

    public static ItemStack nuevo() {
        return asegurar(new ItemStack(DreamRegistro.VENENO.get()));
    }

    /** Si al frasco le faltan las etiquetas (/give, creativo...), se las pone. */
    public static ItemStack asegurar(ItemStack st) {
        CompoundTag t = st.getTag();
        if (t == null || !t.contains("CustomPotionEffects")) {
            PotionUtils.setCustomEffects(st, efectos());
            st.getOrCreateTag().putInt("CustomPotionColor", DreamRegistro.COLOR_MORDISCO);
        }
        // mata al beberlo (VenenoLetal); la marca la heredan las mezclas del caldero
        if (!st.getOrCreateTag().getBoolean(VenenoLetal.TAG)) VenenoLetal.marcar(st);
        return st;
    }

    @Override
    public ItemStack getDefaultInstance() {
        return nuevo();
    }

    @Override
    public String getDescriptionId(ItemStack st) {
        return getDescriptionId();
    }

    @Override
    public ItemStack finishUsingItem(ItemStack st, Level l, LivingEntity e) {
        return super.finishUsingItem(asegurar(st), l, e);
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, Entity e, int slot, boolean sel) {
        if (!l.isClientSide) asegurar(st);
    }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level l, List<Component> tip, TooltipFlag f) {
        super.appendHoverText(st.hasTag() ? st : asegurar(st.copy()), l, tip, f);
        tip.add(Component.translatable("item.miscelanea.veneno_dream.mortal").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        tip.add(Component.empty());
        tip.add(Component.translatable("item.miscelanea.veneno_dream.lore").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }
}
