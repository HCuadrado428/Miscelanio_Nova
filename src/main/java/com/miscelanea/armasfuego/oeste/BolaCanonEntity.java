package com.miscelanea.armasfuego.oeste;

import com.miscelanea.armasfuego.comun.Balistica;
import com.miscelanea.armasfuego.comun.EquilibrioArmas;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Bolaño de piedra del cañón de mano: vuela en parábola y estalla donde da. */
public class BolaCanonEntity extends ThrowableItemProjectile {
    public BolaCanonEntity(EntityType<? extends BolaCanonEntity> t, Level l) {
        super(t, l);
    }

    public BolaCanonEntity(EntityType<? extends BolaCanonEntity> t, LivingEntity tirador, Level l) {
        super(t, tirador, l);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBBLESTONE;
    }

    @Override
    protected float getGravity() {
        return 0.035F;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount > 1) level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, 0, 0);
        if (!level().isClientSide && tickCount > 200) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        if (r.getEntity() instanceof LivingEntity v && level() instanceof ServerLevel)
            Balistica.herir(v, getOwner(), EquilibrioArmas.canonDano(), 0.3F, CanonManoItem.DANO, false);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (!(level() instanceof ServerLevel l)) return;
        Vec3 q = r.getLocation();
        Entity directo = r instanceof EntityHitResult eh ? eh.getEntity() : null;
        // estallido: un tercio del daño alrededor y un empujón (sin romper bloques salvo que lo diga la config)
        float alrededor = EquilibrioArmas.canonDano() / 3F;
        for (LivingEntity v : l.getEntitiesOfClass(LivingEntity.class, new AABB(q, q).inflate(2.5), e -> e.isAlive() && e != directo)) {
            double d = v.position().distanceTo(q);
            if (d > 2.5) continue;
            float k = (float) (1 - d / 2.5);
            Balistica.herir(v, getOwner(), alrededor * k, 0.0F, CanonManoItem.DANO, false);
            Vec3 emp = v.position().subtract(q).normalize().scale(0.6 * k);
            v.push(emp.x, 0.25 * k, emp.z);
            v.hurtMarked = true;
        }
        if (EquilibrioArmas.canonRompe()) {
            l.explode(this, q.x, q.y, q.z, 1.6F, Level.ExplosionInteraction.TNT);
        } else {
            l.sendParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            l.sendParticles(ParticleTypes.LARGE_SMOKE, q.x, q.y, q.z, 6, 0.3, 0.2, 0.3, 0.02);
            if (r instanceof BlockHitResult bh)
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, l.getBlockState(bh.getBlockPos())), q.x, q.y, q.z, 16, 0.3, 0.3, 0.3, 0.15);
            l.playSound(null, q.x, q.y, q.z, ModSounds.CANON_DISPARO.get(), SoundSource.PLAYERS, 2.0F, 1.3F);
            l.gameEvent(this, GameEvent.EXPLODE, q);
        }
        discard();
    }
}
