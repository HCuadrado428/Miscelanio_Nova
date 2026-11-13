package com.miscelanea.espera;

import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/**
 * El panel de fluorescentes del techo de la Sala de Espera: encendido (da luz) o fundido (gris, con los bichos muertos
 * dentro). Es un bloque normal y no parpadea: nada que recalcular de luz mientras se juega.
 */
public class FluorescenteBlock extends Block {
    public static final BooleanProperty ENCENDIDO = BooleanProperty.create("encendido");

    public FluorescenteBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ENCENDIDO, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ENCENDIDO);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState();
    }
}
