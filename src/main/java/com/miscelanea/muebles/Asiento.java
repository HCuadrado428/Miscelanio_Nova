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
import net.minecraft.util.Mth;
import net.minecraftforge.network.NetworkHooks;

/** Entidad invisible sobre la que se sienta el jugador; desaparece al levantarse o si rompen el mueble. */
public class Asiento extends Entity {
    /** El asiento queda {@link #SOBRE_COJIN} por debajo del cojín: así el trasero del jugador (sentado, ~0,58 sobre sus pies
     *  y 0,35 por debajo del asiento) apoya justo encima del cojín, con las piernas por fuera. */
    public static final double SOBRE_COJIN = 0.23;
    /** Desde el asiento, altura a la que se busca el bloque del mueble (dentro del cojín). */
    static final double ALTO_BLOQUE = SOBRE_COJIN - 0.1;

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
            BlockPos b = BlockPos.containing(getX(), getY() + ALTO_BLOQUE, getZ());
            boolean mueble = level().getBlockState(b).getBlock() instanceof SillaBlock || level().getBlockState(b).getBlock() instanceof SofaBlock
                    || level().getBlockState(b).getBlock() instanceof TabureteBlock
                    || level().getBlockState(b).getBlock() instanceof com.miscelanea.arcade.CabinaArcadeBlock;
            if (getPassengers().isEmpty() || !mueble) discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    /** Como en las barcas: el cuerpo mira hacia delante del mueble y la cabeza gira como mucho 105°. */
    private void encarar(Entity e) {
        e.setYBodyRot(getYRot());
        float f = Mth.wrapDegrees(e.getYRot() - getYRot());
        float g = Mth.clamp(f, -105.0F, 105.0F);
        e.yRotO += g - f;
        e.setYRot(e.getYRot() + g - f);
        e.setYHeadRot(e.getYRot());
    }

    @Override
    protected void positionRider(Entity e, Entity.MoveFunction mover) {
        super.positionRider(e, mover);
        encarar(e);
    }

    @Override
    public void onPassengerTurned(Entity e) {
        encarar(e);
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        BlockPos b = BlockPos.containing(getX(), getY() + ALTO_BLOQUE, getZ());
        Direction d = Direction.fromYRot(getYRot());
        if (level().getBlockState(b).getBlock() instanceof com.miscelanea.arcade.CabinaArcadeBlock) d = d.getOpposite();
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
