package com.miscelanea.carcel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Taquilla de dos bloques para esconderse (clic derecho). Dentro solo ves por las rendijas
 * y los monstruos que no te vieron entrar te pierden. Se sale con Shift, saltando o con clic derecho.
 */
public class TaquillaBlock extends HorizontalDirectionalBlock {
    public static final EnumProperty<DoubleBlockHalf> MITAD = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final BooleanProperty ABIERTA = BlockStateProperties.OPEN;

    private static final VoxelShape N = Shapes.or(Block.box(1, 0, 1, 15, 16, 15));

    public TaquillaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(MITAD, DoubleBlockHalf.LOWER).setValue(ABIERTA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, MITAD, ABIERTA);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos pos = ctx.getClickedPos();
        if (pos.getY() >= ctx.getLevel().getMaxBuildHeight() - 1 || !ctx.getLevel().getBlockState(pos.above()).canBeReplaced(ctx)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(MITAD, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState vecino, LevelAccessor level, BlockPos pos, BlockPos vecinoPos) {
        DoubleBlockHalf half = s.getValue(MITAD);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            // la otra mitad tiene que seguir siendo una taquilla
            return vecino.is(this) && vecino.getValue(MITAD) != half
                    ? s.setValue(ABIERTA, vecino.getValue(ABIERTA))
                    : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(s, dir, vecino, level, pos, vecinoPos);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return N;
    }

    /** Quien está escondido dentro no choca con la taquilla (si no, el juego le empujaría fuera). */
    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (ctx instanceof net.minecraft.world.phys.shapes.EntityCollisionContext ec
                && ec.getEntity() instanceof Player p && Escondites.escondido(p)) {
            return Shapes.empty();
        }
        return N;
    }

    public static BlockPos base(BlockState s, BlockPos pos) {
        return s.getValue(MITAD) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) {
            BlockPos base = base(s, pos);
            if (Escondites.escondido(sp)) Escondites.salir(sp);
            else Escondites.entrar(sp, base, s.getValue(FACING));
        }
        return InteractionResult.CONSUME;
    }

    /** Abre/cierra la puerta (animación al entrar/salir). */
    public static void puerta(Level level, BlockPos base, boolean abierta) {
        BlockState s = level.getBlockState(base);
        if (!(s.getBlock() instanceof TaquillaBlock)) return;
        level.setBlock(base, s.setValue(ABIERTA, abierta), Block.UPDATE_ALL);
        BlockState arriba = level.getBlockState(base.above());
        if (arriba.getBlock() instanceof TaquillaBlock) level.setBlock(base.above(), arriba.setValue(ABIERTA, abierta), Block.UPDATE_ALL);
    }
}
