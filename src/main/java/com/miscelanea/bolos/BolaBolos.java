package com.miscelanea.bolos;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/** Bola de bolos rodando: poca fricción, un poco de efecto (curva) y se para si choca con una pared. */
public class BolaBolos extends Entity {
    public float efecto;
    public float giro, giroO;
    private int parada;
    /** Quién la tiró (para devolvérsela) y si era en creativo (entonces no se gastó y no se devuelve). */
    @Nullable public UUID dueno;
    public boolean creativo, devuelta;

    public BolaBolos(EntityType<? extends BolaBolos> t, Level l) {
        super(t, l);
        setMaxUpStep(0.25F);
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
            if (parada > 30 || tickCount > 600 || getY() < level().getMinBuildHeight()) {
                devolver();
                discard();
            }
        } else move(MoverType.SELF, getDeltaMovement());
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    /** Devuelve la bola (el objeto) a quien la tiró, una sola vez. Si no está, se queda en el suelo donde paró. */
    public void devolver() {
        if (level().isClientSide || devuelta) return;
        devuelta = true;
        if (creativo) return;
        ItemStack st = new ItemStack(ModItems.BOLA_BOLOS.get());
        Player p = dueno != null ? level().getPlayerByUUID(dueno) : null;
        if (p != null && p.isAlive()) {
            if (!p.getInventory().add(st)) p.drop(st, false);
        } else spawnAtLocation(st, 0.2F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
        dueno = t.hasUUID("dueno") ? t.getUUID("dueno") : null;
        creativo = t.getBoolean("creat");
        devuelta = t.getBoolean("dev");
        efecto = t.getFloat("efecto");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
        if (dueno != null) t.putUUID("dueno", dueno);
        t.putBoolean("creat", creativo);
        t.putBoolean("dev", devuelta);
        t.putFloat("efecto", efecto);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.BOLA_BOLOS.get());
    }

    /**
     * Bolera hacia la que va la bola: la que queda más en línea con el tiro (menos desvío lateral). Así dos calles
     * pegadas no se confunden: cada tiro va a la suya.
     */
    static BoleraBlockEntity buscarBolera(Level l, Vec3 desde, Vec3 dir) {
        BoleraBlockEntity mejor = null;
        double puntos = 1e9;
        double dl = Math.max(1e-6, dir.horizontalDistance()), ux = dir.x / dl, uz = dir.z / dl;
        BlockPos c = BlockPos.containing(desde);
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-32, -3, -32), c.offset(32, 3, 32))) {
            if (!(l.getBlockEntity(q) instanceof BoleraBlockEntity be)) continue;
            Vec3 d = Vec3.atCenterOf(q).subtract(desde);
            double delante = d.x * ux + d.z * uz, lado = Math.abs(d.x * uz - d.z * ux);
            if (delante < 1.5 || lado > 1.0 + delante * 0.1) continue;
            // la bolera debería mirar hacia quien tira; si no, cuenta menos
            Direction f = be.facing();
            double mal = f.getStepX() * ux + f.getStepZ() * uz > -0.5 ? 3 : 0;
            double p = lado * 2 + delante * 0.03 + mal;
            if (p < puntos) {
                puntos = p;
                mejor = be;
            }
        }
        return mejor;
    }
}
