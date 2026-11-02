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
        Vec3 v = getDeltaMovement().add(0, -0.05, 0);
        Vec3 antes = v;
        move(MoverType.SELF, v);
        Vec3 d = getDeltaMovement();
        if (onGround()) {
            if (antes.y < -0.15 && !level().isClientSide)
                level().playSound(null, getX(), getY(), getZ(), ModSounds.PE_CAE.get(), SoundSource.NEUTRAL, (float) Math.min(1, -antes.y), boliche() ? 1.5F : 1F);
            d = new Vec3(d.x * 0.72, 0, d.z * 0.72);
        } else d = d.scale(0.99);
        if (horizontalCollision) d = new Vec3(-antes.x * 0.3, d.y, -antes.z * 0.3);
        setDeltaMovement(d);
        if (!level().isClientSide && d.horizontalDistance() > 0.01) chocar();
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
