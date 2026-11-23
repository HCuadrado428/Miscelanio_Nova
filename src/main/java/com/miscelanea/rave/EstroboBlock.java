package com.miscelanea.rave;

import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Estroboscopio: destella en blanco a la velocidad del modo. Quien lo tiene a la vista y cerca ve el destello en toda la
 * pantalla (más flojo cuanto más lejos); con la opción de accesibilidad "Ocultar destellos" del juego, no.
 */
public class EstroboBlock extends LuzRaveBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape[] FORMAS = new VoxelShape[6];

    public EstroboBlock(Properties p) {
        super(p, EstroboBlockEntity::new, RenderShape.MODEL);
        registerDefaultState(stateDefinition.any().setValue(LucesRave.ENCENDIDA, true).setValue(LucesRave.MODO, 1).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LucesRave.ENCENDIDA, LucesRave.MODO, FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape caja(BlockState s) {
        Direction d = s.getValue(FACING);
        VoxelShape f = FORMAS[d.get3DDataValue()];
        if (f == null) f = FORMAS[d.get3DDataValue()] = com.miscelanea.camaras.Montaje.forma(d, 1, 0, 2.6, 15, 13.2, 12);
        return f;
    }
}
