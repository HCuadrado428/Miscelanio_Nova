package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Suelo LED de discoteca: baldosas que cambian de color formando ondas; si cerca suena una mesa de DJ, cambian al ritmo de
 * la canción y laten con cada pulso.
 */
public class SueloLedBlock extends BaseEntityBlock {
    public SueloLedBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new SueloLedBlockEntity(p, s);
    }
}
