package com.miscelanea.aspira;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Reserva de efectos POSITIVOS guardada en la mochila.
 * Funciona como un faro: el efecto se pone solo mientras hace falta (corres, te quemas, te falta vida...)
 * y se va gastando la reserva solo durante ese tiempo. Si no hay reserva de ese efecto, no pasa nada.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Reserva {
    private static final String RESERVA = "Reserva";
    /** Máximo acumulable por efecto: 20 minutos. */
    private static final int MAX_TICKS = 24000;
    /** Se revisa cada 5 ticks y se gastan 5 ticks de reserva por revisión activa. */
    private static final int INTERVALO = 5;
    private static final String CD_INSTANT = Miscelanea.MOD_ID + ":reserva_instant";

    public record Entrada(MobEffect efecto, int amplificador, int ticks) {}

    private Reserva() {}

    // ------------------------------------------------------------------ datos

    /**
     * Añade reserva de un efecto. En efectos instantáneos (curación) "ticks" son dosis.
     * Si ya había, se suma; si llega con más potencia, se queda la potencia mayor.
     */
    public static boolean anadir(ItemStack mochila, MobEffect efecto, int amplificador, int ticks) {
        if (!efecto.isBeneficial() || ticks <= 0) return false;
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(efecto);
        if (id == null) return false;
        ListTag list = mochila.getOrCreateTag().getList(RESERVA, Tag.TAG_COMPOUND);
        int max = efecto.isInstantenous() ? 10 : MAX_TICKS;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            if (t.getString("Id").equals(id.toString())) {
                t.putInt("T", Math.min(max, t.getInt("T") + ticks));
                t.putInt("Amp", Math.max(t.getInt("Amp"), amplificador));
                mochila.getTag().put(RESERVA, list);
                return true;
            }
        }
        CompoundTag t = new CompoundTag();
        t.putString("Id", id.toString());
        t.putInt("Amp", amplificador);
        t.putInt("T", Math.min(max, ticks));
        list.add(t);
        mochila.getTag().put(RESERVA, list);
        return true;
    }

    public static List<Entrada> leer(ItemStack mochila) {
        List<Entrada> out = new ArrayList<>();
        if (!mochila.hasTag()) return out;
        ListTag list = mochila.getTag().getList(RESERVA, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(t.getString("Id")));
            if (ef != null) out.add(new Entrada(ef, t.getInt("Amp"), t.getInt("T")));
        }
        return out;
    }

    // ------------------------------------------------------------------ uso automático

    /** ¿Le hace falta este efecto al jugador ahora mismo? */
    private static boolean necesita(Player p, MobEffect ef) {
        if (ef == MobEffects.MOVEMENT_SPEED || ef == MobEffects.JUMP) return p.isSprinting();
        if (ef == MobEffects.REGENERATION) return p.getHealth() < p.getMaxHealth();
        if (ef == MobEffects.HEAL) return p.getHealth() <= p.getMaxHealth() * 0.4F;
        if (ef == MobEffects.FIRE_RESISTANCE) return p.isOnFire() || p.isInLava();
        if (ef == MobEffects.WATER_BREATHING) return p.isUnderWater() && p.getAirSupply() < p.getMaxAirSupply();
        if (ef == MobEffects.DOLPHINS_GRACE || ef == MobEffects.CONDUIT_POWER) return p.isInWater();
        if (ef == MobEffects.NIGHT_VISION)
            return p.level().getMaxLocalRawBrightness(BlockPos.containing(p.getEyePosition())) < 7;
        if (ef == MobEffects.SLOW_FALLING) return p.fallDistance > 4.0F;
        if (ef == MobEffects.DAMAGE_RESISTANCE || ef == MobEffects.ABSORPTION || ef == MobEffects.HEALTH_BOOST)
            return p.hurtTime > 0 || p.getHealth() < p.getMaxHealth() * 0.5F;
        if (ef == MobEffects.DAMAGE_BOOST) return p.tickCount - p.getLastHurtMobTimestamp() < 60;
        if (ef == MobEffects.DIG_SPEED) return p.swinging && p.pick(5.0, 1.0F, false).getType() == HitResult.Type.BLOCK;
        if (ef == MobEffects.INVISIBILITY) return p.isCrouching();
        if (ef == MobEffects.SATURATION) return p.getFoodData().needsFood();
        if (ef == MobEffects.LUCK) return p.fishing != null;
        // Cualquier otro efecto positivo: en combate
        return p.hurtTime > 0 || p.tickCount - p.getLastHurtMobTimestamp() < 60;
    }

    /** Duración con la que se aplica cada vez (como un faro, se va refrescando). */
    private static int duracionAplicada(MobEffect ef) {
        if (ef == MobEffects.REGENERATION) return 60;   // tiene que pasar por múltiplos de 50 para curar
        if (ef == MobEffects.NIGHT_VISION) return 220;  // por debajo de 200 parpadea
        return 30;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player p = event.player;
        if (event.phase != TickEvent.Phase.END || p.level().isClientSide || p.tickCount % INTERVALO != 0) return;
        ItemStack mochila = Deposito.mochila(p);
        if (mochila.isEmpty() || !mochila.hasTag() || !mochila.getTag().contains(RESERVA)) return;

        ListTag list = mochila.getTag().getList(RESERVA, Tag.TAG_COMPOUND);
        boolean cambiado = false;
        for (int i = list.size() - 1; i >= 0; i--) {
            CompoundTag t = list.getCompound(i);
            MobEffect ef = ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(t.getString("Id")));
            if (ef == null) {
                list.remove(i);
                cambiado = true;
                continue;
            }
            if (!necesita(p, ef)) continue;
            int amp = t.getInt("Amp");
            int ticks = t.getInt("T");

            if (ef.isInstantenous()) {
                // Dosis: una cada 2 segundos como mucho
                long ahora = p.level().getGameTime();
                if (p.getPersistentData().getLong(CD_INSTANT) > ahora) continue;
                p.getPersistentData().putLong(CD_INSTANT, ahora + 40);
                ef.applyInstantenousEffect(null, null, p, amp, 1.0);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.4F, 1.6F);
                ticks -= 1;
            } else {
                MobEffectInstance actual = p.getEffect(ef);
                // Si ya lo tiene de otra fuente (más fuerte o largo), no se gasta la reserva
                if (actual != null && (actual.getAmplifier() > amp || (!actual.isAmbient() && actual.getDuration() > 100))) continue;
                int dur = duracionAplicada(ef);
                if (actual == null || actual.getDuration() < dur / 2) {
                    if (actual == null) {
                        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.3F, 1.4F);
                    }
                    // ambient = true: partículas suaves como las de un faro
                    p.addEffect(new MobEffectInstance(ef, dur, amp, true, true, true));
                }
                ticks -= INTERVALO;
            }

            if (ticks <= 0) list.remove(i);
            else t.putInt("T", ticks);
            cambiado = true;
        }
        if (cambiado) mochila.getTag().put(RESERVA, list);
    }
}
