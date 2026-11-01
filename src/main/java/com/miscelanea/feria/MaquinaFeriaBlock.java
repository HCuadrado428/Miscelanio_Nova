package com.miscelanea.feria;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Máquinas de feria de un bloque: algodón de azúcar, palomitas y castillo de fuegos artificiales. */
public class MaquinaFeriaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final int ALGODON = 0, PALOMITAS = 1, FUEGOS = 2;
    private static final VoxelShape CARRO = Block.box(1, 0, 1, 15, 16, 15), CAJA = Block.box(1, 0, 1, 15, 12, 15);
    public final int tipo;

    public MaquinaFeriaBlock(Properties p, int tipo) {
        super(p);
        this.tipo = tipo;
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
        return tipo == FUEGOS ? CAJA : CARRO;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!l.isClientSide && l.getBlockEntity(pos) instanceof MaquinaFeriaBlockEntity be) be.usar(p, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void neighborChanged(BlockState s, Level l, BlockPos pos, Block b, BlockPos desde, boolean mov) {
        if (l.isClientSide || tipo != FUEGOS) return;
        boolean señal = l.hasNeighborSignal(pos);
        if (l.getBlockEntity(pos) instanceof MaquinaFeriaBlockEntity be) be.redstone(señal);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new MaquinaFeriaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.MAQUINA_FERIA_BE.get(), MaquinaFeriaBlockEntity::tick);
    }
}
