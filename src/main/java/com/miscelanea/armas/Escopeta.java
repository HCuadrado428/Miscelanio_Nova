package com.miscelanea.armas;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Disparo de la superescopeta: 16 perdigones instantáneos con dispersión; el daño baja con la distancia. */
public final class Escopeta {
    private static final int PERDIGONES = 16;
    private static final double ALCANCE = 28, DISPERSION = 0.075;
    private static final float DANO = 1.75F;

    private Escopeta() {}

    public static void disparar(ServerPlayer p, ItemStack s) {
        if (!Municion.gastar(p, ModItems.CARTUCHOS.get())) return;
        ServerLevel sl = p.serverLevel();
        s.getOrCreateTag().putInt(EscopetaItem.DISPARO, EscopetaItem.TIEMPO_DISPARO);
        Vec3 ojo = p.getEyePosition();
        Vec3 mira = p.getViewVector(1.0F);
        Vec3 der = mira.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : der.normalize();
        Vec3 boca = ojo.add(mira.scale(0.9)).add(der.scale(0.25)).add(0, -0.25, 0);
        RandomSource r = p.getRandom();
        Map<LivingEntity, Float> dano = new HashMap<>();
        for (int i = 0; i < PERDIGONES; i++) {
            Vec3 dir = mira.add(r.nextGaussian() * DISPERSION, r.nextGaussian() * DISPERSION, r.nextGaussian() * DISPERSION).normalize();
            Vec3 fin = ojo.add(dir.scale(ALCANCE));
            BlockHitResult bh = sl.clip(new ClipContext(ojo, fin, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (bh.getType() != HitResult.Type.MISS) fin = bh.getLocation();
            EntityHitResult eh = ProjectileUtil.getEntityHitResult(p, ojo, fin, new AABB(ojo, fin).inflate(1.0),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != p, ojo.distanceToSqr(fin));
            Vec3 impacto;
            if (eh != null && eh.getEntity() instanceof LivingEntity v) {
                impacto = eh.getLocation();
                double d = ojo.distanceTo(impacto);
                float caida = d < 6 ? 1.0F : (float) Mth.clampedLerp(1.0, 0.25, (d - 6) / (ALCANCE - 6));
                dano.merge(v, DANO * caida, Float::sum);
                sl.sendParticles(ParticleTypes.CRIT, impacto.x, impacto.y, impacto.z, 2, 0.05, 0.05, 0.05, 0.2);
            } else {
                impacto = fin;
                if (bh.getType() != HitResult.Type.MISS) {
                    BlockState st = sl.getBlockState(bh.getBlockPos());
                    if (!st.isAir())
                        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), impacto.x, impacto.y, impacto.z, 4, 0.05, 0.05, 0.05, 0.1);
                    sl.sendParticles(ParticleTypes.SMOKE, impacto.x, impacto.y, impacto.z, 1, 0.02, 0.02, 0.02, 0.01);
                }
            }
            // estela del perdigón (pocos puntos para que se vea el cono)
            for (int k = 1; k <= 3; k++) {
                Vec3 q = boca.lerp(impacto, k / 4.0 * r.nextDouble());
                sl.sendParticles(ParticleTypes.SMOKE, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
        for (Map.Entry<LivingEntity, Float> e : dano.entrySet()) {
            LivingEntity v = e.getKey();
            if (v.hurt(sl.damageSources().playerAttack(p), e.getValue())) {
                Vec3 emp = v.position().subtract(p.position()).normalize();
                v.knockback(Math.min(1.6, e.getValue() / 10.0), -emp.x, -emp.z);
            }
        }
        // fogonazo, humo y retroceso
        sl.sendParticles(ParticleTypes.FLASH, boca.x, boca.y, boca.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, boca.x, boca.y, boca.z, 4, 0.08, 0.08, 0.08, 0.02);
        sl.sendParticles(ParticleTypes.FLAME, boca.x, boca.y, boca.z, 6, 0.05, 0.05, 0.05, 0.08);
        Vec3 atras = mira.scale(p.onGround() ? -0.2 : -0.45);
        p.setDeltaMovement(p.getDeltaMovement().add(atras));
        p.hurtMarked = true;
        sl.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ESCOPETA_DISPARO.get(), SoundSource.PLAYERS, 1.6F, 0.95F + r.nextFloat() * 0.1F);
    }
}
