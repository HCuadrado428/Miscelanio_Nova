package com.miscelanea.mesas;

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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Mesa grande de varios bloques (billar, futbolín): la parte 0 es la principal (con la entidad); las demás se colocan a
 * la derecha (largo) y hacia delante (ancho) del jugador que la pone. Cada parte tiene su trozo de modelo y colisión,
 * y al romper cualquiera se quita la mesa entera.
 */
public abstract class MesaGrandeBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PARTE = IntegerProperty.create("parte", 0, 7);
    public final int largo, ancho;
    private final VoxelShape forma;

    protected MesaGrandeBlock(Properties p, int largo, int ancho, double alto) {
        super(p);
        this.largo = largo;
        this.ancho = ancho;
        this.forma = Block.box(0, 0, 0, 16, alto, 16);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PARTE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PARTE);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return forma;
    }

    /** Posición de la parte (i a lo largo, j a lo ancho) a partir de la principal. */
    public BlockPos parte(BlockPos principal, Direction f, int i, int j) {
        return principal.relative(f.getClockWise(), i).relative(f, j);
    }

    public BlockPos principal(BlockState s, BlockPos pos) {
        int k = s.getValue(PARTE);
        Direction f = s.getValue(FACING);
        return pos.relative(f.getCounterClockWise(), k % largo).relative(f.getOpposite(), k / largo);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection();
        BlockPos p = c.getClickedPos();
        Level l = c.getLevel();
        for (int j = 0; j < ancho; j++)
            for (int i = 0; i < largo; i++) {
                BlockPos q = parte(p, f, i, j);
                if (!l.getBlockState(q).canBeReplaced(c) || !l.getWorldBorder().isWithinBounds(q)) return null;
            }
        return defaultBlockState().setValue(FACING, f).setValue(PARTE, 0);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity e, ItemStack st) {
        Direction f = s.getValue(FACING);
        for (int j = 0; j < ancho; j++)
            for (int i = 0; i < largo; i++)
                if (i + j > 0) l.setBlock(parte(p, f, i, j), s.setValue(PARTE, i + j * largo), 3);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos pos, BlockState nuevo, boolean mov) {
        if (!nuevo.is(this) && !l.isClientSide) {
            BlockPos pr = principal(s, pos);
            Direction f = s.getValue(FACING);
            for (int j = 0; j < ancho; j++)
                for (int i = 0; i < largo; i++) {
                    BlockPos q = parte(pr, f, i, j);
                    if (!q.equals(pos) && l.getBlockState(q).is(this)) l.setBlock(q, Blocks.AIR.defaultBlockState(), 35);
                }
        }
        super.onRemove(s, l, pos, nuevo, mov);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(PARTE) == 0 ? nuevaEntidad(p, s) : null;
    }

    protected abstract BlockEntity nuevaEntidad(BlockPos p, BlockState s);

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos pr = principal(s, pos);
        if (l.isClientSide) return usarCliente(l, pr, p);
        if (p instanceof ServerPlayer sp && l.getBlockEntity(pr) instanceof MesaEntidad me) me.usar(sp, hit.getLocation());
        return InteractionResult.CONSUME;
    }

    protected InteractionResult usarCliente(Level l, BlockPos principal, Player p) {
        return InteractionResult.SUCCESS;
    }

    /** Entidad de una mesa: qué hacer cuando un jugador la usa (unirse, jugar...). */
    public interface MesaEntidad {
        void usar(ServerPlayer p, net.minecraft.world.phys.Vec3 donde);

        /** Cliente: datos de física recibidos (posiciones de este tic). */
        void fisica(float[] datos);
    }
}
