package com.miscelanea.torneo;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/** Tablón de torneos de pared: clic derecho para ver el cuadro, apuntarte o (si lo organizas) empezarlo. */
public class TorneoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape[] FORMA = {Block.box(0, 0, 14, 16, 16, 16), Block.box(0, 0, 0, 16, 16, 2), Block.box(0, 0, 0, 2, 16, 16), Block.box(14, 0, 0, 16, 16, 16)};

    public TorneoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction d = c.getClickedFace().getAxis().isHorizontal() ? c.getClickedFace() : c.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, d);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable net.minecraft.world.entity.LivingEntity e, net.minecraft.world.item.ItemStack st) {
        if (!l.isClientSide && e instanceof Player pl && l.getBlockEntity(p) instanceof TorneoBlockEntity be) {
            be.organizador = pl.getUUID();
            be.cambio();
        }
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case EAST -> 2;
            case WEST -> 3;
            default -> 0;
        }];
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.torneo.TorneoScreen.abrir(pos));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new TorneoBlockEntity(p, s);
    }
}
