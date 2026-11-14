package com.miscelanea.sangre;

import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
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
 * Hacha de sangre. No es un hacha normal: bebe de lo que mata y va despertando por fases.
 *
 * <ul>
 *   <li><b>Seca:</b> pega y poco mas.</li>
 *   <li><b>Sedienta:</b> cada golpe guarda sangre. Llena del todo, clic derecho suelta la Siega.</li>
 *   <li><b>Cobradora:</b> agachado y clic derecho, el Diezmo: pagas dos corazones y el siguiente golpe triplica.</li>
 *   <li><b>Viva:</b> cada corte deja un charco que frena al que lo pisa y cura a su dueno.</li>
 * </ul>
 */
public class HachaSangreItem extends AxeItem implements GeoItem {

    public static final String SANGRE = "Sangre", BEBIDO = "Bebido", COBRO = "Cobro", SECA = "SinBeberDesde";
    /** Cuanta sangre cabe en el corazon. */
    public static final int DEPOSITO = 20;
    /** Lo que hay que beber para despertar cada fase. */
    private static final int[] UMBRAL = {0, 120, 420, 900};
    /** Si pasa esto sin beber, el hacha empieza a vaciarse. */
    private static final int OLVIDO = 600;

    private static final RawAnimation QUIETA = RawAnimation.begin().thenLoop("animation.hacha_sangre.quieta");
    private static final RawAnimation LATE = RawAnimation.begin().thenLoop("animation.hacha_sangre.late");
    private static final RawAnimation LLENA = RawAnimation.begin().thenLoop("animation.hacha_sangre.llena");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HachaSangreItem(Properties p) {
        super(Tiers.NETHERITE, 5.0F, -3.1F, p);
    }

    // ---------------------------------------------------------------- lo que guarda el hacha
    private static CompoundTag datos(ItemStack s) {
        return s.getOrCreateTag();
    }

    public static int sangre(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(SANGRE) : 0;
    }

    public static int bebido(ItemStack s) {
        return s.hasTag() ? s.getTag().getInt(BEBIDO) : 0;
    }

    /** En que fase esta: 0 seca, 1 sedienta, 2 cobradora, 3 viva. */
    public static int fase(ItemStack s) {
        int b = bebido(s);
        int f = 0;
        for (int i = 1; i < UMBRAL.length; i++) if (b >= UMBRAL[i]) f = i;
        return f;
    }

    private static void beber(ItemStack s, int cuanto, float dano) {
        CompoundTag t = datos(s);
        t.putInt(SANGRE, Math.min(DEPOSITO, t.getInt(SANGRE) + cuanto));
        t.putInt(BEBIDO, t.getInt(BEBIDO) + Math.max(1, (int) dano));
        t.putInt(SECA, 0);
    }

