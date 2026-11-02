package com.miscelanea.muebles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Entidad invisible sobre la que se sienta el jugador; desaparece al levantarse o si rompen el mueble. */
public class Asiento extends Entity {
    public Asiento(EntityType<? extends Asiento> t, Level l) {
        super(t, l);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            BlockPos b = BlockPos.containing(getX(), getY() + 0.65, getZ());
            boolean mueble = level().getBlockState(b).getBlock() instanceof SillaBlock || level().getBlockState(b).getBlock() instanceof SofaBlock
                    || level().getBlockState(b).getBlock() instanceof TabureteBlock;
            if (getPassengers().isEmpty() || !mueble) discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        BlockPos b = BlockPos.containing(getX(), getY() + 0.65, getZ());
        Direction d = Direction.fromYRot(getYRot());
        BlockPos frente = b.relative(d);
        if (level().getBlockState(frente).getCollisionShape(level(), frente).isEmpty() && level().getBlockState(frente.above()).getCollisionShape(level(), frente.above()).isEmpty())
            return Vec3.atBottomCenterOf(frente);
        return Vec3.atBottomCenterOf(b.above());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
