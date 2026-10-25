package com.miscelanea.gravedad;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.*;

/**
 * Pistola de gravedad (Half-Life 2):
 * - Clic derecho: atrae lo que apuntas (mob, objeto o bloque, que se arranca como bloque que cae) y lo sostiene delante.
 *   Otra vez clic derecho: lo sueltas. Si dejas de llevar la pistola, se cae.
 * - Clic izquierdo: lanzamiento ("punt") de lo que sostienes, o empujón a lo que tengas delante.
 *   Lo lanzado hace daño al chocar con un mob según la velocidad.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Gravedad {
    public static final double ALCANCE = 14.0, DISTANCIA = 2.6, EMPUJON = 5.0;
    private static final Map<UUID, UUID> SOSTIENE = new HashMap<>();          // jugador -> entidad sostenida
    private static final Map<UUID, Lanzado> LANZADOS = new HashMap<>();        // entidad lanzada -> quién y cuánto queda
    private static final Map<UUID, Integer> ATRAYENDO = new HashMap<>();       // ticks atrayendo (antes de agarrar)

    private record Lanzado(UUID quien, int ticks) {}

    private Gravedad() {}

    public static boolean sostiene(Player p) {
        return SOSTIENE.containsKey(p.getUUID());
    }

    private static ItemStack pistola(Player p) {
        if (p.getMainHandItem().getItem() instanceof GravedadItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof GravedadItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** ¿Se puede coger este bloque? (ni muy duros, ni irrompibles, ni con inventario/datos). */
    public static boolean bloqueCogible(ServerLevel l, BlockPos p, BlockState s) {
        if (s.isAir() || !s.getFluidState().isEmpty()) return false;
        float dureza = s.getDestroySpeed(l, p);
        return dureza >= 0 && dureza <= 5.0F && !s.hasBlockEntity();
    }

    /** Lo que hay en la mira (entidad primero, si no bloque). */
    private static Object objetivo(ServerPlayer p, double alcance) {
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1.0F), fin = ojo.add(dir.scale(alcance));
        BlockHitResult bh = p.level().clip(new ClipContext(ojo, fin, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        double max = bh.getType() == HitResult.Type.BLOCK ? bh.getLocation().distanceTo(ojo) : alcance;
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, ojo, ojo.add(dir.scale(max)),
                p.getBoundingBox().expandTowards(dir.scale(max)).inflate(1.0),
                e -> e.isAlive() && !e.isSpectator() && e != p && !(e instanceof Player o && o.isCreative()), max * max);
        if (eh != null) return eh.getEntity();
        if (bh.getType() == HitResult.Type.BLOCK) return bh.getBlockPos();
        return null;
    }

    /** Clic derecho. */
    public static void alternar(ServerPlayer p, ItemStack s) {
        ServerLevel l = p.serverLevel();
        if (sostiene(p)) {
            soltar(p, false);
            return;
        }
        Object o = objetivo(p, ALCANCE);
        Entity e = null;
        if (o instanceof Entity en) e = en;
        else if (o instanceof BlockPos bp) {
            BlockState st = l.getBlockState(bp);
            if (!bloqueCogible(l, bp, st)) {
                l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.7F, 0.6F);
                return;
            }
            FallingBlockEntity fb = FallingBlockEntity.fall(l, bp, st);
            fb.setNoGravity(true);
            fb.time = 1;
            fb.dropItem = true;
            e = fb;
        }
        if (e == null) {
            l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 0.5F);
            return;
        }
        SOSTIENE.put(p.getUUID(), e.getUUID());
        ATRAYENDO.put(p.getUUID(), 0);
        s.getOrCreateTag().putInt(GravedadItem.AGARRADO, e.getId());
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.8F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.35F, 1.9F);
    }

    /** Clic izquierdo: lanza lo sostenido o empuja lo de delante. */
    public static void punt(ServerPlayer p) {
        ItemStack s = pistola(p);
        if (s.isEmpty()) return;
        ServerLevel l = p.serverLevel();
        Entity e = null;
        if (sostiene(p)) {
            e = l.getEntity(SOSTIENE.get(p.getUUID()));
            soltar(p, true);
        } else {
            Object o = objetivo(p, EMPUJON);
            if (o instanceof Entity en) e = en;
            else if (o instanceof BlockPos bp && bloqueCogible(l, bp, l.getBlockState(bp))) e = FallingBlockEntity.fall(l, bp, l.getBlockState(bp));
        }
        s.getOrCreateTag().putInt(GravedadItem.PUNT, 8);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.45F, 1.9F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS, 0.8F, 1.5F);
        Vec3 dir = p.getViewVector(1.0F);
        Vec3 boca = p.getEyePosition().add(dir.scale(0.9)).add(0, -0.25, 0);
        l.sendParticles(new DustParticleOptions(new Vector3f(1.0F, 0.55F, 0.1F), 1.6F), boca.x, boca.y, boca.z, 25, 0.25, 0.25, 0.25, 0.05);
        if (e == null) return;
        double fuerza = e instanceof FallingBlockEntity ? 2.1 : (e instanceof LivingEntity le ? 2.4 / Math.max(1.0, le.getBbWidth() * le.getBbHeight() / 1.2) : 2.8);
        e.setNoGravity(false);
        e.setDeltaMovement(dir.scale(fuerza).add(0, 0.15, 0));
        e.hurtMarked = true;
        if (e instanceof FallingBlockEntity fb) fb.time = 1;
        LANZADOS.put(e.getUUID(), new Lanzado(p.getUUID(), 60));
        // retroceso
        p.setDeltaMovement(p.getDeltaMovement().subtract(dir.scale(0.25)));
        p.hurtMarked = true;
    }

    static void soltar(ServerPlayer p, boolean lanzando) {
        UUID id = SOSTIENE.remove(p.getUUID());
        ATRAYENDO.remove(p.getUUID());
        ItemStack s = pistola(p);
        if (!s.isEmpty() && s.hasTag()) s.getTag().remove(GravedadItem.AGARRADO);
        if (id == null) return;
        Entity e = p.serverLevel().getEntity(id);
        if (e != null) {
            e.setNoGravity(false);
            if (!lanzando) {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.3));
                p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.9F);
            }
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        var srv = ev.getServer();
        for (UUID pid : new ArrayList<>(SOSTIENE.keySet())) {
            ServerPlayer p = srv.getPlayerList().getPlayer(pid);
            if (p == null) {
                SOSTIENE.remove(pid);
                continue;
            }
            Entity e = p.serverLevel().getEntity(SOSTIENE.get(pid));
            if (e == null || !e.isAlive() || pistola(p).isEmpty() || e.distanceTo(p) > ALCANCE + 4) {
                soltar(p, false);
                continue;
            }
            Vec3 destino = p.getEyePosition().add(p.getViewVector(1.0F).scale(DISTANCIA + e.getBbWidth() * 0.5)).add(0, -e.getBbHeight() * 0.5, 0);
            int t = ATRAYENDO.merge(pid, 1, Integer::sum);
            double k = t < 8 ? 0.25 : 0.5;                      // primero lo atrae, luego lo sujeta firme
            Vec3 v = destino.subtract(e.position()).scale(k);
            e.setDeltaMovement(v);
            e.setNoGravity(true);
            e.fallDistance = 0;
            e.hurtMarked = true;
            if (e instanceof FallingBlockEntity fb) fb.time = 1;
            if (e instanceof Mob m) m.getNavigation().stop();
            if (p.tickCount % 30 == 0) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.9F);
        }
        // Lo lanzado hace daño a los mobs con los que choca
        for (Iterator<Map.Entry<UUID, Lanzado>> it = LANZADOS.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Lanzado la = en.getValue();
            Entity e = null;
            for (ServerLevel l : srv.getAllLevels()) {
                e = l.getEntity(en.getKey());
                if (e != null) break;
            }
            if (e == null || la.ticks <= 0) {
                it.remove();
                continue;
            }
            en.setValue(new Lanzado(la.quien, la.ticks - 1));
            double vel = e.getDeltaMovement().length();
            if (vel < 0.35) continue;
            AABB caja = e.getBoundingBox().inflate(0.3);
            final Entity lanzado = e;
            Entity quien = ((ServerLevel) e.level()).getEntity(la.quien);
            for (LivingEntity v : e.level().getEntitiesOfClass(LivingEntity.class, caja, v -> v != lanzado && v != quien && v.isAlive())) {
                float dano = (float) Math.min(16.0, vel * (e instanceof FallingBlockEntity ? 9.0 : 6.0));
                v.hurt(quien instanceof Player pq ? v.damageSources().playerAttack(pq) : v.damageSources().flyIntoWall(), dano);
                v.knockback(0.8, -e.getDeltaMovement().x, -e.getDeltaMovement().z);
                e.level().playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.4F);
                e.setDeltaMovement(e.getDeltaMovement().scale(0.4));
            }
        }
    }
}
