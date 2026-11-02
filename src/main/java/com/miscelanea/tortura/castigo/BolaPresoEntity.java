package com.miscelanea.tortura.castigo;

import com.miscelanea.tortura.TorturaRegistro;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Bola de hierro de preso (The Escapists), unida con una cadena al tobillo del preso. Física propia: cae, bota poco
 * (pesa), rueda y se frena; la cadena tira de ella cuando el preso se aleja (y si se queda enganchada, tira de él).
 * Estados: 0 en el suelo, 1 en brazos del preso (Shift + clic derecho para cogerla, Shift otra vez para dejarla), 2
 * volando (lanzada con clic derecho mientras la lleva: golpea fuerte, pero la cadena la frena enseguida).
 */
public class BolaPresoEntity extends Entity {
    private static final EntityDataAccessor<Integer> PRESO = SynchedEntityData.defineId(BolaPresoEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> ESTADO = SynchedEntityData.defineId(BolaPresoEntity.class, EntityDataSerializers.BYTE);
    public static final byte SUELO = 0, EN_BRAZOS = 1, VOLANDO = 2;
    public static final double CADENA = 2.1, RADIO = 0.25, CADENA_LANZADA = 3.4;
    public static final float DANO = 9F;

    @Nullable private UUID presoId;
    private int sinPreso, vuelo;
    private boolean soltarListo;
    // ---- cliente: giro al rodar
    public final Quaternionf giro = new Quaternionf(), giroO = new Quaternionf();
    private double lx, ly, lz;
    private int lPasos;

    public BolaPresoEntity(EntityType<? extends BolaPresoEntity> t, Level l) {
        super(t, l);
    }

    BolaPresoEntity(Level l, LivingEntity preso) {
        this(TorturaRegistro.BOLA_PRESO_ENT.get(), l);
        presoId = preso.getUUID();
        entityData.set(PRESO, preso.getId());
        Vec3 atras = Vec3.directionFromRotation(0, preso.getYRot()).scale(-0.8);
        Vec3 p = preso.position().add(atras).add(0, 0.3, 0);
        setPos(p.x, p.y, p.z);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PRESO, -1);
        entityData.define(ESTADO, SUELO);
    }

    public int presoIdRed() {
        return entityData.get(PRESO);
    }

    public byte estado() {
        return entityData.get(ESTADO);
    }

    void estado(byte e) {
        entityData.set(ESTADO, e);
    }

    @Nullable
    public UUID presoUUID() {
        return presoId;
    }

