package com.miscelanea.tinta;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

/**
 * Lanzatintas (Splattershot de Splatoon): mantén clic derecho para disparar chorros de tinta.
 * Gasta del depósito de la espalda. Agachado te conviertes en calamar (recargas en tu tinta).
 */
public class LanzatintasItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lanzatintas.idle");
    private static final RawAnimation DISPARO = RawAnimation.begin().thenLoop("animation.lanzatintas.disparo");
    public static final String DISPARANDO = "Disparando";
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LanzatintasItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
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
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (!(e instanceof ServerPlayer p)) return;
        int t = getUseDuration(s) - quedan;
        if (t % 3 != 0) return;
        if (!Deposito.gastar(p, 1.0F)) {
            s.getOrCreateTag().putBoolean(DISPARANDO, false);
            if (t % 12 == 0) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.8F);
            return;
        }
        s.getOrCreateTag().putBoolean(DISPARANDO, true);
        TintaProyectil tp = new TintaProyectil(level, p, ArmaTinta.color(s), 3.5F, 1.15F, 1.0F);
        tp.setPos(p.getX() + p.getLookAngle().x * 0.6, p.getEyeY() - 0.25, p.getZ() + p.getLookAngle().z * 0.6);
        tp.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, 1.55F, 4.0F);
        level.addFreshEntity(tp);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.55F, 1.7F + level.random.nextFloat() * 0.2F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int quedan) {
        if (!level.isClientSide) s.getOrCreateTag().putBoolean(DISPARANDO, false);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean seleccionado) {
        if (!level.isClientSide && s.hasTag() && s.getTag().getBoolean(DISPARANDO)
                && !(e instanceof LivingEntity le && le.isUsingItem() && le.getUseItem() == s)) {
            s.getTag().putBoolean(DISPARANDO, false);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambio) {
        return cambio || a.getItem() != b.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.tinta.ArmaTintaRenderer<>("lanzatintas");
                return r;
            }

            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return com.miscelanea.client.PoseAspiradora.POSE;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "lanzatintas", 2, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            return state.setAndContinue(s != null && s.hasTag() && s.getTag().getBoolean(DISPARANDO) ? DISPARO : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
