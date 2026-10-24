package com.miscelanea.carcel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Alarma de latas: un hilo con latas colgando a la altura de los tobillos.
 * Al cruzarlo suenan muy fuerte y atraen a los monstruos de lejos. Se puede pasar agachado con cuidado (50 %).
 */
public class LatasBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty SONANDO = BlockStateProperties.TRIGGERED;
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 6, 16);

    public LatasBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SONANDO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SONANDO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState s, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || s.getValue(SONANDO) || !(entity instanceof Player p) || p.isSpectator()) return;
        if (p.isShiftKeyDown() && level.random.nextFloat() < 0.5F) return;
        level.setBlock(pos, s.setValue(SONANDO, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, 60);
        for (int i = 0; i < 3; i++) {
            level.playSound(null, pos, SoundEvents.CHAIN_FALL, SoundSource.BLOCKS, 1.5F, 1.6F + i * 0.2F);
        }
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 2.0F);
        Ruido.alertar((ServerLevel) level, Vec3.atCenterOf(pos), 28, p);
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        if (s.getValue(SONANDO)) level.setBlock(pos, s.setValue(SONANDO, false), Block.UPDATE_ALL);
    }
}
