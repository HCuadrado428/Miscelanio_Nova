package com.miscelanea.tortura.saw;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

/**
 * Alambre de espino en concertina (Saw I). Se une con el alambre de al lado (en las 6 direcciones) y con las paredes
 * (en horizontal): los anillos van de uno a otro. No tiene colisión, pero atrapa como una telaraña (se avanza al 30 %)
 * y desgarra al moverse dentro: 1 de daño por cada bloque recorrido y arañazos / cortes donde toca ({@link Alambre}).
 * Golpearlo con la mano corta la mano; se quita con las {@link TenazasItem}.
 */
public class AlambreEspinoBlock extends Block {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
            WEST = BlockStateProperties.WEST, UP = BlockStateProperties.UP, DOWN = BlockStateProperties.DOWN;
    public static final Map<Direction, BooleanProperty> LADOS = new EnumMap<>(Direction.class);
    private static final VoxelShape FORMA = Block.box(1, 1, 1, 15, 15, 15);
    /** Lo que se avanza dentro (como la telaraña, que es 0,25 / 0,05). */
    public static final Vec3 FRENO = new Vec3(0.3, 0.4, 0.3);

    static {
        LADOS.put(Direction.NORTH, NORTH);
        LADOS.put(Direction.EAST, EAST);
        LADOS.put(Direction.SOUTH, SOUTH);
        LADOS.put(Direction.WEST, WEST);
        LADOS.put(Direction.UP, UP);
        LADOS.put(Direction.DOWN, DOWN);
    }

    public AlambreEspinoBlock(Properties p) {
        super(p);
        BlockState s = stateDefinition.any();
        for (BooleanProperty b : LADOS.values()) s = s.setValue(b, false);
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private boolean conecta(BlockGetter l, BlockPos p, Direction d) {
        BlockPos n = p.relative(d);
        BlockState o = l.getBlockState(n);
        if (o.getBlock() instanceof AlambreEspinoBlock) return true;
        return d.getAxis().isHorizontal() && o.isFaceSturdy(l, n, d.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = defaultBlockState();
        for (Map.Entry<Direction, BooleanProperty> e : LADOS.entrySet())
            s = s.setValue(e.getValue(), conecta(c.getLevel(), c.getClickedPos(), e.getKey()));
        return s;
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(BlockState s, Direction d, BlockState vecino, LevelAccessor l, BlockPos p, BlockPos pv) {
        return s.setValue(LADOS.get(d), conecta(l, p, d));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean isPathfindable(BlockState s, BlockGetter l, BlockPos p, PathComputationType t) {
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
        if (!(e instanceof LivingEntity le)) return;
        if (e instanceof Player pl && pl.getAbilities().flying) return;
        e.makeStuckInBlock(s, FRENO);
        if (!l.isClientSide) Alambre.tocar(le, p);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void attack(BlockState s, Level l, BlockPos p, Player pl) {
        if (!l.isClientSide) Alambre.cortarMano(pl, p);
    }
}
