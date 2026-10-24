package com.miscelanea.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Gafas de visión nocturna (casco). Se encienden y apagan con la tecla N.
 * Encendidas gastan batería (barra del objeto); con 0 se apagan. Se recargan con una Pila:
 * pila en la otra mano + clic derecho con las gafas, o clic derecho con la pila llevándolas puestas.
 */
public class GafasItem extends ArmorItem {
    public static final int BATERIA_MAX = 2400; // 2 minutos encendidas

    public GafasItem(ArmorMaterial material, Properties p) {
        super(material, Type.HELMET, p);
    }

    public static boolean encendidas(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean("Encendidas");
    }

    public static int bateria(ItemStack s) {
        return s.hasTag() && s.getTag().contains("Bateria") ? s.getTag().getInt("Bateria") : BATERIA_MAX;
    }

    public static void alternar(ServerPlayer p, ItemStack s) {
        boolean on = !encendidas(s) && bateria(s) > 0;
        s.getOrCreateTag().putBoolean("Encendidas", on);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(),
                on ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.35F, 2.0F);
        if (!on) p.removeEffect(MobEffects.NIGHT_VISION);
    }

    public static void recargar(Player p, ItemStack gafas, ItemStack pila) {
        gafas.getOrCreateTag().putInt("Bateria", BATERIA_MAX);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.7F, 1.6F);
        if (!p.getAbilities().instabuild) pila.shrink(1);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack gafas = player.getItemInHand(hand);
        ItemStack otra = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (otra.is(ModItems.PILA.get()) && bateria(gafas) < BATERIA_MAX) {
            if (!level.isClientSide) recargar(player, gafas, otra);
            return InteractionResultHolder.sidedSuccess(gafas, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public void onArmorTick(ItemStack s, Level level, Player p) {
        if (level.isClientSide || !encendidas(s)) return;
        int b = bateria(s) - 1;
        s.getOrCreateTag().putInt("Bateria", Math.max(0, b));
        if (b <= 0) {
            s.getTag().putBoolean("Encendidas", false);
            p.removeEffect(MobEffects.NIGHT_VISION);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.35F, 1.4F);
            return;
        }
        MobEffectInstance nv = p.getEffect(MobEffects.NIGHT_VISION);
        if (nv == null || nv.getDuration() < 220) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, false, false, false));
        }
    }

    @Override
    public boolean isBarVisible(ItemStack s) {
        return bateria(s) < BATERIA_MAX;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13.0F * bateria(s) / BATERIA_MAX);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return 0x6CFF4A;
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return Miscelanea.MOD_ID + ":textures/models/armor/gafas" + (encendidas(stack) ? "_on" : "") + ".png";
    }
}
