package com.miscelanea.movil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Marco de fotos para la pared. Con el móvil en la mano, clic derecho va pasando las fotos de tu galería; agachado,
 * hacia atrás. La que elijas se queda en el marco y la ve todo el mundo, no solo tú.
 */
public class MarcoFotoBlock extends Block implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape NORTE = Block.box(1, 1, 14, 15, 15, 16);
    private static final VoxelShape SUR = Block.box(1, 1, 0, 15, 15, 2);
    private static final VoxelShape OESTE = Block.box(14, 1, 1, 16, 15, 15);
    private static final VoxelShape ESTE = Block.box(0, 1, 1, 2, 15, 15);

    public MarcoFotoBlock(Properties p) {
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
        return switch (s.getValue(FACING)) {
            case SOUTH -> SUR;
            case WEST -> OESTE;
            case EAST -> ESTE;
            default -> NORTE;
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new MarcoFotoBlockEntity(p, s);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(l.getBlockEntity(p) instanceof MarcoFotoBlockEntity be)) return InteractionResult.PASS;
        if (!(j instanceof ServerPlayer sp)) return InteractionResult.PASS;
        // sin el móvil en la mano no se puede cambiar: es de donde salen las fotos
        if (!j.getItemInHand(m).is(MovilRegistro.MOVIL.get())) {
            j.displayClientMessage(Component.translatable("marco.miscelanea.hace_falta_movil"), true);
            return InteractionResult.CONSUME;
        }
        List<Integer> galeria = Fotos.galeriaDe(sp);
        if (galeria.isEmpty()) {
            j.displayClientMessage(Component.translatable("marco.miscelanea.sin_fotos"), true);
            return InteractionResult.CONSUME;
        }
        int paso = j.isShiftKeyDown() ? -1 : 1;
        int siguiente = Math.floorMod(be.indice() + paso, galeria.size());
        int copia = Fotos.copiaParaEnsenar(sp, galeria.get(siguiente));
        if (copia < 0) {
            j.displayClientMessage(Component.translatable("marco.miscelanea.no_cabe"), true);
            return InteractionResult.CONSUME;
        }
        be.poner(copia, siguiente);
        l.playSound(null, p, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8F, 1.1F);
        j.displayClientMessage(Component.translatable("marco.miscelanea.puesta", siguiente + 1, galeria.size()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
