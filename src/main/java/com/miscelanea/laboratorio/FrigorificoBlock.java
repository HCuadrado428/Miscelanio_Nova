package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * El frigorífico de muestras: un armario de dos bloques de alto con puerta de cristal y menú de cofre (27 huecos) para las
 * muestras, los sueros y los tubos. El hueco de encima es una parte invisible (ver MultiBloque).
 */
public class FrigorificoBlock extends BaseEntityBlock implements com.miscelanea.multibloque.MultiBloque {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final net.minecraft.world.phys.shapes.VoxelShape FORMA = net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 32, 15),
            CHOQUE = net.minecraft.world.level.block.Block.box(1, 0, 1, 15, 16, 15);
    private static final java.util.List<BlockPos> ENCIMA = java.util.List.of(BlockPos.ZERO.above());

    public FrigorificoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(FACING);
    }

    /** El frente (pantalla, puerta, boquillas) mira hacia quien lo pone. */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) {
        return estado(c);
    }

    @Nullable
    private BlockState estado(net.minecraft.world.item.context.BlockPlaceContext c) {
        BlockState s = defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        return com.miscelanea.multibloque.MultiBloque.cabe(this, s, c) ? s : null;
    }

    @Override
    public java.util.List<BlockPos> partes(BlockState s) {
        return ENCIMA;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape formaCompleta(BlockState s) {
        return FORMA;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) {
        return FORMA;
    }

    @Override
    public net.minecraft.world.phys.shapes.VoxelShape getCollisionShape(BlockState s, net.minecraft.world.level.BlockGetter l, BlockPos p, net.minecraft.world.phys.shapes.CollisionContext c) {
        return CHOQUE;
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState viejo, boolean mov) {
        super.onPlace(s, l, p, viejo, mov);
        if (!viejo.is(this)) com.miscelanea.multibloque.MultiBloque.colocar(this, l, p, s);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new FrigorificoBlockEntity(p, s);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean movido) {
        if (!s.is(nuevo.getBlock())) com.miscelanea.multibloque.MultiBloque.quitar(this, l, p, s);
        if (!s.is(nuevo.getBlock()) && l.getBlockEntity(p) instanceof FrigorificoBlockEntity be) {
            Containers.dropContents(l, p, be);
            l.updateNeighbourForOutputSignal(p, this);
        }
        super.onRemove(s, l, p, nuevo, movido);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (l.getBlockEntity(p) instanceof FrigorificoBlockEntity be) pl.openMenu(be);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState s) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(l.getBlockEntity(p));
    }
}
