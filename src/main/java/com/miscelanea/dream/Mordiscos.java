package com.miscelanea.dream;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.qte.Qte;
import com.miscelanea.tortura.qte.QteParams;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * El mordisco venenoso de Dream (lado servidor).
 * <p>
 * Con el efecto «Mordisco venenoso», clic derecho con la mano vacía sobre un jugador o un mob lo agarra: la víctima queda
 * sujeta delante, cara a cara, y cada 1,5 s recibe un mordisco. El veneno es como la baba de un dragón de Komodo: no
 * envenena, paraliza, marea y desangra (Lentitud que sube con cada par de mordiscos, Náuseas, Desangrado y algo de
 * Debilidad, más un poco de daño de los colmillos y heridas en el cuello; partículas verde oscuro y negras). Para soltarse, la víctima (si es jugador) tiene un minijuego de forcejeo (ALTERNAR, A / D): si
 * lo supera se suelta de un empujón; si falla, el mordisco sigue y al poco sale otro (un poco más fácil cada vez). Los
 * mobs se sueltan solos al cabo de unos segundos.
 * <p>
 * Quien muerde suelta agachándose, o si le dan un golpe fuerte (ConfigServidor.mordiscoUmbral). También se acaba si se
 * le pasa el efecto, si se separan, si uno muere, se desconecta o cambia de dimensión.
 * <p>
 * Tras extraer el veneno en una botella ({@link VenenoDream}) no se puede morder durante un rato.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Mordiscos {
    private Mordiscos() {}

    /** Ticks entre mordiscos. */
    private static final int INTERVALO = 30;
    /** Primer mordisco y primer minijuego (ticks después de agarrar). */
    private static final int PRIMER_MORDISCO = 12, PRIMER_QTE = 25;
    /** Espera tras fallar el minijuego antes del siguiente. */
    private static final int REINTENTO_QTE = 40;
    /** Lo más que aguanta sujetando (por si la víctima no hace nada): 1 minuto. */
    private static final int MAX_TICKS = 60 * 20;
    /** Quien muerde no puede volver a agarrar hasta pasado esto; la víctima suelta queda a salvo un rato. */
    private static final int ENFRIAMIENTO = 60, INMUNIDAD = 100;
    private static final double DIST_MAX = 3.5;
    /** Distancia a la que se sujeta a la víctima (de centro a centro). */
    public static final double DISTANCIA = 0.62;

    public static final ResourceKey<DamageType> DANO = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, "mordisco"));

    private static final class Mordisco {
        final LivingEntity mordedor, victima;
        int ticks;
        int prox = PRIMER_MORDISCO;
        int escapeMob;
        float dificultad = 0.55F;
        boolean enQte;
        /** Mordiscos dados en este agarre (la lentitud sube con ellos). */
        int mordiscos;

        Mordisco(LivingEntity mordedor, LivingEntity victima) {
            this.mordedor = mordedor;
            this.victima = victima;
        }
    }

    private static final Map<UUID, Mordisco> POR_MORDEDOR = new HashMap<>();
    private static final Map<UUID, Mordisco> POR_VICTIMA = new HashMap<>();
    /** Tick del servidor hasta el que no se puede (mordedor: volver a morder; víctima: ser mordida). */
    private static final Map<UUID, Long> ESPERA = new HashMap<>(), INMUNE = new HashMap<>();
    private static long tick;

    public static boolean mordiendo(Entity e) {
        return POR_MORDEDOR.containsKey(e.getUUID());
    }

    public static boolean esVictima(Entity e) {
        return POR_VICTIMA.containsKey(e.getUUID());
    }

    // ------------------------------------------------------------------ empezar

    /** Lo que se mira también en el cliente (para no tragarse el clic cuando seguro que no se puede). */
    public static boolean candidato(Player p, Entity t) {
        if (!(t instanceof LivingEntity v) || !v.isAlive() || t == p || p.isSpectator()) return false;
        if (!p.getMainHandItem().isEmpty() || !p.hasEffect(DreamRegistro.MORDISCO.get())) return false;
        if (v instanceof Player vp && (vp.isCreative() || vp.isSpectator())) return false;
        if (v instanceof TamableAnimal ta && ta.isOwnedBy(p)) return false;
        return v.getBbHeight() <= 2.7F && v.getBbWidth() <= 1.5F && !v.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Por qué no se puede morder ahora (clave de texto), "" para no decir nada, o null si se puede. */
    @Nullable
    private static String motivo(ServerPlayer p, LivingEntity v) {
        if (!ConfigServidor.mordiscoActivado()) return "miscelanea.mordisco.desactivado";
        if (mordiendo(p) || esVictima(p)) return "";
        if (ESPERA.getOrDefault(p.getUUID(), 0L) > tick) return "miscelanea.mordisco.espera";
        if (v instanceof Player vp) {
            if (!ConfigServidor.mordiscoJugadores() || !p.server.isPvpAllowed() || !p.canHarmPlayer(vp)) return "miscelanea.mordisco.pvp";
            if (p.level().getDifficulty() == Difficulty.PEACEFUL) return "miscelanea.mordisco.pacifico";
        }
        if (v.isInvulnerable()) return "";
        if (esVictima(v) || mordiendo(v) || v.isVehicle() || com.miscelanea.porra.Agarres.esVictima(v) || com.miscelanea.porra.Agarres.agarrando(v)
                || com.miscelanea.tortura.Cautivos.tieneAlgo(v)) return "miscelanea.mordisco.ocupado";
        if (INMUNE.getOrDefault(v.getUUID(), 0L) > tick) return "miscelanea.mordisco.inmune";
        return null;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        Player p = e.getEntity();
        if (e.getHand() != InteractionHand.MAIN_HAND || !candidato(p, e.getTarget())) return;
        LivingEntity v = (LivingEntity) e.getTarget();
        if (p instanceof ServerPlayer sp) {
            int sinVeneno = ConfigServidor.mordiscoActivado() && !mordiendo(sp) ? VenenoDream.restante(sp) : 0;
            if (sinVeneno > 0) {
                sp.displayClientMessage(VenenoDream.agotado(sinVeneno), true);
                return;
            }
            String m = motivo(sp, v);
            if (m != null) {
                if (!m.isEmpty()) sp.displayClientMessage(Component.translatable(m), true);
                return;
            }
            empezar(sp, v);
        }
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static void empezar(ServerPlayer p, LivingEntity v) {
        Mordisco m = new Mordisco(p, v);
        m.escapeMob = 80 + p.getRandom().nextInt(120);
        com.miscelanea.logros.LogrosMiscelanea.dar(p, "mordisco");
        POR_MORDEDOR.put(p.getUUID(), m);
        POR_VICTIMA.put(v.getUUID(), m);
        if (v.isPassenger()) v.stopRiding();
        if (v instanceof Mob mob) mob.getNavigation().stop();
        if (v instanceof Player vp && vp.isUsingItem()) vp.stopUsingItem();
        ServerLevel l = (ServerLevel) p.level();
        l.playSound(null, v.getX(), v.getY(), v.getZ(), DreamRegistro.AGARRE_SND.get(), SoundSource.PLAYERS, 1.0F, 0.9F + l.random.nextFloat() * 0.2F);
        enviar(m, MordiscoPacket.Tipo.INICIO);
        if (v instanceof ServerPlayer vp) {
            vp.displayClientMessage(Component.translatable("miscelanea.mordisco.atrapado", p.getDisplayName()), true);
            programarQte(m, PRIMER_QTE);
        }
    }

    // ------------------------------------------------------------------ minijuego de la víctima

    private static boolean activo(Mordisco m) {
        return POR_MORDEDOR.get(m.mordedor.getUUID()) == m;
    }

    private static void programarQte(Mordisco m, int ticks) {
        Qte.despues(ticks, () -> iniciarQte(m));
    }

    private static void iniciarQte(Mordisco m) {
        if (!activo(m) || !(m.victima instanceof ServerPlayer vp) || m.enQte) return;
        if (Qte.activo(vp)) {
            // ya está con otro minijuego (de otro sistema): se espera a que acabe
            programarQte(m, 20);
            return;
        }
        m.enQte = true;
        Qte.iniciar(vp, QteTipo.ALTERNAR, QteParams.de(m.dificultad).titulo(Component.translatable("qte.miscelanea.mordisco")),
                (pl, r) -> {
                    m.enQte = false;
                    if (!activo(m)) return;
                    if (r.exito()) {
                        liberarse(m);
                    } else {
                        if (r != QteResultado.CANCELADO) {
                            // la adrenalina ayuda: el siguiente forcejeo es algo más fácil
                            m.dificultad = Math.max(0.2F, m.dificultad - 0.08F);
                            if (pl != null) pl.displayClientMessage(Component.translatable("miscelanea.mordisco.qte_fallo"), true);
                        }
                        programarQte(m, REINTENTO_QTE);
                    }
                });
    }

    // ------------------------------------------------------------------ terminar

    private static void terminar(Mordisco m) {
        if (!activo(m)) return;
        POR_MORDEDOR.remove(m.mordedor.getUUID());
        POR_VICTIMA.remove(m.victima.getUUID());
        ESPERA.put(m.mordedor.getUUID(), tick + ENFRIAMIENTO);
        INMUNE.put(m.victima.getUUID(), tick + INMUNIDAD);
        if (m.enQte && m.victima instanceof ServerPlayer vp) Qte.cancelar(vp);
        enviar(m, MordiscoPacket.Tipo.FIN);
    }

    /** La víctima se suelta (minijuego superado o mob que se escapa): empujón y quien muerde queda aturdido. */
    private static void liberarse(Mordisco m) {
        terminar(m);
        LivingEntity v = m.victima, p = m.mordedor;
        if (!v.isAlive() || !p.isAlive()) return;
        Vec3 dir = v.position().subtract(p.position()).multiply(1, 0, 1);
        dir = dir.lengthSqr() < 1e-4 ? p.getLookAngle().multiply(1, 0, 1).normalize() : dir.normalize();
        empujar(v, new Vec3(dir.x * 0.75, 0.35, dir.z * 0.75));
        empujar(p, new Vec3(-dir.x * 0.4, 0.15, -dir.z * 0.4));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 3, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 1, false, false, true));
        p.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.8F);
        if (v instanceof ServerPlayer vp) vp.displayClientMessage(Component.translatable("miscelanea.mordisco.libre"), true);
        com.miscelanea.logros.LogrosMiscelanea.dar(v, "escapar_mordisco");
        if (p instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("miscelanea.mordisco.escapa", v.getDisplayName()), true);
    }

    /** Quien muerde suelta (agachándose o por un golpe fuerte). */
    private static void soltar(Mordisco m, boolean golpe) {
        terminar(m);
        if (golpe && m.mordedor.isAlive()) {
            m.mordedor.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, false, true));
            if (m.mordedor instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable("miscelanea.mordisco.golpe"), true);
        }
        if (m.victima.isAlive()) {
            Vec3 dir = Vec3.directionFromRotation(0, m.mordedor.getYRot());
            empujar(m.victima, new Vec3(dir.x * 0.3, 0.1, dir.z * 0.3));
        }
    }

    private static void empujar(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
    }

    private static void terminarCon(Entity e) {
        Mordisco m = POR_MORDEDOR.get(e.getUUID());
        if (m == null) m = POR_VICTIMA.get(e.getUUID());
        if (m != null) terminar(m);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        tick++;
        if (tick % 200 == 0) {
            ESPERA.values().removeIf(t -> t <= tick);
            INMUNE.values().removeIf(t -> t <= tick);
        }
        if (POR_MORDEDOR.isEmpty()) return;
        boolean activado = ConfigServidor.mordiscoActivado();
        for (Mordisco m : new ArrayList<>(POR_MORDEDOR.values())) {
            LivingEntity p = m.mordedor, v = m.victima;
            if (!activado || !p.isAlive() || !v.isAlive() || p.isRemoved() || v.isRemoved() || p.level() != v.level()
                    || p.distanceTo(v) > DIST_MAX || !p.hasEffect(DreamRegistro.MORDISCO.get()) || p.isSpectator()
                    || (v instanceof Player vp && (vp.isCreative() || vp.isSpectator())) || m.ticks > MAX_TICKS) {
                terminar(m);
                continue;
            }
            m.ticks++;
            if (p.isShiftKeyDown() && m.ticks > 8) {
                soltar(m, false);
                continue;
            }
            sujetar(m);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, true, false, false));
            if (!(v instanceof Player) && m.ticks >= m.escapeMob) {
                liberarse(m);
                continue;
            }
            if (--m.prox <= 0) {
                m.prox = INTERVALO;
                morder((ServerLevel) p.level(), m);
            }
        }
    }

    private static void morder(ServerLevel l, Mordisco m) {
        LivingEntity v = m.victima, p = m.mordedor;
        // veneno de Komodo: paraliza (lentitud que sube cada dos mordiscos), marea, desangra y debilita
        m.mordiscos++;
        int lentitud = Math.min(ConfigServidor.mordiscoLentitudMax(), (m.mordiscos - 1) / 2);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ConfigServidor.mordiscoLentitudTicks(), lentitud), p);
        if (ConfigServidor.mordiscoNauseaTicks() > 0)
            v.addEffect(new MobEffectInstance(MobEffects.CONFUSION, ConfigServidor.mordiscoNauseaTicks(), 0), p);
        if (ConfigServidor.mordiscoDebilidadTicks() > 0)
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ConfigServidor.mordiscoDebilidadTicks(), 0), p);
        if (ConfigServidor.mordiscoDesangradoTicks() > 0)
            v.addEffect(new MobEffectInstance(DreamRegistro.DESANGRADO.get(), ConfigServidor.mordiscoDesangradoTicks(),
                    ConfigServidor.mordiscoDesangradoNivel(), false, false, true), p);
        float dano = ConfigServidor.mordiscoDano();
        if (dano > 0) {
            v.invulnerableTime = 0;
            v.hurt(new DamageSource(l.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DANO), p), dano);
            v.setDeltaMovement(Vec3.ZERO);
        }
        if (!POR_MORDEDOR.containsKey(p.getUUID())) return; // el daño ha podido matarla (y terminar el mordisco)
        // el cuello: un poco por debajo de los ojos, del lado de quien muerde
        Vec3 haciaP = p.position().subtract(v.position()).multiply(1, 0, 1);
        haciaP = haciaP.lengthSqr() < 1e-4 ? Vec3.ZERO : haciaP.normalize().scale(v.getBbWidth() * 0.4);
        Vec3 c = v.position().add(haciaP).add(0, v.getEyeHeight() - 0.25, 0);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.13F, 0.32F, 0.07F), 1.1F), c.x, c.y, c.z, 14, 0.12, 0.12, 0.12, 0.02);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.04F, 0.06F, 0.03F), 0.9F), c.x, c.y, c.z, 10, 0.1, 0.1, 0.1, 0.02);
        l.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 3, 0.05, 0.05, 0.05, 0.01);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.03F), 1.0F), c.x, c.y - 0.1, c.z, 8, 0.08, 0.15, 0.08, 0.0);
        l.playSound(null, c.x, c.y, c.z, DreamRegistro.MORDISCO_SND.get(), SoundSource.PLAYERS, 1.0F, 0.85F + l.random.nextFloat() * 0.3F);
        enviar(m, MordiscoPacket.Tipo.MORDISCO);
    }

    /** Mantiene a la víctima delante de quien muerde, cara a cara. */
    private static void sujetar(Mordisco m) {
        LivingEntity p = m.mordedor, v = m.victima;
        float yawP = p instanceof Player ? p.getYRot() : p.yBodyRot;
        Vec3 frente = Vec3.directionFromRotation(0, yawP);
        // entrada progresiva en 5 ticks (igual que en el cliente)
        float k = Math.min(1.0F, m.ticks / 5.0F);
        k = k * k * (3.0F - 2.0F * k);
        double dist = 1.0 + (DISTANCIA + v.getBbWidth() * 0.5 - 0.3 - 1.0) * k;
        Vec3 punto = p.position().add(frente.scale(dist));
        float yaw = yawP + 180.0F;
        v.fallDistance = 0.0F;
        v.setDeltaMovement(Vec3.ZERO);
        if (v instanceof ServerPlayer sp) {
            if (m.ticks <= 5 || m.ticks % 10 == 1 || sp.position().distanceToSqr(punto) > 0.5)
                sp.connection.teleport(punto.x, punto.y, punto.z, yaw, 5.0F);
        } else {
            v.moveTo(punto.x, punto.y, punto.z, yaw, 0.0F);
            v.setYHeadRot(yaw);
            v.setYBodyRot(yaw);
            if (v instanceof Mob mob) mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent e) {
        LivingEntity herido = e.getEntity();
        if (herido.level().isClientSide) return;
        Mordisco m = POR_MORDEDOR.get(herido.getUUID());
        if (m != null && e.getAmount() >= ConfigServidor.mordiscoUmbral() && e.getSource().getEntity() != herido) soltar(m, true);
    }

    /** La víctima no puede pegar (ni un mob sujeto puede atacar). */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent e) {
        Entity a = e.getSource().getDirectEntity();
        if (a != null && !a.level().isClientSide && esVictima(a)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent e) {
        if (esVictima(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (esVictima(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (esVictima(e.getEntity())) {
            e.setUseBlock(Event.Result.DENY);
            e.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        terminarCon(e.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        terminarCon(e.getEntity());
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        terminarCon(e.getEntity());
    }

    /** Quien empieza a ver a alguno de los dos a mitad de mordisco también ve las poses. */
    @SubscribeEvent
    public static void onTracking(PlayerEvent.StartTracking e) {
        Mordisco m = POR_MORDEDOR.get(e.getTarget().getUUID());
        if (m == null) m = POR_VICTIMA.get(e.getTarget().getUUID());
        if (m != null && e.getEntity() instanceof ServerPlayer sp)
            DreamRed.a(sp, new MordiscoPacket(MordiscoPacket.Tipo.INICIO, m.mordedor.getId(), m.victima.getId()));
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        POR_MORDEDOR.clear();
        POR_VICTIMA.clear();
        ESPERA.clear();
        INMUNE.clear();
    }

    private static void enviar(Mordisco m, MordiscoPacket.Tipo tipo) {
        MordiscoPacket pkt = new MordiscoPacket(tipo, m.mordedor.getId(), m.victima.getId());
        // quien ve a quien muerde (la víctima siempre lo ve: está pegada a él) y él mismo
        DreamRed.alrededor(m.mordedor, pkt);
    }
}
