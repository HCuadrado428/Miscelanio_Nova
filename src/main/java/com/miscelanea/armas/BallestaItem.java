package com.miscelanea.armas;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.item.TooltipFlag;
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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ballesta de repetición (chu-ko-nu): cargador de 6 virotes que se disparan seguidos mientras mantienes el clic
 * derecho (un clic = un virote), con un ciclo de palanca por disparo. Recarga con la animación de palanca
 * (1,5 s) al vaciarse, con R o con mayúsculas + clic derecho. Usa flechas normales (las de efecto lo conservan).
 */
public class BallestaItem extends Item implements GeoItem {
    private static final String A = "animation.ballesta_repeticion.";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop(A + "idle");
    private static final RawAnimation DISPARAR_A = RawAnimation.begin().thenPlay(A + "disparar_a");
    private static final RawAnimation DISPARAR_B = RawAnimation.begin().thenPlay(A + "disparar_b");
    private static final RawAnimation RECARGAR = RawAnimation.begin().thenPlay(A + "recargar");
    private static final RawAnimation RECARGA_VIROTES = RawAnimation.begin().thenPlay(A + "recarga_virotes");
    private static final RawAnimation[] CARGA = new RawAnimation[Ballesta.MAX + 1];
    static {
        for (int i = 0; i <= Ballesta.MAX; i++) CARGA[i] = RawAnimation.begin().thenLoop(A + "carga_" + i);
    }
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BallestaItem(Properties p) {
        super(p);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || Ballesta.recargando(s)) return InteractionResultHolder.fail(s);
        int n = Ballesta.cargados(s);
        if ((player.isShiftKeyDown() && n < Ballesta.MAX) || n <= 0) {
            if (Ballesta.puedeRecargar(player, s)) {
                if (player instanceof ServerPlayer sp) Ballesta.iniciarRecarga(sp, s);
                return InteractionResultHolder.consume(s);
            }
            if (n <= 0) {
                if (!level.isClientSide)
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
                return InteractionResultHolder.fail(s);
            }
        }
        // mantener pulsado = ráfaga; el disparo lo hace onUseTick (también el primero, en este mismo tick)
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(s);
    }

    @Override
    public void onUseTick(Level level, LivingEntity e, ItemStack s, int quedan) {
        if (!(e instanceof ServerPlayer p)) return;
        if (Ballesta.recargando(s) || Ballesta.cargados(s) <= 0) {
            p.stopUsingItem();
            return;
        }
        long ahora = level.getGameTime();
        if (ahora - s.getOrCreateTag().getLong(Ballesta.ULTIMO) >= Ballesta.CADENCIA) Ballesta.disparar(p, s);
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
        if (!(level instanceof ServerLevel sl)) return;
        var t = s.getOrCreateTag();
        if (!t.contains("GeckoLibID")) GeoItem.getOrAssignId(s, sl);
        if (t.getInt(Ballesta.DISPARO) > 0) t.putInt(Ballesta.DISPARO, t.getInt(Ballesta.DISPARO) - 1);
        if (!(e instanceof ServerPlayer p)) return;
        boolean enMano = sel && p.getMainHandItem() == s;
        if (Ballesta.recargando(s)) {
            if (enMano) Ballesta.tickRecarga(p, s);
            else Ballesta.cancelarRecarga(s);
        } else if (enMano && Ballesta.cargados(s) <= 0 && t.getInt(Ballesta.DISPARO) <= 0 && !p.isUsingItem()
                && (p.getAbilities().instabuild || Ballesta.flechas(p) > 0)) {
            Ballesta.iniciarRecarga(p, s);          // vacía: recarga sola
        }
    }

    // ------------------------------------------------------------------ barra = virotes en el cargador

    @Override
    public boolean isBarVisible(ItemStack s) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack s) {
        return Math.round(13.0F * Ballesta.cargados(s) / Ballesta.MAX);
    }

    @Override
    public int getBarColor(ItemStack s) {
        return Ballesta.recargando(s) ? 0x9A9A9A : Ballesta.cargados(s) <= 1 ? 0xE0533A : 0xE8B74A;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.ballesta_repeticion.virotes", Ballesta.cargados(s), Ballesta.MAX).withStyle(ChatFormatting.GOLD));
        lista.add(Component.translatable("item.miscelanea.ballesta_repeticion.ayuda").withStyle(ChatFormatting.GRAY));
    }

    // El NBT cambia con cada disparo: que no re-equipe ni corte el uso
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

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("ballesta_repeticion", false);
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
        // mecanismo: palanca, cargador, cuerda y palas
        c.add(new AnimationController<>(this, "arma", 1, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s != null && s.hasTag()) {
                if (Ballesta.recargando(s)) return state.setAndContinue(RECARGAR);
                if (s.getTag().getInt(Ballesta.DISPARO) > 0)
                    return state.setAndContinue(s.getTag().getInt(Ballesta.TIROS) % 2 == 0 ? DISPARAR_A : DISPARAR_B);
            }
            return state.setAndContinue(IDLE);
        }));
        // virotes que se ven en el cargador (sin transición: aparecen y desaparecen de golpe)
        c.add(new AnimationController<>(this, "virotes", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s == null) return state.setAndContinue(CARGA[Ballesta.MAX]);
            if (Ballesta.recargando(s)) return state.setAndContinue(RECARGA_VIROTES);
            return state.setAndContinue(CARGA[Math.min(Ballesta.MAX, Ballesta.cargados(s))]);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
