package com.miscelanea.programa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Botón rojo de seta (sala de seguridad, furgoneta). Avisa a la prueba de que se ha pulsado. */
public class BotonRojoBlock extends ParedBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public BotonRojoBlock(Properties p) {
        super(p, 4, 4, 12, 12, 5);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POWERED);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (s.getValue(POWERED)) return InteractionResult.CONSUME;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        level.setBlock(pos, s.setValue(POWERED, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, 20);
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, 0.6F);
        if (player instanceof ServerPlayer sp) Prueba.boton((ServerLevel) level, pos, sp);
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (s.getValue(POWERED)) {
            level.setBlock(pos, s.setValue(POWERED, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 1.0F, 0.5F);
        }
    }
}
