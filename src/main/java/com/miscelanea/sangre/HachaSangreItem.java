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
import com.miscelanea.registry.ModSounds;
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
 *   <li><b>Insaciable:</b> el Diezmo sale gratis una vez por minuto y los charcos dan velocidad a su dueno.</li>
 * </ul>
 */
public class HachaSangreItem extends AxeItem implements GeoItem {

    public static final String SANGRE = "Sangre", BEBIDO = "Bebido", COBRO = "Cobro", SECA = "SinBeberDesde", LIBRE = "DiezmoLibre";
    private static final int COLOR_CHARCO = 0x8A0E14;
    /** Cuanta sangre cabe en el corazon. */
    public static final int DEPOSITO = 20;

    private static final RawAnimation QUIETA = RawAnimation.begin().thenLoop("animation.hacha_sangre.quieta");
    private static final RawAnimation LATE = RawAnimation.begin().thenLoop("animation.hacha_sangre.late");
    private static final RawAnimation LLENA = RawAnimation.begin().thenLoop("animation.hacha_sangre.llena");
    private static final RawAnimation GOLPE = RawAnimation.begin().thenPlay("animation.hacha_sangre.golpe");
    private static final RawAnimation SIEGA = RawAnimation.begin().thenPlay("animation.hacha_sangre.siega");
    private static final RawAnimation DIEZMO = RawAnimation.begin().thenPlay("animation.hacha_sangre.diezmo");
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

    /** En que fase esta: 0 seca, 1 sedienta, 2 cobradora, 3 viva, 4 insaciable. */
    public static int fase(ItemStack s) {
        int b = bebido(s);
        int f = 0;
        for (int i = 1; i <= 4; i++) if (b >= HachaSangreConfig.umbral(i)) f = i;
        return f;
    }

    private static int nivel(ItemStack s, net.minecraft.world.item.enchantment.Enchantment e) {
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(e, s);
    }

    /** Un golpe extra nunca mata de un tiro a un jugador con la vida llena (si la config lo pide). */
    private static float tope(LivingEntity v, float dano) {
        if (v instanceof Player && HachaSangreConfig.topeJugadores() && v.getHealth() >= v.getMaxHealth() - 0.01F)
            return Math.min(dano, v.getMaxHealth() - 1.0F);
        return dano;
    }

    private static void beber(ItemStack s, int cuanto, float dano) {
        cuanto = Math.max(1, Math.round(cuanto * (1.0F + 0.5F * nivel(s, com.miscelanea.registry.ModEnchantments.SANGRE_SED.get()))));
        CompoundTag t = datos(s);
        t.putInt(SANGRE, Math.min(DEPOSITO, t.getInt(SANGRE) + cuanto));
        t.putInt(BEBIDO, t.getInt(BEBIDO) + Math.max(1, (int) dano));
        t.putInt(SECA, 0);
    }

