package com.miscelanea.gravedad;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

/** Pistola de gravedad de Half-Life 2 (ver {@link Gravedad}). Las garras se abren al sostener algo y dan un golpe al lanzar. */
public class GravedadItem extends Item implements GeoItem {
    public static final String AGARRADO = "Agarrado", PUNT = "Punt";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pistola_gravedad.idle");
    private static final RawAnimation SOSTENER = RawAnimation.begin().thenLoop("animation.pistola_gravedad.sostener");
    private static final RawAnimation LANZAR = RawAnimation.begin().thenPlay("animation.pistola_gravedad.lanzar");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public GravedadItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) Gravedad.alternar(sp, s);
        player.getCooldowns().addCooldown(this, 6);
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !s.hasTag()) return;
        int p = s.getTag().getInt(PUNT);
        if (p > 0) s.getTag().putInt(PUNT, p - 1);
        if (s.getTag().contains(AGARRADO) && e instanceof Player pl && !Gravedad.sostiene(pl)) s.getTag().remove(AGARRADO);
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
                if (r == null) r = new com.miscelanea.client.gravedad.GravedadRenderer();
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
        c.add(new AnimationController<>(this, "garras", 3, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag()) {
                if (s.getTag().getInt(PUNT) > 0) return state.setAndContinue(LANZAR);
                if (s.getTag().contains(AGARRADO)) return state.setAndContinue(SOSTENER);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
