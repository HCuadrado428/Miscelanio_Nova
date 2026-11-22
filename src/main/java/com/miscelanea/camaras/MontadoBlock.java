package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Base de la cámara de vigilancia y de la televisión: se cuelgan de una pared (mirando hacia fuera) o del techo (mirando
 * hacia abajo); en el suelo se ponen mirando hacia donde mira quien las coloca. Clic derecho = canal siguiente; agachado,
 * el anterior.
 */
public abstract class MontadoBlock extends BaseEntityBlock {
    private final BiFunction<BlockPos, BlockState, ? extends BlockEntity> fabrica;
    private final Supplier<? extends BlockEntityType<?>> tipo;
    private final VoxelShape[] formas = new VoxelShape[6];

    protected MontadoBlock(Properties p, BiFunction<BlockPos, BlockState, ? extends BlockEntity> fabrica, Supplier<? extends BlockEntityType<?>> tipo) {
        super(p);
        this.fabrica = fabrica;
        this.tipo = tipo;
        registerDefaultState(stateDefinition.any().setValue(Montaje.FACING, Direction.NORTH));
    }

    /** La forma con el modelo mirando al norte. */
    protected abstract double[] caja();

    protected abstract int canalMin();

    protected abstract int canalActual(BlockEntity be);

    protected abstract void ponerCanal(BlockEntity be, int canal);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(Montaje.FACING);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(Montaje.FACING);
        VoxelShape f = formas[d.get3DDataValue()];
        if (f == null) {
            double[] c0 = caja();
            f = formas[d.get3DDataValue()] = Montaje.forma(d, c0[0], c0[1], c0[2], c0[3], c0[4], c0[5]);
        }
        return f;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction clic = c.getClickedFace();
        // la pared o el techo en que se apoya: mira hacia fuera de ella; en el suelo, hacia donde mira quien la pone
        Direction d = clic == Direction.UP ? c.getHorizontalDirection() : clic;
        return defaultBlockState().setValue(Montaje.FACING, d);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return fabrica.apply(p, s);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity quien, ItemStack i) {
        super.setPlacedBy(l, p, s, quien, i);
        if (l.getBlockEntity(p) instanceof VigilanciaBlockEntity v) {
            if (quien != null) v.giro(quien.getYRot());
            if (i.hasCustomHoverName()) v.nombre(i.getHoverName().getString());
        }
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        BlockEntity be = l.getBlockEntity(p);
        if (be == null) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        int min = canalMin(), n = canalActual(be) + (j.isShiftKeyDown() ? -1 : 1);
        if (n > 99) n = min;
        if (n < min) n = 99;
        ponerCanal(be, n);
        j.displayClientMessage(n == 0 ? Component.translatable("camaras.miscelanea.canal_auto") : Component.translatable("camaras.miscelanea.canal", n), true);
        return InteractionResult.CONSUME;
    }
}
