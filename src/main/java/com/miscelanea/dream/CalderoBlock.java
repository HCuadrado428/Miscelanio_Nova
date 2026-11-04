package com.miscelanea.dream;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Caldero de mezclas: clic derecho con una poción para echarla (devuelve la botella), con una botella vacía para sacar un
 * frasco de la mezcla, con pólvora para que los frascos salgan arrojadizos; agachado con la mano vacía se vacía.
 * Con la mano vacía (de pie) dice lo que lleva dentro.
 */
public class CalderoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    private static final VoxelShape FORMA = Shapes.join(
            Shapes.or(Block.box(1.5, 0, 1.5, 14.5, 13, 14.5), Block.box(1, 13, 1, 15, 14.5, 15)),
            Block.box(3.5, 5, 3.5, 12.5, 14.5, 12.5), BooleanOp.ONLY_FIRST);

    public CalderoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** Altura (en píxeles del bloque) de la superficie del líquido para un nivel 0..1 (igual en el modelo y el renderer). */
    public static float alturaLiquido(float nivel) {
        return 5.4F + 6.4F * Math.max(0.25F, nivel);
    }

    /** Apotema (px) del octógono del líquido a esa altura: la panza es más ancha que el hombro. */
    public static float radioLiquido(float nivel) {
        return alturaLiquido(nivel) <= 10.0F ? 5.75F : 5.05F;
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
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (!(l.getBlockEntity(pos) instanceof CalderoBlockEntity be)) return InteractionResult.PASS;
        ItemStack st = p.getItemInHand(hand);
        if (st.isEmpty()) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            // con la mano principal vacía y algo útil en la otra, que lo use la otra mano
            ItemStack otra = p.getOffhandItem();
            if (otra.is(Items.GLASS_BOTTLE) || otra.is(Items.GUNPOWDER) || Mezclas.esPocion(otra)) return InteractionResult.PASS;
            if (!l.isClientSide) {
                if (p.isSecondaryUseActive()) be.vaciar(p);
                else be.mostrar(p);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (st.is(Items.GLASS_BOTTLE)) {
            if (!l.isClientSide) be.llenar(p, hand, st);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (st.is(Items.GUNPOWDER)) {
            if (!l.isClientSide) be.polvora(p, st);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (Mezclas.esPocion(st)) {
            if (!l.isClientSide) be.verter(p, hand, st);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState n, boolean mov) {
        if (!s.is(n.getBlock()) && l.getBlockEntity(pos) instanceof CalderoBlockEntity be) be.soltarAlRomper();
        super.onRemove(s, l, pos, n, mov);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState s) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState s, Level l, BlockPos pos) {
        return l.getBlockEntity(pos) instanceof CalderoBlockEntity be ? be.quedan * 3 : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new CalderoBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? createTickerHelper(t, DreamRegistro.CALDERO_BE.get(), CalderoBlockEntity::tickCliente) : null;
    }
}
