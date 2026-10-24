package com.miscelanea.carcel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Terminal de ejecución: ponlo junto al final de la vía. Cuando llega el carro del chivato queda enganchado.
 * Al electrocutarlo, el terminal da señal de redstone durante 10 s (para abrir la salida, etc.).
 */
public class TerminalBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public TerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    public static void activar(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof TerminalBlock t)) return;
        level.setBlock(pos, s.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, t, 200);
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        if (s.getValue(POWERED)) level.setBlock(pos, s.setValue(POWERED, false), Block.UPDATE_ALL);
    }

    @Override
    public boolean isSignalSource(BlockState s) {
        return true;
    }

    @Override
    public int getSignal(BlockState s, BlockGetter level, BlockPos pos, Direction dir) {
        return s.getValue(POWERED) ? 15 : 0;
    }
}
