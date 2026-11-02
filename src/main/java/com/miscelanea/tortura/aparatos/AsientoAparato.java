package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/**
 * Asiento invisible de un aparato: la víctima va "montada" en él, así que no se puede mover ni caer (en el servidor y
 * en el cliente) y queda exactamente donde el aparato dice ({@link AparatoBlockEntity#sitioVictima}). Desaparece si el
 * aparato ya no existe o si se queda sin pasajero.
 */
public class AsientoAparato extends Entity {
    private static final EntityDataAccessor<Boolean> SENTAR = SynchedEntityData.defineId(AsientoAparato.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> GIRO_CABEZA = SynchedEntityData.defineId(AsientoAparato.class, EntityDataSerializers.FLOAT);
    private BlockPos aparato = BlockPos.ZERO;
    private int vacio;

    public AsientoAparato(EntityType<? extends AsientoAparato> t, Level l) {
        super(t, l);
        this.noPhysics = true;
        setNoGravity(true);
    }

    public AsientoAparato(Level l, BlockPos aparato, Vec3 pos, float yaw, boolean sentar, float giroCabeza) {
        this(AparatosRegistro.ASIENTO.get(), l);
        this.aparato = aparato.immutable();
        setPos(pos.x, pos.y, pos.z);
        setYRot(yaw);
        this.yRotO = yaw;
        entityData.set(SENTAR, sentar);
        entityData.set(GIRO_CABEZA, giroCabeza);
    }

    public boolean esDe(BlockPos p) {
        return aparato.equals(p);
    }

    public BlockPos aparato() {
        return aparato;
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(SENTAR, false);
        entityData.define(GIRO_CABEZA, 100F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        aparato = BlockPos.of(t.getLong("Aparato"));
        entityData.set(SENTAR, t.getBoolean("Sentar"));
        if (t.contains("GiroCabeza")) entityData.set(GIRO_CABEZA, t.getFloat("GiroCabeza"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putLong("Aparato", aparato.asLong());
        t.putBoolean("Sentar", entityData.get(SENTAR));
        t.putFloat("GiroCabeza", entityData.get(GIRO_CABEZA));
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(Vec3.ZERO);
        if (level().isClientSide) return;
        if (getPassengers().isEmpty()) {
            if (++vacio > 5) discard();
            return;
        }
        vacio = 0;
        if (tickCount % 10 == 0) {
            Entity pas = getPassengers().get(0);
            if (!(level().getBlockEntity(aparato) instanceof AparatoBlockEntity be) || !be.esVictima(pas)) {
                ejectPassengers();
                discard();
            }
        }
    }

    @Override
    public boolean shouldRiderSit() {
        return entityData.get(SENTAR);
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.0;
    }

    @Override
    protected void positionRider(Entity e, Entity.MoveFunction mover) {
        if (!hasPassenger(e)) return;
        mover.accept(e, getX(), getY(), getZ());
        encarar(e);
    }

    /** El cuerpo mira hacia donde manda el aparato y la cabeza gira como mucho lo que se deje. */
    private void encarar(Entity e) {
        float max = entityData.get(GIRO_CABEZA);
        e.setYBodyRot(getYRot());
        if (e instanceof LivingEntity le) {
            le.yBodyRotO = getYRot();
        }
        float f = Mth.wrapDegrees(e.getYRot() - getYRot());
        float g = Mth.clamp(f, -max, max);
        e.yRotO += g - f;
        e.setYRot(e.getYRot() + g - f);
        e.setYHeadRot(e.getYRot());
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
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        if (level().getBlockEntity(aparato) instanceof AparatoBlockEntity be) return be.salida(e);
        return super.getDismountLocationForPassenger(e);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
