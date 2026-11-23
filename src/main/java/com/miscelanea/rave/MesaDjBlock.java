package com.miscelanea.rave;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.multibloque.MultiBloque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Cabina de DJ: tres bloques de ancho (el del centro es el de verdad y los de los lados, relleno: ver {@link MultiBloque}). Clic
 * derecho en cualquier parte abre su pantalla (elegir canción, volumen, bucle, lista y luces del local).
 */
public class MesaDjBlock extends BaseEntityBlock implements MultiBloque {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape[] FORMAS = new VoxelShape[6], CHOQUES = new VoxelShape[6];

    public MesaDjBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
        return MultiBloque.cabe(this, s, c) ? s : null;
    }

    @Override
    public List<BlockPos> partes(BlockState s) {
        Direction d = s.getValue(FACING);
        return List.of(BlockPos.ZERO.relative(d.getClockWise()), BlockPos.ZERO.relative(d.getCounterClockWise()));
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        Direction d = s.getValue(FACING);
        VoxelShape f = FORMAS[d.get3DDataValue()];
        if (f == null) f = FORMAS[d.get3DDataValue()] = Shapes.or(Montaje.forma(d, -16, 0, 1, 32, 13.5, 15), Montaje.forma(d, -13, 13.5, 3, 29, 16, 14));
        return f;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        int i = s.getValue(FACING).get3DDataValue();
        VoxelShape f = CHOQUES[i];
        if (f == null) f = CHOQUES[i] = MultiBloque.trozo(formaCompleta(s));
        return f;
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState viejo, boolean mov) {
        super.onPlace(s, l, p, viejo, mov);
        if (!viejo.is(this)) MultiBloque.colocar(this, l, p, s);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        if (!nuevo.is(this)) MultiBloque.quitar(this, l, p, s);
        super.onRemove(s, l, p, nuevo, mov);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return formaCompleta(s);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new MesaDjBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, RaveRegistro.MESA_BE.get(), MesaDjBlockEntity::tickCliente);
        return createTickerHelper(t, RaveRegistro.MESA_BE.get(), MesaDjBlockEntity::tickServidor);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.MesaDjScreen.abrir(p));
        return InteractionResult.sidedSuccess(l.isClientSide);
    }
}
