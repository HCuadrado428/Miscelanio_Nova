package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import net.minecraftforge.entity.PartEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lado del servidor del hombre de estática: quién lo es (y con qué disposición del cuerpo), la sincronización con los
 * clientes, el moveset (sin armas, toque estático, caídas suaves, fragmentarse, fundirse en la oscuridad) y el tick de
 * los poderes que duran (pantallas, luces, charcos, tentáculos y tareas con retraso).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Estatica {
    private Estatica() {}

    private static final String SEMILLA = "MiscEstSemilla", CINE = "MiscEstCineHasta", ENF = "MiscEstEnf_", AVISO = "MiscEstAviso";
    /** Últimas banderas mandadas de cada jugador (para mandar solo los cambios, más un repaso cada 2 s). */
    private static final Map<UUID, Integer> ENVIADO = new HashMap<>();
    /** Fundirse en la oscuridad: ticks quieto a oscuras. */
    private static final Map<UUID, Integer> QUIETO = new HashMap<>();
    private static final Map<UUID, Vec3> ULTIMA_POS = new HashMap<>();
    /** Tareas con retraso (arrastrar...). */
    private static final List<Tarea> TAREAS = new ArrayList<>();

    private record Tarea(long cuando, Runnable r) {}

    // ------------------------------------------------------------------ consultas
    public static boolean es(@Nullable Entity e) {
        return e instanceof LivingEntity le && le.hasEffect(EstaticaRegistro.EFECTO.get());
    }

    public static int semilla(Player p) {
        return p.getPersistentData().getInt(SEMILLA);
    }

    public static boolean enCinematica(Player p) {
        return p.getPersistentData().getLong(CINE) > p.level().getGameTime();
    }

    public static boolean fundido(Player p) {
        Integer q = QUIETO.get(p.getUUID());
        return q != null && q >= Math.max(1, ConfigServidor.estaticaOscuridadTicks()) && ConfigServidor.estaticaOscuridad() && es(p);
    }

    /** ¿Es un arma? (las del mod van en la etiqueta miscelanea:armas). */
    public static boolean esArma(ItemStack st) {
        if (st.isEmpty()) return false;
        return st.is(EstaticaRegistro.ARMAS) || st.getItem() instanceof SwordItem || st.getItem() instanceof AxeItem || st.getItem() instanceof TridentItem
                || st.getItem() instanceof ProjectileWeaponItem || st.getItem() instanceof ShieldItem;
    }

    public static boolean jefe(@Nullable Entity e) {
        return e instanceof EnderDragon || e instanceof WitherBoss || (e != null && e.getType().is(Tags.EntityTypes.BOSSES));
    }

    /**
     * ¿Se puede golpear con los poderes? (ni creativo/espectador, respetando el PvP; los jefes solo si su factor no es 0).
     * Para agarrar, arrastrar o aturdir mira además {@link #agarrable}.
     */
    public static boolean objetivoValido(ServerPlayer p, @Nullable Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || e.isRemoved() || e.level() != p.level()) return false;
        if (jefe(e) && ConfigServidor.estaticaJefesFactor() <= 0) return false;
        if (e instanceof Player o) return !o.isCreative() && !o.isSpectator() && p.canHarmPlayer(o);
        if (e instanceof net.minecraft.world.entity.decoration.ArmorStand) return false;
        if (e instanceof net.minecraft.world.entity.TamableAnimal ta && ta.isOwnedBy(p)) return false;
        return !e.isInvulnerable();
    }

    /** ¿Se puede agarrar con los tentáculos / arrastrar / aturdir? (los jefes, solo si se permite en la configuración). */
    public static boolean agarrable(ServerPlayer p, @Nullable Entity e) {
        return objetivoValido(p, e) && (!jefe(e) || ConfigServidor.estaticaJefesAgarrables());
    }

    // ------------------------------------------------------------------ daño de estática (atraviesa armadura / protección)
    private static ResourceKey<DamageType> tipo(String n) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(Miscelanea.MOD_ID, n));
    }

    public static final ResourceKey<DamageType> DANO = tipo("estatica"), DANO_PERFORANTE = tipo("estatica_perforante"), DANO_PURO = tipo("estatica_pura");

    public static boolean danoEstatico(DamageSource s) {
        return s.is(DANO) || s.is(DANO_PERFORANTE) || s.is(DANO_PURO);
    }

    public static DamageSource fuente(ServerPlayer p) {
        var k = switch (ConfigServidor.estaticaPerforacion()) {
            case 0 -> DANO;
            case 1 -> DANO_PERFORANTE;
            default -> DANO_PURO;
        };
        return p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolder(k)
                .map(h -> new DamageSource(h, p)).orElseGet(() -> p.damageSources().playerAttack(p));
    }

    /** Factor de los jefes (1 con el resto). */
    public static float factor(Entity e) {
        return jefe(e) ? ConfigServidor.estaticaJefesFactor() : 1F;
    }

    /** Daño de estática del jugador a 'v' (con el factor de jefes; las partes de un ser grande reenvían el daño a su dueño). */
    public static boolean golpear(ServerPlayer p, Entity v, float dano) {
        Entity dueño = v instanceof PartEntity<?> pe ? pe.getParent() : v;
        float d = dano * factor(dueño);
        if (d <= 0) return false;
        return v.hurt(fuente(p), d);
    }

    /** Empuje horizontal (y algo hacia arriba) respetando la resistencia al empuje. */
    public static void empujar(LivingEntity v, Vec3 dir, double fuerza, double arriba) {
        double k = (1 - v.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)) * factor(v);
        if (k <= 0) return;
        Vec3 h = new Vec3(dir.x, 0, dir.z);
        h = h.lengthSqr() < 1.0E-6 ? Vec3.ZERO : h.normalize().scale(fuerza * k);
        Vec3 m = v.getDeltaMovement();
        v.setDeltaMovement(m.x * 0.3 + h.x, Math.max(m.y, arriba * k), m.z * 0.3 + h.z);
        v.hasImpulse = true;
        v.hurtMarked = true;
    }

    /** Efecto sin remolino de partículas ni icono raro (las víctimas tampoco sueltan partículas de estática). */
    public static void efecto(LivingEntity v, net.minecraft.world.effect.MobEffect ef, int ticks, int nivel, @Nullable Entity de) {
        int t = Math.round(ticks * factor(v));
        if (t > 0) v.addEffect(new MobEffectInstance(ef, t, nivel, false, false, true), de);
    }

    // ------------------------------------------------------------------ interferencia (cargas) y aturdimiento
    private record Interf(int n, long hasta) {}

    private static final Map<UUID, Interf> INTERF = new HashMap<>();
    /** Aturdidos: entidad → tick en que se acaba. */
    private static final Map<LivingEntity, Long> ATURDIDOS = new java.util.WeakHashMap<>();

    public static boolean aturdido(LivingEntity e) {
        Long h = ATURDIDOS.get(e);
        return h != null && h > e.level().getGameTime();
    }

    /** Suma 'n' cargas de interferencia: lentitud y debilidad crecientes; al llenarse, aturde un momento y se vacía. */
    public static void interferir(ServerPlayer p, LivingEntity v, int n) {
        if (!v.isAlive()) return;
        long t = v.level().getGameTime();
        int max = ConfigServidor.estaticaInterferenciaMax();
        Interf a = INTERF.get(v.getUUID());
        int c = (a == null || a.hasta() < t ? 0 : a.n()) + n;
        int dur = ConfigServidor.estaticaInterferenciaTicks();
        if (c >= max && ConfigServidor.estaticaAturdirTicks() > 0 && agarrable(p, v)) {
            INTERF.remove(v.getUUID());
            aturdir(p, v, ConfigServidor.estaticaAturdirTicks());
            return;
        }
        c = Math.min(c, max);
        INTERF.put(v.getUUID(), new Interf(c, t + dur));
        efecto(v, MobEffects.MOVEMENT_SLOWDOWN, dur, c - 1, p);
        efecto(v, MobEffects.WEAKNESS, dur, c - 1, p);
    }

    public static void aturdir(ServerPlayer p, LivingEntity v, int ticks) {
        ServerLevel l = p.serverLevel();
        ATURDIDOS.put(v, l.getGameTime() + ticks);
        efecto(v, MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, p);
        efecto(v, MobEffects.WEAKNESS, ticks, 4, p);
        efecto(v, MobEffects.DIG_SLOWDOWN, ticks, 3, p);
        if (v instanceof Mob m) m.getNavigation().stop();
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(ticks, true, 0));
        Vec3 c = v.getBoundingBox().getCenter();
        EstaticaRed.cerca(l, c.x, c.y, c.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.ATURDIDO, v.getId(), c, ticks));
        sonido(l, c, EstaticaRegistro.ESTALLIDO.get(), 0.7F, 1.6F);
    }

    private static void tickAturdidos(long t) {
        if (!ATURDIDOS.isEmpty()) {
            for (Iterator<Map.Entry<LivingEntity, Long>> it = ATURDIDOS.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                LivingEntity v = en.getKey();
                if (!v.isAlive() || v.isRemoved() || en.getValue() <= v.level().getGameTime()) {
                    it.remove();
                    continue;
                }
                Vec3 m = v.getDeltaMovement();
                v.setDeltaMovement(0, Math.min(m.y, 0), 0);
                if (v instanceof Mob mob) mob.getNavigation().stop();
                if (v instanceof ServerPlayer) v.hurtMarked = true;
            }
        }
        if (t % 100 == 0) INTERF.values().removeIf(a -> a.hasta() < t);
    }

    // ------------------------------------------------------------------ enfriamientos y avisos
    public static boolean listo(ServerPlayer p, Poder pd) {
        long falta = p.getPersistentData().getLong(ENF + pd.id) - p.level().getGameTime();
        if (falta <= 0) return true;
        aviso(p, Component.translatable("estatica.miscelanea.espera", Component.translatable(pd.clave()), String.format("%.1f", falta / 20F)));
        return false;
    }

    public static void gastar(ServerPlayer p, Poder pd) {
        p.getPersistentData().putLong(ENF + pd.id, p.level().getGameTime() + pd.enfriamiento());
    }

    /** Mensaje en la barra de acción, como mucho uno por segundo. */
    public static void aviso(Player p, Component c) {
        long t = p.level().getGameTime();
        if (t - p.getPersistentData().getLong(AVISO) < 20) return;
        p.getPersistentData().putLong(AVISO, t);
        p.displayClientMessage(c, true);
    }

    public static void despues(int ticks, Runnable r, long ahora) {
        TAREAS.add(new Tarea(ahora + ticks, r));
    }

    // ------------------------------------------------------------------ utilidades de los poderes
    public static void sonido(ServerLevel l, Vec3 p, SoundEvent s, float vol, float tono) {
        l.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, tono);
    }

    /**
     * El sitio más lejano de 'desde' hacia 'dir' (hasta 'max' bloques) donde cabe la entidad sin atravesar bloques.
     * Devuelve 'desde' si no cabe en ningún otro.
     */
    public static Vec3 libre(ServerLevel l, Entity e, Vec3 desde, Vec3 dir, double max) {
        if (dir.lengthSqr() < 1.0E-6) return desde;
        dir = dir.normalize();
        double h = e.getBbHeight() * 0.5;
        Vec3 a = desde.add(0, h, 0), b = a.add(dir.scale(max));
        BlockHitResult hit = l.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, e));
        double lim = hit.getType() == HitResult.Type.MISS ? max : Math.max(0, hit.getLocation().distanceTo(a) - e.getBbWidth() * 0.6);
        for (double d = lim; d > 0.2; d -= 0.25) {
            Vec3 q = desde.add(dir.scale(d));
            if (cabe(l, e, q)) return q;
            if (cabe(l, e, q.add(0, 1, 0))) return q.add(0, 1, 0);
        }
        return desde;
    }

    public static boolean cabe(ServerLevel l, Entity e, Vec3 pos) {
        AABB caja = e.getBoundingBox().move(pos.subtract(e.position()));
        return l.noCollision(e, caja) && !l.containsAnyLiquid(caja.deflate(0.1));
    }

    public static void mover(Entity e, Vec3 pos, float yaw, float pitch) {
        if (e instanceof ServerPlayer sp) sp.connection.teleport(pos.x, pos.y, pos.z, yaw, pitch);
        else {
            e.moveTo(pos.x, pos.y, pos.z, yaw, pitch);
            e.setYHeadRot(yaw);
        }
        e.fallDistance = 0;
    }

    /** El primer ser vivo válido en la mirada (hasta 'alcance'), sin atravesar bloques; prueba antes la pista del cliente. */
    @Nullable
    public static LivingEntity mirado(ServerPlayer p, double alcance, int pista) {
        ServerLevel l = p.serverLevel();
        Vec3 ojo = p.getEyePosition(), fin = ojo.add(p.getLookAngle().scale(alcance));
        BlockHitResult bl = l.clip(new ClipContext(ojo, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        double lim = bl.getType() == HitResult.Type.MISS ? alcance : bl.getLocation().distanceTo(ojo);
        Entity h = l.getEntity(pista);
        if (h != null && objetivoValido(p, h) && h.distanceTo(p) <= alcance + 1.5) {
            Vec3 c = h.getBoundingBox().getCenter();
            BlockHitResult ver = l.clip(new ClipContext(ojo, c, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (ver.getType() == HitResult.Type.MISS || ver.getLocation().distanceToSqr(ojo) >= c.distanceToSqr(ojo) - 0.5) return (LivingEntity) h;
        }
        LivingEntity mejor = null;
        double md = lim;
        for (Entity e : l.getEntities(p, p.getBoundingBox().expandTowards(p.getLookAngle().scale(alcance)).inflate(1.5), x -> objetivoValido(p, x))) {
            var c = e.getBoundingBox().inflate(0.4).clip(ojo, fin);
            if (c.isEmpty()) continue;
            double d = c.get().distanceTo(ojo);
            if (d < md) {
                md = d;
                mejor = (LivingEntity) e;
            }
        }
        return mejor;
    }

    // ------------------------------------------------------------------ transformación y sincronización
    /** Al beber: la cinemática para todos los que lo ven (y para él). */
    public static void transformar(ServerPlayer p) {
        // cada vez que se bebe, disposición nueva (lado de la cara y trozo del cuerpo), aunque ya fuera de estática
        p.getPersistentData().putInt(SEMILLA, p.getRandom().nextInt());
        if (!ConfigServidor.estaticaCinematica()) {
            sincronizar(p);
            return;
        }
        p.getPersistentData().putLong(CINE, p.level().getGameTime() + 112);
        sincronizar(p);
        EstaticaRed.alrededor(p, new EstaticaRed.Cine(p.getId(), semilla(p), 0, p.getX(), p.getY(), p.getZ(), p.yBodyRot));
    }

    private static int banderas(ServerPlayer p) {
        if (!es(p)) return 0;
        int b = EstaticaRed.Estado.ACTIVO;
        if (PantallasServidor.dentro(p)) b |= EstaticaRed.Estado.DENTRO;
        if (fundido(p)) b |= EstaticaRed.Estado.FUNDIDO;
        return b;
    }

    public static void sincronizar(ServerPlayer p) {
        int b = banderas(p);
        ENVIADO.put(p.getUUID(), b);
        EstaticaRed.alrededor(p, new EstaticaRed.Estado(p.getId(), semilla(p), b));
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer quien && es(t))
            EstaticaRed.a(quien, new EstaticaRed.Estado(t.getId(), semilla(t), banderas(t)));
    }

    @SubscribeEvent
    public static void efectoPuesto(MobEffectEvent.Added e) {
        if (e.getEffectInstance().getEffect() != EstaticaRegistro.EFECTO.get() || !(e.getEntity() instanceof ServerPlayer p)) return;
        // disposición nueva cada vez que se transforma (el lado de la cara y el trozo de cuerpo)
        if (e.getOldEffectInstance() == null) p.getPersistentData().putInt(SEMILLA, p.getRandom().nextInt());
        ENVIADO.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void efectoQuitado(MobEffectEvent.Remove e) {
        if (e.getEffectInstance() != null && e.getEffectInstance().getEffect() == EstaticaRegistro.EFECTO.get() && e.getEntity() instanceof ServerPlayer p) acabar(p);
    }

    @SubscribeEvent
    public static void efectoAcabado(MobEffectEvent.Expired e) {
        if (e.getEffectInstance() != null && e.getEffectInstance().getEffect() == EstaticaRegistro.EFECTO.get() && e.getEntity() instanceof ServerPlayer p) acabar(p);
    }

    private static void acabar(ServerPlayer p) {
        PantallasServidor.salir(p, -1);
        QUIETO.remove(p.getUUID());
        Tentaculos.soltarDe(p);
        ENVIADO.remove(p.getUUID());
        // el efecto aún no se ha quitado del todo en este evento: se manda "nada" a mano
        EstaticaRed.alrededor(p, new EstaticaRed.Estado(p.getId(), semilla(p), 0));
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            PantallasServidor.salir(p, -1);
            Tentaculos.soltarDe(p);
            ENVIADO.remove(p.getUUID());
            QUIETO.remove(p.getUUID());
            ULTIMA_POS.remove(p.getUUID());
            Charcos.olvidar(p.getUUID());
        }
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            p.getPersistentData().remove(CINE);
            ENVIADO.remove(p.getUUID());
            EstaticaRed.alrededor(p, new EstaticaRed.Estado(p.getId(), semilla(p), banderas(p)));
        }
    }

    @SubscribeEvent
    public static void parar(ServerStoppingEvent e) {
        Luces.restaurarTodo(e.getServer());
        TAREAS.clear();
        Tentaculos.limpiar();
        Charcos.limpiar();
        INTERF.clear();
        ATURDIDOS.clear();
        ENVIADO.clear();
        QUIETO.clear();
    }

    // ------------------------------------------------------------------ ticks
    @SubscribeEvent
    public static void tickJugador(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        boolean es = es(p);
        if (!es) {
            if (PantallasServidor.dentro(p)) PantallasServidor.salir(p, -1);
            Integer v = ENVIADO.get(p.getUUID());
            if (v != null && v != 0) sincronizar(p);
            return;
        }
        long t = p.level().getGameTime();
        // la estática nunca suelta el remolino de partículas de las pociones (también si se puso con /effect sin hideParticles)
        MobEffectInstance ins = p.getEffect(EstaticaRegistro.EFECTO.get());
        if (ins != null && ins.isVisible())
            p.addEffect(new MobEffectInstance(EstaticaRegistro.EFECTO.get(), ins.getDuration(), ins.getAmplifier(), ins.isAmbient(), false, ins.showIcon()));
        // fundirse en la oscuridad: quieto (sin moverse del sitio) y con poca luz
        Vec3 ant = ULTIMA_POS.put(p.getUUID(), p.position());
        boolean quieto = ant != null && ant.distanceToSqr(p.position()) < 4.0E-4 && !p.swinging;
        boolean oscuro = p.level().getMaxLocalRawBrightness(p.blockPosition().above()) <= ConfigServidor.estaticaOscuridadLuz();
        QUIETO.merge(p.getUUID(), 1, Integer::sum);
        if (!quieto || !oscuro || PantallasServidor.dentro(p)) QUIETO.put(p.getUUID(), 0);
        if (QUIETO.get(p.getUUID()) > 100000) QUIETO.put(p.getUUID(), 100000);
        // se cura a oscuras
        float regen = ConfigServidor.estaticaRegeneracion();
        if (regen > 0 && oscuro && t % 20 == p.getId() % 20 && p.getHealth() < p.getMaxHealth() && !p.isDeadOrDying()) p.heal(regen);

        PantallasServidor.tick(p);
        if (t % 10 == p.getId() % 10 && ConfigServidor.estaticaLuces() && !PantallasServidor.dentro(p)) Luces.apagarCerca(p);
        if (ConfigServidor.estaticaCharcos() && !PantallasServidor.dentro(p)) Charcos.tickJugador(p);

        Integer v = ENVIADO.get(p.getUUID());
        int b = banderas(p);
        if (v == null || v != b || t % 40 == p.getId() % 40) sincronizar(p);
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var srv = e.getServer();
        long t = srv.overworld().getGameTime();
        if (!TAREAS.isEmpty()) {
            List<Tarea> ya = new ArrayList<>();
            for (Iterator<Tarea> it = TAREAS.iterator(); it.hasNext(); ) {
                Tarea ta = it.next();
                if (ta.cuando() <= t) {
                    ya.add(ta);
                    it.remove();
                }
            }
            for (Tarea ta : ya) {
                try {
                    ta.r().run();
                } catch (RuntimeException ex) {
                    Miscelanea.LOGGER.warn("[miscelanea] Fallo en una tarea de la estática", ex);
                }
            }
        }
        Tentaculos.tick(srv);
        tickAturdidos(t);
        if (t % 4 == 0) Charcos.tick(srv);
        if (t % 20 == 0) Luces.tick(srv);
    }

    // ------------------------------------------------------------------ moveset: sin armas
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void atacar(AttackEntityEvent e) {
        Player p = e.getEntity();
        if (!es(p)) return;
        QUIETO.put(p.getUUID(), 0);
        if (enCinematica(p) || (p instanceof ServerPlayer sp && PantallasServidor.dentro(sp))) {
            e.setCanceled(true);
            return;
        }
        if (esArma(p.getMainHandItem())) {
            e.setCanceled(true);
            if (!p.level().isClientSide) aviso(p, Component.translatable("estatica.miscelanea.sin_armas"));
            return;
        }
        if (!ConfigServidor.estaticaToque() || !p.getMainHandItem().isEmpty()) return;
        Entity t = e.getTarget();
        Entity dueño = t instanceof PartEntity<?> pe ? pe.getParent() : t;
        if (!(dueño instanceof LivingEntity) || !t.isAttackable() || t.skipAttackInteraction(p)) return;
        // el puñetazo de estática sustituye al golpe normal (en los dos lados; el cliente ya ha mandado el ataque)
        e.setCanceled(true);
        if (p instanceof ServerPlayer sp) punetazo(sp, t, (LivingEntity) dueño);
    }

    /** Puñetazo de estática: daño perforante, empuje, interferencia y una descarga que salta a los de alrededor. */
    private static void punetazo(ServerPlayer p, Entity t, LivingEntity v) {
        float carga = p.getAttackStrengthScale(0.5F);
        p.resetAttackStrengthTicker();
        if (dueñoInvalido(p, v)) return;
        // Fuerza / debilidad cuentan (el ataque base de la mano es 1)
        float base = ConfigServidor.estaticaToqueDano() + (float) Math.max(-ConfigServidor.estaticaToqueDano(), p.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1);
        float dano = base * (0.2F + carga * carga * 0.8F);
        boolean critico = carga > 0.9F && p.fallDistance > 0 && !p.onGround() && !p.onClimbable() && !p.isInWater();
        if (critico) dano *= 1.5F;
        float vidaAntes = v.getHealth();
        if (!golpear(p, t, dano)) return;
        p.setLastHurtMob(v);
        ServerLevel l = p.serverLevel();
        Vec3 c = v.getBoundingBox().getCenter();
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        empujar(v, new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), ConfigServidor.estaticaPunoEmpuje() * (0.5 + 0.5 * carga), 0.25 + 0.15 * carga);
        if (carga > 0.5F) interferir(p, v, 1);
        EstaticaRed.cerca(l, c.x, c.y, c.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, v.getId(), c, critico ? 1 : 0));
        sonido(l, c, EstaticaRegistro.CHISPA_SONIDO.get(), 1F, 0.9F + p.getRandom().nextFloat() * 0.2F);
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(20, false, ConfigServidor.estaticaToqueCorromperTicks()));
        p.causeFoodExhaustion(0.1F);
        if (vidaAntes > v.getHealth()) p.awardStat(net.minecraft.stats.Stats.DAMAGE_DEALT, Math.round((vidaAntes - v.getHealth()) * 10F));
        // la descarga salta a los más cercanos
        int n = carga > 0.5F ? ConfigServidor.estaticaPunoCadena() : 0;
        if (n <= 0) return;
        double r = ConfigServidor.estaticaPunoCadenaRadio();
        List<LivingEntity> cerca = l.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(r),
                x -> x != v && objetivoValido(p, x) && x.distanceTo(v) <= r && x.hasLineOfSight(v));
        cerca.sort(java.util.Comparator.comparingDouble(x -> x.distanceToSqr(v)));
        Vec3 desde = c;
        for (int i = 0; i < Math.min(n, cerca.size()); i++) {
            LivingEntity o = cerca.get(i);
            Vec3 oc = o.getBoundingBox().getCenter();
            EstaticaRed.cerca(l, oc.x, oc.y, oc.z, 48, new EstaticaRed.Fx(EstaticaRed.Fx.ARCO, o.getId(), desde.x, desde.y, desde.z, oc.x, oc.y, oc.z, 0));
            if (golpear(p, o, dano * ConfigServidor.estaticaPunoCadenaDano())) {
                interferir(p, o, 1);
                empujar(o, oc.subtract(desde), 0.35, 0.2);
                if (o instanceof ServerPlayer op) EstaticaRed.a(op, new EstaticaRed.Nieve(12, false, 0));
            }
            desde = oc;
        }
        sonido(l, c, EstaticaRegistro.CRUJIDO.get(), 0.8F, 1.3F);
    }

    private static boolean dueñoInvalido(ServerPlayer p, LivingEntity v) {
        if (v == p || !v.isAlive()) return true;
        if (v instanceof Player o) return o.isCreative() || o.isSpectator() || !p.canHarmPlayer(o);
        return jefe(v) && ConfigServidor.estaticaJefesFactor() <= 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void usarObjeto(PlayerInteractEvent.RightClickItem e) {
        if (!es(e.getEntity())) return;
        if (esArma(e.getItemStack()) || enCinematica(e.getEntity())) {
            e.setCanceled(true);
            e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            if (!e.getLevel().isClientSide && esArma(e.getItemStack())) aviso(e.getEntity(), Component.translatable("estatica.miscelanea.sin_armas"));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void empezarUso(LivingEntityUseItemEvent.Start e) {
        if (es(e.getEntity()) && esArma(e.getItem())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ caídas suaves
    @SubscribeEvent
    public static void caer(LivingFallEvent e) {
        if (!es(e.getEntity())) return;
        if (ConfigServidor.estaticaCaida() <= 0) e.setCanceled(true);
        else e.setDamageMultiplier(e.getDamageMultiplier() * ConfigServidor.estaticaCaida());
    }

    // ------------------------------------------------------------------ golpes: invulnerable en pantallas/cinemática, fragmentarse
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void recibirAtaque(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !es(p)) return;
        if (PantallasServidor.dentro(p) || enCinematica(p)) {
            if (!e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
            return;
        }
        DamageSource s = e.getSource();
        Entity atk = s.getEntity();
        if (!ConfigServidor.estaticaFragmentarse() || REBOTE || s.is(DamageTypeTags.IS_EXPLOSION) || atk == p) return;
        if (p.getPersistentData().getLong(ENF + "fragmentarse") > p.level().getGameTime()) return;
        if (s.getDirectEntity() instanceof Projectile pr) {
            if (ConfigServidor.estaticaFragmentarseProyectiles()) esquivar(e, p, pr);
            return;
        }
        if (!(atk instanceof LivingEntity la) || s.getDirectEntity() != atk || s.is(DamageTypeTags.IS_PROJECTILE)) return;
        // detrás del atacante, mirándolo
        ServerLevel l = p.serverLevel();
        Vec3 mira = new Vec3(la.getLookAngle().x, 0, la.getLookAngle().z);
        if (mira.lengthSqr() < 1.0E-4) mira = new Vec3(0, 0, 1);
        mira = mira.normalize();
        Vec3 detras = la.position().subtract(mira.scale(1.6));
        Vec3 dest = cabe(l, p, detras) ? detras : cabe(l, p, detras.add(0, 1, 0)) ? detras.add(0, 1, 0) : null;
        if (dest == null) return;
        e.setCanceled(true);
        p.getPersistentData().putLong(ENF + "fragmentarse", p.level().getGameTime() + ConfigServidor.estaticaFragmentarseEnfriamiento());
        Vec3 desde = p.position();
        float yaw = (float) (Mth.atan2(mira.z, mira.x) * Mth.RAD_TO_DEG) - 90F;   // mirando hacia la espalda del atacante
        mover(p, dest, yaw, 5F);
        EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 48, new EstaticaRed.Fx(EstaticaRed.Fx.FRAGMENTO, p.getId(), desde.x, desde.y, desde.z, dest.x, dest.y, dest.z, 0));
        sonido(l, desde, EstaticaRegistro.ESTALLIDO.get(), 0.7F, 1.4F);
        sonido(l, dest, EstaticaRegistro.PARPADEO.get(), 0.9F, 0.8F);
        // y le devuelve una descarga
        float d = ConfigServidor.estaticaFragmentarseDano();
        if (d > 0 && objetivoValido(p, la)) {
            REBOTE = true;
            try {
                if (golpear(p, la, d)) interferir(p, la, 1);
            } finally {
                REBOTE = false;
            }
            Vec3 c = la.getBoundingBox().getCenter();
            EstaticaRed.cerca(l, c.x, c.y, c.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, la.getId(), c, 0));
        }
    }

    /** Mientras devuelve la descarga de Fragmentarse (para que dos de estática no se la devuelvan sin fin). */
    private static boolean REBOTE;

    /** Fragmentarse contra un proyectil: se deshace y aparece unos bloques a un lado de su trayectoria. */
    private static void esquivar(LivingAttackEvent e, ServerPlayer p, Projectile pr) {
        ServerLevel l = p.serverLevel();
        Vec3 v = pr.getDeltaMovement();
        Vec3 lado = new Vec3(-v.z, 0, v.x);
        if (lado.lengthSqr() < 1.0E-6) lado = new Vec3(1, 0, 0);
        lado = lado.normalize();
        if (p.getRandom().nextBoolean()) lado = lado.scale(-1);
        Vec3 desde = p.position();
        Vec3 dest = libre(l, p, desde, lado, 2.5);
        if (dest.distanceToSqr(desde) < 1.0) dest = libre(l, p, desde, lado.scale(-1), 2.5);
        if (dest.distanceToSqr(desde) < 1.0) return;
        e.setCanceled(true);
        p.getPersistentData().putLong(ENF + "fragmentarse", p.level().getGameTime() + ConfigServidor.estaticaFragmentarseEnfriamiento());
        mover(p, dest, p.getYRot(), p.getXRot());
        EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 48, new EstaticaRed.Fx(EstaticaRed.Fx.FRAGMENTO, p.getId(), desde.x, desde.y, desde.z, dest.x, dest.y, dest.z, 0));
        sonido(l, desde, EstaticaRegistro.ESTALLIDO.get(), 0.6F, 1.6F);
    }

    // ------------------------------------------------------------------ resistencia pasiva y romper el fundido al recibir daño
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void herir(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (!(v instanceof Player pv) || !es(pv)) return;
        QUIETO.put(pv.getUUID(), 0);
        float red = ConfigServidor.estaticaReduccion();
        if (red > 0 && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setAmount(e.getAmount() * (1 - red));
    }

    /** Para quien quiera saber si un jugador está quieto a oscuras (el fundido se pierde al atacar o usar poderes). */
    public static void despertar(Player p) {
        QUIETO.put(p.getUUID(), 0);
    }

    /** Lo que se guarda en el jugador (para depurar): semilla y fin de la cinemática. */
    public static CompoundTag datos(Player p) {
        CompoundTag t = new CompoundTag();
        t.putInt(SEMILLA, semilla(p));
        t.putLong(CINE, p.getPersistentData().getLong(CINE));
        return t;
    }
}
