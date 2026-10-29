package com.miscelanea.llave;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/** Strike Raid: la Llave Espada lanzada girando como un bumerán; golpea a la ida y a la vuelta y vuelve a la mano. */
public class LlaveLanzada extends Projectile implements GeoEntity {
    private static final RawAnimation GIRO = RawAnimation.begin().thenLoop("animation.llave_lanzada.giro");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<Integer> golpeados = new HashSet<>();
    private LivingEntity objetivo;
    private boolean vuelta;
    private int vida;

    public LlaveLanzada(EntityType<? extends LlaveLanzada> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public LlaveLanzada(Level level, Player duenyo, LivingEntity objetivo) {
        this(ModEntities.LLAVE_LANZADA.get(), level);
        setOwner(duenyo);
        this.objetivo = objetivo;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        vida++;
        Entity duenyo = getOwner();
        if (level().isClientSide) {
            Vec3 v = getDeltaMovement();
            setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
            if (vida % 2 == 0) level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.2, getZ(), 0, 0, 0);
            return;
        }
        if (!(duenyo instanceof ServerPlayer p) || !p.isAlive() || p.level() != level() || vida > 100) {
            discard();
            return;
        }
        Vec3 v;
        if (!vuelta) {
            if (objetivo != null && objetivo.isAlive()) {
                Vec3 obj = objetivo.position().add(0, objetivo.getBbHeight() * 0.5, 0);
                v = obj.subtract(position()).normalize().scale(1.3);
                if (obj.distanceTo(position()) < 0.9) vuelta = true;
            } else {
                v = getDeltaMovement();
            }
            if (vida > 10) vuelta = true;
            HitResult hit = level().clip(new ClipContext(position(), position().add(v), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
            if (hit.getType() != HitResult.Type.MISS) {
                vuelta = true;
                v = Vec3.ZERO;
            }
            if (vuelta) golpeados.clear();
        } else {
            Vec3 mano = p.position().add(0, p.getBbHeight() * 0.6, 0);
            Vec3 d = mano.subtract(position());
            if (d.length() < 1.4) {
                level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.LLAVE_IMPACTO.get(), SoundSource.PLAYERS, 0.5F, 1.6F);
                discard();
                return;
            }
            v = d.normalize().scale(Math.min(1.7, 0.9 + vida * 0.05));
        }
        setDeltaMovement(v);
        // golpea a todo lo que pilla en el camino (una vez a la ida y otra a la vuelta)
        for (Entity e : level().getEntities(this, getBoundingBox().expandTowards(v).inflate(0.6))) {
            if (!(e instanceof LivingEntity le) || e == p || !le.isAlive() || !golpeados.add(e.getId())) continue;
            le.invulnerableTime = 0;
            if (le.hurt(damageSources().playerAttack(p), 6.0F)) {
                le.knockback(0.4, -v.x, -v.z);
                ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, le.getX(), le.getY() + le.getBbHeight() * 0.5, le.getZ(), 8, 0.2, 0.2, 0.2, 0.3);
                level().playSound(null, le.getX(), le.getY(), le.getZ(), ModSounds.LLAVE_IMPACTO.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
                Magia.ganar(p, 2F);
            }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        if (vida % 4 == 0) level().playSound(null, getX(), getY(), getZ(), ModSounds.LLAVE_GOLPE.get(), SoundSource.PLAYERS, 0.4F, 1.6F);
    }

    @Override
    public void remove(RemovalReason motivo) {
        if (!level().isClientSide && getOwner() instanceof Player p) {
            for (var s : new net.minecraft.world.item.ItemStack[]{p.getMainHandItem(), p.getOffhandItem()})
                if (s.getItem() instanceof LlaveEspadaItem && LlaveEspadaItem.lanzada(s)) LlaveEspadaItem.ponerLanzada(s, false);
            for (var s : p.getInventory().items)
                if (s.getItem() instanceof LlaveEspadaItem && LlaveEspadaItem.lanzada(s)) LlaveEspadaItem.ponerLanzada(s, false);
        }
        super.remove(motivo);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "giro", 0, s -> s.setAndContinue(GIRO)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
