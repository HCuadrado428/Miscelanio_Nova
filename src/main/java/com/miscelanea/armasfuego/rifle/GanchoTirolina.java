package com.miscelanea.armasfuego.rifle;

import com.miscelanea.tirolina.Polea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Gancho con el que se baja por una tirolina del rifle. Hereda de la polea de las tirolinas de la feria (así el
 * jugador se cuelga con la misma pose y suena el mismo zumbido) pero sigue una cuerda guardada en {@link Tirolinas}.
 * Va más rápido cuanto más inclinada está la cuerda; agachado (o hacia atrás) frena. Solo existe mientras alguien baja.
 */
public class GanchoTirolina extends Polea {
    private static final EntityDataAccessor<Float> T = SynchedEntityData.defineId(GanchoTirolina.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> V = SynchedEntityData.defineId(GanchoTirolina.class, EntityDataSerializers.FLOAT);
    /** Dirección de la cuerda (de a hacia b), para orientar el gancho en el cliente. */
    private static final EntityDataAccessor<Vector3f> DIR = SynchedEntityData.defineId(GanchoTirolina.class, EntityDataSerializers.VECTOR3);

    private int linea;
    private Vec3 a = Vec3.ZERO, b = Vec3.ZERO;
    private int nivel, sentido = 1, vida;
    private boolean soltando;

    public GanchoTirolina(EntityType<? extends GanchoTirolina> t, Level l) {
        super(t, l);
    }

    public void preparar(Tirolinas.Linea x, float t, int sentido) {
        linea = x.id();
        a = x.a();
        b = x.b();
        nivel = x.nivel();
        this.sentido = sentido;
        Vec3 d = b.subtract(a).normalize();
        entityData.set(DIR, new Vector3f((float) d.x, (float) d.y, (float) d.z));
        entityData.set(T, t);
        entityData.set(V, sentido * 0.05F);
        Vec3 p = Cuerda.punto(a, b, t);
        setPos(p.x, p.y - 0.75, p.z);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(T, 0F);
        entityData.define(V, 0F);
        entityData.define(DIR, new Vector3f(1, 0, 0));
    }

    @Override
    public Vec3 a() {
        return a;
    }

    @Override
    public Vec3 b() {
        return b;
    }

    @Override
    public float t() {
        return entityData.get(T);
    }

    @Override
    public float velocidad() {
        return entityData.get(V);
    }

    public Vector3f direccion() {
        return entityData.get(DIR);
    }

    @Override
    public void tick() {
        // sin la física de la polea de postes: la de esta cuerda va aquí
        baseTick();
        if (level().isClientSide) return;
        vida++;
        Entity jinete = getFirstPassenger();
        if (jinete == null) {
            discard();
            return;
        }
        Tirolinas.Linea x = Tirolinas.buscar((ServerLevel) level(), linea);
        if (x == null || vida > 20 * 120) {
            // la han cortado (o algo raro): se cae
            soltar(jinete, Vec3.ZERO);
            return;
        }
        // en vez del "pulsa Mayús para bajarte" de vanilla (aquí agacharse frena)
        if (vida == 3 && jinete instanceof net.minecraft.world.entity.player.Player pl)
            pl.displayClientMessage(net.minecraft.network.chat.Component.translatable("msg.miscelanea.rifle.colgado"), true);
        float mult = 1F + 0.25F * nivel;
        float t = t(), v = velocidad();
        Vec3 tan = Cuerda.tangente(a, b, t);
        double largo = Math.max(0.5, tan.length());
        Vec3 u = tan.scale(1 / largo);
        v += (float) (-0.05 * u.y * mult);                   // gravedad a lo largo de la cuerda
        v *= 0.994F;                                         // aire
        v -= Math.signum(v) * 0.0012F;                       // rozamiento del gancho
        boolean frena = jinete.isShiftKeyDown() || jinete instanceof LivingEntity le && le.zza < -0.1F;
        boolean empuja = jinete instanceof LivingEntity le2 && le2.zza > 0.1F;
        if (frena) v *= 0.86F;
        else if (empuja) v += sentido * 0.004F;
        // nunca se queda parado a medio camino (ni se vuelve atrás)
        float min = frena ? 0.025F : 0.07F;
        if (v * sentido < min) v = sentido * min;
        float max = 1.2F * mult;
        v = Math.max(-max, Math.min(max, v));
        t += (float) (v / largo);
        float tc = Math.max(0, Math.min(1, t));
        Vec3 p = Cuerda.punto(a, b, tc);
        // ¿choca con algo? pies en el suelo cerca del final, o el cuerpo contra una pared
        boolean avanzado = sentido > 0 ? tc > 0.1F : tc < 0.9F;
        boolean choca = avanzado && (solido(p.x, p.y - 2.5, p.z) || solido(p.x, p.y - 1.4, p.z));
        if (t >= 1F || t <= 0F || choca) {
            entityData.set(T, tc);
            llegar(jinete, p, v);
            return;
        }
        entityData.set(T, t);
        entityData.set(V, v);
        setPos(p.x, p.y - 0.75, p.z);
        if (Math.abs(v) > 0.6F && tickCount % 2 == 0)
            ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.05);
    }

    private boolean solido(double x, double y, double z) {
        BlockPos q = BlockPos.containing(x, y, z);
        return !level().getBlockState(q).getCollisionShape(level(), q).isEmpty();
    }

    /** Llega al final (o choca): se suelta en el suelo justo antes, con la inercia. */
    private void llegar(Entity jinete, Vec3 p, float v) {
        Vec3 d = b.subtract(a);
        Vec3 h = new Vec3(d.x, 0, d.z);
        h = h.lengthSqr() < 1e-6 ? Vec3.ZERO : h.normalize().scale(Math.signum(v));
        Vec3 fin = new Vec3(p.x - h.x * 0.6, p.y - 2.8, p.z - h.z * 0.6);
        BlockPos bp = BlockPos.containing(fin);
        for (int i = 0; i < 4 && (!vacio(bp) || !vacio(bp.above())); i++) bp = bp.above();
        soltando = true;
        jinete.stopRiding();
        soltando = false;
        jinete.teleportTo(fin.x, Math.max(fin.y, bp.getY()), fin.z);
        jinete.setDeltaMovement(h.scale(Math.abs(v) * 0.6).add(0, 0.1, 0));
        jinete.hurtMarked = true;
        jinete.fallDistance = 0;
        discard();
    }

    private boolean vacio(BlockPos q) {
        return level().getBlockState(q).getCollisionShape(level(), q).isEmpty();
    }

    private void soltar(Entity jinete, Vec3 mov) {
        soltando = true;
        jinete.stopRiding();
        soltando = false;
        jinete.setDeltaMovement(mov);
        jinete.hurtMarked = true;
        discard();
    }

    @Override
    public boolean dejaBajar() {
        return soltando || isRemoved();
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity e) {
        Vec3 p = Cuerda.punto(a, b, t());
        return new Vec3(p.x, p.y - 2.8, p.z);
    }
}
