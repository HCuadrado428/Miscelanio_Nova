package com.miscelanea.armas;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/** Lanzacohetes: cargador de 4; recarga sola de uno en uno mientras lo llevas en la mano y no disparas. */
public class LanzacohetesItem extends Item implements GeoItem {
    public static final String CARGADOR = "Cargador", DISPARO = "Disparo", RECARGA_T = "RecargaT", RECARGANDO = "Recargando";
    public static final int MAX = 4, CADENCIA = 16, T_RECARGA = 16;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.lanzacohetes.idle");
    private static final RawAnimation VACIO = RawAnimation.begin().thenLoop("animation.lanzacohetes.vacio");
    private static final RawAnimation DISPARAR = RawAnimation.begin().thenPlay("animation.lanzacohetes.disparar");
    private static final RawAnimation RECARGAR = RawAnimation.begin().thenPlay("animation.lanzacohetes.recargar");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public LanzacohetesItem(Properties p) {
        super(p);
    }

    public static int cargador(ItemStack s) {
        return s.hasTag() && s.getTag().contains(CARGADOR) ? s.getTag().getInt(CARGADOR) : MAX;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        if (cargador(s) <= 0) {
            if (!level.isClientSide) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.2F);
            player.getCooldowns().addCooldown(this, 8);
            return InteractionResultHolder.fail(s);
        }
        if (level.isClientSide) {
            player.setXRot(player.getXRot() - 3.0F);
        } else {
            disparar((ServerPlayer) player, s);
        }
        player.getCooldowns().addCooldown(this, CADENCIA);
        return InteractionResultHolder.consume(s);
    }

    private static void disparar(ServerPlayer p, ItemStack s) {
        var t = s.getOrCreateTag();
        t.putInt(CARGADOR, cargador(s) - 1);
        t.putInt(DISPARO, 10);
        t.putInt(RECARGA_T, 0);
        Vec3 ojo = p.getEyePosition();
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        // apunta a lo que hay bajo la mira (el cohete sale del hombro derecho y converge)
        Vec3 lejos = ojo.add(mira.scale(64));
        var hit = p.level().clip(new ClipContext(ojo, lejos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 objetivo = hit.getType() != HitResult.Type.MISS ? hit.getLocation() : lejos;
        Vec3 inicio = ojo.add(der.scale(0.25)).add(0, -0.2, 0).add(mira.scale(0.6));
        Vec3 dir = objetivo.subtract(inicio);
        dir = dir.lengthSqr() < 0.25 ? mira : dir.normalize();
        CoheteEntity c = new CoheteEntity(p.level(), p);
        c.setPos(inicio.x, inicio.y - 0.125, inicio.z);
        c.shoot(dir.x, dir.y, dir.z, 1.15F, 0.0F);
        p.level().addFreshEntity(c);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LANZACOHETES_DISPARO.get(), SoundSource.PLAYERS, 1.5F, 0.95F + p.getRandom().nextFloat() * 0.1F);
        if (p.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Vec3 atras = inicio.subtract(mira.scale(1.4));
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE, atras.x, atras.y, atras.z, 6, 0.15, 0.15, 0.15, 0.03);
            sl.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME, inicio.x, inicio.y, inicio.z, 4, 0.05, 0.05, 0.05, 0.05);
        }
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide) return;
        var t = s.getOrCreateTag();
        if (!t.contains(CARGADOR)) t.putInt(CARGADOR, MAX);
        if (t.getInt(DISPARO) > 0) t.putInt(DISPARO, t.getInt(DISPARO) - 1);
        if (t.getInt(RECARGANDO) > 0) t.putInt(RECARGANDO, t.getInt(RECARGANDO) - 1);
        if (sel && e instanceof Player p && t.getInt(CARGADOR) < MAX && !p.getCooldowns().isOnCooldown(this)) {
            int r = t.getInt(RECARGA_T) + 1;
            if (r >= T_RECARGA) {
                r = 0;
                if (Municion.gastar(p, ModItems.COHETE.get())) {
                    t.putInt(CARGADOR, t.getInt(CARGADOR) + 1);
                    t.putInt(RECARGANDO, 16);
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LANZACOHETES_RECARGA.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
                }
            }
            t.putInt(RECARGA_T, r);
        }
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.lanzacohetes.ayuda").withStyle(ChatFormatting.GRAY));
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
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("lanzacohetes", false);
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
        c.add(new AnimationController<>(this, "arma", 1, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null) {
                if (s.hasTag() && s.getTag().getInt(DISPARO) > 0) return state.setAndContinue(DISPARAR);
                if (s.hasTag() && s.getTag().getInt(RECARGANDO) > 0) return state.setAndContinue(RECARGAR);
                if (cargador(s) <= 0) return state.setAndContinue(VACIO);
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
