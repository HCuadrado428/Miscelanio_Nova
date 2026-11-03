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
import net.minecraft.util.Mth;
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

    public boolean esPulpo() {
        AtraccionBlockEntity b = be();
        return b != null && b.tipo() == AtraccionBlock.PULPO;
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
        double[] p = b.puesto(indice(), level().getGameTime() + (double) parcial);
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
            // si el chunk de la atracción no está cargado el asiento se queda quieto esperando (antes desaparecía
            // y tiraba a la gente desde lo alto de la noria)
            if (!level().isClientSide && tickCount > 20 && level().isLoaded(atraccion())) {
                // la atracción ya no existe: baja a los pasajeros al suelo, junto a donde estaba la base
                for (Entity p : java.util.List.copyOf(getPassengers())) {
                    p.stopRiding();
                    Vec3 s = sueloSeguro();
                    p.teleportTo(s.x, s.y, s.z);
                    p.fallDistance = 0;
                }
                discard();
            }
            return;
        }
        colocar(0);
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive() || p.isPassenger() || !canAddPassenger(p)) return InteractionResult.PASS;
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

    /** En el caballito la silla queda 0,26 por detrás de la barra (hacia atrás del caballo). */
    private static final double SILLA_Z = -0.26;

    @Override
    public double getPassengersRidingOffset() {
        // medido con la pose de montar: la parte de abajo de los muslos queda 0,588 por encima de los pies del jugador, y
        // los pies van 0,35 por debajo de este valor → cojín de la cápsula de la noria a 0,50 (bajo la cadera), banco de la
        // taza del pulpo a 0,248, silla del caballito a 0,758
        return esNoria() ? 0.262 : esPulpo() ? 0.01 : 0.52;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().size() < (esNoria() ? 4 : esPulpo() ? 2 : 1);
    }

    /** En la cápsula de la noria caben cuatro: dos bancos, uno frente a otro, en los costados (x) de la cápsula; así se ve a
     *  todos de perfil por los huecos de delante y de detrás. */
    @Override
    protected void positionRider(Entity p, MoveFunction mover) {
        if (!hasPassenger(p)) return;
        boolean pulpo = esPulpo();
        float giro = Mth.wrapDegrees(getYRot() - yRotO);
        double yaw = Math.toRadians(getYRot()), c = Math.cos(yaw), s = Math.sin(yaw);
        if (!esNoria() && !pulpo) {
            // caballito: el jinete va en la silla, un poco por detrás de la barra (que pasa por la cruz del caballo)
            mover.accept(p, getX() - SILLA_Z * s, getY() + getPassengersRidingOffset() + p.getMyRidingOffset(), getZ() + SILLA_Z * c);
            Montar.seguir(p, giro, getYRot());
            return;
        }
        int i = getPassengers().indexOf(p);
        double ox, oz;   // sobre el banco de cada uno
        if (pulpo) {
            ox = i % 2 == 0 ? -0.42 : 0.42;
            oz = 0.32;
        } else {
            ox = i < 2 ? -0.58 : 0.58;
            oz = i % 2 == 0 ? -0.42 : 0.42;
        }
        mover.accept(p, getX() + ox * c - oz * s, getY() + getPassengersRidingOffset() + p.getMyRidingOffset(), getZ() + oz * c + ox * s);
        // en la noria los dos bancos van frente a frente
        Montar.seguir(p, giro, getYRot() + mira(i, pulpo));
    }

    @Override
    public void onPassengerTurned(Entity p) {
        int i = getPassengers().indexOf(p);
        Montar.encarar(p, getYRot() + mira(i, esPulpo()));
    }

    /** Hacia dónde mira el cuerpo del pasajero p (grados), con el asiento interpolado al fotograma pt. */
    public float rumboPasajero(Entity p, float pt) {
        int i = getPassengers().indexOf(p);
        boolean pulpo = esPulpo();
        float base = Mth.rotLerp(pt, yRotO, getYRot());
        return esNoria() || pulpo ? base + mira(Math.max(i, 0), pulpo) : base;
    }

    /** Hacia dónde mira el pasajero respecto a la cabina: en la noria los bancos van frente a frente (el de x- mira hacia
     *  +x, que es girar -90°, y el de x+ al revés); en el pulpo el banco está al fondo de la cesta y se mira hacia delante
     *  (hacia el lado abierto). */
    private static float mira(int i, boolean pulpo) {
        return pulpo ? 180F : i < 2 ? -90F : 90F;
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        AtraccionBlockEntity b = be();
        if (b != null && b.tipo() == AtraccionBlock.PULPO) return Vec3.atBottomCenterOf(atraccion().relative(b.facing(), 7));
        if (b != null && b.tipo() == AtraccionBlock.NORIA) {
            // al bajar de la noria se baja en el suelo, delante de la base
            return Vec3.atBottomCenterOf(atraccion().relative(b.facing(), 3));
        }
        if (b == null) return sueloSeguro();
        return super.getDismountLocationForPassenger(e);
    }

    /** Un sitio en el suelo junto a la base de la atracción (si no se sabe nada mejor). */
    private Vec3 sueloSeguro() {
        BlockPos q = atraccion().above();
        for (int i = 0; i < 8 && !level().getBlockState(q).getCollisionShape(level(), q).isEmpty(); i++) q = q.above();
        for (int i = 0; i < 24 && level().getBlockState(q.below()).getCollisionShape(level(), q.below()).isEmpty(); i++) q = q.below();
        return Vec3.atBottomCenterOf(q);
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
