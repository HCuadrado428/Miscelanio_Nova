package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Tubo y panel de neón: se pegan a la cara en que se ponen (suelo, techo o pared), brillan en uno de 16 colores y se cambian de
 * color con un tinte (clic derecho). La forma es una caja del modelo que mira hacia arriba, girada como el blockstate.
 */
public class NeonBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private final double[] caja;
    private final VoxelShape[] formas = new VoxelShape[6];

    public NeonBlock(Properties p, double... caja) {
        super(p);
        this.caja = caja;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(Muebles.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, Muebles.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        Direction d = s.getValue(FACING);
        VoxelShape f = formas[d.get3DDataValue()];
        if (f == null) f = formas[d.get3DDataValue()] = Neon.forma(d, caja[0], caja[1], caja[2], caja[3], caja[4], caja[5]);
        return f;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        return Muebles.tenir(s, l, p, j, m);
    }
}
