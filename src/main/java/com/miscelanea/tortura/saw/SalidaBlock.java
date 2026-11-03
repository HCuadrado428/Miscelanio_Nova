package com.miscelanea.tortura.saw;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * LA SALIDA del laberinto (Saw I): puerta de acero de dos bloques con reloj y bombilla. La mitad de abajo lleva el
 * {@link SalidaBlockEntity} (el reloj); la de arriba copia el estado. No se puede romper en supervivencia.
 * <ul>
 *   <li>Parada: clic derecho ARRIBA = +30 s (Shift: -30 s); clic derecho ABAJO = arranca la cuenta atrás.</li>
 *   <li>Contando: clic derecho = abrirla y salir (la puerta se abre un momento; quien pasa por ella se salva).</li>
 *   <li>A cero: cerrada con llave y las luces se apagan para quien siga dentro. Una llave maestra o la oxidada del pozo
 *       la abren; en creativo, Shift + clic derecho la reinicia.</li>
 * </ul>
 */
public class SalidaBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<FaseSalida> ESTADO = EnumProperty.create("estado", FaseSalida.class);

    private static final VoxelShape CERRADA_NS = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape CERRADA_EO = Block.box(6, 0, 0, 10, 16, 16);
    private static final VoxelShape ABIERTA_NS = Shapes.or(Block.box(0, 0, 6, 2, 16, 10), Block.box(14, 0, 6, 16, 16, 10));
    private static final VoxelShape ABIERTA_EO = Shapes.or(Block.box(6, 0, 0, 10, 16, 2), Block.box(6, 0, 14, 10, 16, 16));

    public SalidaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(ESTADO, FaseSalida.ESPERA));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, HALF, ESTADO);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        boolean ns = s.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (s.getValue(ESTADO) == FaseSalida.ABIERTA) return ns ? ABIERTA_NS : ABIERTA_EO;
        return ns ? CERRADA_NS : CERRADA_EO;
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
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction d, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        DoubleBlockHalf h = s.getValue(HALF);
        if (d.getAxis() == Direction.Axis.Y && (h == DoubleBlockHalf.LOWER) == (d == Direction.UP)) {
            if (!vecino.is(this) || vecino.getValue(HALF) == h) return Blocks.AIR.defaultBlockState();
            return s.setValue(FACING, vecino.getValue(FACING)).setValue(ESTADO, vecino.getValue(ESTADO));
        }
        return s;
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player pl) {
        if (!l.isClientSide && pl.isCreative() && s.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos abajo = p.below();
            BlockState sa = l.getBlockState(abajo);
            if (sa.is(this) && sa.getValue(HALF) == DoubleBlockHalf.LOWER) {
                l.setBlock(abajo, Blocks.AIR.defaultBlockState(), 35);
                l.levelEvent(pl, 2001, abajo, Block.getId(sa));
            }
        }
        super.playerWillDestroy(l, p, s, pl);
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
        return s.getValue(ESTADO) == FaseSalida.ABIERTA && t == PathComputationType.LAND;
    }

    /** La mitad de abajo (la del reloj). */
    public static BlockPos abajo(BlockState s, BlockPos p) {
        return s.getValue(HALF) == DoubleBlockHalf.UPPER ? p.below() : p;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(l.getBlockEntity(abajo(s, pos)) instanceof SalidaBlockEntity be)) return InteractionResult.PASS;
        if (!l.isClientSide) be.usar(p, s.getValue(HALF) == DoubleBlockHalf.UPPER, p.getItemInHand(hand));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(HALF) == DoubleBlockHalf.LOWER ? new SalidaBlockEntity(p, s) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide || s.getValue(HALF) != DoubleBlockHalf.LOWER) return null;
        return createTickerHelper(t, SawRegistro.SALIDA_BE.get(), SalidaBlockEntity::tickServidor);
    }
}
