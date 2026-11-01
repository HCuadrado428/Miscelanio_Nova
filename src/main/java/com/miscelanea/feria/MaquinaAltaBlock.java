package com.miscelanea.feria;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Máquinas de premios de dos bloques de alto: gancho, tragaperras y gachapón. Funcionan con fichas. */
public class MaquinaAltaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final int GANCHO = 0, TRAGAPERRAS = 1, GACHAPON = 2;
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 16, 15);
    public final int tipo;

    public MaquinaAltaBlock(Properties p, int tipo) {
        super(p);
        this.tipo = tipo;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF);
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
        BlockPos p = c.getClickedPos();
        Level l = c.getLevel();
        if (p.getY() >= l.getMaxBuildHeight() - 1 || !l.getBlockState(p.above()).canBeReplaced(c)) return null;
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity e, ItemStack st) {
        l.setBlock(p.above(), s.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        if (s.getValue(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState d = l.getBlockState(p.below());
        return d.is(this) && d.getValue(HALF) == DoubleBlockHalf.LOWER;
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
        DoubleBlockHalf h = s.getValue(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (h == DoubleBlockHalf.LOWER) == (dir == Direction.UP))
            return otro.is(this) && otro.getValue(HALF) != h ? s : Blocks.AIR.defaultBlockState();
        return s;
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player pl) {
        if (!l.isClientSide && pl.isCreative() && s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos d = p.below();
            if (l.getBlockState(d).is(this)) {
                l.setBlock(d, Blocks.AIR.defaultBlockState(), 35);
                l.levelEvent(pl, 2001, d, Block.getId(l.getBlockState(d)));
            }
        }
        super.playerWillDestroy(l, p, s, pl);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos abajo = s.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
        if (!l.isClientSide && l.getBlockEntity(abajo) instanceof MaquinaAltaBlockEntity be) be.usar(p, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? new MaquinaAltaBlockEntity(p, s) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.MAQUINA_ALTA_BE.get(), MaquinaAltaBlockEntity::tick);
    }
}
