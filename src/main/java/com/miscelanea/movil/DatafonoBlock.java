package com.miscelanea.movil;

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

/**
 * Datáfono para tiendas: quien lo pone es el dueño y elige precio y concepto; los demás pagan desde su saldo del Banco
 * con clic derecho (sale una pantalla para confirmar). El dinero va directo a la cuenta del dueño.
 */
public class DatafonoBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape NS = Block.box(4, 0, 5, 12, 10, 11), EO = Block.box(5, 0, 4, 11, 10, 12);

    public DatafonoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(FACING).getAxis() == Direction.Axis.Z ? NS : EO;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new DatafonoBlockEntity(pos, st);
    }

    @Override
    public void setPlacedBy(Level l, BlockPos pos, BlockState st, @Nullable LivingEntity quien, ItemStack s) {
        super.setPlacedBy(l, pos, st, quien, s);
        if (!l.isClientSide && quien instanceof ServerPlayer sp && l.getBlockEntity(pos) instanceof DatafonoBlockEntity be) be.ponerDueno(sp);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand h, BlockHitResult hit) {
        if (!MovilConfig.banco()) return InteractionResult.PASS;
        if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.movil.DatafonoScreen.abrir(pos));
        else if (p instanceof ServerPlayer sp) Banco.estado(sp);
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