    @Nullable
    public LivingEntity preso() {
        if (level().isClientSide) {
            Entity e = level().getEntity(presoIdRed());
            return e instanceof LivingEntity le ? le : null;
        }
        if (presoId == null || !(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(presoId);
        return e instanceof LivingEntity le && e.isAlive() ? le : null;
    }

    /** Tobillo (aprox.) del preso: de ahí sale la cadena. */
    public static Vec3 tobillo(LivingEntity p, float pt) {
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.getPosition(pt).add(der.scale(0.12 * p.getBbWidth() / 0.6)).add(0, 0.12, 0);
    }

    /** Donde va la bola en brazos: delante del pecho. */
    public static Vec3 enBrazos(LivingEntity p, float pt) {
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        return p.getPosition(pt).add(f.scale(0.48)).add(0, p.getBbHeight() * 0.42 - RADIO, 0);
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            tickCliente();
            return;
        }
        LivingEntity p = preso();
        if (p == null || !Bolas.esSuya(p, this)) {
            if (++sinPreso > 40) discard();
            fisica(null);
            return;
        }
        sinPreso = 0;
        if (entityData.get(PRESO) != p.getId()) entityData.set(PRESO, p.getId());
        if (estado() == EN_BRAZOS) {
            if (!(p instanceof Player pl) || p.isPassenger()) {
                estado(SUELO);
            } else {
                Vec3 b = enBrazos(p, 1F);
                setPos(b.x, b.y, b.z);
                setDeltaMovement(Vec3.ZERO);
                // Shift otra vez (tras soltarlo) la deja en el suelo
                if (!pl.isShiftKeyDown()) soltarListo = true;
                else if (soltarListo) {
                    estado(SUELO);
                    level().playSound(null, getX(), getY(), getZ(), TorturaRegistro.BOLA_GOLPE.get(), SoundSource.PLAYERS, 0.6F, 1.1F);
                }
                return;
            }
        }
        fisica(p);
    }

    private void fisica(@Nullable LivingEntity p) {
        boolean volando = estado() == VOLANDO;
        Vec3 v = getDeltaMovement().add(0, -0.08, 0);
        if (volando && p != null) {
            vuelo++;
            Vec3 a = position(), b = a.add(v);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level(), this, a, b, getBoundingBox().expandTowards(v).inflate(0.6),
                    e -> e != p && e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e.isPickable() && !e.isPassengerOfSameVehicle(p));
            if (hit != null) {
                Entity t = hit.getEntity();
                t.hurt(damageSources().thrown(this, p), DANO);
                if (t instanceof LivingEntity le) le.knockback(0.6, -v.x, -v.z);
                level().playSound(null, t.getX(), t.getY() + 1, t.getZ(), TorturaRegistro.BOLA_GOLPE.get(), SoundSource.PLAYERS, 1.2F, 0.7F);
                v = new Vec3(-v.x * 0.15, 0.1, -v.z * 0.15);
                estado(SUELO);
                volando = false;
            }
        }
        Vec3 antes = v;
        setDeltaMovement(v);
        move(MoverType.SELF, v);
        v = getDeltaMovement();
        if (horizontalCollision) {
            v = new Vec3(Math.abs(v.x) < 1.0E-4 ? -antes.x * 0.3 : v.x, v.y, Math.abs(v.z) < 1.0E-4 ? -antes.z * 0.3 : v.z);
            if (antes.horizontalDistance() > 0.15) golpe(0.6F);
        }
        if (verticalCollision && antes.y < -0.22) {
            v = new Vec3(v.x * 0.7, -antes.y * 0.28, v.z * 0.7);
            golpe((float) Math.min(1.2, -antes.y * 1.4));
            if (volando && vuelo > 2) estado(SUELO);
        }
        if (onGround()) v = new Vec3(v.x * 0.8, v.y, v.z * 0.8);
        else v = v.scale(0.985);
        if (p != null) v = cadena(p, v, volando);
        setDeltaMovement(v);
        if (onGround() && v.horizontalDistance() > 0.035 && tickCount % 6 == 0)
            level().playSound(null, getX(), getY(), getZ(), TorturaRegistro.BOLA_ARRASTRE.get(), SoundSource.PLAYERS,
                    (float) Math.min(0.8, 0.25 + v.horizontalDistance() * 3), 0.85F + random.nextFloat() * 0.3F);
    }

    /** La cadena: tira de la bola hacia el tobillo y, si va muy tensa (enganchada o lanzada), tira del preso. */
    private Vec3 cadena(LivingEntity p, Vec3 v, boolean volando) {
        Vec3 anc = tobillo(p, 1F), c = position().add(0, RADIO, 0);
        Vec3 d = c.subtract(anc);
        double dist = d.length(), largo = volando ? CADENA_LANZADA : CADENA;
        if (dist <= largo || dist < 1.0E-4) return v;
        Vec3 n = d.scale(1 / dist);
        double sobra = dist - largo;
        double radial = v.dot(n);
        if (radial > 0) v = v.subtract(n.scale(radial));
        // la arrastra hacia él (chocando con los bloques)
        Vec3 corr = n.scale(-Math.min(sobra, 0.5) * 0.75);
        move(MoverType.SELF, new Vec3(corr.x, Math.max(corr.y, onGround() ? 0.0 : corr.y), corr.z));
        v = v.add(corr.scale(0.5));
        if (volando) {
            // tirón: la bola se para y el preso da un traspié hacia ella
            v = v.scale(0.3);
            empujar(p, n.scale(Math.min(0.5, sobra * 0.5)));
            estado(SUELO);
        } else if (sobra > 0.45) {
            // enganchada o muy pesada: el preso no puede seguir
            empujar(p, n.scale(Math.min(0.3, (sobra - 0.45) * 0.25)));
        }
        if (dist > 12) {
            // teletransportado o algo raro: vuelve junto a él
            Vec3 b = anc.add(n.scale(-0.8));
            setPos(b.x, b.y, b.z);
            v = Vec3.ZERO;
        }
        return v;
    }

