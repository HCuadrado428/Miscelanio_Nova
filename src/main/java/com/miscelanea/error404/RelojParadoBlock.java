package com.miscelanea.error404;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * El reloj de pared del aula, parado en las cuatro y diez. Va pegado a la pared de detrás. Si no lo miras, a veces
 * marca otra hora (propiedad "hora": 0 = las cuatro y diez; ver {@link Cambios}).
 */
public class RelojParadoBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty HORA = IntegerProperty.create("hora", 0, 4);
    private static final VoxelShape[] FORMA = {
            Block.box(2, 2, 15, 14, 14, 16), Block.box(2, 2, 0, 14, 14, 1),
            Block.box(15, 2, 2, 16, 14, 14), Block.box(0, 2, 2, 1, 14, 14)};

    public RelojParadoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HORA, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HORA);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        }];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }
}
