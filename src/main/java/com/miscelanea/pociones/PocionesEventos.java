package com.miscelanea.pociones;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de las pociones del cuerpo (servidor, y lo que hace falta en los dos lados):
 * <ul>
 *   <li>Espejismo: al ponerse elige a quién imitar; el estado va a los clientes cada segundo ({@link EstadoPacket}).</li>
 *   <li>Sombra: pose de reptar forzada (caja 0,5 x 0,5), nada de usar, pegar, romper ni poner; sin daño de caída.</li>
 *   <li>Gigante / enano: caja y ojos a escala ({@link Tamanos}), salto, empujón x3, pisotón al aterrizar; el enano
 *       recibe x1,5 de daño.</li>
 *   <li>Sed de sangre: con algo en la mano no se pega; a puño limpio 50 de daño, empujón enorme y, si mata, la víctima
 *       revienta (partículas en el cliente). Mina x1,5 más rápido. Golpe de música al ponerse.</li>
 *   <li>Niebla viviente: los mobs no te ponen en el punto de mira a más de 3 bloques.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PocionesEventos {
    private PocionesEventos() {}

    private static final String CLAVE_AIRE = "MiscelaneaGigAire", CLAVE_CAIDA = "MiscelaneaGigCaida", CLAVE_REVENTAR = "MiscelaneaReventar";
    public static final float DANO_PUNO = 50F;

    private static boolean sombra(Entity e) {
        return e instanceof LivingEntity v && v.hasEffect(PocionesRegistro.SOMBRA.get());
    }

    private static boolean sed(Entity e) {
        return e instanceof LivingEntity v && v.hasEffect(PocionesRegistro.SED_DE_SANGRE.get());
    }

    /** Sed de sangre a puño limpio (nada en la mano principal). */
    private static boolean punoSediento(Entity e) {
        return sed(e) && ((LivingEntity) e).getMainHandItem().isEmpty();
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.isAlive()) return;
        Tamanos.tick(v);
        if (v.level().isClientSide) return;
        if ((v.tickCount + v.getId()) % 20 == 0 && Espejismos.tieneAlgo(v)) Espejismos.enviarATodos(v, null);
        if (Tamanos.pasosPorEfectos(v) > 0) pisoton(v);
    }

    /** Gigante: al aterrizar tras un salto (o una caída) de un bloque o más, pisotón. */
    private static void pisoton(LivingEntity v) {
        CompoundTag d = v.getPersistentData();
        boolean enAire = !v.onGround();
        if (!enAire && d.getBoolean(CLAVE_AIRE) && d.getFloat(CLAVE_CAIDA) >= 1.0F && v.level() instanceof ServerLevel sl) {
            int pasos = Tamanos.pasosPorEfectos(v);
            double radio = 3 + (pasos - 1) * 1.5;
            Vec3 c = v.position();
            for (LivingEntity t : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radio, 2, radio), t -> t != v && t.isAlive() && !t.isSpectator())) {
                if (t.distanceToSqr(c) > radio * radio) continue;
                Vec3 dir = t.position().subtract(c);
                dir = new Vec3(dir.x, 0, dir.z);
                dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
                t.hurt(v instanceof Player p ? sl.damageSources().playerAttack(p) : sl.damageSources().mobAttack(v), 4F * pasos);
                t.setDeltaMovement(dir.x * 0.9, 0.55 + 0.12 * pasos, dir.z * 0.9);
                t.hurtMarked = true;
            }
            BlockPos suelo = BlockPos.containing(c.x, c.y - 0.2, c.z);
            BlockState bs = sl.getBlockState(suelo);
            if (!bs.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, bs), c.x, c.y + 0.1, c.z, 30 + 20 * pasos, radio * 0.5, 0.15, radio * 0.5, 0.2);
            sl.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.2, c.z, 10 * pasos, radio * 0.4, 0.1, radio * 0.4, 0.05);
            sl.playSound(null, c.x, c.y, c.z, PocionesRegistro.PISOTON.get(), SoundSource.PLAYERS, 1.2F + 0.3F * pasos, 1.1F - 0.15F * pasos);
        }
        d.putBoolean(CLAVE_AIRE, enAire);
        if (enAire) d.putFloat(CLAVE_CAIDA, v.fallDistance);
    }

    /** Sombra: la pose de reptar en los dos lados (después de que Player.tick la haya calculado). */
    @SubscribeEvent
    public static void tickJugador(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Player p = e.player;
        if (sombra(p) && !p.isSleeping() && !p.isFallFlying() && !p.isPassenger() && !p.isSpectator() && p.getPose() != Pose.SWIMMING)
            p.setPose(Pose.SWIMMING);
    }

    // ------------------------------------------------------------------ caja y ojos
    @SubscribeEvent
    public static void tamano(EntityEvent.Size e) {
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        boolean sombra;
        float escala;
        try {
            sombra = v instanceof Player && v.hasEffect(PocionesRegistro.SOMBRA.get());
            escala = Tamanos.escala(v);
        } catch (RuntimeException ex) {
            return;   // la entidad aún no está lista
        }
        if (!sombra && escala == 1F) return;
        EntityDimensions base = e.getNewSize();
        float ojos = e.getNewEyeHeight();
        if (sombra) {
            base = EntityDimensions.fixed(0.5F, 0.5F);
            ojos = 0.4F;
        }
        e.setNewSize(base.scale(escala));
        e.setNewEyeHeight(ojos * escala);
    }

    // ------------------------------------------------------------------ al ponerse / quitarse
    @SubscribeEvent
    public static void puesto(MobEffectEvent.Added e) {
        LivingEntity v = e.getEntity();
        MobEffectInstance inst = e.getEffectInstance();
        if (v.level().isClientSide || inst == null) return;
        MobEffect ef = inst.getEffect();
        if (ef == PocionesRegistro.ESPEJISMO.get() && v instanceof ServerPlayer sp) {
            java.util.UUID obj = Espejismos.objetivo(sp);
            if (obj == null || obj.equals(sp.getUUID())) Espejismos.elegir(sp);
        } else if (ef == PocionesRegistro.SED_DE_SANGRE.get()) {
            v.level().playSound(null, v.getX(), v.getY(), v.getZ(), PocionesRegistro.BERSERK.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        if (ef == PocionesRegistro.ESPEJISMO.get() || ef == PocionesRegistro.SOMBRA.get() || ef == PocionesRegistro.NIEBLA_VIVIENTE.get()
                || ef == PocionesRegistro.GIGANTE.get() || ef == PocionesRegistro.ENANO.get())
            Espejismos.enviarATodos(v, null);
    }

    @SubscribeEvent
    public static void quitado(MobEffectEvent.Remove e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide && esNuestro(e.getEffect())) {
            Espejismos.enviarATodos(v, e.getEffect());
            if (e.getEffect() == PocionesRegistro.ESPEJISMO.get()) v.getPersistentData().remove(Espejismos.CLAVE);
        }
    }

    @SubscribeEvent
    public static void caducado(MobEffectEvent.Expired e) {
        LivingEntity v = e.getEntity();
        MobEffectInstance inst = e.getEffectInstance();
        if (!v.level().isClientSide && inst != null && esNuestro(inst.getEffect())) {
            Espejismos.enviarATodos(v, inst.getEffect());
            if (inst.getEffect() == PocionesRegistro.ESPEJISMO.get()) v.getPersistentData().remove(Espejismos.CLAVE);
        }
    }

    private static boolean esNuestro(MobEffect ef) {
        return ef == PocionesRegistro.ESPEJISMO.get() || ef == PocionesRegistro.SOMBRA.get() || ef == PocionesRegistro.NIEBLA_VIVIENTE.get()
                || ef == PocionesRegistro.GIGANTE.get() || ef == PocionesRegistro.ENANO.get();
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity t && e.getEntity() instanceof ServerPlayer quien && Espejismos.tieneAlgo(t))
            Espejismos.enviarA(t, quien);
    }

    // ------------------------------------------------------------------ sombra: no puede hacer nada con las manos
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void usarObjeto(PlayerInteractEvent.RightClickItem e) {
        if (sombra(e.getEntity())) cancelar(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void usarBloque(PlayerInteractEvent.RightClickBlock e) {
        if (sombra(e.getEntity())) cancelar(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void picarBloque(PlayerInteractEvent.LeftClickBlock e) {
        if (sombra(e.getEntity())) cancelar(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void usarEntidad(PlayerInteractEvent.EntityInteract e) {
        if (sombra(e.getEntity())) cancelar(e);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void usarEntidadPunto(PlayerInteractEvent.EntityInteractSpecific e) {
        if (sombra(e.getEntity())) cancelar(e);
    }

    private static void cancelar(PlayerInteractEvent e) {
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void romper(BlockEvent.BreakEvent e) {
        if (sombra(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void poner(BlockEvent.EntityPlaceEvent e) {
        if (sombra(e.getEntity())) e.setCanceled(true);
    }

    /** Sombra: no pega. Sed de sangre: con algo en la mano, el golpe no sale (solo a puño limpio). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void atacar(AttackEntityEvent e) {
        Player p = e.getEntity();
        if (sombra(p) || (sed(p) && !p.getMainHandItem().isEmpty())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void caer(LivingFallEvent e) {
        LivingEntity v = e.getEntity();
        if (sombra(v)) {
            e.setCanceled(true);
            return;
        }
        float s = Tamanos.escala(v);
        if (s > 1F) e.setDistance(e.getDistance() / s);   // el gigante cae "menos" a su escala
    }

    // ------------------------------------------------------------------ daño
    @SubscribeEvent
    public static void herir(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Entity a = e.getSource().getEntity();
        if (a instanceof Player p && e.getSource().getDirectEntity() == p && punoSediento(p)) e.setAmount(Math.max(e.getAmount(), DANO_PUNO));
        if (Tamanos.pasosPorEfectos(v) < 0) e.setAmount(e.getAmount() * 1.5F);
    }

    /** Empujón: x3 del gigante, x5 del puñetazo sediento (el atacante es el último que le ha pegado, ahora mismo). */
    @SubscribeEvent
    public static void empujon(LivingKnockBackEvent e) {
        LivingEntity v = e.getEntity();
        LivingEntity a = v.getLastHurtByMob();
        if (a == null || v.getLastHurtByMobTimestamp() < v.tickCount - 1) return;
        float k = 1F;
        if (Tamanos.pasosPorEfectos(a) > 0) k *= 3F;
        if (punoSediento(a)) k *= 5F;
        if (k != 1F) e.setStrength(e.getStrength() * k);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        Entity a = e.getSource().getEntity();
        if (a instanceof Player p && e.getSource().getDirectEntity() == p && punoSediento(p)) v.getPersistentData().putBoolean(CLAVE_REVENTAR, true);
    }

    @SubscribeEvent
    public static void botin(LivingDropsEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !v.getPersistentData().getBoolean(CLAVE_REVENTAR)) return;
        v.getPersistentData().remove(CLAVE_REVENTAR);
        List<ItemStack> trozos = new ArrayList<>();
        for (ItemEntity it : e.getDrops()) {
            if (trozos.size() >= 8) break;
            trozos.add(it.getItem().copy());
        }
        PocionesRed.alrededor(v, new ReventarPacket(v.getX(), v.getY(), v.getZ(), v.getBbWidth(), v.getBbHeight(), trozos));
    }

    @SubscribeEvent
    public static void velocidadRomper(PlayerEvent.BreakSpeed e) {
        if (sed(e.getEntity())) e.setNewSpeed(e.getNewSpeed() * 1.5F);
    }

    // ------------------------------------------------------------------ salto del gigante (los dos lados)
    @SubscribeEvent
    public static void saltar(LivingEvent.LivingJumpEvent e) {
        LivingEntity v = e.getEntity();
        int pasos = Tamanos.pasos(v);
        if (pasos <= 0) return;
        Vec3 d = v.getDeltaMovement();
        v.setDeltaMovement(d.x, d.y * Math.sqrt(Tamanos.escala(pasos)), d.z);
    }

    // ------------------------------------------------------------------ niebla viviente: no te ven de lejos
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cambiarObjetivo(LivingChangeTargetEvent e) {
        LivingEntity t = e.getNewTarget();
        if (t != null && e.getEntity() instanceof Mob m && t.hasEffect(PocionesRegistro.NIEBLA_VIVIENTE.get()) && m.distanceToSqr(t) > 9.0) e.setCanceled(true);
    }
}
