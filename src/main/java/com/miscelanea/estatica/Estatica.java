package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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

    /** ¿Se puede agarrar / arrastrar / golpear con los poderes? (ni jefes, ni creativo/espectador, y respetando el PvP). */
    public static boolean objetivoValido(ServerPlayer p, @Nullable Entity e) {
        if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || e.isRemoved() || e.level() != p.level()) return false;
        if (e instanceof EnderDragon || e instanceof WitherBoss || e.getType().is(Tags.EntityTypes.BOSSES)) return false;
        if (e instanceof Player o) return !o.isCreative() && !o.isSpectator() && p.canHarmPlayer(o);
        return !e.isInvulnerable();
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

    public static void particulas(ServerLevel l, ParticleOptions o, Vec3 p, int n, double dx, double dy, double dz, double v) {
        l.sendParticles(o, p.x, p.y, p.z, n, dx, dy, dz, v);
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
        // fundirse en la oscuridad: quieto (sin moverse del sitio) y con poca luz
        Vec3 ant = ULTIMA_POS.put(p.getUUID(), p.position());
        boolean quieto = ant != null && ant.distanceToSqr(p.position()) < 4.0E-4 && !p.swinging;
        boolean oscuro = p.level().getMaxLocalRawBrightness(p.blockPosition().above()) <= ConfigServidor.estaticaOscuridadLuz();
        QUIETO.merge(p.getUUID(), 1, Integer::sum);
        if (!quieto || !oscuro || PantallasServidor.dentro(p)) QUIETO.put(p.getUUID(), 0);
        if (QUIETO.get(p.getUUID()) > 100000) QUIETO.put(p.getUUID(), 100000);

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
        }
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
        if (es(e.getEntity())) e.setDamageMultiplier(e.getDamageMultiplier() * ConfigServidor.estaticaCaida());
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
        if (!ConfigServidor.estaticaFragmentarse() || !(atk instanceof LivingEntity la) || s.getDirectEntity() != atk || s.is(DamageTypeTags.IS_PROJECTILE)
                || s.is(DamageTypeTags.IS_EXPLOSION) || atk == p) return;
        if (p.getPersistentData().getLong(ENF + "fragmentarse") > p.level().getGameTime()) return;
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
        float yaw = (float) (Mth.atan2(mira.z, mira.x) * Mth.RAD_TO_DEG) - 90F + 180F;
        mover(p, dest, yaw, 5F);
        EstaticaRed.cerca(l, desde.x, desde.y, desde.z, 48, new EstaticaRed.Fx(EstaticaRed.Fx.FRAGMENTO, p.getId(), desde.x, desde.y, desde.z, dest.x, dest.y, dest.z, 0));
        sonido(l, desde, EstaticaRegistro.ESTALLIDO.get(), 0.7F, 1.4F);
        sonido(l, dest, EstaticaRegistro.PARPADEO.get(), 0.9F, 0.8F);
    }

    // ------------------------------------------------------------------ toque estático (mano vacía) y romper el fundido al recibir daño
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void herir(LivingHurtEvent e) {
        LivingEntity v = e.getEntity();
        if (v instanceof Player pv && es(pv)) QUIETO.put(pv.getUUID(), 0);
        DamageSource s = e.getSource();
        if (!(s.getEntity() instanceof ServerPlayer atk) || s.getDirectEntity() != atk || !s.is(DamageTypes.PLAYER_ATTACK) || !es(atk)) return;
        if (!ConfigServidor.estaticaToque() || !atk.getMainHandItem().isEmpty()) return;
        e.setAmount(e.getAmount() + ConfigServidor.estaticaToqueDano());
        int lento = ConfigServidor.estaticaToqueLentitudTicks();
        if (lento > 0) v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, lento, 1), atk);
        ServerLevel l = atk.serverLevel();
        Vec3 c = v.getBoundingBox().getCenter();
        EstaticaRed.cerca(l, c.x, c.y, c.z, 48, EstaticaRed.Fx.en(EstaticaRed.Fx.CHISPAZO, v.getId(), c, 0));
        sonido(l, c, EstaticaRegistro.CHISPA_SONIDO.get(), 1F, 0.9F + atk.getRandom().nextFloat() * 0.2F);
        if (v instanceof ServerPlayer vp) EstaticaRed.a(vp, new EstaticaRed.Nieve(20, false, ConfigServidor.estaticaToqueCorromperTicks()));
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
