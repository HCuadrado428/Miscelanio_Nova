package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Gancho de techo con torno: se pone debajo de un bloque macizo (cuelga de él). Clic derecho con un cautivo: lo cuelga
 * boca abajo por los tobillos ({@link GanchoBlockEntity}). FACING solo orienta el torno y su manivela (como los aparatos,
 * el modelo se diseña mirando al norte).
 */
public class GanchoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = AparatoBlock.FACING;
    private static final VoxelShape FORMA = Block.box(3, 4, 3, 13, 16, 13);

    public GanchoBlock(Properties p) {
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
        return FORMA;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        return canSurvive(s, c.getLevel(), c.getClickedPos()) ? s : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        BlockPos arriba = p.above();
        return l.getBlockState(arriba).isFaceSturdy(l, arriba, Direction.DOWN);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction dir, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        if (dir == Direction.UP && !canSurvive(s, l, p)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(s, dir, vecino, l, p, pv);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean movido) {
        if (!s.is(nuevo.getBlock()) && l.getBlockEntity(p) instanceof AparatoBlockEntity be) be.sacar(false);
        super.onRemove(s, l, p, nuevo, movido);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(l.getBlockEntity(pos) instanceof AparatoBlockEntity be)) return InteractionResult.PASS;
        if (Aparatos.en(p)) return InteractionResult.FAIL;
        if (!l.isClientSide) be.usar(p, false, hit);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new GanchoBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.GANCHO_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.GANCHO_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
