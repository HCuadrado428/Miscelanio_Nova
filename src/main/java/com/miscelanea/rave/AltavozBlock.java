package com.miscelanea.rave;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.multibloque.MultiBloque;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Torre de altavoces de concierto: tres bloques de alto (subgrave abajo y dos cajas colgadas encima; el bloque de verdad es el
 * de abajo: ver {@link MultiBloque}). Mira hacia quien la pone y suma alcance a la cabina de DJ cercana.
 */
public class AltavozBlock extends BaseEntityBlock implements MultiBloque {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final List<BlockPos> PARTES = List.of(BlockPos.ZERO.above(), BlockPos.ZERO.above(2));
    private static final VoxelShape[] FORMAS = new VoxelShape[6], CHOQUES = new VoxelShape[6];

    public AltavozBlock(Properties p) {
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
        return PARTES;
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        Direction d = s.getValue(FACING);
        VoxelShape f = FORMAS[d.get3DDataValue()];
        if (f == null) f = FORMAS[d.get3DDataValue()] = Shapes.or(Montaje.forma(d, -2, 0, 1, 18, 19, 15), Montaje.forma(d, -2.4, 19, 2, 18.4, 48, 14));
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
        return new AltavozBlockEntity(p, s);
    }
}
