package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** La cámara de vigilancia (ver {@link MontadoBlock}). */
public class VigilanciaBlock extends MontadoBlock {
    public VigilanciaBlock(Properties p) {
        super(p, VigilanciaBlockEntity::new, () -> CamarasRegistro.VIGILANCIA_BE.get());
    }

    @Override
    protected double[] caja() { return new double[]{3, 3, 1, 13, 12, 16}; }

    @Override
    protected int canalMin() { return 1; }

    @Override
    protected int canalActual(BlockEntity be) { return be instanceof VigilanciaBlockEntity v ? v.canal() : 1; }

    @Override
    protected void ponerCanal(BlockEntity be, int canal) {
        if (be instanceof VigilanciaBlockEntity v) v.canal(canal);
    }
}
