package com.miscelanea.espera;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/** Un bloque de dos de alto hecho de dos bloques (la pizarra): el de arriba lleva el marco de arriba. */
public class MitadBlock extends Block {
    public static final BooleanProperty ARRIBA = BooleanProperty.create("arriba");

    public MitadBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ARRIBA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ARRIBA);
    }

    /** Al ponerlo encima de otro igual, es la mitad de arriba. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(ARRIBA, c.getLevel().getBlockState(c.getClickedPos().below()).is(this));
    }
}
