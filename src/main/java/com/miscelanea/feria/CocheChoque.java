package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;

/**
 * Coche de choque: se conduce con adelante/atrás y los giros como una barca, y al chocar con otro coche (o con una
 * pared) rebota con un buen golpe, chispas y ruido. Lo mueve el cliente del que conduce (como las barcas).
 */
public class CocheChoque extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(CocheChoque.class, EntityDataSerializers.INT);
    private float velocidad;
    private int golpeCd;
    public float sacudida;
    private int vida = 3;

    public CocheChoque(EntityType<? extends CocheChoque> t, Level l) {
        super(t, l);
        setMaxUpStep(0.6F);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public void color(int c) {
        entityData.set(COLOR, c);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(COLOR, 0);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof Player p ? p : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (golpeCd > 0) golpeCd--;
        if (sacudida > 0) sacudida *= 0.8F;
        if (isControlledByLocalInstance()) {
            LivingEntity c = getControllingPassenger();
            float adelante = c != null ? c.zza : 0, giro = c != null ? c.xxa : 0;
            if (adelante > 0) velocidad = Math.min(0.38F, velocidad + 0.025F);
            else if (adelante < 0) velocidad = Math.max(-0.18F, velocidad - 0.03F);
            else velocidad *= 0.93F;
            if (Math.abs(velocidad) < 0.003F) velocidad = 0;
            setYRot(getYRot() - giro * (3F + Math.abs(velocidad) * 10F) * (velocidad < 0 ? -1 : 1));
            float r = getYRot() * Mth.DEG_TO_RAD;
            Vec3 v = getDeltaMovement();
            Vec3 deseo = new Vec3(-Mth.sin(r) * velocidad, v.y, Mth.cos(r) * velocidad);
            // derrape: la velocidad lateral se va igualando a la de delante poco a poco
            Vec3 mezcla = new Vec3(Mth.lerp(0.35, v.x, deseo.x), onGround() ? 0 : v.y - 0.08, Mth.lerp(0.35, v.z, deseo.z));
            setDeltaMovement(mezcla);
            chocarConCoches();
            Vec3 antes = getDeltaMovement();
            move(MoverType.SELF, getDeltaMovement());
            if (horizontalCollision && antes.horizontalDistance() > 0.15 && golpeCd == 0) {
                golpe(antes.horizontalDistance());
                velocidad = -velocidad * 0.5F;
                setDeltaMovement(-antes.x * 0.5, getDeltaMovement().y, -antes.z * 0.5);
            }
        } else if (getControllingPassenger() == null && !level().isClientSide) {
            // coche vacío: se frena, cae y puede recibir empujones
            setDeltaMovement(getDeltaMovement().multiply(0.9, 1, 0.9));
            if (!onGround()) setDeltaMovement(getDeltaMovement().add(0, -0.08, 0));
            move(MoverType.SELF, getDeltaMovement());
            chocarConCoches();
        }
    }

    void chocarConCoches() {
        for (CocheChoque o : level().getEntitiesOfClass(CocheChoque.class, getBoundingBox().inflate(0.3))) {
            if (o == this) continue;
            Vec3 d = position().subtract(o.position());
            double dist = Math.sqrt(d.x * d.x + d.z * d.z);
            if (dist > 1.5 || dist < 1e-4) continue;
            Vec3 n = new Vec3(d.x / dist, 0, d.z / dist);
            Vec3 rel = getDeltaMovement().subtract(o.getDeltaMovement());
            double vn = rel.x * n.x + rel.z * n.z;
            if (vn >= 0) continue;
            // rebote elástico: este coche se lleva el empujón
            setDeltaMovement(getDeltaMovement().add(n.scale(-vn * 1.4 + 0.08)));
            velocidad *= 0.3F;
            if (golpeCd == 0) golpe(-vn);
        }
    }

    void golpe(double fuerza) {
        golpeCd = 10;
        sacudida = (float) Math.min(1, fuerza * 3);
        float vol = (float) Math.min(1, 0.3 + fuerza * 2), tono = 0.9F + random.nextFloat() * 0.2F;
        if (level().isClientSide) {
            level().playLocalSound(getX(), getY(), getZ(), ModSounds.FE_CHOQUE.get(), SoundSource.NEUTRAL, vol, tono, false);
            if (isControlledByLocalInstance()) com.miscelanea.network.ModNetwork.CHANNEL.sendToServer(new ChoquePacket(getId(), (float) fuerza));
        }
        else level().playSound(null, getX(), getY(), getZ(), ModSounds.FE_CHOQUE.get(), SoundSource.NEUTRAL, vol, tono);
        for (int i = 0; i < 6; i++)
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX() + random.nextGaussian() * 0.4, getY() + 0.5, getZ() + random.nextGaussian() * 0.4, 0, 0.1, 0);
    }

    @Override
    public InteractionResult interact(Player p, InteractionHand hand) {
        if (p.isSecondaryUseActive() || isVehicle()) return InteractionResult.PASS;
        if (!level().isClientSide) p.startRiding(this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean hurt(DamageSource s, float d) {
        if (level().isClientSide || isRemoved()) return true;
        sacudida = 1;
        if (--vida <= 0 || (s.getEntity() instanceof Player p && p.getAbilities().instabuild)) {
            ItemStack st = new ItemStack(ModItems.COCHE_CHOQUE.get());
            st.getOrCreateTag().putInt("color", color());
            if (!(s.getEntity() instanceof Player p && p.getAbilities().instabuild)) spawnAtLocation(st);
            discard();
        }
        return true;
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0.15;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return getPassengers().isEmpty();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        color(t.getInt("color"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        t.putInt("color", color());
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
