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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;

/**
 * Monitor de laboratorio: un panel plano de un bloque con marco de acero que se cuelga de la pared. Varios juntos, mirando
 * al mismo lado y en rectángulo, forman <b>una sola pantalla grande</b> (hasta 8x8): la imagen ocupa toda la pared y el marco
 * solo queda por fuera. Manda el monitor de abajo a la izquierda (su canal es el de toda la pared). Clic derecho: la imagen
 * en pantalla grande; agachado + clic: canal siguiente de la pared.
 */
public class MonitorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int MAX = 8;
    private final VoxelShape[] formas = new VoxelShape[6];

    public MonitorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(FACING);
        VoxelShape f = formas[d.get3DDataValue()];
        if (f == null) f = formas[d.get3DDataValue()] = Montaje.forma(d, 0, 0, 13, 16, 16, 16);
        return f;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction clic = c.getClickedFace();
        return defaultBlockState().setValue(FACING, clic.getAxis().isHorizontal() ? clic : c.getHorizontalDirection().getOpposite());
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new MonitorBlockEntity(p, s);
    }

    // ------------------------------------------------------------------ la pared de monitores
    private static boolean igual(BlockGetter l, BlockPos q, Direction d) {
        BlockState s = l.getBlockState(q);
        return s.getBlock() instanceof MonitorBlock && s.getValue(FACING) == d;
    }

    /** Derecha de quien mira la pantalla. */
    public static Direction derecha(Direction d) {
        return d.getCounterClockWise();
    }

    /** Cuántos monitores iguales hay seguidos {a la izquierda, a la derecha, abajo, arriba} de este (contando desde él). */
    public static int[] grupo(BlockGetter l, BlockPos p, Direction d) {
        Direction der = derecha(d);
        int[] r = new int[4];
        Direction[] dirs = {der.getOpposite(), der, Direction.DOWN, Direction.UP};
        for (int i = 0; i < 4; i++) {
            BlockPos q = p;
            while (r[i] < MAX - 1 && igual(l, q = q.relative(dirs[i]), d)) r[i]++;
        }
        return r;
    }

    /** El monitor que manda en la pared: el de abajo a la izquierda. */
    public static BlockPos origen(BlockGetter l, BlockPos p, Direction d) {
        int[] g = grupo(l, p, d);
        return p.relative(derecha(d).getOpposite(), g[0]).below(g[2]);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity quien, ItemStack i) {
        super.setPlacedBy(l, p, s, quien, i);
        if (l.isClientSide) return;
        // al añadir un monitor a una pared, toma su canal (por si pasa a ser el que manda)
        Direction d = s.getValue(FACING);
        for (Direction v : new Direction[]{derecha(d), derecha(d).getOpposite(), Direction.UP, Direction.DOWN})
            if (igual(l, p.relative(v), d) && l.getBlockEntity(origen(l, p.relative(v), d)) instanceof TeleBlockEntity o
                    && l.getBlockEntity(p) instanceof TeleBlockEntity yo) {
                yo.canal(o.canal());
                break;
            }
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        BlockPos o = origen(l, p, s.getValue(FACING));
        if (!j.isShiftKeyDown()) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.camaras.VistaCamaraScreen.abrir(o));
            return InteractionResult.SUCCESS;
        }
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (!(l.getBlockEntity(o) instanceof TeleBlockEntity t)) return InteractionResult.PASS;
        int n = t.canal() + 1;
        if (n > 99) n = 0;
        t.canal(n);
        j.displayClientMessage(n == 0 ? Component.translatable("camaras.miscelanea.canal_auto") : Component.translatable("camaras.miscelanea.canal", n), true);
        return InteractionResult.CONSUME;
    }
}
