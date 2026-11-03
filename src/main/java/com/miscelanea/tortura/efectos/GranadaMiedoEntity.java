package com.miscelanea.tortura.efectos;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Granada de toxina del miedo en vuelo (echa humo). Al chocar suelta una nube persistente (como una poción persistente:
 * {@link AreaEffectCloud}) de gas verde: quien la respira se lleva {@link #DURACION} ticks de miedo.
 */
public class GranadaMiedoEntity extends ThrowableItemProjectile {
    /** Lo que da la nube (la nube aplica la cuarta parte de lo que lleva: 3600 / 4 = 900 = 45 s). */
    public static final int DURACION = 900;

    public GranadaMiedoEntity(EntityType<? extends GranadaMiedoEntity> t, Level l) {
        super(t, l);
    }

    public GranadaMiedoEntity(Level l, LivingEntity quien) {
        super(EfectosRegistro.GRANADA_LANZADA.get(), quien, l);
    }

    @Override
    protected Item getDefaultItem() {
        return EfectosRegistro.GRANADA_MIEDO.get();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide && tickCount % 2 == 0)
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.1, getZ(), 0, 0.02, 0);
    }

    @Override
    protected void onHit(HitResult r) {
        super.onHit(r);
        if (level().isClientSide || isRemoved() || !(level() instanceof ServerLevel sl)) return;
        Vec3 p = r.getLocation();
        AreaEffectCloud nube = new AreaEffectCloud(sl, p.x, p.y, p.z);
        Entity o = getOwner();
        if (o instanceof LivingEntity le) nube.setOwner(le);
        nube.setRadius(3.8F);
        nube.setRadiusOnUse(-0.05F);
        nube.setWaitTime(6);
        nube.setDuration(260);
        nube.setRadiusPerTick(-0.004F);
        nube.setFixedColor(MiedoEffect.COLOR);
        nube.addEffect(new MobEffectInstance(EfectosRegistro.MIEDO.get(), DURACION * 4));
        sl.addFreshEntity(nube);
        sl.playSound(null, p.x, p.y, p.z, EfectosRegistro.GAS_SISEO.get(), SoundSource.PLAYERS, 1.4F, 0.9F + random.nextFloat() * 0.2F);
        SustoPacket sp = new SustoPacket(SustoPacket.GAS, p.x, p.y, p.z);
        for (ServerPlayer pl : sl.players()) if (pl.position().distanceToSqr(p) < 48 * 48) EfectosRed.a(pl, sp);
        discard();
    }
}
