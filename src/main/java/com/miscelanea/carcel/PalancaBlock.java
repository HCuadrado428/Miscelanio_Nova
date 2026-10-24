package com.miscelanea.carcel;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Palanca de descarga (en la pared). Mantén clic derecho para cargar la ejecución del chivato enganchado
 * a un terminal en 24 bloques. Varias palancas suman entre sí; mientras la usas no puedes vigilar tu espalda.
 */
public class PalancaBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape N = Block.box(3, 2, 12, 13, 14, 16);
    private static final VoxelShape S = Block.box(3, 2, 0, 13, 14, 4);
    private static final VoxelShape E = Block.box(0, 2, 3, 4, 14, 13);
    private static final VoxelShape W = Block.box(12, 2, 3, 16, 14, 13);

    public PalancaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getClickedFace().getAxis().isHorizontal() ? ctx.getClickedFace() : ctx.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, d);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return switch (s.getValue(FACING)) {
            case SOUTH -> S;
            case EAST -> E;
            case WEST -> W;
            default -> N;
        };
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        boolean alguno = false;
        for (CarroChivato c : level.getEntitiesOfClass(CarroChivato.class, new AABB(pos).inflate(24),
                c -> c.estado() == CarroChivato.ENGANCHADO)) {
            c.sumarProgreso(2.5F);
            alguno = true;
        }
        if (!s.getValue(POWERED)) {
            level.setBlock(pos, s.setValue(POWERED, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.8F, 0.6F);
        }
        level.scheduleTick(pos, this, 8);
        if (alguno) {
            if (level.getGameTime() % 8 == 0) level.playSound(null, pos, ModSounds.CHISPAZO.get(), SoundSource.BLOCKS, 0.5F, 0.8F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, 2, 0.15, 0.15, 0.15, 0.05);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        if (s.getValue(POWERED)) {
            level.setBlock(pos, s.setValue(POWERED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, 0.5F);
        }
    }
}