    /** Lanza una animacion del hacha en todos los clientes que la ven. */
    private void animar(ItemStack s, LivingEntity quien, String cual) {
        if (quien.level() instanceof ServerLevel sl)
            triggerAnim(quien, GeoItem.getOrAssignId(s, sl), "hacha_sangre", cual);
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
            victima.hurt(quien.damageSources().playerAttack(quien instanceof Player p ? p : null),
                    tope(victima, dano * HachaSangreConfig.diezmoExtra()));
            if (l instanceof ServerLevel sl)
                sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victima.getX(), victima.getY(0.6), victima.getZ(), 14, 0.4, 0.4, 0.4, 0.1);
            if (!victima.isAlive() && quien instanceof Player p) {
                p.heal(HachaSangreConfig.diezmoCura());
                t.putInt(SANGRE, DEPOSITO);
                com.miscelanea.logros.LogrosMiscelanea.dar(p, "hacha_sangre_diezmo");
            }
        }

        // contra alguien que ya sangra (Desangrado) bebe mas
        int cuanto = fase >= 1 ? 2 : 1;
        if (victima.hasEffect(com.miscelanea.dream.DreamRegistro.DESANGRADO.get())) cuanto *= HachaSangreConfig.sinergia();
        beber(s, cuanto, dano);
        int hemorragia = nivel(s, com.miscelanea.registry.ModEnchantments.SANGRE_HEMORRAGIA.get());
        if (hemorragia > 0)
            victima.addEffect(new MobEffectInstance(com.miscelanea.dream.DreamRegistro.DESANGRADO.get(), (3 + 2 * hemorragia) * 20, 0));
        animar(s, quien, "golpe");
        float lleno = sangre(s) / (float) DEPOSITO;
        l.playSound(null, victima.blockPosition(), ModSounds.HS_TRAGO.get(), SoundSource.PLAYERS,
                0.7F, 0.85F + lleno * 0.4F);
        if (fase(s) > fase && quien instanceof Player p) {        // acaba de despertar una fase
            p.displayClientMessage(Component.translatable("hacha_sangre.miscelanea.despierta",
                    Component.translatable(getDescriptionId() + ".fase" + fase(s))).withStyle(ChatFormatting.DARK_RED), false);
            l.playSound(null, p.blockPosition(), ModSounds.HS_LATIDO.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
            com.miscelanea.logros.LogrosMiscelanea.dar(p, "hacha_sangre_fase" + fase(s));
        }

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
        c.setRadius(HachaSangreConfig.charcoRadio());
        c.setDuration(HachaSangreConfig.charcoTicks());
        c.setRadiusOnUse(-0.1F);
        c.setWaitTime(5);
        c.setFixedColor(COLOR_CHARCO);
        c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
        c.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
        l.addFreshEntity(c);
        l.playSound(null, victima.blockPosition(), ModSounds.HS_CHARCO.get(), SoundSource.PLAYERS, 0.8F, 0.95F);
    }

    // ---------------------------------------------------------------- clic derecho
    @Override
    public InteractionResultHolder<ItemStack> use(Level l, Player j, InteractionHand m) {
        ItemStack s = j.getItemInHand(m);
        int fase = fase(s);
        CompoundTag t = datos(s);

        if (j.isShiftKeyDown()) {
            // el Diezmo: pagas con tu sangre (en la fase 4, una vez cada cierto tiempo sale gratis)
            if (fase < 2) return InteractionResultHolder.pass(s);
            if (t.getBoolean(COBRO)) return InteractionResultHolder.pass(s);
            boolean libre = fase >= 4 && l.getGameTime() - t.getLong(LIBRE) >= HachaSangreConfig.libreTicks();
            if (!libre && j.getHealth() <= HachaSangreConfig.diezmoCoste() + 1.0F) {
                if (!l.isClientSide) j.displayClientMessage(
                        Component.translatable("hacha_sangre.miscelanea.sin_sangre").withStyle(ChatFormatting.DARK_RED), true);
                return InteractionResultHolder.fail(s);
            }
            if (!l.isClientSide) {
                if (libre) t.putLong(LIBRE, l.getGameTime());
                else j.hurt(j.damageSources().magic(), HachaSangreConfig.diezmoCoste());
                t.putBoolean(COBRO, true);
                animar(s, j, "diezmo");
                l.playSound(null, j.blockPosition(), ModSounds.HS_DIEZMO.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
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
        double r = HachaSangreConfig.siegaRadio();
        List<LivingEntity> cerca = l.getEntitiesOfClass(LivingEntity.class, j.getBoundingBox().inflate(r),
                e -> e != j && e.isAlive() && !(e instanceof Player p && p.isCreative()));
        for (LivingEntity e : cerca) {
            e.hurt(j.damageSources().playerAttack(j), tope(e, HachaSangreConfig.siegaDano()));
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
        animar(s, j, "siega");
        l.playSound(null, j.blockPosition(), ModSounds.HS_SIEGA.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        com.miscelanea.logros.LogrosMiscelanea.dar(j, "hacha_sangre_siega");
        j.getCooldowns().addCooldown(this, HachaSangreConfig.siegaEnfriamientoTicks());
    }

    // ---------------------------------------------------------------- el hacha se seca si no bebe
    @Override
    public void inventoryTick(ItemStack s, Level l, Entity e, int ranura, boolean mano) {
        if (l.isClientSide || !s.hasTag()) return;
        CompoundTag t = s.getTag();
        if (fase(s) < 1 || t.getInt(SANGRE) <= 0) return;
        // el corazon se oye latir en la mano, mas rapido cuanta mas sangre lleva
        if (mano && e instanceof Player p) {
            int ritmo = Math.max(18, 56 - t.getInt(SANGRE) * 2);
            if (p.tickCount % ritmo == 0)
                l.playSound(null, p.blockPosition(), ModSounds.HS_LATIDO.get(), SoundSource.PLAYERS,
                        0.28F + t.getInt(SANGRE) * 0.012F, 0.95F);
        }
        // gotas de sangre en la mano: mas densas cuanto mas deposito y mas grandes en la fase 4
        if (mano && e instanceof Player p && HachaSangreConfig.efectos() && l instanceof ServerLevel sl && p.tickCount % 4 == 0
                && l.random.nextInt(DEPOSITO) < t.getInt(SANGRE)) {
            boolean insaciable = fase(s) >= 4;
            var m = p.getLookAngle();
            sl.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.54F, 0.05F, 0.08F), insaciable ? 1.4F : 0.9F),
                    p.getX() + m.x * 0.7, p.getEyeY() - 0.45, p.getZ() + m.z * 0.7, insaciable ? 3 : 1, 0.12, 0.05, 0.12, 0.0);
        }
        int seco = t.getInt(SECA) + 1;
        int olvido = HachaSangreConfig.olvidoTicks() + 300 * nivel(s, com.miscelanea.registry.ModEnchantments.SANGRE_COAGULO.get());
        if (seco >= olvido) {
            t.putInt(SANGRE, Math.max(0, t.getInt(SANGRE) - 1));
            seco = olvido - 60;
        }
        t.putInt(SECA, seco);
        // fase viva: quien lleva el hacha se cura en sus charcos y no se frena en ellos (en la fase 4, ademas corre)
        if (fase(s) >= 3 && e instanceof Player p && p.tickCount % 10 == 0) {
            var charcos = l.getEntitiesOfClass(AreaEffectCloud.class, p.getBoundingBox().inflate(0.3),
                    c -> c.getOwner() == p && c.getColor() == COLOR_CHARCO);
            if (!charcos.isEmpty()) {
                p.heal(HachaSangreConfig.charcoCura());
                for (var eff : List.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.WEAKNESS)) {
                    MobEffectInstance m = p.getEffect(eff);
                    if (m != null && m.getDuration() <= 60) p.removeEffect(eff);
                }
                if (fase(s) >= 4) p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false));
            }
        }
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
        if (fase < 4)
            lista.add(Component.translatable("hacha_sangre.miscelanea.falta",
                    HachaSangreConfig.umbral(fase + 1) - bebido(s)).withStyle(ChatFormatting.DARK_GRAY));
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
        }).triggerableAnim("golpe", GOLPE).triggerableAnim("siega", SIEGA).triggerableAnim("diezmo", DIEZMO));
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
                if (r == null) r = new com.miscelanea.client.armas.HachaSangreRenderer();
                return r;
            }
        });
    }
}
