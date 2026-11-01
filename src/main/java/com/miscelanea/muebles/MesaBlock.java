package com.miscelanea.muebles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Mesa de madera: varias juntas forman una mesa grande y solo quedan patas en las esquinas exteriores. */
public class MesaBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH,
            EAST = BlockStateProperties.EAST, WEST = BlockStateProperties.WEST;
    private static final VoxelShape TABLERO = Block.box(0, 11.5, 0, 16, 15.5, 16);

    public MesaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, SOUTH, EAST, WEST);
    }

    private BlockState unir(BlockState s, BlockGetter l, BlockPos p) {
        return s.setValue(NORTH, l.getBlockState(p.north()).is(this)).setValue(SOUTH, l.getBlockState(p.south()).is(this))
                .setValue(EAST, l.getBlockState(p.east()).is(this)).setValue(WEST, l.getBlockState(p.west()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return unir(defaultBlockState(), c.getLevel(), c.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState o, LevelAccessor l, BlockPos p, BlockPos po) {
        return d.getAxis().isHorizontal() ? unir(s, l, p) : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        VoxelShape v = TABLERO;
        if (!s.getValue(NORTH) && !s.getValue(WEST)) v = Shapes.or(v, Block.box(1, 0, 1, 3, 11.5, 3));
        if (!s.getValue(NORTH) && !s.getValue(EAST)) v = Shapes.or(v, Block.box(13, 0, 1, 15, 11.5, 3));
        if (!s.getValue(SOUTH) && !s.getValue(WEST)) v = Shapes.or(v, Block.box(1, 0, 13, 3, 11.5, 15));
        if (!s.getValue(SOUTH) && !s.getValue(EAST)) v = Shapes.or(v, Block.box(13, 0, 13, 15, 11.5, 15));
        return v;
    }
}
