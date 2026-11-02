package com.miscelanea.armas;

import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Onda de choque del golpe sísmico: un anillo de polvo y piedras que se abre por el suelo durante medio segundo. */
public class OndaEntity extends Entity {
    private float radio = 6;

    public OndaEntity(EntityType<? extends OndaEntity> t, Level l) {
        super(t, l);
        noPhysics = true;
    }

    public OndaEntity(Level l, double x, double y, double z, float radio) {
        this(ModEntities.ONDA.get(), l);
        setPos(x, y, z);
        this.radio = radio;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel sl)) return;
        int dura = 12;
        float r = 0.8F + (radio - 0.8F) * tickCount / dura;
        int n = (int) (r * 7);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + tickCount * 0.1;
            double x = getX() + Math.cos(a) * r, z = getZ() + Math.sin(a) * r;
            BlockPos b = BlockPos.containing(x, getY() - 0.2, z);
            BlockState st = sl.getBlockState(b);
            if (st.isAir()) st = sl.getBlockState(b.below());
            if (!st.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), x, getY() + 0.15, z, 3, 0.15, 0.1, 0.15, 0.25);
            if (i % 2 == 0) sl.sendParticles(ParticleTypes.CLOUD, x, getY() + 0.3, z, 1, 0.05, 0.05, 0.05, 0.03);
            if (i % 5 == 0) sl.sendParticles(ParticleTypes.CRIT, x, getY() + 0.4, z, 1, 0, 0.1, 0, 0.2);
        }
        if (tickCount >= dura) discard();
    }
}
