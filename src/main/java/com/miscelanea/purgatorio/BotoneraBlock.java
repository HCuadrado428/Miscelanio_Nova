package com.miscelanea.purgatorio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * La botonera del ascensor del hotel del Purgatorio: un panel de latón en la pared de la cabina. Al apretarla (clic
 * derecho) se cierran las puertas, se oye el motor y, al rato, el ascensor te deja en otro piso (ver
 * {@link Purgatorio#llamarAscensor}). Fuera del Purgatorio solo hace clic.
 */
public class BotoneraBlock extends HorizontalDirectionalBlock {
    /** Pegada a la pared de detrás, según hacia dónde mire (norte, sur, oeste, este). */
    private static final VoxelShape[] FORMA = {
            Block.box(4, 2, 14, 12, 14, 16), Block.box(4, 2, 0, 12, 14, 2),
            Block.box(14, 2, 4, 16, 14, 12), Block.box(0, 2, 4, 2, 14, 12)};

    public BotoneraBlock(Properties p) {
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
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        }];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand m, BlockHitResult h) {
        l.playSound(pl, p, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.4F);
        if (!l.isClientSide && pl instanceof ServerPlayer sp) Purgatorio.llamarAscensor(sp, p);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
