package com.miscelanea.carcel.enemigos;

import com.miscelanea.porra.Agarres;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Carcelero (jefe): grande, lleva la porra y el generador. Te persigue y, cuando te alcanza, te agarra
 * con el mismo sistema que la porra del jugador: te levanta, te da descargas y, si te queda poca vida,
 * te ejecuta. Forcejea (teclas de movimiento) para soltarte. Si otro le golpea, te suelta.
 */
public class Jefe extends EnemigoCarcel {
    private int enfriarAgarre;

    public Jefe(EntityType<? extends Monster> type, Level level) {
        super(type, level, "jefe");
        this.xpReward = 40;
    }

    public static AttributeSupplier.Builder atributos() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 90.0)
                .add(Attributes.MOVEMENT_SPEED, 0.27)
                .add(Attributes.ATTACK_DAMAGE, 8.0)
                .add(Attributes.FOLLOW_RANGE, 40.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8);
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance d, MobSpawnType t,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag) {
        // la porra en la mano (la lógica del agarre la necesita; el modelo ya la dibuja)
        setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORRA.get()));
        setDropChance(EquipmentSlot.MAINHAND, 0.1F);
        return super.finalizeSpawn(level, d, t, data, tag);
    }

    @Override
    protected void registrarAtaque() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, true) {
            @Override
            public boolean canUse() {
                return !Agarres.agarrando(Jefe.this) && super.canUse();
            }
        });
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        if (getMainHandItem().isEmpty()) setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.PORRA.get()));
        if (estadoEspecial() != 0) return;
        if (!Agarres.agarrando(this) && rematarCaido(16)) {
            if (tickCount % 6 == 0 && level() instanceof net.minecraft.server.level.ServerLevel sl)
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 1.2, getZ(), 6, 0.3, 0.3, 0.3, 0.2);
            return;
        }
        if (Agarres.agarrando(this)) {
            getNavigation().stop();
            Agarres.accionMob(this);
            enfriarAgarre = 120;
            return;
        }
        if (enfriarAgarre > 0) {
            enfriarAgarre--;
            return;
        }
        LivingEntity t = getTarget();
        if (t != null && distanceTo(t) < 2.2 && hasLineOfSight(t) && Agarres.agarrable(t)) {
            Agarres.agarrar(this, t);
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.0F, 1.3F);
        }
    }

    /** La porra eléctrica desactiva el rig 30 s. */
    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity t) {
        boolean r = super.doHurtTarget(t);
        if (r && t instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.miscelanea.programa.Rigs.desactivar(sp, 600);
            level().playSound(null, getX(), getY(), getZ(), ModSounds.DESCARGA.get(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        return r;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "mover", 5, state -> {
            if (com.miscelanea.client.porra.ClientAgarres.activo(getId()) != null) {
                return state.setAndContinue(anim("agarrar"));
            }
            return moverse(state);
        }));
        c.add(new AnimationController<>(this, "golpe", 0, state -> {
            if (swinging) return state.setAndContinue(animUnaVez("golpe"));
            state.getController().forceAnimationReset();
            return PlayState.STOP;
        }));
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.JEFE_RESPIRA.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource s) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.8F;
    }
}
