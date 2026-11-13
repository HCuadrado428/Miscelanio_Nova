package com.miscelanea.espera;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Una recreativa fundida de la Recreativa Infinita: el mueble de una de las de verdad (MUEBLE elige cuál) con la pantalla
 * negra. Es un bloque normal de dos alturas, sin bloque-entidad ni nada que dibujar aparte, así que una sala llena de
 * ellas no le cuesta nada al ordenador. Solo hay en el Espera y no suelta nada.
 */
public class RecreativaApagadaBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    /** Los muebles que se usan (los de un jugador; cada uno con su pintura). */
    public static final String[] MUEBLES = {"astro", "tetrominos", "serpiente", "ladrillos", "pong", "pajaro", "invasores", "mazmorra"};
    public static final IntegerProperty MUEBLE = IntegerProperty.create("mueble", 0, MUEBLES.length - 1);
    private static final VoxelShape ABAJO = Block.box(0, 0, 0, 16, 16, 16);
    private static final VoxelShape[] ARRIBA = {Block.box(0, 0, 2, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 14), Block.box(0, 0, 0, 14, 16, 16), Block.box(2, 0, 0, 16, 16, 16)};

    public RecreativaApagadaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(MUEBLE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, MUEBLE);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (s.getValue(HALF) == DoubleBlockHalf.LOWER) return ABAJO;
        return ARRIBA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case EAST -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
        DoubleBlockHalf h = s.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (h == DoubleBlockHalf.LOWER) == (dir == Direction.UP))
            return otro.is(this) && otro.getValue(HALF) != h ? s : Blocks.AIR.defaultBlockState();
        return s;
    }
}
