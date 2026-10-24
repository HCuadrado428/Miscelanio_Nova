package com.miscelanea.medigun;

import com.miscelanea.client.medigun.MedigunRenderer;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * Medigun. Necesita la mochila médica puesta.
 * - Mantén clic derecho apuntando a un jugador: se engancha el rayo y le cura (y le sobrecura un poco).
 *   El rayo aguanta aunque dejes de apuntarle, mientras esté cerca y a la vista.
 * - Curando se llena la ÜberCarga (la barra del objeto). Llena: Shift + clic derecho → tú y tu paciente
 *   sois invulnerables 8 segundos.
 * - Se tiñe en la mesa de crafteo con cualquier tinte (cambia el color del rayo).
 */
public class MedigunItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.medigun.idle");
    private static final RawAnimation CURANDO = RawAnimation.begin().thenLoop("animation.medigun.curando");
    private static final RawAnimation UBER = RawAnimation.begin().thenLoop("animation.medigun.uber");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public MedigunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!MochilaMedicaItem.lleva(player)) {
            level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL,
                    SoundSource.PLAYERS, 0.5F, 1.4F);
            return InteractionResultHolder.fail(stack);
        }
        if (player.isShiftKeyDown() && Medigun.carga(stack) >= Medigun.MAX && !Medigun.uberActiva(stack)) {
            if (player instanceof ServerPlayer sp) Medigun.activarUber(sp, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(entity instanceof ServerPlayer sp)) return;
        if (!MochilaMedicaItem.lleva(sp)) {
            sp.stopUsingItem();
            return;
        }
        Medigun.tick(sp, stack);
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity instanceof ServerPlayer sp) Medigun.soltar(sp, stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof LivingEntity le)) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("GeckoLibID")) GeoItem.getOrAssignId(stack, sl);
        Medigun.tickObjeto(le, stack);
    }

    // ------------------------------------------------------------------ barra = ÜberCarga

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Medigun.carga(stack) > 0.0F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Medigun.carga(stack) / Medigun.MAX);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        int c = Medigun.color(stack);
        if (Medigun.carga(stack) >= Medigun.MAX || Medigun.uberActiva(stack)) {
            // parpadea hacia blanco cuando está lista o en uso
            float t = 0.5F + 0.5F * Mth.sin(System.currentTimeMillis() / 120.0F);
            return Medigun.mezclar(c, 0xFFFFFF, t * 0.6F);
        }
        return c;
    }

    // El NBT cambia mientras curas: que no re-equipe ni corte el uso
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean canContinueUsing(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() == newStack.getItem();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private MedigunRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new MedigunRenderer();
                return this.renderer;
            }

            // Tercera persona: a dos manos, igual que la aspiradora
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity,
                    InteractionHand hand, ItemStack stack) {
                return com.miscelanea.client.PoseAspiradora.POSE;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "medigun", 3, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag()) {
                if (s.getTag().getBoolean(Medigun.UBER_ACTIVA)) return state.setAndContinue(UBER);
                if (s.getTag().getBoolean(Medigun.CURANDO)) return state.setAndContinue(CURANDO);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
