package com.miscelanea.programa;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Pilotos de pared: luz roja de puerta con trampa, y alarma de ruido (sensor que suena si pasas de pie). */
public class LuzBlock extends ParedBlock {
    public static final BooleanProperty DISPARADA = BooleanProperty.create("disparada");

    public LuzBlock(Properties p) {
        super(p, 5, 5, 11, 11, 2);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DISPARADA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, DISPARADA);
    }
}
