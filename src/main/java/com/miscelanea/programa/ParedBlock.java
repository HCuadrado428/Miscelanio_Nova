package com.miscelanea.programa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Base de los bloques pegados a una pared (el FACING apunta hacia fuera de la pared). */
public abstract class ParedBlock extends HorizontalDirectionalBlock {
    private final VoxelShape n, s, e, w;

    protected ParedBlock(Properties p, double x0, double y0, double x1, double y1, double grosor) {
        super(p);
        n = Block.box(x0, y0, 16 - grosor, x1, y1, 16);
        s = Block.box(16 - x1, y0, 0, 16 - x0, y1, grosor);
        e = Block.box(0, y0, x0, grosor, y1, x1);
        w = Block.box(16 - grosor, y0, 16 - x1, 16, y1, 16 - x0);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getClickedFace().getAxis().isHorizontal() ? ctx.getClickedFace() : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, d);
    }

    @Override
    public VoxelShape getShape(BlockState st, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (st.getValue(FACING)) {
            case SOUTH -> s;
            case EAST -> e;
            case WEST -> w;
            default -> n;
        };
    }
}
