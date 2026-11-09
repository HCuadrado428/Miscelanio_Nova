package com.miscelanea.armasfuego.rifle;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rifle de cerrojo con un catalejo montado. Mantener el clic derecho = mirar por el catalejo (el zoom y la mira del
 * catalejo vanilla); clic izquierdo = disparar o, si no hay bala lista, accionar el cerrojo; agachado + clic izquierdo
 * = accionar el cerrojo a mano (cambia de munición) o, mirando por el catalejo, sacar una foto.
 */
public class RifleItem extends Item {
    public RifleItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (e instanceof ServerPlayer p) Rifle.apuntando(p, s);
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int quedan) {
        e.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
        if (e instanceof Player p) Rifle.dejarDeApuntar(p);
    }

    @Override
    public int getUseDuration(ItemStack s) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack s) {
        return UseAnim.NONE;
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (e instanceof ServerPlayer p && sel && p.getMainHandItem() == s) Rifle.tickCerrojo(p, s);
    }

    /** No rompe bloques ni da golpes: el clic izquierdo es el gatillo. */
    @Override
    public boolean canAttackBlock(BlockState st, Level level, BlockPos pos, Player p) {
        return false;
    }

    @Override
    public boolean onEntitySwing(ItemStack s, LivingEntity e) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 1;
    }

    @Override
    public boolean isValidRepairItem(ItemStack s, ItemStack material) {
        return material.is(Items.IRON_INGOT);
    }

    // ------------------------------------------------------------------ cobre: encerar con panal, rascar con hacha

    /** En el inventario, como con un saco: coge un panal (o un hacha) y haz clic derecho sobre el rifle. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack s, ItemStack otro, Slot slot, ClickAction accion, Player p, SlotAccess acceso) {
        if (accion != ClickAction.SECONDARY || otro.isEmpty()) return false;
        var t = s.getOrCreateTag();
        if (otro.is(Items.HONEYCOMB)) {
            if (Rifle.encerado(s)) return false;
            t.putBoolean(Rifle.ENCERADO, true);
            if (!p.getAbilities().instabuild) otro.shrink(1);
            if (p.level().isClientSide) p.playSound(SoundEvents.HONEYCOMB_WAX_ON, 1.0F, 1.0F);
            return true;
        }
        if (otro.getItem() instanceof AxeItem) {
            if (Rifle.encerado(s)) {
                t.putBoolean(Rifle.ENCERADO, false);
                if (p.level().isClientSide) p.playSound(SoundEvents.AXE_WAX_OFF, 1.0F, 1.0F);
            } else if (Rifle.oxido(s) > 0) {
                t.putInt(Rifle.OXIDO, Rifle.oxido(s) - 1);
                if (p.level().isClientSide) p.playSound(SoundEvents.AXE_SCRAPE, 1.0F, 1.0F);
            } else {
                return false;
            }
            if (!p.level().isClientSide) otro.hurtAndBreak(1, p, q -> {});
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ ayuda

    private static final String[] COBRE = {"normal", "expuesto", "erosionado", "oxidado"};

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        String rec = Rifle.recamara(s);
        Item bala = Rifle.item(rec);
        Component recamara = Rifle.gastada(s) ? Component.translatable("item.miscelanea.rifle.casquillo")
                : bala == null ? Component.translatable("item.miscelanea.rifle.vacia") : bala.getDescription();
        lista.add(Component.translatable("item.miscelanea.rifle.recamara", recamara).withStyle(ChatFormatting.GOLD));
        Component cobre = Component.translatable("item.miscelanea.rifle.cobre." + COBRE[Rifle.oxido(s)]);
        lista.add(Component.translatable(Rifle.encerado(s) ? "item.miscelanea.rifle.estado_encerado" : "item.miscelanea.rifle.estado", cobre)
                .withStyle(ChatFormatting.DARK_AQUA));
        for (int i = 1; i <= 4; i++) lista.add(Component.translatable("item.miscelanea.rifle.ayuda" + i).withStyle(ChatFormatting.GRAY));
    }

    // El NBT cambia con el cerrojo: que no re-equipe ni corte el uso
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack a, ItemStack b) {
        return a.getItem() != b.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack a, ItemStack b) {
        return a.getItem() == b.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new com.miscelanea.client.armasfuego.rifle.RifleManoCliente());
    }
}
