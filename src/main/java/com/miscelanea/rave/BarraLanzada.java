package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

/** Barra luminosa lanzada: al caer queda en el suelo encendida (ver {@link BarraLuminosaBlock}); si no cabe, vuelve a ser objeto. */
public class BarraLanzada extends ThrowableItemProjectile {
    public BarraLanzada(EntityType<? extends BarraLanzada> t, Level l) {
        super(t, l);
    }

    public BarraLanzada(Level l, LivingEntity quien) {
        super(RaveRegistro.BARRA_LANZADA.get(), quien, l);
    }

    @Override
    protected Item getDefaultItem() {
        return RaveRegistro.BARRA.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult r) {
        super.onHitEntity(r);
        if (!level().isClientSide) {
            spawnAtLocation(getItem().copy());
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult r) {
        super.onHitBlock(r);
        if (level().isClientSide) return;
        BlockPos p = r.getBlockPos().relative(r.getDirection());
        BlockState aqui = level().getBlockState(p);
        // se queda tumbada en el suelo (si da en una pared, cae al pie)
        if (r.getDirection() != net.minecraft.core.Direction.UP && aqui.canBeReplaced() && level().getBlockState(p.below()).canBeReplaced()
                && !level().getBlockState(p.below(2)).canBeReplaced()) p = p.below();
        aqui = level().getBlockState(p);
        if (aqui.canBeReplaced() && aqui.getFluidState().isEmpty() && level().getBlockState(p.below()).isFaceSturdy(level(), p.below(), net.minecraft.core.Direction.UP)) {
            var eje = Math.abs(getDeltaMovement().x) > Math.abs(getDeltaMovement().z) ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z;
            level().setBlock(p, RaveRegistro.BARRA_SUELO.get().defaultBlockState().setValue(BarraLuminosaBlock.EJE, eje)
                    .setValue(Muebles.COLOR, Neon.de(getItem())), 3);
            level().playSound(null, p, net.minecraft.sounds.SoundEvents.GLASS_HIT, net.minecraft.sounds.SoundSource.BLOCKS, 0.6F, 1.6F);
        } else spawnAtLocation(getItem().copy());
        discard();
    }
}
