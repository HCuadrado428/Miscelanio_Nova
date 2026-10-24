package com.miscelanea.carcel;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Botella o ladrillo lanzado. Al dar a un bicho le aturde unos segundos; al chocar hace ruido
 * y los monstruos van a mirar allí (sirve para distraer). Si cae sobre una rejilla eléctrica la funde.
 */
public class Arrojable extends ThrowableItemProjectile {
    public Arrojable(EntityType<? extends Arrojable> type, Level level) {
        super(type, level);
    }

    public Arrojable(Level level, LivingEntity quien, ItemStack stack) {
        super(ModEntities.ARROJABLE.get(), quien, level);
        setItem(stack.copyWithCount(1));
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.BOTELLA.get();
    }

    private boolean esBotella() {
        return !getItem().is(Items.BRICK) && !getItem().is(Items.NETHER_BRICK);
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        if (hit.getEntity() instanceof LivingEntity le && le != getOwner()) {
            le.hurt(damageSources().thrown(this, getOwner()), esBotella() ? 1.0F : 2.0F);
            le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 5, false, false));
            le.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 2, false, false));
            if (le instanceof Mob m) {
                m.getNavigation().stop();
                m.setTarget(null);
            }
        }
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (!(level() instanceof ServerLevel sl)) return;
        if (esBotella()) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK, SoundSource.NEUTRAL, 1.2F, 0.9F + random.nextFloat() * 0.3F);
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 12, 0.1, 0.1, 0.1, 0.12);
        } else {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.STONE_HIT, SoundSource.NEUTRAL, 1.5F, 0.8F);
            sl.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.08);
        }
        Ruido.alertar(sl, position(), 20, this);
        discard();
    }
}
