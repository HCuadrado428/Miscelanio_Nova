package com.miscelanea.porra;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.AgarrePacket;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModDamageTypes;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Agarres con la porra eléctrica (lado servidor).
 * La víctima queda fija delante del agresor, recibe descargas periódicas y puede forcejear para soltarse.
 * Otro jugador puede liberarla golpeando al agresor. Si le queda poca vida, el agresor puede ejecutarla.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Agarres {
    public static final double ALCANCE = 3.0;
    private static final int DURACION_MAX = 7 * 20;
    private static final int INTERVALO_DESCARGA = 30;
    private static final float COSTE_DESCARGA = 8.0F;
    private static final float DANO_DESCARGA = 3.0F;
    /** Ticks que quedan bloqueados los ítems de uso de la víctima tras una descarga del agarre. */
    private static final int BLOQUEO_AGARRE = 200;
    /** Y tras un golpe normal con la porra. */
    private static final int BLOQUEO_GOLPE = 100;
    private static final float COSTE_GOLPE = 3.0F;
    private static final float UMBRAL_EJECUCION = 0.3F;
    private static final int DURACION_EJECUCION = 45;
    private static final float RESISTENCIA_MAX = 100.0F;
    private static final String CD_CHOQUE = Miscelanea.MOD_ID + ":cd_choque";

    private static final class Agarre {
        final Player agresor;
        final LivingEntity victima;
        int ticks;
        int proxDescarga = 15;
        int ejecucion = -1;
        float resistencia = RESISTENCIA_MAX;
        int ultimoForcejeo = -100;

        Agarre(Player agresor, LivingEntity victima) {
            this.agresor = agresor;
            this.victima = victima;
        }
    }

    private static final Map<UUID, Agarre> POR_AGRESOR = new HashMap<>();
    private static final Map<UUID, Agarre> POR_VICTIMA = new HashMap<>();

    private Agarres() {}

    public static boolean esVictima(Entity e) {
        return POR_VICTIMA.containsKey(e.getUUID());
    }

    public static boolean agarrando(Entity e) {
        return POR_AGRESOR.containsKey(e.getUUID());
    }

    private static boolean llevaPorra(Player p) {
        return p.getMainHandItem().is(ModItems.PORRA.get());
    }

    // ------------------------------------------------------------------ inicio / fin

    /** Clic derecho con la porra. */
    public static void usarPorra(Player p) {
        Agarre a = POR_AGRESOR.get(p.getUUID());
        if (a != null) {
            if (a.ejecucion >= 0) return;
            if (a.victima.getHealth() <= a.victima.getMaxHealth() * UMBRAL_EJECUCION) iniciarEjecucion(a);
            else soltar(a, true);
            return;
        }
        if (esVictima(p) || !BateriaItem.tiene(p, COSTE_DESCARGA)) {
            fallo(p);
            return;
        }
        LivingEntity objetivo = objetivo(p);
        if (objetivo != null) agarrar(p, objetivo);
    }

    @Nullable
    private static LivingEntity objetivo(Player p) {
        Vec3 ojo = p.getEyePosition();
        Vec3 fin = ojo.add(p.getViewVector(1.0F).scale(ALCANCE));
        AABB caja = p.getBoundingBox().expandTowards(p.getViewVector(1.0F).scale(ALCANCE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(p, ojo, fin, caja,
                e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator() && puedeSerAgarrado(le),
                ALCANCE * ALCANCE);
        return hit != null ? (LivingEntity) hit.getEntity() : null;
    }

    private static boolean puedeSerAgarrado(LivingEntity e) {
        if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) return false;
        if (esVictima(e) || agarrando(e)) return false;
        return e.getBbHeight() <= 2.6F && e.getBbWidth() <= 1.4F && e.getMaxHealth() < 100.0F;
    }

    public static void agarrar(Player p, LivingEntity v) {
        if (!(p.level() instanceof ServerLevel level) || !puedeSerAgarrado(v) || agarrando(p) || esVictima(p)) return;
        Agarre a = new Agarre(p, v);
        POR_AGRESOR.put(p.getUUID(), a);
        POR_VICTIMA.put(v.getUUID(), a);
        if (v instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (v.isPassenger()) v.stopRiding();
        level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.7F);
        level.playSound(null, v.getX(), v.getY(), v.getZ(), ModSounds.CHISPAZO.get(), SoundSource.PLAYERS, 0.8F, 1.0F);
        enviar(a, AgarrePacket.Tipo.INICIO, 0.0F);
    }

    private static void soltar(Agarre a, boolean empujon) {
        POR_AGRESOR.remove(a.agresor.getUUID());
        POR_VICTIMA.remove(a.victima.getUUID());
        if (empujon && a.victima.isAlive()) {
            Vec3 dir = a.agresor.getViewVector(1.0F);
            empujar(a.victima, new Vec3(dir.x * 0.9, 0.35, dir.z * 0.9));
        }
        enviar(a, AgarrePacket.Tipo.FIN, 0.0F);
    }

    /** La víctima se suelta forcejeando: el agresor queda aturdido. */
    private static void liberarse(Agarre a) {
        soltar(a, false);
        aturdir(a.agresor, 30);
        Vec3 dir = a.victima.position().subtract(a.agresor.position()).normalize();
        empujar(a.victima, new Vec3(dir.x * 0.6, 0.3, dir.z * 0.6));
        empujar(a.agresor, new Vec3(-dir.x * 0.5, 0.2, -dir.z * 0.5));
        a.agresor.level().playSound(null, a.agresor.getX(), a.agresor.getY(), a.agresor.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static void aturdir(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 3, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 1, false, false, true));
    }

    private static void empujar(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
    }

    private static void fallo(Player p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.4F, 1.8F);
    }

    // ------------------------------------------------------------------ descargas y ejecución

    private static void descargar(ServerLevel level, Agarre a) {
        if (!BateriaItem.gastar(a.agresor, COSTE_DESCARGA)) {
            soltar(a, true);
            return;
        }
        PorraItem.marcarDescarga(a.agresor.getMainHandItem());
        LivingEntity v = a.victima;
        v.invulnerableTime = 0;
        v.hurt(ModDamageTypes.descarga(level, a.agresor), DANO_DESCARGA);
        bloquearItems(v, BLOQUEO_AGARRE);
        a.resistencia = Math.min(RESISTENCIA_MAX, a.resistencia + 12.0F);

        Vec3 c = v.position().add(0, v.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 35, 0.35, 0.5, 0.35, 0.25);
        level.playSound(null, c.x, c.y, c.z, ModSounds.DESCARGA.get(), SoundSource.PLAYERS, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
        enviar(a, AgarrePacket.Tipo.DESCARGA, a.resistencia);
        arco(a.agresor, v);
    }

    private static void iniciarEjecucion(Agarre a) {
        a.ejecucion = 0;
        enviar(a, AgarrePacket.Tipo.EJECUCION, 0.0F);
        a.agresor.level().playSound(null, a.agresor.getX(), a.agresor.getY(), a.agresor.getZ(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
    }

    private static void tickEjecucion(ServerLevel level, Agarre a) {
        a.ejecucion++;
        LivingEntity v = a.victima;
        Vec3 c = v.position().add(0, v.getBbHeight() * 0.7, 0);
        if (a.ejecucion < 30) {
            // La porra se carga en alto, cada vez más chispas
            if (a.ejecucion % 3 == 0) {
                Vec3 p = a.agresor.getEyePosition().add(0, 0.6, 0);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2 + a.ejecucion / 5, 0.15, 0.15, 0.15, 0.1);
                level.playSound(null, p.x, p.y, p.z, ModSounds.CHISPAZO.get(), SoundSource.PLAYERS, 0.5F, 1.0F + a.ejecucion * 0.02F);
            }
        } else if (a.ejecucion == 30) {
            PorraItem.marcarDescarga(a.agresor.getMainHandItem());
            BateriaItem.gastar(a.agresor, COSTE_DESCARGA * 2);
        } else if (a.ejecucion < DURACION_EJECUCION) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 12, 0.3, 0.5, 0.3, 0.3);
            if (a.ejecucion % 4 == 0) {
                level.playSound(null, c.x, c.y, c.z, ModSounds.DESCARGA.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
            }
        } else {
            v.invulnerableTime = 0;
            v.hurt(ModDamageTypes.descarga(level, a.agresor), 1000.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 80, 0.4, 0.7, 0.4, 0.5);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 15, 0.3, 0.5, 0.3, 0.02);
            level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 1.0F, 1.2F);
            soltar(a, false);
        }
    }

    /** Bloquea los ítems de uso (escudo, arco, aspiradora…) de la víctima durante unos ticks. */
    private static void bloquearItems(LivingEntity v, int ticks) {
        if (v instanceof Player pl) {
            for (int i = 0; i < 9; i++) bloquear(pl, pl.getInventory().getItem(i), ticks);
            bloquear(pl, pl.getOffhandItem(), ticks);
            if (pl.isUsingItem()) pl.stopUsingItem();
        } else {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks / 2, 2));
            v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks / 2, 1));
        }
    }

    private static void bloquear(Player pl, ItemStack s, int ticks) {
        if (s.isEmpty()) return;
        Item item = s.getItem();
        if (item == ModItems.PORRA.get()) return;
        pl.getCooldowns().addCooldown(item, ticks);
    }

    // ------------------------------------------------------------------ forcejeo

    /** La víctima ha pulsado una tecla para forcejear. */
    public static void forcejear(ServerPlayer v) {
        Agarre a = POR_VICTIMA.get(v.getUUID());
        if (a == null || a.ejecucion >= 0) return;
        if (a.ticks - a.ultimoForcejeo < 2) return;
        a.ultimoForcejeo = a.ticks;
        a.resistencia -= 6.0F;
        if (a.resistencia <= 0.0F) {
            liberarse(a);
        } else {
            enviar(a, AgarrePacket.Tipo.FORCEJEO, a.resistencia);
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        for (Agarre a : new ArrayList<>(POR_AGRESOR.values())) {
            Player p = a.agresor;
            LivingEntity v = a.victima;
            if (!p.isAlive() || !v.isAlive() || p.level() != v.level() || p.distanceTo(v) > 4.0
                    || !llevaPorra(p) || BateriaItem.de(p).isEmpty()) {
                soltar(a, false);
                continue;
            }
            ServerLevel level = (ServerLevel) p.level();
            a.ticks++;
            sujetar(a);
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, true, false, false));

            if (a.ejecucion >= 0) {
                tickEjecucion(level, a);
                continue;
            }
            if (--a.proxDescarga <= 0) {
                a.proxDescarga = INTERVALO_DESCARGA;
                descargar(level, a);
                if (!POR_AGRESOR.containsKey(p.getUUID())) continue;
            }
            if (a.ticks > DURACION_MAX) soltar(a, true);
        }

        // Quien choca corriendo contra alguien con la porra queda agarrado
        if (event.getServer().getTickCount() % 2 == 0) {
            for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                if (!llevaPorra(p) || agarrando(p) || esVictima(p) || !BateriaItem.tiene(p, COSTE_DESCARGA)) continue;
                long ahora = p.level().getGameTime();
                if (p.getPersistentData().getLong(CD_CHOQUE) > ahora) continue;
                for (LivingEntity o : p.level().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(0.5),
                        o -> o != p && o.isAlive() && puedeSerAgarrado(o))) {
                    Vec3 haciaP = p.position().subtract(o.position()).multiply(1, 0, 1).normalize();
                    boolean corriendo;
                    if (o instanceof Player op) {
                        corriendo = op.isSprinting() && op.getViewVector(1.0F).multiply(1, 0, 1).normalize().dot(haciaP) > 0.5;
                    } else {
                        Vec3 vel = o.getDeltaMovement().multiply(1, 0, 1);
                        corriendo = vel.length() > 0.12 && vel.normalize().dot(haciaP) > 0.5;
                    }
                    if (!corriendo) continue;
                    p.getPersistentData().putLong(CD_CHOQUE, ahora + 60);
                    agarrar(p, o);
                    break;
                }
            }
        }
    }

    /** Mantiene a la víctima delante del agresor, un poco levantada, mirándole. */
    private static void sujetar(Agarre a) {
        Player p = a.agresor;
        LivingEntity v = a.victima;
        Vec3 frente = Vec3.directionFromRotation(0, p.getYRot());
        // Entrada progresiva: la acerca de 1,1 a 0,7 bloques y la levanta 0,2 en 5 ticks (igual en el cliente)
        float k = Math.min(1.0F, a.ticks / 5.0F);
        k = k * k * (3.0F - 2.0F * k);
        Vec3 punto = p.position().add(frente.scale(1.1 + (0.7 - 1.1) * k)).add(0, 0.2 * k, 0);
        float yaw = p.getYRot() + 180.0F;
        float pitch = -15.0F;

        v.fallDistance = 0.0F;
        v.setDeltaMovement(Vec3.ZERO);
        if (v instanceof ServerPlayer sp) {
            // El cliente de la víctima también se coloca solo; esto corrige cada medio segundo
            if (a.ticks <= 5 || a.ticks % 10 == 1 || sp.position().distanceToSqr(punto) > 0.5) {
                sp.connection.teleport(punto.x, punto.y, punto.z, yaw, pitch);
            }
        } else {
            v.moveTo(punto.x, punto.y, punto.z, yaw, pitch);
            v.setYHeadRot(yaw);
            v.setYBodyRot(yaw);
            if (v instanceof Mob mob) mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ eventos

    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity herido = event.getEntity();
        Entity atacante = event.getSource().getEntity();
        if (herido.level().isClientSide) return;

        // Rescate: si alguien que no es la víctima golpea al agresor, lo suelta aturdido
        Agarre a = POR_AGRESOR.get(herido.getUUID());
        if (a != null && atacante != null && atacante != a.victima && atacante != herido) {
            soltar(a, false);
            aturdir(herido, 25);
        }

        // Golpe normal con la porra: chispazo extra y bloqueo de ítems
        if (atacante instanceof Player p && event.getSource().getDirectEntity() == p && llevaPorra(p)
                && !event.getSource().is(ModDamageTypes.DESCARGA) && BateriaItem.gastar(p, COSTE_GOLPE)
                && p.level() instanceof ServerLevel level) {
            event.setAmount(event.getAmount() + 3.0F);
            bloquearItems(herido, BLOQUEO_GOLPE);
            PorraItem.marcarDescarga(p.getMainHandItem());
            Vec3 c = herido.position().add(0, herido.getBbHeight() * 0.6, 0);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 15, 0.25, 0.35, 0.25, 0.2);
            level.playSound(null, c.x, c.y, c.z, ModSounds.CHISPAZO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            arco(p, herido);
        }
    }

    /** Arco eléctrico visible de la punta de la porra a la víctima (unos pocos ticks). */
    private static void arco(LivingEntity agresor, LivingEntity victima) {
        com.miscelanea.network.ModNetwork.CHANNEL.send(
                net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> agresor),
                new com.miscelanea.network.LazoPacket(agresor.getId(), victima.getId(), 2));
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        terminarCon(event.getEntity());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        terminarCon(event.getEntity());
    }

    private static void terminarCon(Entity e) {
        Agarre a = POR_AGRESOR.get(e.getUUID());
        if (a == null) a = POR_VICTIMA.get(e.getUUID());
        if (a != null) soltar(a, false);
    }

    // La víctima no puede atacar ni usar nada mientras está agarrada
    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (esVictima(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (esVictima(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (esVictima(event.getEntity())) {
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (esVictima(event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (esVictima(event.getEntity())) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ red

    private static void enviar(Agarre a, AgarrePacket.Tipo tipo, float valor) {
        AgarrePacket pkt = new AgarrePacket(tipo, a.agresor.getId(), a.victima.getId(), valor);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> a.agresor), pkt);
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> a.victima), pkt);
    }

    /** Agresor de una víctima (para el cliente no hace falta; se usa en eventos del servidor). */
    @Nullable
    public static Player agresorDe(Entity victima) {
        Agarre a = POR_VICTIMA.get(victima.getUUID());
        return a != null ? a.agresor : null;
    }
}
