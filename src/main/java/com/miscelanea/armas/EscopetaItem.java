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
import net.minecraft.world.level.Level;
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
 * Superescopeta de dos cañones con gancho:
 * - Clic derecho: dispara los dos cañones (perdigones a corta distancia) y recarga sola abriendo el arma.
 * - Clic izquierdo (mantener): lanza el gancho (ver {@link Gancho}).
 */
public class EscopetaItem extends Item implements GeoItem {
    public static final String DISPARO = "Disparo", GANCHO = "GanchoFuera", ESPERA = "GanchoEspera", LANZADO = "GanchoLanzado";
    public static final int TIEMPO_DISPARO = 26;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.superescopeta.idle");
    private static final RawAnimation DISPARAR = RawAnimation.begin().thenPlay("animation.superescopeta.disparar");
    private static final RawAnimation G_LANZAR = RawAnimation.begin().thenPlay("animation.superescopeta.gancho_lanzar");
    private static final RawAnimation G_FUERA = RawAnimation.begin().thenLoop("animation.superescopeta.gancho_fuera");
    private static final RawAnimation G_IDLE = RawAnimation.begin().thenLoop("animation.superescopeta.gancho_idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public EscopetaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(s);
        if (!Municion.tiene(player, ModItems.CARTUCHOS.get())) {
            if (!level.isClientSide) level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.fail(s);
        }
        if (level.isClientSide) {
            player.setXRot(player.getXRot() - 7.0F);   // retroceso en la mira
        } else {
            Escopeta.disparar((ServerPlayer) player, s);
        }
        player.getCooldowns().addCooldown(this, TIEMPO_DISPARO);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void inventoryTick(ItemStack s, Level level, Entity e, int slot, boolean sel) {
        if (level.isClientSide || !s.hasTag()) return;
        var t = s.getTag();
        int d = t.getInt(DISPARO);
        if (d > 0) {
            t.putInt(DISPARO, d - 1);
            if (d == TIEMPO_DISPARO - 7)
                level.playSound(null, e.getX(), e.getY(), e.getZ(), ModSounds.ESCOPETA_RECARGA.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        }
        if (t.getInt(ESPERA) > 0) t.putInt(ESPERA, t.getInt(ESPERA) - 1);
        if (t.getInt(LANZADO) > 0) t.putInt(LANZADO, t.getInt(LANZADO) - 1);
        if (t.getBoolean(GANCHO) && e instanceof Player p && Gancho.activo(p) == null) t.putBoolean(GANCHO, false);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.superescopeta.ayuda").withStyle(ChatFormatting.GRAY));
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
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("superescopeta", true);
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
            if (s != null && s.hasTag() && s.getTag().getInt(DISPARO) > 0) return state.setAndContinue(DISPARAR);
            return state.setAndContinue(IDLE);
        }));
        c.add(new AnimationController<>(this, "gancho", 1, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag()) {
                if (s.getTag().getInt(LANZADO) > 0) return state.setAndContinue(G_LANZAR);
                if (s.getTag().getBoolean(GANCHO)) return state.setAndContinue(G_FUERA);
            }
            return state.setAndContinue(G_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
