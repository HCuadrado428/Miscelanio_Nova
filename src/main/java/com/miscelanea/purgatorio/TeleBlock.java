package com.miscelanea.purgatorio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Una tele de tubo encendida sin señal (la nieve se mueve sola: es una textura animada), de alguna habitación del Hotel
 * del Videoclip. Es un bloque normal, sin bloque-entidad: su luz es la del bloque y el ruido de la nieve lo pone el
 * cliente para la tele más cercana que haya visto hace poco (ver {@link #VISTAS}).
 */
public class TeleBlock extends HorizontalDirectionalBlock {
    /** La caja de la tele y el tubo de atrás, según hacia dónde mire la pantalla (norte, sur, oeste, este). */
    private static final VoxelShape[] FORMA = {
            Block.box(1, 0, 2.9, 15, 12, 16), Block.box(1, 0, 0, 15, 12, 13.1),
            Block.box(2.9, 0, 1, 16, 12, 15), Block.box(0, 0, 1, 13.1, 12, 15)};
    /** Solo en el cliente: dónde se ha visto una tele hace poco (bloque -> milisegundo). Se limpia solo. */
    public static final Map<BlockPos, Long> VISTAS = new ConcurrentHashMap<>();

    public TeleBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA[switch (s.getValue(FACING)) {
            case SOUTH -> 1;
            case WEST -> 2;
            case EAST -> 3;
            default -> 0;
        }];
    }

    /** Lo llama el cliente de vez en cuando para los bloques que tiene cerca: así sabe que hay una tele ahí. */
    @Override
    public void animateTick(BlockState s, Level l, BlockPos p, RandomSource r) {
        if (VISTAS.size() < 64) VISTAS.put(p.immutable(), System.currentTimeMillis());
        if (r.nextInt(12) == 0) {
            // alguna chispa de electricidad estática en la pantalla
            Direction f = s.getValue(FACING);
            l.addParticle(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5 + f.getStepX() * 0.45 + (r.nextDouble() - 0.5) * 0.5,
                    p.getY() + 0.25 + r.nextDouble() * 0.5, p.getZ() + 0.5 + f.getStepZ() * 0.45 + (r.nextDouble() - 0.5) * 0.5, 0, 0, 0);
        }
    }
}
