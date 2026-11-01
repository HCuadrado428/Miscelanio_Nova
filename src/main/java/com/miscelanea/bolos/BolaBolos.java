package com.miscelanea.bolos;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

/** Bola de bolos rodando: poca fricción, un poco de efecto (curva) y se para si choca con una pared. */
public class BolaBolos extends Entity {
    public float efecto;
    public float giro, giroO;
    private int parada;

    public BolaBolos(EntityType<? extends BolaBolos> t, Level l) {
        super(t, l);
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    public void tick() {
        super.tick();
        giroO = giro;
        Vec3 v = getDeltaMovement();
        double h = v.horizontalDistance();
        giro += (float) (h / 0.17 * 57.3);
        if (!level().isClientSide) {
            if (onGround()) {
                // efecto: la velocidad gira poco a poco
                double a = efecto * 0.012;
                v = new Vec3(v.x * Math.cos(a) - v.z * Math.sin(a), v.y, v.x * Math.sin(a) + v.z * Math.cos(a)).multiply(0.993, 1, 0.993);
            }
            v = v.add(0, -0.06, 0);
            setDeltaMovement(v);
            Vec3 antes = v;
            move(MoverType.SELF, v);
            if (horizontalCollision) {
                setDeltaMovement(antes.multiply(0.1, 1, 0.1));
                if (antes.horizontalDistance() > 0.1) level().playSound(null, getX(), getY(), getZ(), ModSounds.BO_BOLO.get(), SoundSource.NEUTRAL, 0.5F, 0.6F);
            }
            if (onGround()) setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
            if (getDeltaMovement().horizontalDistance() < 0.01) parada++;
            else parada = 0;
            if (tickCount % 12 == 0 && h > 0.05 && onGround())
                level().playSound(null, getX(), getY(), getZ(), ModSounds.BO_RUEDA.get(), SoundSource.NEUTRAL, (float) Math.min(1, h * 2), 1F);
            if (parada > 30 || tickCount > 600 || getY() < level().getMinBuildHeight()) discard();
        } else move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.BOLA_BOLOS.get());
    }

    static BoleraBlockEntity buscarBolera(Level l, Vec3 desde, Vec3 dir) {
        BoleraBlockEntity mejor = null;
        double puntos = -1;
        BlockPos c = BlockPos.containing(desde);
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-32, -3, -32), c.offset(32, 3, 32))) {
            if (!(l.getBlockEntity(q) instanceof BoleraBlockEntity be)) continue;
            Vec3 d = Vec3.atCenterOf(q).subtract(desde);
            double dist = d.horizontalDistance();
            if (dist < 1.5) continue;
            double coseno = (d.x * dir.x + d.z * dir.z) / (dist * Math.max(1e-6, dir.horizontalDistance()));
            if (coseno < 0.85) continue;
            double p = coseno * 10 - dist * 0.05;
            if (p > puntos) {
                puntos = p;
                mejor = be;
            }
        }
        return mejor;
    }
}