    private static void empujar(LivingEntity p, Vec3 hacia) {
        p.setDeltaMovement(p.getDeltaMovement().add(hacia.x, Math.max(0, hacia.y) * 0.3, hacia.z));
        p.hurtMarked = true;
    }

    private void golpe(float fuerza) {
        level().playSound(null, getX(), getY(), getZ(), TorturaRegistro.BOLA_GOLPE.get(), SoundSource.PLAYERS, Math.min(1.2F, fuerza), 0.8F + random.nextFloat() * 0.2F);
    }

    /** Lanzarla (el preso la lleva en brazos). */
    void lanzar(ServerPlayer p) {
        Vec3 mira = p.getLookAngle();
        Vec3 b = enBrazos(p, 1F).add(mira.scale(0.3));
        setPos(b.x, b.y, b.z);
        setDeltaMovement(mira.scale(0.95).add(0, 0.22, 0).add(p.getDeltaMovement().multiply(1, 0, 1)));
        estado(VOLANDO);
        vuelo = 0;
        level().playSound(null, p.getX(), p.getY() + 1, p.getZ(), TorturaRegistro.TOMATE_LANZAR.get(), SoundSource.PLAYERS, 1F, 0.5F);
    }

    // ------------------------------------------------------------------ cliente
    private void tickCliente() {
        giroO.set(giro);
        if (lPasos > 0) {
            double nx = getX() + (lx - getX()) / lPasos, ny = getY() + (ly - getY()) / lPasos, nz = getZ() + (lz - getZ()) / lPasos;
            lPasos--;
            setPos(nx, ny, nz);
        }
        LivingEntity p = preso();
        if (estado() == EN_BRAZOS && p != null) {
            Vec3 b = enBrazos(p, 1F);
            setPos(b.x, b.y, b.z);
            return;
        }
        // rueda: gira sobre el eje perpendicular al avance (ángulo = recorrido / radio)
        double dx = getX() - xo, dz = getZ() - zo;
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist > 1.0E-4 && dist < 2) {
            float ang = (float) (dist / RADIO);
            Quaternionf q = new Quaternionf().rotationAxis(ang, (float) (dz / dist), 0F, (float) (-dx / dist));
            giro.premul(q).normalize();
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int pasos, boolean tele) {
        lx = x;
        ly = y;
        lz = z;
        lPasos = tele ? 1 : 3;
        setRot(yRot, xRot);
    }

    // ------------------------------------------------------------------ uso
    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        boolean suya = level().isClientSide ? presoIdRed() == p.getId() : presoId != null && presoId.equals(p.getUUID());
        if (!suya || !p.isShiftKeyDown() || estado() != SUELO || p.isPassenger()) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        estado(EN_BRAZOS);
        soltarListo = false;
        level().playSound(null, getX(), getY(), getZ(), TorturaRegistro.CADENA_TINTINEO.get(), SoundSource.PLAYERS, 0.7F, 0.8F);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean hurt(DamageSource s, float d) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 64 * 64;
    }

    // ------------------------------------------------------------------ guardar
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        presoId = t.hasUUID("Preso") ? t.getUUID("Preso") : null;
        estado(SUELO);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        if (presoId != null) t.putUUID("Preso", presoId);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
