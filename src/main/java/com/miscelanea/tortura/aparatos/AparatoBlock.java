package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * Aparato de dos bloques: la parte PRINCIPAL lleva el BlockEntity y dibuja el modelo entero (que se sale hasta el otro
 * bloque); el ANEXO solo da forma y colisión (render invisible). Romper cualquiera de las dos quita las dos.
 * Los modelos se diseñan mirando al norte; FACING es hacia dónde mira el frente (hacia quien lo pone).
 */
public abstract class AparatoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ANEXO = BooleanProperty.create("anexo");

    protected AparatoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ANEXO, false));
    }

    /** Dirección de la parte principal al anexo, según hacia dónde mira el frente. */
    public abstract Direction haciaAnexo(Direction frente);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ANEXO);
    }

    public static BlockPos principal(BlockState s, BlockPos p) {
        if (!(s.getBlock() instanceof AparatoBlock ab) || !s.getValue(ANEXO)) return p;
        return p.relative(ab.haciaAnexo(s.getValue(FACING)).getOpposite());
    }

    private BlockPos otra(BlockState s, BlockPos p) {
        Direction d = haciaAnexo(s.getValue(FACING));
        return s.getValue(ANEXO) ? p.relative(d.getOpposite()) : p.relative(d);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return s.getValue(ANEXO) ? RenderShape.INVISIBLE : RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        Direction f = c.getHorizontalDirection().getOpposite();
        BlockPos p = c.getClickedPos();
        BlockPos o = p.relative(haciaAnexo(f));
        Level l = c.getLevel();
        if (o.getY() < l.getMinBuildHeight() || o.getY() >= l.getMaxBuildHeight() || !l.getBlockState(o).canBeReplaced(c)
                || !l.getWorldBorder().isWithinBounds(o)) return null;
        return defaultBlockState().setValue(FACING, f);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity e, ItemStack st) {
        super.setPlacedBy(l, p, s, e, st);
        if (!l.isClientSide) l.setBlock(p.relative(haciaAnexo(s.getValue(FACING))), s.setValue(ANEXO, true), 3);
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader l, BlockPos p) {
        if (!s.getValue(ANEXO)) return true;
        BlockState o = l.getBlockState(otra(s, p));
        return o.is(this) && !o.getValue(ANEXO) && o.getValue(FACING) == s.getValue(FACING);
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        if (pv.equals(otra(s, p))) {
            boolean ok = vecino.is(this) && vecino.getValue(ANEXO) != s.getValue(ANEXO) && vecino.getValue(FACING) == s.getValue(FACING);
            if (!ok) return Blocks.AIR.defaultBlockState();
        }
        return s;
    }

    @Override
    public void playerWillDestroy(Level l, BlockPos p, BlockState s, Player pl) {
        if (!l.isClientSide && s.getValue(ANEXO)) {
            // romper el anexo rompe la parte principal (que es la que suelta el objeto, y en creativo nada)
            BlockPos o = otra(s, p);
            BlockState os = l.getBlockState(o);
            if (os.is(this) && !os.getValue(ANEXO)) l.destroyBlock(o, !pl.isCreative(), pl);
        }
        super.playerWillDestroy(l, p, s, pl);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean movido) {
        if (!s.is(nuevo.getBlock()) && !s.getValue(ANEXO) && l.getBlockEntity(p) instanceof AparatoBlockEntity be) be.sacar(false);
        super.onRemove(s, l, p, nuevo, movido);
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        BlockPos pr = principal(s, pos);
        if (!(l.getBlockEntity(pr) instanceof AparatoBlockEntity be)) return InteractionResult.PASS;
        if (Aparatos.en(p)) return InteractionResult.FAIL;   // la víctima no toca nada
        if (!l.isClientSide) be.usar(p, s.getValue(ANEXO), hit);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, net.minecraft.world.level.pathfinder.PathComputationType t) {
        return false;
    }

    @Nullable
    @Override
    public abstract BlockEntity newBlockEntity(BlockPos p, BlockState s);
}
