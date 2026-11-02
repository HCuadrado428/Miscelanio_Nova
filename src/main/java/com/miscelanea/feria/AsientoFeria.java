package com.miscelanea.feria;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Cabina de la noria o caballito del tiovivo. Su sitio sale de la atracción (ángulo según el tiempo), así que se mueve
 * igual en el servidor y en cada cliente. Clic derecho para subirse; agacharse para bajar.
 */
public class AsientoFeria extends Entity {
    private static final EntityDataAccessor<BlockPos> ATRACCION = SynchedEntityData.defineId(AsientoFeria.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> INDICE = SynchedEntityData.defineId(AsientoFeria.class, EntityDataSerializers.INT);

    public AsientoFeria(EntityType<? extends AsientoFeria> t, Level l) {
        super(t, l);
        this.noPhysics = true;
    }

    public void preparar(BlockPos atraccion, int i) {
        entityData.set(ATRACCION, atraccion);
        entityData.set(INDICE, i);
        colocar(1);
    }

    public BlockPos atraccion() {
        return entityData.get(ATRACCION);
    }

    public int indice() {
        return entityData.get(INDICE);
    }

    public AtraccionBlockEntity be() {
        return level().getBlockEntity(atraccion()) instanceof AtraccionBlockEntity b ? b : null;
    }

    public boolean esNoria() {
        AtraccionBlockEntity b = be();
        return b == null || b.tipo() == AtraccionBlock.NORIA;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(ATRACCION, BlockPos.ZERO);
        entityData.define(INDICE, 0);
    }

    void colocar(float parcial) {
        AtraccionBlockEntity b = be();
        if (b == null) return;
        double[] p = b.puesto(indice(), level().getGameTime() + parcial);
        setPos(p[0], p[1], p[2]);
        setYRot((float) p[3]);
    }

    @Override
    public void tick() {
        xo = getX();
        yo = getY();
        zo = getZ();
        yRotO = getYRot();
        super.tick();
        AtraccionBlockEntity b = be();
        if (b == null) {
            if (!level().isClientSide && tickCount > 20) discard();
            return;
        }
        colocar(0);
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) p.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public double getPassengersRidingOffset() {
        return esNoria() ? 0.75 : 0.85;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < (esNoria() ? 4 : 1);
    }

    /** En la cabina de la noria caben cuatro: dos bancos, uno frente a otro. */
    @Override
    protected void positionRider(Entity p, MoveFunction mover) {
        if (!hasPassenger(p)) return;
        if (!esNoria()) {
            super.positionRider(p, mover);
            return;
        }
        int i = getPassengers().indexOf(p);
        double ox = i % 2 == 0 ? -0.45 : 0.45, oz = i < 2 ? -0.6 : 0.6;
        double yaw = Math.toRadians(getYRot()), c = Math.cos(yaw), s = Math.sin(yaw);
        mover.accept(p, getX() + ox * c - oz * s, getY() + getPassengersRidingOffset() + p.getMyRidingOffset(), getZ() + oz * c + ox * s);
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        AtraccionBlockEntity b = be();
        if (b != null && b.tipo() == AtraccionBlock.NORIA) {
            // al bajar de la noria se baja en el suelo, delante de la base
            return Vec3.atBottomCenterOf(atraccion().relative(b.facing(), 3));
        }
        return super.getDismountLocationForPassenger(e);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(ATRACCION, BlockPos.of(t.getLong("atr")));
        entityData.set(INDICE, t.getInt("i"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putLong("atr", atraccion().asLong());
        t.putInt("i", indice());
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 96 * 96;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
