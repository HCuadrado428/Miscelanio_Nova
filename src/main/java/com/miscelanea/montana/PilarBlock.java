package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Pilar de la montaña rusa: un tubo de acero que sale solo debajo de la vía cuando está en el aire (uno sí y otro no, como
 * en las de verdad) y baja hasta el suelo. No se fabrica ni suelta nada: si se quita la vía, su pilar se cae entero.
 * Arriba lleva la placa que sujeta la vía y abajo la zapata de hormigón. Es un bloque normal (sin bloque-entidad).
 */
public class PilarBlock extends Block {
    public static final BooleanProperty ARRIBA = BooleanProperty.create("arriba");
    public static final BooleanProperty ABAJO = BooleanProperty.create("abajo");
    /** Lo más largo que se hace un pilar (si debajo no hay suelo en este trecho, no se pone). */
    public static final int MAX = 40;
    private static final VoxelShape TUBO = Block.box(5, 0, 5, 11, 16, 11);

    public PilarBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ARRIBA, false).setValue(ABAJO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ARRIBA, ABAJO);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return TUBO;
    }

    static boolean sujeta(BlockState s) {
        return s.getBlock() instanceof PilarBlock || s.getBlock() instanceof ViaMontanaBlock || s.getBlock() instanceof RizoBlock;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return estado(c.getLevel(), c.getClickedPos());
    }

    BlockState estado(BlockGetter l, BlockPos p) {
        return defaultBlockState().setValue(ARRIBA, !(l.getBlockState(p.above()).getBlock() instanceof PilarBlock))
                .setValue(ABAJO, !(l.getBlockState(p.below()).getBlock() instanceof PilarBlock));
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
        if (d == Direction.UP) {
            // sin vía (ni pilar) encima, se cae: y el de debajo detrás, hasta el suelo
            if (!sujeta(otro)) return Blocks.AIR.defaultBlockState();
            return s.setValue(ARRIBA, !(otro.getBlock() instanceof PilarBlock));
        }
        if (d == Direction.DOWN) return s.setValue(ABAJO, !(otro.getBlock() instanceof PilarBlock));
        return s;
    }

    /** ¿Lleva pilar esta vía? Una de cada dos (en damero), y solo si está en el aire. */
    static boolean toca(BlockPos p) {
        return ((p.getX() + p.getZ()) & 1) == 0;
    }

    /** Pone el pilar debajo de una vía (si está en el aire y hay suelo a menos de {@link #MAX} bloques). */
    public static void levantar(Level l, BlockPos via) {
        if (l.isClientSide || !toca(via)) return;
        BlockPos.MutableBlockPos m = via.mutable().move(Direction.DOWN);
        if (!libre(l, m)) return;
        int n = 0;
        while (n < MAX && m.getY() > l.getMinBuildHeight() && libre(l, m)) {
            m.move(Direction.DOWN);
            n++;
        }
        if (n >= MAX || m.getY() <= l.getMinBuildHeight()) return;   // no hay suelo cerca: se queda colgando como está
        Block b = com.miscelanea.registry.ModBlocks.PILAR_MONTANA.get();
        for (int i = 1; i <= n; i++) {
            BlockState s = b.defaultBlockState().setValue(ARRIBA, i == 1).setValue(ABAJO, i == n);
            l.setBlock(via.below(i), s, Block.UPDATE_CLIENTS);
        }
    }

    private static boolean libre(Level l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        return s.isAir() || (s.canBeReplaced() && s.getFluidState().isEmpty());
    }
}