    // ---------------------------------------------------------------- pegar
    @Override
    public boolean hurtEnemy(ItemStack s, LivingEntity victima, LivingEntity quien) {
        super.hurtEnemy(s, victima, quien);
        Level l = quien.level();
        CompoundTag t = datos(s);
        int fase = fase(s);
        float dano = 5.0F;

        // el Diezmo cobrado: este golpe vale por tres
        if (t.getBoolean(COBRO)) {
            t.putBoolean(COBRO, false);
            victima.hurt(quien.damageSources().playerAttack(quien instanceof Player p ? p : null), dano * 2.0F);
            if (l instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victima.getX(), victima.getY(0.6), victima.getZ(), 14, 0.4, 0.4, 0.4, 0.1);
            if (!victima.isAlive() && quien instanceof Player p) {
                p.heal(8.0F);
                t.putInt(SANGRE, DEPOSITO);
            }
        }

        beber(s, fase >= 1 ? 2 : 1, dano);

        if (l instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.FALLING_LAVA, victima.getX(), victima.getY(0.7), victima.getZ(),
                    6 + fase * 3, 0.3, 0.3, 0.3, 0.0);
            // la fase viva deja un charco donde ha cortado
            if (fase >= 3) charco(sl, victima, quien);
        }
        return true;
    }

    /** Un charco de sangre: frena al que lo pisa y cura a quien lo derramo. */
    private void charco(ServerLevel l, LivingEntity victima, LivingEntity duenno) {
        AreaEffectCloud c = new AreaEffectCloud(l, victima.getX(), victima.getY(), victima.getZ());
        if (duenno instanceof Player p) c.setOwner(p);
        c.setRadius(2.0F);
        c.setDuration(160);
        c.setRadiusOnUse(-0.1F);
        c.setWaitTime(5);
        c.setFixedColor(0x8A0E14);
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        l.addFreshEntity(c);
    }

    // ---------------------------------------------------------------- clic derecho
    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
        ItemStack s = j.getItemInHand(m);
        int fase = fase(s);
        CompoundTag t = datos(s);

        if (j.isShiftKeyDown()) {
            // el Diezmo: pagas con tu sangre
            if (fase < 2) return InteractionResultHolder.pass(s);
            if (t.getBoolean(COBRO)) return InteractionResultHolder.pass(s);
            if (j.getHealth() <= 5.0F) {
                if (!l.isClientSide) j.displayClientMessage(
                        Component.translatable("hacha_sangre.miscelanea.sin_sangre").withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(s);
            }
            if (!l.isClientSide) {
                j.hurt(j.damageSources().magic(), 4.0F);
                t.putBoolean(COBRO, true);
                l.playSound(null, j.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                        SoundSource.PLAYERS, 0.8F, 0.6F);
                j.displayClientMessage(Component.translatable("hacha_sangre.miscelanea.diezmo").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
        }

        // la Siega: solo con el deposito lleno
        if (fase < 1 || sangre(s) < DEPOSITO) return InteractionResultHolder.pass(s);
        if (!l.isClientSide) siega(l, j, s);
        j.swing(m, true);
        return InteractionResultHolder.sidedSuccess(s, l.isClientSide);
    }

    /** El giro: corta todo lo que hay alrededor y vacia el deposito. */
    private void siega(Level l, Player j, ItemStack s) {
        double r = 4.0;
        List<LivingEntity> cerca = l.getEntitiesOfClass(LivingEntity.class, j.getBoundingBox().inflate(r),
                e -> e != j && e.isAlive() && !(e instanceof Player p && p.isCreative()));
        for (LivingEntity e : cerca) {
            e.hurt(j.damageSources().playerAttack(j), 9.0F);
            e.knockback(0.7F, j.getX() - e.getX(), j.getZ() - e.getZ());
        }
        datos(s).putInt(SANGRE, 0);
        if (l instanceof ServerLevel sl) {
            for (int i = 0; i < 60; i++) {
                double a = i / 60.0 * Math.PI * 2;
                sl.sendParticles(ParticleTypes.CRIT, j.getX() + Math.cos(a) * r * 0.8, j.getY(0.5),
                        j.getZ() + Math.sin(a) * r * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
                sl.sendParticles(ParticleTypes.FALLING_LAVA, j.getX() + Math.cos(a) * r * 0.6, j.getY(0.9),
                        j.getZ() + Math.sin(a) * r * 0.6, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        l.playSound(null, j.blockPosition(), net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0F, 0.5F);
        j.getCooldowns().addCooldown(this, 60);
    }

    // ---------------------------------------------------------------- el hacha se seca si no bebe
    @Override
    public void inventoryTick(ItemStack s, Level l, Entity e, int ranura, boolean mano) {
        if (l.isClientSide || !s.hasTag()) return;
        CompoundTag t = s.getTag();
        if (fase(s) < 1 || t.getInt(SANGRE) <= 0) return;
        int seco = t.getInt(SECA) + 1;
        if (seco >= OLVIDO) {
            t.putInt(SANGRE, Math.max(0, t.getInt(SANGRE) - 1));
            seco = OLVIDO - 60;
        }
        t.putInt(SECA, seco);
    }

    // ---------------------------------------------------------------- lo que cuenta de si misma
    @Override
    public Component getName(ItemStack s) {
        return Component.translatable(getDescriptionId() + ".fase" + fase(s));
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        int fase = fase(s);
        lista.add(Component.translatable("hacha_sangre.miscelanea.deposito", sangre(s), DEPOSITO)
                .withStyle(sangre(s) >= DEPOSITO ? ChatFormatting.RED : ChatFormatting.DARK_RED));
        for (int i = 1; i <= fase; i++)
            lista.add(Component.translatable("hacha_sangre.miscelanea.poder" + i).withStyle(ChatFormatting.GRAY));
        if (fase < 3)
            lista.add(Component.translatable("hacha_sangre.miscelanea.falta",
                    UMBRAL[fase + 1] - bebido(s)).withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isFoil(ItemStack s) {
        return sangre(s) >= DEPOSITO;
    }

    // ---------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "hacha_sangre", 0, state -> {
            ItemStack s = state.getData(DataTickets.ITEMSTACK);
            if (s == null || fase(s) == 0) return state.setAndContinue(QUIETA);
            if (sangre(s) >= DEPOSITO) return state.setAndContinue(LLENA);
            return state.setAndContinue(LATE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer r;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (r == null) r = new com.miscelanea.client.armas.ArmaGeoRenderer<>("hacha_sangre", true);
                return r;
            }
        });
    }
}
