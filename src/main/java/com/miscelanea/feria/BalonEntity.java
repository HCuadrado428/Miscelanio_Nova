package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Balón de la máquina de canastas: vuela con su parábola, bota y desaparece a los pocos segundos. */
public class BalonEntity extends Entity {
    public boolean anotado;
    public double distancia;
    public float giro, giroO;

    public BalonEntity(EntityType<? extends BalonEntity> t, Level l) {
        super(t, l);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        giroO = giro;
        giro += 18;
        Vec3 v = getDeltaMovement().add(0, -0.045, 0).scale(0.99);
        Vec3 antes = v;
        move(MoverType.SELF, v);
        Vec3 d = getDeltaMovement();
        if (horizontalCollision) d = new Vec3(Math.abs(d.x) < 1e-4 ? -antes.x * 0.6 : d.x, d.y, Math.abs(d.z) < 1e-4 ? -antes.z * 0.6 : d.z);
        if (verticalCollision && antes.y < -0.08) {
            d = new Vec3(d.x * 0.8, -antes.y * 0.6, d.z * 0.8);
            if (!level().isClientSide) level().playSound(null, getX(), getY(), getZ(), ModSounds.FE_BOTE.get(), SoundSource.NEUTRAL, 0.6F, 1F);
        }
        setDeltaMovement(d);
        if (!level().isClientSide && tickCount > 90) discard();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
