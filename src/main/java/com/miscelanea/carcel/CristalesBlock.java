package com.miscelanea.carcel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cristales rotos en el suelo. Pisarlos andando hace ruido y atrae a los monstruos; agachado no.
 */
public class CristalesBlock extends Block {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 1, 16);

    public CristalesBlock(Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public boolean canSurvive(BlockState s, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState s, net.minecraft.core.Direction dir, BlockState vecino,
                                  net.minecraft.world.level.LevelAccessor level, BlockPos pos, BlockPos vecinoPos) {
        return !s.canSurvive(level, pos) ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
                : super.updateShape(s, dir, vecino, level, pos, vecinoPos);
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState s, Entity entity) {
        if (level.isClientSide || !(entity instanceof Player p) || p.isSpectator()) return;
        boolean sigilo = p.isShiftKeyDown();
        // Como mucho un crujido cada medio segundo por jugador
        long ahora = level.getGameTime();
        if (ahora - p.getPersistentData().getLong("miscelanea:cristal") < 10) return;
        p.getPersistentData().putLong("miscelanea:cristal", ahora);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, sigilo ? 0.15F : 0.9F,
                1.4F + level.random.nextFloat() * 0.4F);
        if (!sigilo) Ruido.alertar((ServerLevel) level, Vec3.atCenterOf(pos), 16, p);
        super.stepOn(level, pos, s, entity);
    }
}
