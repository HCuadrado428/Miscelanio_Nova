package com.miscelanea.sedante;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.tortura.Cautivos;
import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El sedante de suavis. Al pincharlo:
 * <ol>
 *   <li>La víctima se marea y va más lenta unos segundos mientras le sube el sueño. Si es un jugador, tiene un
 *       forcejeo como el del mordisco de Dream (A / D muy rápido) para "luchar contra el sueño": más difícil si se le
 *       pinchó por la espalda. Si lo supera, se le pasa (solo un poco de mareo).</li>
 *   <li>Si no lo supera (o es un mob), se queda dormido en el suelo el tiempo de la config (30 s por defecto): no se
 *       mueve, no ve casi nada y no puede usar nada. Va con el mismo "derribado" que el lazo, así que se le ve tumbado y
 *       se le puede atar con el lazo… pero eso ya es cosa vuestra.</li>
 * </ol>
 * Si te lo pinchas a ti mismo, te duermes sin forcejeo (una siesta). Un golpe fuerte (más de 4 de daño) despierta.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Sedante {
    private Sedante() {}

    /** Ticks que tarda en hacer efecto (lo que dura el forcejeo como mucho). */
    static final int SUBIDA = 6 * 20;
    /** Pinchazos en curso: víctima → ticks que faltan para dormirse. */
    private static final Map<UUID, Integer> SUBIENDO = new HashMap<>();

    public static boolean dormido(LivingEntity e) {
        return e.hasEffect(ModEffects.SEDADO.get()) && Lazo.estado(e) == Lazo.DERRIBADO;
    }

    /** Pincha a la víctima. {@code espalda}: se lo han clavado sin que lo viera venir. */
    public static void pinchar(LivingEntity v, LivingEntity quien, boolean espalda) {
        if (!(v.level() instanceof ServerLevel sl) || SUBIENDO.containsKey(v.getUUID()) || dormido(v)) return;
        sl.playSound(null, v.getX(), v.getY() + 1, v.getZ(), com.miscelanea.tortura.aparatos.AparatosRegistro.INTERROGATORIO_JERINGA.get(), SoundSource.PLAYERS, 0.9F, 1.1F);
        if (v == quien) {
            dormir(v);
            return;
        }
        v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, SUBIDA + 60, 0, false, false, true));
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SUBIDA, 2, false, false, true));
        SUBIENDO.put(v.getUUID(), SUBIDA);
        if (v instanceof ServerPlayer vp) {
            vp.displayClientMessage(Component.translatable("miscelanea.sedante.pinchado").withStyle(ChatFormatting.DARK_PURPLE), true);
            float dif = espalda ? 0.72F : 0.5F;
            if (Qte.activo(vp)) Qte.cancelar(vp);
            Qte.iniciar(vp, QteTipo.ALTERNAR, QteParams.de(dif).titulo(Component.translatable("qte.miscelanea.sedante")), (pl, r) -> {
                if (!SUBIENDO.containsKey(v.getUUID())) return;
                if (r.exito()) {
                    SUBIENDO.remove(v.getUUID());
                    v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    if (pl != null) pl.displayClientMessage(Component.translatable("miscelanea.sedante.resiste").withStyle(ChatFormatting.GREEN), true);
                } else if (r != QteResultado.CANCELADO) SUBIENDO.put(v.getUUID(), Math.min(SUBIENDO.get(v.getUUID()), 10));
            });
        }
    }

    /** A dormir: tumbado (el "derribado" del lazo) el tiempo de la config. */
    static void dormir(LivingEntity v) {
        SUBIENDO.remove(v.getUUID());
        int t = ConfigServidor.sedanteTicks();
        if (Lazo.estado(v) != 0) Lazo.limpiar(v);
        CompoundTag d = Cautivos.leer(v);
        d.putInt("Lazo", Lazo.DERRIBADO);
        d.putInt("LazoT", t);
        Cautivos.escribir(v, d);
        Cautivos.sincronizar(v);
        v.addEffect(new MobEffectInstance(ModEffects.SEDADO.get(), t, 0, false, false, true));
        v.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, t + 20, 0, false, false, false));
        v.removeEffect(MobEffects.CONFUSION);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("miscelanea.sedante.dormido").withStyle(ChatFormatting.DARK_PURPLE), true);
        v.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.8F, 0.6F);
    }

    /** Se despierta antes de tiempo (golpe fuerte). */
    public static void despertar(LivingEntity v) {
        if (!dormido(v)) return;
        v.removeEffect(ModEffects.SEDADO.get());
        v.removeEffect(MobEffects.BLINDNESS);
        Lazo.soltar(v, false);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("miscelanea.sedante.despiertas").withStyle(ChatFormatting.GREEN), true);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || SUBIENDO.isEmpty()) return;
        var srv = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;
        for (var it = SUBIENDO.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            LivingEntity v = null;
            for (ServerLevel l : srv.getAllLevels()) {
                if (l.getEntity(en.getKey()) instanceof LivingEntity le) {
                    v = le;
                    break;
                }
            }
            if (v == null || !v.isAlive()) {
                it.remove();
                continue;
            }
            int t = en.getValue() - 1;
            if (t > 0) {
                en.setValue(t);
                continue;
            }
            it.remove();
            if (v instanceof ServerPlayer vp && Qte.tipoActivo(vp) == QteTipo.ALTERNAR) Qte.cancelar(vp);
            final LivingEntity vv = v;
            Qte.despues(1, () -> dormir(vv));
        }
    }

    @SubscribeEvent
    public static void golpe(net.minecraftforge.event.entity.living.LivingDamageEvent e) {
        if (e.getAmount() > 4 && dormido(e.getEntity())) despertar(e.getEntity());
    }

    /** Cuando el "derribado" se acaba solo, se quita también el sueño. */
    @SubscribeEvent
    public static void fin(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.player.level().isClientSide) return;
        if (e.player.hasEffect(ModEffects.SEDADO.get()) && Lazo.estado(e.player) != Lazo.DERRIBADO) {
            e.player.removeEffect(ModEffects.SEDADO.get());
            e.player.removeEffect(MobEffects.BLINDNESS);
        }
    }
}
