package com.miscelanea.armas;

import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Cañón de plasma (inspirado en la BFG de Doom). Solo del creativo. Mantén clic derecho: se carga un segundo con un
 * zumbido que sube y suelta una esfera verde lenta que lanza rayos a todo lo que pasa cerca y estalla al chocar.
 */
public class BfgItem extends Item implements GeoItem {
    public static final int CARGA = 22, ENFRIAR = 60;
    public static final String DISPARO = "Disparo";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.bfg.idle");
    private static final RawAnimation CARGAR = RawAnimation.begin().thenLoop("animation.bfg.cargar");
    private static final RawAnimation DISPARAR = RawAnimation.begin().thenPlay("animation.bfg.disparar");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BfgItem(Properties p) {
        super(p);
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
    public InteractionResultHolder<ItemStack> use(Level level, Player p, InteractionHand hand) {
        ItemStack s = p.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || p.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.pass(s);
        p.startUsingItem(hand);
        if (!level.isClientSide) level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.BFG_CARGA.get(), SoundSource.PLAYERS, 1.4F, 1F);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int restante) {
        int t = getUseDuration(s) - restante;
        if (level instanceof ServerLevel sl && t % 3 == 0) {
            Vec3 boca = e.getEyePosition().add(e.getViewVector(1).scale(1.2)).add(0, -0.25, 0);
            sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.3F, 1F, 0.3F), 1.2F), boca.x, boca.y, boca.z,
                    3 + t / 4, 0.2, 0.2, 0.2, 0);
        }
        if (t >= CARGA) {
            if (!level.isClientSide && e instanceof Player p) disparar(p, s);
            e.stopUsingItem();
            if (e instanceof Player p) p.getCooldowns().addCooldown(this, ENFRIAR);
        }
    }

    private static void disparar(Player p, ItemStack s) {
        s.getOrCreateTag().putInt(DISPARO, 12);
        Vec3 mira = p.getViewVector(1);
        Vec3 inicio = p.getEyePosition().add(mira.scale(1.1)).add(0, -0.3, 0);
        PlasmaEntity b = new PlasmaEntity(p.level(), p);
        b.setPos(inicio.x, inicio.y, inicio.z);
        b.setDeltaMovement(mira.scale(0.55));
        p.level().addFreshEntity(b);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.BFG_DISPARO.get(), SoundSource.PLAYERS, 2.5F, 1F);
        p.setDeltaMovement(p.getDeltaMovement().subtract(mira.scale(0.35)));
        p.hurtMarked = true;
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (!level.isClientSide && s.hasTag() && s.getTag().getInt(DISPARO) > 0) s.getTag().putInt(DISPARO, s.getTag().getInt(DISPARO) - 1);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        l.add(Component.translatable("item.miscelanea.bfg.ayuda").withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
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
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("bfg", true);
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
            Entity e = state.getData(DataTickets.ENTITY);
            if (s != null && s.hasTag() && s.getTag().getInt(DISPARO) > 0) return state.setAndContinue(DISPARAR);
            if (e instanceof LivingEntity le && le.isUsingItem() && le.getUseItem().getItem() == this) return state.setAndContinue(CARGAR);
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
