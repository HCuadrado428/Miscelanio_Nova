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
    public static final int CARGA = 30, DISPARO = 60, ENFRIAR = 400;
    public static final double ALCANCE = 40.0;
    private static final float DUREZA_MAX = 3.0F;
    private static final String CD = Miscelanea.MOD_ID + ":nucleo_cd";

    private static final Map<UUID, Long> ACTIVOS = new HashMap<>();

    private Aliento() {}

    public static void activar(ServerPlayer p) {
        if (!NucleoItem.lleva(p) || ACTIVOS.containsKey(p.getUUID()) || !p.isAlive() || p.isSpectator()) return;
        long ahora = p.level().getGameTime();
        if (ahora < p.getPersistentData().getLong(CD)) {
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.5F, 1.8F);
            return;
        }
        ACTIVOS.put(p.getUUID(), ahora);
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.NUCLEO_CARGA.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        avisar(p, ahora);
    }

    /** Boca del jugador (de ahí sale el rayo). */
    public static Vec3 boca(LivingEntity p, float pt) {
        Vec3 mira = p.getViewVector(pt);
        return p.getEyePosition(pt).add(mira.scale(0.35)).add(0, -0.15, 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVOS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Long>> it = ACTIVOS.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            ServerPlayer p = event.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) {
                it.remove();
                continue;
            }
            ServerLevel level = p.serverLevel();
            long t = level.getGameTime() - en.getValue();
            if (!p.isAlive() || !NucleoItem.lleva(p)) {
                it.remove();
                avisar(p, -1);
                continue;
            }
            if (t < CARGA) {
                cargar(level, p, t);
            } else if (t < CARGA + DISPARO) {
                if (t == CARGA) {
                    level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.5F, 0.6F);
                }
                disparar(level, p, (int) (t - CARGA));
            } else {
                it.remove();
                p.getPersistentData().putLong(CD, level.getGameTime() + ENFRIAR);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.NUCLEO_FIN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            }
        }
    }

    private static void cargar(ServerLevel level, ServerPlayer p, long t) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 3, false, false, false));
        // el error sube desde los pies hasta la cabeza
        if (t % 2 == 0) {
            double y = p.getY() + p.getBbHeight() * (t / (double) CARGA);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), y, p.getZ(), 6, 0.35, 0.1, 0.35, 0.02);
        }
    }

    private static void disparar(ServerLevel level, ServerPlayer p, int t) {
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 2, false, false, false));
        Vec3 o = boca(p, 1.0F);
        Vec3 d = p.getViewVector(1.0F);
        Vec3 fin = o.add(d.scale(ALCANCE));

        // Avanza rompiendo hasta 3 bloques blandos por tick; se para en el primero duro
        Vec3 desde = o;
        int rotos = 0;
        for (int intento = 0; intento < 6; intento++) {
            BlockHitResult hit = level.clip(new ClipContext(desde, o.add(d.scale(ALCANCE)), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, p));
            if (hit.getType() == HitResult.Type.MISS) {
                fin = o.add(d.scale(ALCANCE));
                break;
            }
            BlockPos bp = hit.getBlockPos();
            if (rotos < 3 && romper(level, p, bp)) {
                rotos++;
                // túnel un poco más ancho
                for (Direction dir : Direction.values()) {
                    if (dir.getAxis() == Direction.getNearest(d.x, d.y, d.z).getAxis()) continue;
                    if (level.random.nextFloat() < 0.35F) romper(level, p, bp.relative(dir));
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
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, fin.x, fin.y, fin.z, 20, 0.4, 0.4, 0.4, 0.15);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, fin.x, fin.y, fin.z, 3, 0.3, 0.3, 0.3, 0.02);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, fin.x, fin.y, fin.z, 6, 0.3, 0.3, 0.3, 0.05);
        }
        if (t % 8 == 0) {
            level.playSound(null, fin.x, fin.y, fin.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.7F, 0.6F + level.random.nextFloat() * 0.2F);
        }

        // Daño a lo largo del rayo
        if (t % 4 == 0) {
            AABB caja = new AABB(o, fin).inflate(1.2);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caja, e -> e != p && e.isAlive() && !e.isSpectator())) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (distanciaASegmento(c, o, fin) > 0.7 + e.getBbWidth() * 0.5) continue;
                e.invulnerableTime = 0;
                e.hurt(ModDamageTypes.nucleo(level, p), 7.0F);
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), p);
                e.setDeltaMovement(e.getDeltaMovement().add(d.scale(0.5)).add(0, 0.1, 0));
                e.hurtMarked = true;
            }
        }
        // Retroceso suave
        Vec3 v = p.getDeltaMovement().subtract(d.x * 0.02, 0, d.z * 0.02);
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

    private static void avisar(ServerPlayer p, long inicio) {
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new NucleoEstadoPacket(p.getId(), inicio));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVOS.remove(event.getEntity().getUUID());
    }
}
