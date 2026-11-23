package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * Andamio de escenario (truss): un nudo de aluminio que se une solo con los andamios de al lado, arriba y abajo, como las
 * vallas pero en las seis direcciones. Con polvo de piedra luminosa se le ponen luces LED (un tinte les cambia el color; con
 * la mano vacía y agachado se quitan). De él se pueden colgar los focos y la bola de discoteca.
 */
public class TrussBlock extends Block {
    public static final BooleanProperty NORTE = BlockStateProperties.NORTH, SUR = BlockStateProperties.SOUTH, ESTE = BlockStateProperties.EAST,
            OESTE = BlockStateProperties.WEST, ARRIBA = BlockStateProperties.UP, ABAJO = BlockStateProperties.DOWN;
    public static final BooleanProperty LUCES = BooleanProperty.create("luces");
    private static final Map<Direction, BooleanProperty> LADOS = Map.of(Direction.NORTH, NORTE, Direction.SOUTH, SUR, Direction.EAST, ESTE,
            Direction.WEST, OESTE, Direction.UP, ARRIBA, Direction.DOWN, ABAJO);
    private static final VoxelShape NUDO = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> BRAZOS = new HashMap<>();
    private final Map<BlockState, VoxelShape> formas = new HashMap<>();

    static {
        for (Direction d : Direction.values()) BRAZOS.put(d, Neon.forma(rotarAArriba(d), 5, 11, 5, 11, 16, 11));
    }

    /** El brazo está dibujado hacia arriba; para el giro de {@link Neon#forma} la dirección es la misma. */
    private static Direction rotarAArriba(Direction d) {
        return d;
    }

    public TrussBlock(Properties p) {
        super(p);
        BlockState s = stateDefinition.any().setValue(LUCES, false).setValue(Muebles.COLOR, DyeColor.WHITE);
        for (BooleanProperty b : LADOS.values()) s = s.setValue(b, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTE, SUR, ESTE, OESTE, ARRIBA, ABAJO, LUCES, Muebles.COLOR);
    }

    private boolean une(BlockState vecino) {
        return vecino.getBlock() instanceof TrussBlock;
    }

    private BlockState conectar(BlockState s, LevelAccessor l, BlockPos p) {
        for (Map.Entry<Direction, BooleanProperty> e : LADOS.entrySet()) s = s.setValue(e.getValue(), une(l.getBlockState(p.relative(e.getKey()))));
        return s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return conectar(defaultBlockState(), c.getLevel(), c.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        return s.setValue(LADOS.get(d), une(vecino));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return formas.computeIfAbsent(s, k -> {
            VoxelShape f = NUDO;
            for (Map.Entry<Direction, BooleanProperty> e : LADOS.entrySet()) if (k.getValue(e.getValue())) f = Shapes.or(f, BRAZOS.get(e.getKey()));
            return f;
        });
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        ItemStack st = j.getItemInHand(m);
        if (st.getItem() instanceof DyeItem && s.getValue(LUCES)) return Muebles.tenir(s, l, p, j, m);
        if (st.is(Items.GLOWSTONE_DUST) && !s.getValue(LUCES)) {
            if (!l.isClientSide) {
                l.setBlock(p, s.setValue(LUCES, true), 3);
                l.playSound(null, p, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.3F);
                if (!j.getAbilities().instabuild) st.shrink(1);
            }
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (st.isEmpty() && j.isShiftKeyDown() && s.getValue(LUCES)) {
            if (!l.isClientSide) l.setBlock(p, s.setValue(LUCES, false), 3);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
