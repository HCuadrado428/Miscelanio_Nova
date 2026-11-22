package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Decoración del laboratorio con frente (la taquilla, el cartel de biopeligro): mira hacia quien la pone. La forma se
 * da mirando al norte (x0, y0, z0, x1, y1, z1 en píxeles) y se gira según hacia dónde mire.
 */
public class DecoracionOrientadaBlock extends HorizontalDirectionalBlock {
    private final VoxelShape[] formas = new VoxelShape[4];

    public DecoracionOrientadaBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(p);
        formas[0] = Block.box(x0, y0, z0, x1, y1, z1);                                     // norte
        formas[1] = Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);                           // este
        formas[2] = Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);                 // sur
        formas[3] = Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);                           // oeste
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return switch (s.getValue(FACING)) {
            case EAST -> formas[1];
            case SOUTH -> formas[2];
            case WEST -> formas[3];
            default -> formas[0];
        };
    }
}
