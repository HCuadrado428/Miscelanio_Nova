package com.miscelanea.mesas;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Futbolín de 3x2 bloques. */
public class FutbolinBlock extends MesaGrandeBlock {
    public FutbolinBlock(Properties p) {
        super(p, 3, 2, 14);
    }

    @Override
    protected BlockEntity nuevaEntidad(BlockPos p, BlockState s) {
        return new FutbolinBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.FUTBOLIN_BE.get(), FutbolinBlockEntity::tickServidor);
    }
}
