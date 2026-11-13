package com.miscelanea.espera;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

/** Las taquillas del pasillo del colegio (dos por bloque, de dos de alto): la de abajo con el candado, la de arriba con el número. */
public class TaquillaBlock extends HorizontalDirectionalBlock {
    public TaquillaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MitadBlock.ARRIBA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, MitadBlock.ARRIBA);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite())
                .setValue(MitadBlock.ARRIBA, c.getLevel().getBlockState(c.getClickedPos().below()).is(this));
    }
}
