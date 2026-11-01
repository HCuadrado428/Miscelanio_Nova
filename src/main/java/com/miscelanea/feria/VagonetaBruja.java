package com.miscelanea.feria;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Vagoneta del tren de la bruja: va sola por los raíles a paso de paseo cuando lleva a alguien (no hacen falta raíles
 * propulsores) y suena un órgano tétrico. Las cajas de sustos que haya al lado de la vía saltan al pasar.
 */
public class VagonetaBruja extends Minecart {
    public static final double PASO = 0.2;

    public VagonetaBruja(EntityType<?> t, Level l) {
        super(t, l);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isVehicle()) return;
        boolean enVia = level().getBlockState(blockPosition()).is(BlockTags.RAILS) || level().getBlockState(blockPosition().below()).is(BlockTags.RAILS);
        if (enVia) {
            Vec3 v = getDeltaMovement();
            double h = v.horizontalDistance();
            if (h < 0.02) {
                // parada: arranca hacia donde mira quien va montado
                Entity c = getFirstPassenger();
                if (c != null) {
                    float r = c.getYRot() * Mth.DEG_TO_RAD;
                    setDeltaMovement(-Mth.sin(r) * PASO, v.y, Mth.cos(r) * PASO);
                }
            } else if (h < PASO) setDeltaMovement(v.x * PASO / h, v.y, v.z * PASO / h);
        }
        if (tickCount % 100 == 0)
            level().playSound(null, getX(), getY(), getZ(), ModSounds.FE_TREN.get(), SoundSource.NEUTRAL, 0.7F, 1F);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.VAGONETA_BRUJA.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(ModItems.VAGONETA_BRUJA.get());
    }
}
