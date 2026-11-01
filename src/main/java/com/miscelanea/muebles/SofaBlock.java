package com.miscelanea.muebles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/** Sofá tintable que se une con los sofás de al lado que miran hacia el mismo sitio (los brazos solo quedan en los extremos). */
public class SofaBlock extends Block {
    public enum Forma implements StringRepresentable {
        SOLO, IZQ, DER, MEDIO;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Forma> FORMA = EnumProperty.create("forma", Forma.class);
    private static final Map<Direction, VoxelShape> FORMAS = new EnumMap<>(Direction.class);

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            VoxelShape base = Block.box(0, 0, 0, 16, 9, 16);
            VoxelShape resp = switch (d) {
                case NORTH -> Block.box(0, 9, 12.5, 16, 18, 16);
                case SOUTH -> Block.box(0, 9, 0, 16, 18, 3.5);
                case EAST -> Block.box(0, 9, 0, 3.5, 18, 16);
                default -> Block.box(12.5, 9, 0, 16, 18, 16);
            };
            FORMAS.put(d, Shapes.or(base, resp));
        }
    }

    public SofaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FORMA, Forma.SOLO).setValue(Muebles.COLOR, DyeColor.RED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, FORMA, Muebles.COLOR);
    }

    private static boolean une(BlockGetter l, BlockPos p, Direction f) {
        BlockState s = l.getBlockState(p);
        return s.getBlock() instanceof SofaBlock && s.getValue(FACING) == f;
    }

    private static BlockState conForma(BlockState s, BlockGetter l, BlockPos p) {
        Direction f = s.getValue(FACING);
        // el sentado mira hacia el lado contrario a FACING... no: FACING es hacia donde mira quien se sienta
        Direction izquierda = f.getCounterClockWise();
        boolean i = une(l, p.relative(izquierda), f), d = une(l, p.relative(izquierda.getOpposite()), f);
        Forma fo = i && d ? Forma.MEDIO : i ? Forma.DER : d ? Forma.IZQ : Forma.SOLO;
        return s.setValue(FORMA, fo);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return conForma(defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite()), c.getLevel(), c.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction dir, BlockState otro, LevelAccessor l, BlockPos p, BlockPos po) {
        return dir.getAxis().isHorizontal() ? conForma(s, l, p) : s;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMAS.get(s.getValue(FACING));
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = Muebles.tenir(s, level, pos, p, hand);
        if (r != InteractionResult.PASS) return r;
        return Muebles.sentar(level, pos, p, 9.0 / 16.0, s.getValue(FACING));
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
