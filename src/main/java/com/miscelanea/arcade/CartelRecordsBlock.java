package com.miscelanea.arcade;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Cartel de récords: panel luminoso que muestra las tablas de la recreativa. Varios carteles juntos en la misma pared
 * (mirando al mismo lado) forman uno grande que enseña varios juegos a la vez.
 */
public class CartelRecordsBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] FORMAS = {
            Block.box(0, 0, 14, 16, 16, 16),   // mira al norte (pegado a la pared del sur)
            Block.box(0, 0, 0, 16, 16, 2),     // sur
            Block.box(14, 0, 0, 16, 16, 16),   // oeste
            Block.box(0, 0, 0, 2, 16, 16)};    // este

    public CartelRecordsBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMAS[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        }];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction d = c.getClickedFace();
        if (d.getAxis().isVertical()) d = c.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, d);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new CartelRecordsBlockEntity(p, s);
    }

    /** Busca el rectángulo de carteles al que pertenece pos: {esquina de arriba a la izquierda (vista de frente), ancho, alto} o null si va suelto. */
    @Nullable
    public static Object[] grupo(BlockGetter l, BlockPos pos, Direction f) {
        Direction izq = f.getClockWise(), der = f.getCounterClockWise();
        BlockPos a = pos;
        for (int it = 0; it < 32; it++) {
            boolean movido = false;
            while (es(l, a.above(), f) && a.getY() - pos.getY() < 7) {
                a = a.above();
                movido = true;
            }
            while (es(l, a.relative(izq), f) && distancia(pos, a, izq) < 11) {
                a = a.relative(izq);
                movido = true;
            }
            if (!movido) break;
        }
        int w = 1, h = 1;
        while (w < 12 && es(l, a.relative(der, w), f)) w++;
        while (h < 8 && es(l, a.below(h), f)) h++;
        // que el rectángulo esté completo; si no, recortar el alto
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (!es(l, a.relative(der, x).below(y), f)) {
                    h = y;
                    break;
                }
        if (h < 1) return null;
        int dx = distancia(a, pos, der), dy = a.getY() - pos.getY();
        if (dx < 0 || dx >= w || dy < 0 || dy >= h) return null;
        return new Object[]{a, w, h};
    }

    private static int distancia(BlockPos a, BlockPos b, Direction d) {
        return (b.getX() - a.getX()) * d.getStepX() + (b.getZ() - a.getZ()) * d.getStepZ();
    }

    private static boolean es(BlockGetter l, BlockPos p, Direction f) {
        BlockState s = l.getBlockState(p);
        return s.getBlock() instanceof CartelRecordsBlock && s.getValue(FACING) == f;
    }
}
