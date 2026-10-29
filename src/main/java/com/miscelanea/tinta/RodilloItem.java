package com.miscelanea.tinta;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Rodillo (Splat Roller de Splatoon):
 * - Mantén clic derecho y anda: pinta una franja ancha del suelo y atropella a los mobs que pilla.
 * - Un toque corto de clic derecho: lanza una ola de tinta en abanico (el "flick").
 * - Si el toque es en el aire, el golpe es vertical: franja estrecha que llega más lejos.
 */
public class RodilloItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rodillo.idle");
    private static final RawAnimation RODAR = RawAnimation.begin().thenLoop("animation.rodillo.rodar");
    private static final RawAnimation FLICK = RawAnimation.begin().thenPlay("animation.rodillo.flick");
    private static final RawAnimation VERTICAL = RawAnimation.begin().thenPlay("animation.rodillo.vertical");
    public static final String RODANDO = "Rodando", FLICK_T = "Flick", FLICK_V = "FlickVertical";
    private static final Map<UUID, Vec3> ULTIMA = new HashMap<>();
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RodilloItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        ULTIMA.put(player.getUUID(), player.position());
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
        Vec3 antes = ULTIMA.getOrDefault(p.getUUID(), p.position());
        Vec3 ahora = p.position();
        ULTIMA.put(p.getUUID(), ahora);
        if (t < 6) return;
        boolean anda = ahora.subtract(antes).horizontalDistanceSqr() > 0.0004 && p.onGround();
        if (!anda || !Deposito.gastar(p, 0.4F)) {
            s.getOrCreateTag().putBoolean(RODANDO, false);
            return;
        }
        s.getOrCreateTag().putBoolean(RODANDO, true);
        ServerLevel sl = (ServerLevel) level;
        int color = ArmaTinta.color(s);
        Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 der = new Vec3(-fwd.z, 0, fwd.x);
        for (int k = -2; k <= 2; k++) {
            Vec3 punto = ahora.add(fwd.scale(1.2)).add(der.scale(k * 0.45)).add(0, 0.2, 0);
            Tinta.pintarSuelo(sl, punto, color);
        }
        if (t % 6 == 0) level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.8F);
        // Atropello: los mobs que pilla el rodillo
        AABB caja = new AABB(ahora.add(fwd.scale(1.2)), ahora.add(fwd.scale(1.2))).inflate(1.2, 0.8, 1.2).move(0, 0.6, 0);
        for (LivingEntity v : sl.getEntitiesOfClass(LivingEntity.class, caja, v -> v != p && v.isAlive())) {
            if (v instanceof Player o && ArmaTinta.colorJugador(o) == color) continue;
            if (v.hurt(sl.damageSources().playerAttack(p), 8.0F)) {
                v.knockback(0.6, -fwd.x, -fwd.z);
                Tinta.salpicar(sl, v.position(), 1.0, color);
                level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 0.7F);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack s, Level level, LivingEntity e, int quedan) {
        if (level.isClientSide || !(e instanceof ServerPlayer p)) return;
        s.getOrCreateTag().putBoolean(RODANDO, false);
        int t = getUseDuration(s) - quedan;
        if (t >= 8) return;
        // Flick: ola de tinta en abanico
        if (!Deposito.gastar(p, 9.0F)) {
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.8F);
            return;
        }
        int color = ArmaTinta.color(s);
        if (!p.onGround() && !p.isInWater()) {
            // En el aire: golpe VERTICAL. Una franja estrecha y larga hacia delante que llega más lejos.
            s.getOrCreateTag().putInt(FLICK_T, 0);
            s.getOrCreateTag().putInt(FLICK_V, 12);
            for (int i = 0; i < 7; i++) {
                TintaProyectil tp = new TintaProyectil(level, p, color, 7.0F, 1.3F, 1.5F);
                tp.setPos(p.getX(), p.getEyeY() - 0.2, p.getZ());
                tp.shootFromRotation(p, Math.min(p.getXRot(), 15.0F) - 6.0F, p.getYRot(), 0.0F, 0.75F + i * 0.17F, 0.6F);
                level.addFreshEntity(tp);
            }
            // El rodillo pega en el suelo justo delante
            Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
            for (double d = 0.8; d <= 2.0; d += 0.6) Tinta.pintarSuelo((ServerLevel) level, p.position().add(fwd.scale(d)), color);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 0.6F);
        } else {
            // En el suelo: ola horizontal en abanico
            s.getOrCreateTag().putInt(FLICK_V, 0);
            s.getOrCreateTag().putInt(FLICK_T, 10);
            for (int i = -3; i <= 3; i++) {
                TintaProyectil tp = new TintaProyectil(level, p, color, 6.0F, 1.4F, 1.6F);
                tp.setPos(p.getX(), p.getEyeY() - 0.4, p.getZ());
                tp.shootFromRotation(p, Math.min(p.getXRot(), 10.0F) - 8.0F, p.getYRot() + i * 9.0F, 0.0F, 0.95F, 2.0F);
                level.addFreshEntity(tp);
            }
        }
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.9F, 1.3F);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (!level.isClientSide && s.hasTag() && s.getTag().getInt(FLICK_T) > 0) s.getTag().putInt(FLICK_T, s.getTag().getInt(FLICK_T) - 1);
        if (!level.isClientSide && s.hasTag() && s.getTag().getInt(FLICK_V) > 0) s.getTag().putInt(FLICK_V, s.getTag().getInt(FLICK_V) - 1);
        if (!level.isClientSide && s.hasTag() && s.getTag().getBoolean(RODANDO)
                && !(e instanceof LivingEntity le && le.isUsingItem() && le.getUseItem() == s)) {
            s.getTag().putBoolean(RODANDO, false);
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
                if (r == null) r = new com.miscelanea.client.tinta.ArmaTintaRenderer<>("rodillo");
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
        c.add(new AnimationController<>(this, "rodillo", 2, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag()) {
                if (s.getTag().getInt(FLICK_V) > 0) return state.setAndContinue(VERTICAL);
                if (s.getTag().getInt(FLICK_T) > 0) return state.setAndContinue(FLICK);
                if (s.getTag().getBoolean(RODANDO)) return state.setAndContinue(RODAR);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
