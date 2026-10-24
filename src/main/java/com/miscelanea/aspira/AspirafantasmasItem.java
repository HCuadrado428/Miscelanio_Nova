package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.BoquillaRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * La boquilla del Aspirafantasmas 3000. Necesita llevar puesta la mochila.
 * Clic derecho (mantener) = aspirar. Shift + clic derecho = modo del encantamiento.
 */
public class AspirafantasmasItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.aspirafantasmas.idle");
    private static final RawAnimation ASPIRAR = RawAnimation.begin().thenLoop("animation.aspirafantasmas.aspirar");
    private static final RawAnimation SOBRECALENTADA = RawAnimation.begin().thenLoop("animation.aspirafantasmas.sobrecalentada");
    private static final RawAnimation SOPLAR_ANIM = RawAnimation.begin().thenLoop("animation.aspirafantasmas.soplar");
    private static final RawAnimation LANZALLAMAS_ANIM = RawAnimation.begin().thenLoop("animation.aspirafantasmas.lanzallamas");
    private static final RawAnimation DESTELLO_ANIM = RawAnimation.begin().thenPlayAndHold("animation.aspirafantasmas.destello");

    private static final String MODO = "Modo";
    public static final String ACTIVA = "Activa";
    private static final int TOQUE = 6; // menos de 6 ticks = toque rápido

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public AspirafantasmasItem(Properties properties) {
        super(properties);
    }

    private static net.minecraft.network.chat.MutableComponent msg(String key, Object... args) {
        return Component.translatable("msg." + Miscelanea.MOD_ID + "." + key, args);
    }

    /** Modo con el que se está usando ahora (vale en el cliente: va en el NBT). */
    public static Modo modoActivo(ItemStack stack) {
        return Modo.byOrdinal(stack.hasTag() ? stack.getTag().getInt(MODO) : 0);
    }

    public static boolean activa(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(ACTIVA);
    }

    // =================================================================== uso

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!Deposito.llevaMochila(player)) {
            level.playSound(player, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.4F);
            return InteractionResultHolder.fail(stack);
        }
        if (Calor.sobrecalentada(stack)) {
            return InteractionResultHolder.fail(stack);
        }

        Modo modo = Modo.ASPIRAR;
        Modo especial = Modo.especial(stack);
        if (player.isShiftKeyDown()) {
            // Sin encantamiento: expulsa aire (soplido básico). Alquimista: lanzallamas con el tanque
            modo = especial == null ? Modo.SOPLAR : especial == Modo.ALQUIMISTA ? Modo.LANZALLAMAS : especial;
        } else if (especial == Modo.ALQUIMISTA) {
            modo = Modo.ALQUIMISTA; // clic derecho: aspira y absorbe pociones y efectos
        }
        if (modo == Modo.LANZALLAMAS && Deposito.tanque(Deposito.mochila(player)).isEmpty()) {
            level.playSound(player, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
            return InteractionResultHolder.fail(stack);
        }

        if (!modo.mantener()) {
            if (!level.isClientSide) {
                switch (modo) {
                    case TURBO -> {
                        Modos.turbo(player);
                        calentar(player, stack, 25.0F);
                        player.getCooldowns().addCooldown(this, 8);
                    }
                    case DESATASCADOR -> {
                        Modos.dispararDesatascador(player);
                        calentar(player, stack, 20.0F);
                        player.getCooldowns().addCooldown(this, 15);
                    }
                    default -> {}
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        }

        stack.getOrCreateTag().putInt(MODO, modo.ordinal());
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private void calentar(Player player, ItemStack stack, float cantidad) {
        if (Calor.sumar(stack, cantidad)) {
            Calor.sobrecalentar(player, stack);
            player.getCooldowns().addCooldown(this, 40);
            if (player.isUsingItem()) player.stopUsingItem();
        }
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
        if (level.isClientSide || !(entity instanceof Player player)) return;
        if (!Deposito.llevaMochila(player)) {
            player.stopUsingItem();
            return;
        }
        int usado = getUseDuration(stack) - remaining;
        Modo modo = Modo.byOrdinal(stack.getOrCreateTag().getInt(MODO));

        float coste = switch (modo) {
            case SOPLIDO -> 1.5F;
            case LANZALLAMAS -> 0.8F;
            case DESTELLO -> 0.5F;
            default -> 1.0F;
        };
        // Sujetar a alguien con el desatascador calienta poco: se puede aguantar un buen rato (~25 s)
        if (modo == Modo.ASPIRAR || modo == Modo.ALQUIMISTA) {
            DesatascadorEntity des = Succion.desatascador(player);
            if (des != null && des.agarrado()) coste = 0.4F;
        }
        if (Calor.sumar(stack, coste)) {
            Calor.sobrecalentar(player, stack);
            player.getCooldowns().addCooldown(this, 40);
            player.stopUsingItem();
            return;
        }
        if (!activa(stack)) stack.getTag().putBoolean(ACTIVA, true);

        switch (modo) {
            case ASPIRAR -> Succion.tick(player, usado);
            case DESTELLO -> Modos.destelloCargando(player, usado);
            case SOPLIDO -> Modos.soplido(player, usado, true);
            case SOPLAR -> Modos.soplido(player, usado, false);
            case ALQUIMISTA -> {
                Succion.tick(player, usado);
                Modos.alquimista(player, usado);
            }
            case LANZALLAMAS -> {
                if (!Modos.lanzallamas(player, usado)) player.stopUsingItem();
            }
            default -> {}
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide || !(entity instanceof Player player)) return;
        int usado = getUseDuration(stack) - timeLeft;
        Modo modo = Modo.byOrdinal(stack.getOrCreateTag().getInt(MODO));

        switch (modo) {
            case ASPIRAR -> Succion.soltar(player);
            case DESTELLO -> {
                if (usado >= Modos.CARGA_DESTELLO) {
                    Modos.destello(player);
                    calentar(player, stack, 30.0F);
                    player.getCooldowns().addCooldown(this, 20);
                }
            }
            case SOPLIDO -> {
                if (usado < TOQUE) {
                    if (Modos.canon(player)) calentar(player, stack, 15.0F);
                }
            }
            case ALQUIMISTA -> Succion.soltar(player);
            default -> {}
        }
    }

    @Override
    public void onStopUsing(ItemStack stack, LivingEntity entity, int count) {
        if (entity.level().isClientSide) return;
        if (stack.hasTag()) stack.getTag().remove(ACTIVA);
        if (entity instanceof Player player) Succion.parar(player);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof LivingEntity le)) return;

        // Id para que cada aspiradora tenga sus propias animaciones
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("GeckoLibID")) GeoItem.getOrAssignId(stack, serverLevel);

        boolean usando = le.isUsingItem() && le.getUseItem() == stack;
        if (usando) return;

        if (activa(stack)) stack.getTag().remove(ACTIVA);
        Calor.enfriar(stack);

        if (Calor.sobrecalentada(stack) && level.getGameTime() % 4 == 0) {
            // Humo saliendo de la mochila
            Vec3 look = le.getViewVector(1.0F);
            Vec3 espalda = le.position().add(-look.x * 0.4, 1.4, -look.z * 0.4);
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, espalda.x, espalda.y, espalda.z, 1, 0.1, 0.05, 0.1, 0.01);
        }
    }

    // ============================================================ barra de calor

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return Calor.get(stack) > 0.0F;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Calor.fraccion(stack));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Calor.colorBarra(stack, System.currentTimeMillis() / 50L);
    }

    // El NBT cambia cada tick (calor): que no "re-equipe" ni corte el uso por eso
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

    // ============================================================ encantable

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return true;
    }

    @Override
    public int getEnchantmentValue() {
        return 12;
    }

    // ============================================================ tooltip

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        String p = "tooltip." + Miscelanea.MOD_ID + ".";
        tooltip.add(Component.translatable(p + "requiere_mochila").withStyle(ChatFormatting.DARK_GRAY));
        Modo modo = Modo.especial(stack);
        tooltip.add(Component.translatable(p + (modo == Modo.ALQUIMISTA ? "clic_alquimista" : "clic")).withStyle(ChatFormatting.GRAY));
        if (modo != null) {
            tooltip.add(Component.translatable(p + "shift." + modo.id()).withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable(p + "sin_modo").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        }
        if (Calor.get(stack) > 0) {
            tooltip.add(Component.translatable(p + "calor", Math.round(Calor.fraccion(stack) * 100))
                    .withStyle(Calor.sobrecalentada(stack) ? ChatFormatting.RED : ChatFormatting.GOLD));
        }
    }

    // ============================================================ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BoquillaRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (this.renderer == null) this.renderer = new BoquillaRenderer();
                return this.renderer;
            }

            // Tercera persona: a dos manos (una en el gatillo, otra en la empuñadura delantera)
            @Override
            public net.minecraft.client.model.HumanoidModel.ArmPose getArmPose(LivingEntity entity,
                    InteractionHand hand, ItemStack stack) {
                return com.miscelanea.client.PoseAspiradora.POSE;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "principal", 4, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && Calor.sobrecalentada(s)) return state.setAndContinue(SOBRECALENTADA);
            if (s != null && activa(s)) {
                return state.setAndContinue(switch (modoActivo(s)) {
                    case SOPLAR, SOPLIDO -> SOPLAR_ANIM;
                    case LANZALLAMAS -> LANZALLAMAS_ANIM;
                    case DESTELLO -> DESTELLO_ANIM;
                    default -> ASPIRAR;
                });
            }
            return state.setAndContinue(IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}
