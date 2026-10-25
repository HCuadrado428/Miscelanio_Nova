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

    /** 0 = normal, 1 = aturdido (rig Stun / objeto lanzado), 2 = cegado (rig Blind). Para las animaciones. */
    private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> ESTADO =
            net.minecraft.network.syncher.SynchedEntityData.defineId(EnemigoCarcel.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
    private int golpeBarricada;

    protected EnemigoCarcel(EntityType<? extends Monster> type, Level level, String nombre) {
        super(type, level);
        this.prefijo = "animation." + nombre + ".";
        this.xpReward = 10;
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation g) g.setCanOpenDoors(true);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ESTADO, 0);
    }

    /** Va a por un jugador caído cercano y lo remata (Screamer, Coyle). Devuelve true si está en ello. */
    protected boolean rematarCaido(double radio) {
        for (Player p : level().getEntitiesOfClass(Player.class, getBoundingBox().inflate(radio), com.miscelanea.programa.Jugadores::caido)) {
            if (distanceTo(p) < 1.8) {
                getNavigation().stop();
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                if (p instanceof net.minecraft.server.level.ServerPlayer sp) com.miscelanea.programa.Prueba.morir(sp);
            } else {
                getNavigation().moveTo(p, 1.1);
            }
            getLookControl().setLookAt(p);
            return true;
        }
        return false;
    }

    public int estadoEspecial() {
        return entityData.get(ESTADO);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        var lento = getEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        int st = hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) ? 2 : (lento != null && lento.getAmplifier() >= 5 ? 1 : 0);
        if (st != entityData.get(ESTADO)) entityData.set(ESTADO, st);
        if (st != 0) {
            getNavigation().stop();
            setTarget(null);
            return;
        }
        // Barricadas: si persigue a alguien y tiene una delante, la revienta a golpes
        if (getTarget() != null) {
            net.minecraft.core.BlockPos delante = blockPosition().relative(getDirection());
            boolean hay = false;
            for (int dy = 0; dy <= 1 && !hay; dy++) {
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    net.minecraft.core.BlockPos q = blockPosition().above(dy).relative(d);
                    if (level().getBlockState(q).getBlock() instanceof com.miscelanea.programa.BarricadaBlock) {
                        delante = q;
                        hay = true;
                        break;
                    }
                }
            }
            if (hay && ++golpeBarricada % 25 == 0) {
                swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                com.miscelanea.programa.BarricadaBlock.golpear((net.minecraft.server.level.ServerLevel) level(), delante, this);
            }
        }
    }

    protected RawAnimation anim(String n) {
        return RawAnimation.begin().thenLoop(prefijo + n);
    }

    protected RawAnimation animUnaVez(String n) {
        return RawAnimation.begin().thenPlay(prefijo + n);
    }

    /** Distancia a la que te ven: menos si vas agachado; nada si estás escondido. */
    protected boolean puedeVer(net.minecraft.world.entity.LivingEntity e) {
        if (hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS)) return false;
        if (e instanceof Player p) {
            if (Escondites.escondido(p) || p.isCreative() || p.isSpectator()) return false;
            if (com.miscelanea.programa.Jugadores.caido(p)) return false;
            // En la oscuridad te ven mucho peor (como en The Outlast Trials)
            boolean iluminado = level().getMaxLocalRawBrightness(p.blockPosition()) >= 7 || p.isCurrentlyGlowing();
            double max = p.isCrouching() ? (iluminado ? 10.0 : 4.0) : (iluminado ? 24.0 : 10.0);
            return distanceTo(p) <= max && hasLineOfSight(p);
        }
        return true;
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new net.minecraft.world.entity.ai.goal.OpenDoorGoal(this, false));
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
        if (estadoEspecial() == 1) return state.setAndContinue(anim("aturdido"));
        if (estadoEspecial() == 2) return state.setAndContinue(anim("cegado"));
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
