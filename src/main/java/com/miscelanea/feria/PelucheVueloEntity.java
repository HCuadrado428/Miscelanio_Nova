package com.miscelanea.feria;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Peluche por los aires. Rebota, chilla cada vez que da en algo y no hace daño a nadie. Cuando se queda quieto, vuelve a
 * ser un objeto que se puede recoger.
 */
public class PelucheVueloEntity extends ThrowableItemProjectile {

    /** Cuánta velocidad conserva en cada rebote. */
    private static final double REBOTE = 0.45D;
    /** Cuántos rebotes aguanta antes de quedarse en el suelo. */
    private static final int REBOTES = 3;

    private int rebotes = 0;
    private int variante = 0;

    public PelucheVueloEntity(EntityType<? extends PelucheVueloEntity> t, Level l) {
        super(t, l);
    }

    public PelucheVueloEntity(Level l, LivingEntity lanzador, int variante) {
        super(ModEntities.PELUCHE_VUELO.get(), lanzador, l);
        this.variante = variante;
    }

    @Override
    protected Item getDefaultItem() {
        return Peluches.OBJETOS.get(Peluches.NOMBRES[0]).get();
    }

    @Override
    protected float getGravity() {
        return 0.045F;
    }

    private void chilla(float volumen) {
        float tono = 1.3F + (variante - 1.5F) * 0.05F - rebotes * 0.07F;
        level().playSound(null, getX(), getY(), getZ(), ModSounds.PEL_CHIRRIDO.get(), SoundSource.NEUTRAL,
                volumen, tono);
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        // un peluche no hace daño: solo chilla y sale rebotado
        chilla(0.8F);
        setDeltaMovement(getDeltaMovement().scale(-REBOTE));
        rebotes++;
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        chilla(0.6F);
        rebotes++;
        if (rebotes > REBOTES) {
            posar();
            return;
        }
        Vec3 v = getDeltaMovement();
        switch (r.getDirection().getAxis()) {
            case X -> setDeltaMovement(-v.x * REBOTE, v.y * REBOTE, v.z * REBOTE);
            case Y -> setDeltaMovement(v.x * REBOTE, -v.y * REBOTE, v.z * REBOTE);
            case Z -> setDeltaMovement(v.x * REBOTE, v.y * REBOTE, -v.z * REBOTE);
        }
        if (getDeltaMovement().lengthSqr() < 0.004D) posar();
    }

    /** Se queda quieto: vuelve a ser un objeto en el suelo. */
    private void posar() {
        level().playSound(null, getX(), getY(), getZ(), ModSounds.PEL_POF.get(), SoundSource.NEUTRAL,
                0.55F, 0.95F + (variante % 3) * 0.05F);
        if (!level().isClientSide) {
            ItemStack caido = getItem();
            spawnAtLocation(caido.isEmpty() ? new ItemStack(getDefaultItem()) : caido.copyWithCount(1));
            discard();
        }
    }

    @Override
    protected void onHit(HitResult r) {
        // no llamamos al super para no aplicar el efecto de un proyectil normal
        if (r.getType() == HitResult.Type.BLOCK) onHitBlock((BlockHitResult) r);
        else if (r.getType() == HitResult.Type.ENTITY) onHitEntity((EntityHitResult) r);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount > 600) posar();
    }

    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putInt("rebotes", rebotes);
        t.putInt("variante", variante);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag t) {
        super.readAdditionalSaveData(t);
        rebotes = t.getInt("rebotes");
        variante = t.getInt("variante");
    }
}
