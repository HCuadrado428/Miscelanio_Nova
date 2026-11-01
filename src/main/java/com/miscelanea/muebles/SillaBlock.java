package com.miscelanea.muebles;

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
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Silla con cojín tintable. Clic derecho para sentarte; con un tinte cambias el color. */
public class SillaBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final Map<Direction, VoxelShape> FORMAS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape asiento = Block.box(2.5, 0, 2.5, 13.5, 10.5, 13.5);
            VoxelShape resp = switch (d) {
                case NORTH -> Block.box(3, 10, 12, 13, 20, 13.5);
                case SOUTH -> Block.box(3, 10, 2.5, 13, 20, 4);
                case EAST -> Block.box(2.5, 10, 3, 4, 20, 13);
                default -> Block.box(12, 10, 3, 13.5, 20, 13);
            };
            FORMAS.put(d, Shapes.or(asiento, resp));
        }
    }

    public SillaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Muebles.COLOR, DyeColor.RED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, Muebles.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMAS.get(s.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = Muebles.tenir(s, level, pos, p, hand);
        if (r != InteractionResult.PASS) return r;
        return Muebles.sentar(level, pos, p, 10.5 / 16.0, s.getValue(FACING));
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(FACING, r.rotate(s.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.rotate(m.getRotation(s.getValue(FACING)));
    }
}
