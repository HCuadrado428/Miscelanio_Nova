package com.miscelanea.petanca;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import java.util.UUID;

/**
 * Bola de petanca (o el boliche): cae con una parábola, rueda poco y se queda en el suelo. Choca con las demás bolas.
 * Clic derecho para recogerla. Lleva el color y el nombre de quien la tiró.
 */
public class BolaPetanca extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(BolaPetanca.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> BOLICHE = SynchedEntityData.defineId(BolaPetanca.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<String> DUENO = SynchedEntityData.defineId(BolaPetanca.class, EntityDataSerializers.STRING);
    public UUID duenoId;

    public BolaPetanca(EntityType<? extends BolaPetanca> t, Level l) {
        super(t, l);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0xC0C0C8);
        entityData.define(BOLICHE, false);
        entityData.define(DUENO, "");
    }

    public void preparar(Player p, boolean boliche) {
        duenoId = p.getUUID();
        entityData.set(DUENO, p.getGameProfile().getName());
        entityData.set(BOLICHE, boliche);
        int h = p.getUUID().hashCode();
        float hue = (h & 0xFFFF) / 65535F;
        entityData.set(COLOR, java.awt.Color.HSBtoRGB(hue, 0.55F, 0.95F) & 0xFFFFFF);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public boolean boliche() {
        return entityData.get(BOLICHE);
    }

    public String dueno() {
        return entityData.get(DUENO);
    }

    public double radio() {
        return boliche() ? 0.07 : 0.13;
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 v = getDeltaMovement();
        boolean enSuelo = onGround();
        // quieta en el suelo: no hace falta simular nada (solo comprobar que sigue habiendo suelo debajo)
        if (enSuelo && v.horizontalDistanceSqr() < 1e-6 && Math.abs(v.y) < 1e-3) {
            v = new Vec3(0, -0.04, 0);
            move(MoverType.SELF, v);
            if (onGround()) {
                setDeltaMovement(Vec3.ZERO);
                return;
            }
            setDeltaMovement(v);
            return;
        }
        // la gravedad se ACUMULA en la velocidad (antes se calculaba pero nunca se guardaba y la bola subía sin fin)
        v = new Vec3(v.x, v.y - 0.04, v.z);
        setDeltaMovement(v);
        Vec3 antes = v;
        move(MoverType.SELF, v);
        // move() anula las componentes que chocan con bloques (la Y al caer al suelo)
        Vec3 d = getDeltaMovement();
        if (onGround()) {
            if (!enSuelo) {
                // impacto contra el suelo: la tierra se come buena parte de la velocidad (la bola "muerde" y no rebota)
                if (antes.y < -0.15 && !level().isClientSide)
                    level().playSound(null, getX(), getY(), getZ(), ModSounds.PE_CAE.get(), SoundSource.NEUTRAL, (float) Math.min(1, -antes.y), boliche() ? 1.5F : 1F);
                double frena = antes.y < -0.2 ? 0.45 : 0.7;
                d = new Vec3(d.x * frena, 0, d.z * frena);
            }
            // rodando: rozamiento con el suelo, se para enseguida; nunca rebota hacia arriba
            d = new Vec3(d.x * 0.82, 0, d.z * 0.82);
            if (d.horizontalDistanceSqr() < 4e-4) d = Vec3.ZERO;
        } else {
            // en el aire: un pelín de resistencia del aire
            d = new Vec3(d.x * 0.99, Math.max(-1.5, d.y * 0.99), d.z * 0.99);
        }
        // contra una pared: rebota flojito hacia atrás
        if (horizontalCollision) {
            double nx = Math.abs(d.x) < 1e-6 && Math.abs(antes.x) > 1e-6 ? -antes.x * 0.3 : d.x;
            double nz = Math.abs(d.z) < 1e-6 && Math.abs(antes.z) > 1e-6 ? -antes.z * 0.3 : d.z;
            d = new Vec3(nx, d.y, nz);
        }
        setDeltaMovement(d);
        if (!level().isClientSide && d.horizontalDistance() > 0.01) chocar();
    }

    /** Clave para agrupar bolas por jugador: el UUID si se conoce (bolas antiguas: el nombre). */
    public Object clave() {
        return duenoId != null ? duenoId : dueno();
    }

    private void chocar() {
        for (BolaPetanca o : level().getEntitiesOfClass(BolaPetanca.class, getBoundingBox().inflate(0.25))) {
            if (o == this) continue;
            Vec3 dd = position().subtract(o.position());
            double dist = Math.hypot(dd.x, dd.z), min = radio() + o.radio();
            if (dist >= min || dist < 1e-4) continue;
            Vec3 n = new Vec3(dd.x / dist, 0, dd.z / dist);
            Vec3 v = getDeltaMovement();
            double vn = v.x * n.x + v.z * n.z;
            if (vn >= 0) continue;
            double masa = boliche() == o.boliche() ? 1 : boliche() ? 0.3 : 3;
            o.setDeltaMovement(o.getDeltaMovement().add(n.scale(vn * Math.min(1.6, masa))));
            setDeltaMovement(v.subtract(n.scale(vn * 0.9)));
            level().playSound(null, getX(), getY(), getZ(), ModSounds.PE_CHOQUE.get(), SoundSource.NEUTRAL, 1F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (!level().isClientSide) {
            ItemStack st = new ItemStack(boliche() ? ModItems.BOLICHE.get() : ModItems.BOLAS_PETANCA.get());
            if (!p.getInventory().add(st)) p.drop(st, false);
            discard();
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        entityData.set(COLOR, t.getInt("color"));
        entityData.set(BOLICHE, t.getBoolean("bol"));
        entityData.set(DUENO, t.getString("dueno"));
        if (t.hasUUID("id")) duenoId = t.getUUID("id");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("color", color());
        t.putBoolean("bol", boliche());
        t.putString("dueno", dueno());
        if (duenoId != null) t.putUUID("id", duenoId);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
