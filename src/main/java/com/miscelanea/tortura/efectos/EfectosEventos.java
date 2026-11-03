package com.miscelanea.tortura.efectos;

import com.miscelanea.Miscelanea;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos del servidor del collar explosivo y de la toxina del miedo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class EfectosEventos {
    private EfectosEventos() {}

    /** Dosis del miedo: cuándo se subió la última vez (tiempo del mundo) y la marca de "subir en el próximo tick". */
    private static final String CLAVE_DOSIS = "MiscelaneaMiedoDosis", CLAVE_SUBIR = "MiscelaneaMiedoSubir";
    /** Entre dos subidas de dosis tiene que pasar este tiempo (la nube de gas aplica el efecto cada segundo). */
    private static final int MARGEN_DOSIS = 100;
    public static final int DOSIS_MAX = 2;

    // ------------------------------------------------------------------ ticks
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !v.isAlive()) return;
        if (Collares.tiene(v)) Collares.tick(v);
        if (v.hasEffect(EfectosRegistro.MIEDO.get())) subirDosis(v);
    }

    // ------------------------------------------------------------------ miedo: las dosis se acumulan (I → II → III)
    /**
     * Otra dosis de toxina con el miedo ya puesto (otro dardo, seguir respirando el gas): si han pasado
     * {@link #MARGEN_DOSIS} ticks desde la última subida, se marca para subir el nivel en el próximo tick (dentro del
     * evento no se puede cambiar el efecto que se está añadiendo).
     */
    @SubscribeEvent
    public static void dosis(MobEffectEvent.Added e) {
        LivingEntity v = e.getEntity();
        MobEffectInstance nuevo = e.getEffectInstance();
        if (v.level().isClientSide || nuevo == null || nuevo.getEffect() != EfectosRegistro.MIEDO.get()) return;
        CompoundTag d = v.getPersistentData();
        long ahora = v.level().getGameTime();
        MobEffectInstance viejo = e.getOldEffectInstance();
        if (viejo == null) {
            d.putLong(CLAVE_DOSIS, ahora);
            return;
        }
        if (ahora - d.getLong(CLAVE_DOSIS) < MARGEN_DOSIS || viejo.getAmplifier() >= DOSIS_MAX || nuevo.getAmplifier() > viejo.getAmplifier()) return;
        d.putLong(CLAVE_DOSIS, ahora);
        d.putBoolean(CLAVE_SUBIR, true);
    }

    private static void subirDosis(LivingEntity v) {
        CompoundTag d = v.getPersistentData();
        if (!d.getBoolean(CLAVE_SUBIR)) return;
        d.remove(CLAVE_SUBIR);
        MobEffectInstance m = v.getEffect(EfectosRegistro.MIEDO.get());
        if (m == null || m.getAmplifier() >= DOSIS_MAX) return;
        int dur = m.isInfiniteDuration() ? MobEffectInstance.INFINITE_DURATION : Math.max(m.getDuration(), 600) + 200;
        v.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), dur, m.getAmplifier() + 1));
    }

    // ------------------------------------------------------------------ poner el collar, alicates sobre otro
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        if (e.isCanceled() || !(e.getTarget() instanceof LivingEntity t) || e.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = e.getEntity();
        ItemStack st = e.getItemStack();
        boolean servidor = !p.level().isClientSide;
        if (st.getItem() instanceof CollarItem) {
            if (servidor) Collares.poner(p, t, st);
        } else if (st.getItem() instanceof AlicatesItem) {
            if (servidor && p instanceof ServerPlayer sp) Collares.cortarCables(sp, t);
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ------------------------------------------------------------------ morir, entrar, salir, dimensión, cuerpo nuevo
    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide) Collares.borrar(v);
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !Collares.tiene(p)) return;
        // con la cuenta final en marcha no se escapa nadie; si solo estaba en alarma, al volver empieza de cero
        if (Collares.detonando(p)) Collares.estallarYa(p);
        else {
            var d = Collares.leer(p);
            d.remove("Alarma");
            p.getPersistentData().put(Collares.CLAVE, d);
        }
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Collares.tiene(p)) Collares.sincronizar(p);
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Collares.sincronizar(p);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Collares.sincronizar(p);
    }

    /** Volver del End (sin morir) crea un cuerpo nuevo: el collar va con él. Al morir no (ya se borró). */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        Player viejo = e.getOriginal();
        if (viejo.getPersistentData().contains(Collares.CLAVE))
            e.getEntity().getPersistentData().put(Collares.CLAVE, viejo.getPersistentData().getCompound(Collares.CLAVE).copy());
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity t && e.getEntity() instanceof ServerPlayer quien && Collares.tiene(t))
            Collares.sincronizarCon(t, quien);
    }

    // ------------------------------------------------------------------ miedo: los mobs no atacan
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cambiarObjetivo(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof Mob && e.getEntity().hasEffect(EfectosRegistro.MIEDO.get())) e.setCanceled(true);
    }
}
