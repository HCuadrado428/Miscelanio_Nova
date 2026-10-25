package com.miscelanea.programa;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/** Interruptor de circuito del generador: luz roja (apagado) / verde (encendido). */
public class InterruptorBlock extends ParedBlock {
    public static final BooleanProperty ON = BooleanProperty.create("on");

    public InterruptorBlock(Properties p) {
        super(p, 3, 1, 13, 15, 4);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ON);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (s.getValue(ON)) return InteractionResult.CONSUME;
        level.setBlock(pos, s.setValue(ON, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0F, 0.5F);
        level.playSound(null, pos, ModSounds.CHISPAZO.get(), SoundSource.BLOCKS, 0.4F, 1.2F);
        com.miscelanea.carcel.Ruido.alertar((ServerLevel) level, net.minecraft.world.phys.Vec3.atCenterOf(pos), 10, player);
        return InteractionResult.CONSUME;
    }
}
