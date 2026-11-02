package com.miscelanea.mesas;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Mesa de air hockey de 3x2 bloques. */
public class AirHockeyBlock extends MesaGrandeBlock {
    public AirHockeyBlock(Properties p) {
        super(p, 3, 2, 13);
    }

    @Override
    protected BlockEntity nuevaEntidad(BlockPos p, BlockState s) {
        return new AirHockeyBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.AIRHOCKEY_BE.get(), AirHockeyBlockEntity::tickServidor);
    }
}
