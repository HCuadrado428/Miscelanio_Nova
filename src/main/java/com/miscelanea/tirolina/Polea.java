package com.miscelanea.tirolina;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Polea que baja por el cable con el jugador colgado: gravedad a lo largo de la pendiente, rozamiento y velocidad máxima. */
public class Polea extends Entity {
    private static final EntityDataAccessor<BlockPos> A = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<BlockPos> B = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> T = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(Polea.class, EntityDataSerializers.FLOAT);
    private int vida;
    /** +1 si va hacia el poste B, -1 si va hacia el A. */
    private int sentido;
    private boolean soltando;

    public Polea(EntityType<? extends Polea> t, net.minecraft.world.level.Level l) {
        super(t, l);
        this.noPhysics = true;
    }

    public void preparar(BlockPos a, BlockPos b, float t, float v) {
        entityData.set(A, a);
        entityData.set(B, b);
        entityData.set(T, t);
        entityData.set(V, v);
        sentido = v >= 0 ? 1 : -1;
        Vec3 p = Cable.punto(Cable.anclaje(a), Cable.anclaje(b), t);
        setPos(p.x, p.y - 0.75, p.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(A, BlockPos.ZERO);
        entityData.define(B, BlockPos.ZERO);
        entityData.define(T, 0F);
        entityData.define(V, 0F);
    }

    public Vec3 a() {
        return Cable.anclaje(entityData.get(A));
    }

    public Vec3 b() {
        return Cable.anclaje(entityData.get(B));
    }

    public float t() {
        return entityData.get(T);
    }

    public float velocidad() {
        return entityData.get(V);
    }

    /** Velocidad mínima hacia el destino (bloques/tic) y la mínima frenando: nunca se queda parado a mitad de cable. */
    public static final float V_MIN = 0.09F, V_MIN_FRENO = 0.035F;

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        vida++;
        Entity jinete = getFirstPassenger();
        if (jinete == null) {
            discard();
            return;
        }
        Vec3 a = a(), b = b();
        float t = t(), v = velocidad();
        if (sentido == 0) sentido = v >= 0 ? 1 : -1;
        if (vida > 20 * 120) {
            // red de seguridad: nunca se suelta en el aire, se le deja en el poste más cercano
            llegar(jinete, t >= 0.5F, 0);
            return;
        }
        Vec3 tan = Cable.tangente(a, b, t);
        double largo = Math.max(0.5, tan.length());
        Vec3 u = tan.scale(1 / largo);
        v += (float) (-0.045 * u.y);                       // gravedad a lo largo del cable
        v *= 0.994F;                                         // rozamiento del aire
        v -= Math.signum(v) * 0.0015F;                       // rozamiento de la polea
        // frenar: agachado (o tecla atrás) aprieta la polea contra el cable
        boolean frena = jinete.isShiftKeyDown() || jinete instanceof LivingEntity le && le.zza < -0.1F;
        if (frena) v *= 0.9F;
        // siempre hacia el destino y con una velocidad mínima: con postes a la misma altura antes se paraba en
        // medio (o se volvía atrás) y a los dos minutos el jugador caía desde lo alto
        float min = frena ? V_MIN_FRENO : V_MIN;
        if (v * sentido < min) v = sentido * min;
        v = Math.max(-1.3F, Math.min(1.3F, v));
        t += (float) (v / largo);
        Vec3 cp = Cable.punto(a, b, Math.max(0, Math.min(1, t)));
        // si los pies van a tocar el suelo, se suelta (el cable acaba cerca del suelo)
        BlockPos pies = BlockPos.containing(cp.x, cp.y - 2.6, cp.z);
        // (solo cerca del poste de llegada: en la salida los pies están a la altura del propio poste)
        boolean cercaFin = sentido > 0 ? t > 0.92F : t < 0.08F;
        boolean suelo = cercaFin && !level().getBlockState(pies).getCollisionShape(level(), pies).isEmpty();
        if (t >= 1F || t <= 0F || suelo) {
            t = Math.max(0F, Math.min(1F, t));
            entityData.set(T, t);
            llegar(jinete, sentido > 0, Math.abs(v));
            return;
        }
        entityData.set(T, t);
        entityData.set(V, v);
        Vec3 p = Cable.punto(a, b, t);
        setPos(p.x, p.y - 0.75, p.z);
        if (Math.abs(v) > 0.6F && tickCount % 2 == 0)
            ((ServerLevel) level()).sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.05);
    }

    /** Dirección horizontal del viaje hacia el poste B (enB) o hacia el A. */
    private Vec3 haciaPoste(boolean enB) {
        Vec3 d = b().subtract(a());
        d = new Vec3(d.x, 0, d.z);
        if (d.lengthSqr() < 1e-6) d = new Vec3(1, 0, 0);
        d = d.normalize();
        return enB ? d : d.scale(-1);
    }

    /** Sitio seguro en el suelo delante del poste de llegada (enB: el poste B; si no, el A). */
    private Vec3 aterrizaje(boolean enB) {
        Vec3 dir = haciaPoste(enB);
        BlockPos poste = entityData.get(enB ? B : A);
        Vec3 cp = Cable.punto(a(), b(), enB ? 1 : 0);
        Vec3 fin = new Vec3(cp.x - dir.x * 1.3, Math.min(cp.y - 2.4, poste.getY() + 1.5), cp.z - dir.z * 1.3);
        BlockPos bp = BlockPos.containing(fin);
        for (int i = 0; i < 6 && !level().getBlockState(bp).getCollisionShape(level(), bp).isEmpty(); i++) bp = bp.above();
        for (int i = 0; i < 8 && level().getBlockState(bp.below()).getCollisionShape(level(), bp.below()).isEmpty(); i++) bp = bp.below();
        return new Vec3(fin.x, bp.getY(), fin.z);
    }

    private void llegar(Entity jinete, boolean enB, float v) {
        com.miscelanea.logros.LogrosMiscelanea.dar(jinete, "tirolina");
        soltando = true;
        jinete.stopRiding();
        soltando = false;
        // se suelta delante del poste de llegada, sobre el suelo
        Vec3 dir = haciaPoste(enB);
        Vec3 fin = aterrizaje(enB);
        jinete.teleportTo(fin.x, fin.y, fin.z);
        jinete.setDeltaMovement(dir.x * v * 0.5, v > 0 ? 0.25 : 0, dir.z * v * 0.5);
        jinete.hurtMarked = true;
        jinete.fallDistance = 0;
        discard();
    }

    /** ¿Puede el jugador soltarse ahora? Solo cuando la polea lo deja en el poste (agacharse sirve para frenar). */
    public boolean dejaBajar() {
        return soltando || isRemoved();
    }

    @Override
    public double getPassengersRidingOffset() {
        return -1.7;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        // si por lo que sea se baja a medio camino, al suelo junto al poste más cercano (nunca desde lo alto)
        return aterrizaje(t() >= 0.5F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
