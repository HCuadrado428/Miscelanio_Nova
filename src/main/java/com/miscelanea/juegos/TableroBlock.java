package com.miscelanea.juegos;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
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

/** Tablero de ajedrez/damas. Agachado + clic derecho cambia de juego. Las casillas se tocan con clic derecho apuntando a ellas. */
public class TableroBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 1.5, 16);

    public TableroBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection());
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
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new TableroBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? null : createTickerHelper(t, ModBlocks.TABLERO_BE.get(), TableroBlockEntity::tickServidor);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (!p.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!l.isClientSide && l.getBlockEntity(pos) instanceof TableroBlockEntity be) {
            // en mitad de una partida solo la pueden cambiar sus jugadores (o cualquiera si la han abandonado)
            if (p instanceof ServerPlayer sp && !be.puedeReiniciar(sp)) {
                p.displayClientMessage(Component.translatableWithFallback("msg.miscelanea.tablero.no_reiniciar",
                        "Solo los jugadores pueden cambiar esta partida").withStyle(ChatFormatting.RED), true);
                return InteractionResult.CONSUME;
            }
            be.reiniciar(be.modo + 1);
            p.displayClientMessage(Component.translatable("msg.miscelanea.tablero.modo", Component.translatable("tablero.miscelanea.modo." + be.modo))
                    .withStyle(ChatFormatting.YELLOW), true);
            l.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
