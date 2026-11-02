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
    public static final int GANCHO = 0, TRAGAPERRAS = 1, GACHAPON = 2, CANASTA = 3, BOXEO = 4, MARTILLO = 5;
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
        boolean arriba = s.getValue(HALF) == DoubleBlockHalf.UPPER;
        Direction f = s.getValue(FACING);
        if (tipo == CANASTA && arriba) return girada(f, 0, 0, 13, 16, 16, 16);
        if (tipo == MARTILLO) return arriba ? girada(f, 6, 0, 10, 10, 16, 14) : net.minecraft.world.phys.shapes.Shapes.or(girada(f, 1, 0, 1, 15, 4, 15), girada(f, 6, 4, 10, 10, 16, 14));
        return FORMA;
    }

    /** Caja dada para el bloque mirando al norte (frente en z=0), girada según hacia dónde mira. */
    static VoxelShape girada(Direction f, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (f) {
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
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
