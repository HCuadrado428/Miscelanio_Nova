package com.miscelanea.mesas;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Mesa de billar de 4x2 bloques. */
public class BillarBlock extends MesaGrandeBlock {
    public BillarBlock(Properties p) {
        super(p, 4, 2, 13.5);
    }

    @Override
    protected BlockEntity nuevaEntidad(BlockPos p, BlockState s) {
        return new BillarBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.BILLAR_BE.get(), BillarBlockEntity::tickServidor);
    }
}
