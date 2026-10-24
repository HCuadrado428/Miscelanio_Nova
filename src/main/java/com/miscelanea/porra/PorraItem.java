package com.miscelanea.porra;

import com.miscelanea.client.porra.PorraRenderer;
import com.miscelanea.registry.ModSounds;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
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
 * Porra eléctrica. Con la batería puesta:
 * - Golpe normal: chispazo extra y bloquea los ítems de uso de la víctima un rato.
 * - Clic derecho cerca de alguien: lo agarra (ver {@link Agarres}).
 * - Si alguien choca contigo corriendo, queda agarrado.
 * - Agarrando: clic derecho lo suelta de un empujón; si le queda poca vida, lo ejecuta.
 */
public class PorraItem extends SwordItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.porra.idle");
    private static final RawAnimation DESCARGA = RawAnimation.begin().thenLoop("animation.porra.descarga");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PorraItem(Tier tier, int damage, float speed, Properties properties) {
        super(tier, damage, speed, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (!level.isClientSide) Agarres.usarPorra(player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    /** Marca la porra como "descargando" unos ticks (animación de chispas). */
    public static void marcarDescarga(ItemStack stack) {
        stack.getOrCreateTag().putInt(BateriaItem.DESCARGANDO, 6);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl)) return;
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("GeckoLibID")) GeoItem.getOrAssignId(stack, sl);
        tag = stack.getTag();
        int d = tag.getInt(BateriaItem.DESCARGANDO);
        if (d > 0) {
            if (d == 1) tag.remove(BateriaItem.DESCARGANDO);
            else tag.putInt(BateriaItem.DESCARGANDO, d - 1);
        }

        // Chisporroteo de vez en cuando mientras se lleva en la mano con batería: delata tu posición
        if (selected && entity instanceof LivingEntity le && BateriaItem.tiene(le, 1.0F)
                && level.random.nextInt(50) == 0) {
            Vec3 look = le.getViewVector(1.0F);
            Vec3 p = le.getEyePosition().add(look.scale(0.6)).add(0, -0.1, 0);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 4, 0.08, 0.08, 0.08, 0.05);
            level.playSound(null, p.x, p.y, p.z, ModSounds.CHISPAZO.get(), SoundSource.PLAYERS, 0.25F,
                    0.9F + level.random.nextFloat() * 0.3F);
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private PorraRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new PorraRenderer();
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "porra", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag() && s.getTag().getInt(BateriaItem.DESCARGANDO) > 0) return state.setAndContinue(DESCARGA);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
