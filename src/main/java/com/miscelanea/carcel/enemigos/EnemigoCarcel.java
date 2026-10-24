package com.miscelanea.carcel.enemigos;

import com.miscelanea.carcel.Escondites;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Base de los enemigos de la cárcel: patrullan, van a los ruidos ({@link com.miscelanea.carcel.Ruido}),
 * no ven a quien está escondido en una taquilla y ven peor a quien va agachado.
 */
public abstract class EnemigoCarcel extends Monster implements GeoEntity {
    protected final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final String prefijo;

    protected EnemigoCarcel(EntityType<? extends Monster> type, Level level, String nombre) {
        super(type, level);
        this.prefijo = "animation." + nombre + ".";
        this.xpReward = 10;
    }

    protected RawAnimation anim(String n) {
        return RawAnimation.begin().thenLoop(prefijo + n);
    }

    protected RawAnimation animUnaVez(String n) {
        return RawAnimation.begin().thenPlay(prefijo + n);
    }

    /** Distancia a la que te ven: menos si vas agachado; nada si estás escondido. */
    protected boolean puedeVer(net.minecraft.world.entity.LivingEntity e) {
        if (e instanceof Player p) {
            if (Escondites.escondido(p) || p.isCreative() || p.isSpectator()) return false;
            double max = p.isCrouching() ? 7.0 : 20.0;
            return distanceTo(p) <= max && hasLineOfSight(p);
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        registrarAtaque();
        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::puedeVer));
    }

    protected void registrarAtaque() {
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false));
    }

    /** Animación de andar/correr/quieto según la velocidad. */
    protected PlayState moverse(AnimationState<? extends EnemigoCarcel> state) {
        if (state.isMoving()) {
            return state.setAndContinue(getTarget() != null ? anim("correr") : anim("andar"));
        }
        return state.setAndContinue(anim("quieto"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public boolean removeWhenFarAway(double distancia) {
        return false; // para montar mapas: no desaparecen
    }
}
