package com.miscelanea.nucleo;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.network.NucleoEstadoPacket;
import com.miscelanea.registry.ModBlocks;
import com.miscelanea.registry.ModDamageTypes;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Aliento del núcleo, a lo aliento atómico:
 * 1,5 s de carga (el error sube por el cuerpo, echas la cabeza atrás) y 3 s de rayo morado y negro por la boca
 * que sigue a donde miras. Hace daño y marchita a lo que toca, y rompe los bloques blandos (no obsidiana,
 * bedrock, cofres ni nada con inventario). Algunos bloques rotos se quedan "sin textura" un rato. Recarga: 20 s.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Aliento {
    /** Carga mínima para disparar, carga máxima (rayo al máximo) y carga a la que se dispara solo. */
    public static final int CARGA_MIN = 12, CARGA_MAX = 100, CARGA_AUTO = 160;
    private static final float DUREZA_MAX = 3.0F;
    private static final String CD = Miscelanea.MOD_ID + ":nucleo_cd";

    /** Estado de cada jugador: cargando (disparo &lt; 0) o disparando. */
    private static final class Estado {
        long carga;
        long disparo = -1;
        float potencia;
    }

    private static final Map<UUID, Estado> ACTIVOS = new HashMap<>();

    private Aliento() {}

    // ---- tamaños según la potencia (0 = carga mínima, 1 = carga máxima); el cliente usa las mismas fórmulas
    public static float potencia(long ticksCarga) {
        return Math.max(0.0F, Math.min(1.0F, (ticksCarga - CARGA_MIN) / (float) (CARGA_MAX - CARGA_MIN)));
    }

    public static int duracion(float p) {
        return 30 + Math.round(60 * p);
    }

    public static double alcance(float p) {
        return 24.0 + 32.0 * p;
    }

    public static float grosor(float p) {
        return 0.45F + 1.85F * p;
    }

    public static int enfriar(float p) {
        return 120 + Math.round(380 * p);
    }

    /** Se pulsa (o se mantiene) Shift + clic derecho con la mano vacía. */
    public static void pulsar(ServerPlayer p) {
        if (!NucleoItem.lleva(p) || ACTIVOS.containsKey(p.getUUID()) || !p.isAlive() || p.isSpectator()) return;
        long ahora = p.level().getGameTime();
        if (ahora < p.getPersistentData().getLong(CD)) {
            if (ahora - p.getPersistentData().getLong(CD + "_aviso") > 20) {
                p.getPersistentData().putLong(CD + "_aviso", ahora);
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
            }
            return;
        }
        Estado e = new Estado();
        e.carga = ahora;
        ACTIVOS.put(p.getUUID(), e);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.NUCLEO_CARGA.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        avisar(p, e);
    }

    /** Se suelta: dispara con la carga acumulada (o se apaga si apenas se cargó). */
    public static void soltar(ServerPlayer p) {
        Estado e = ACTIVOS.get(p.getUUID());
        if (e == null || e.disparo >= 0) return;
        long ahora = p.level().getGameTime();
        long cargado = ahora - e.carga;
        if (cargado < CARGA_MIN) {
            ACTIVOS.remove(p.getUUID());
            p.getPersistentData().putLong(CD, ahora + 20);
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.NUCLEO_FIN.get(), SoundSource.PLAYERS, 0.6F, 1.6F);
            e.carga = -1;
            avisar(p, e);
            return;
        }
        e.potencia = potencia(cargado);
        e.disparo = ahora;
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS,
                1.0F + e.potencia, 0.9F - 0.35F * e.potencia);
        avisar(p, e);
    }

    /** Boca del jugador (de ahí sale el rayo). */
    public static Vec3 boca(LivingEntity p, float pt) {
        Vec3 mira = p.getViewVector(pt);
        return p.getEyePosition(pt).add(mira.scale(0.35)).add(0, -0.15, 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVOS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Estado>> it = ACTIVOS.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            ServerLevel level = p.serverLevel();
            Estado e = en.getValue();
            long ahora = level.getGameTime();
            if (!p.isAlive() || !NucleoItem.lleva(p)) {
                it.remove();
                e.carga = -1;
                e.disparo = -1;
                avisar(p, e);
                continue;
            }
            if (e.disparo < 0) {
                long t = ahora - e.carga;
                cargar(level, p, t);
                if (t >= CARGA_AUTO) soltar(p); // no se puede aguantar para siempre
                continue;
            }
            long t = ahora - e.disparo;
            if (t < duracion(e.potencia)) {
                disparar(level, p, (int) t, e.potencia);
            } else {
                it.remove();
                p.getPersistentData().putLong(CD, ahora + enfriar(e.potencia));
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.NUCLEO_FIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static void cargar(ServerLevel level, ServerPlayer p, long t) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
        float k = Math.min(1.0F, t / (float) CARGA_MAX);
        // el error sube desde los pies hasta la cabeza, y cuanto más carga, más se nota
        if (t % 2 == 0) {
            double y = p.getY() + p.getBbHeight() * k;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), y, p.getZ(), 4 + (int) (k * 10), 0.35 + k * 0.3, 0.1, 0.35 + k * 0.3, 0.02 + k * 0.05);
        }
        if (t == CARGA_MAX) {
            // carga completa: chispazo para que se note
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 0.5F);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.8, 0.6, 0.3);
        }
    }

    private static void disparar(ServerLevel level, ServerPlayer p, int t, float pot) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
        double alcance = alcance(pot);
        Vec3 o = boca(p, 1.0F);
        Vec3 d = p.getViewVector(1.0F);
        Vec3 fin = o.add(d.scale(alcance));
        // radio del túnel: 0 (un bloque) con poca carga, hasta 2 con carga máxima
        int radio = pot < 0.35F ? 0 : pot < 0.75F ? 1 : 2;
        int porTick = 2 + Math.round(3 * pot);

        Vec3 desde = o;
        int rotos = 0;
        for (int intento = 0; intento < 8; intento++) {
            BlockHitResult hit = level.clip(new ClipContext(desde, o.add(d.scale(alcance)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
                fin = o.add(d.scale(alcance));
                break;
            }
            BlockPos bp = hit.getBlockPos();
            if (rotos < porTick && romper(level, p, bp)) {
                rotos++;
                if (radio > 0) {
                    for (BlockPos q : BlockPos.betweenClosed(bp.offset(-radio, -radio, -radio), bp.offset(radio, radio, radio))) {
                        if (q.distSqr(bp) <= radio * radio + 0.5 && level.random.nextFloat() < 0.7F) romper(level, p, q.immutable());
                    }
                }
                desde = hit.getLocation().add(d.scale(0.05));
                fin = hit.getLocation();
                continue;
            }
            fin = hit.getLocation();
            break;
        }

        // Impacto
        if (t % 2 == 0) {
            float r = 0.4F + pot;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, fin.x, fin.y, fin.z, 20 + (int) (pot * 40), r, r, r, 0.15 + pot * 0.2);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, fin.x, fin.y, fin.z, 3 + (int) (pot * 6), r * 0.7, r * 0.7, r * 0.7, 0.02);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, fin.x, fin.y, fin.z, 6, r * 0.7, r * 0.7, r * 0.7, 0.05);
        }
        if (t % (pot > 0.75F ? 5 : 8) == 0) {
            level.playSound(null, fin.x, fin.y, fin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.5F + pot,
                    0.75F - 0.25F * pot + level.random.nextFloat() * 0.15F);
        }

        // Daño a lo largo del rayo
        if (t % 4 == 0) {
            double ancho = 0.5 + grosor(pot) * 0.6;
            AABB caja = new AABB(o, fin).inflate(ancho + 1.0);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caja, e -> e != p && e.isAlive() && !e.isSpectator())) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (distanciaASegmento(c, o, fin) > ancho + e.getBbWidth() * 0.5) continue;
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.nucleo(level, p), 4.0F + 8.0F * pot);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, pot > 0.6F ? 2 : 1), p);
                e.setDeltaMovement(e.getDeltaMovement().add(d.scale(0.3 + 0.6 * pot)).add(0, 0.1, 0));
                e.hurtMarked = true;
            }
        }
        // Retroceso: más fuerte cuanto más grande es el rayo
        Vec3 v = p.getDeltaMovement().subtract(d.x * (0.01 + 0.04 * pot), 0, d.z * (0.01 + 0.04 * pot));
        p.setDeltaMovement(v);
        p.hurtMarked = true;
    }

    private static double distanciaASegmento(Vec3 c, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double l2 = ab.lengthSqr();
        double k = l2 < 1.0E-6 ? 0 : Math.max(0, Math.min(1, c.subtract(a).dot(ab) / l2));
        return c.distanceTo(a.add(ab.scale(k)));
    }

    /** Rompe el bloque si es blando y se puede. Algunos se quedan como bloque corrupto. */
    private static boolean romper(ServerLevel level, ServerPlayer p, BlockPos bp) {
        BlockState st = level.getBlockState(bp);
        if (st.isAir() || st.is(ModBlocks.BLOQUE_CORRUPTO.get())) return st.is(ModBlocks.BLOQUE_CORRUPTO.get());
        float dureza = st.getDestroySpeed(level, bp);
        if (dureza < 0 || dureza > DUREZA_MAX || st.hasBlockEntity() || st.is(BlockTags.WITHER_IMMUNE)) return false;
        if (!level.mayInteract(p, bp) || !p.mayBuild()) return false;
        if (MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, bp, st, p))) return false;
        if (level.random.nextFloat() < 0.12F) {
            level.levelEvent(2001, bp, net.minecraft.world.level.block.Block.getId(st));
            level.setBlock(bp, ModBlocks.BLOQUE_CORRUPTO.get().defaultBlockState(), 3);
        } else {
            level.destroyBlock(bp, level.random.nextFloat() < 0.15F, p);
        }
        return true;
    }

    private static void avisar(ServerPlayer p, Estado e) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new NucleoEstadoPacket(p.getId(), e.carga, e.disparo, e.potencia));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVOS.remove(event.getEntity().getUUID());
    }
}
