package com.miscelanea.nucleo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Bloque "sin textura" que deja el aliento del núcleo. Al rato se deshace solo. */
public class BloqueCorrupto extends Block {
    public BloqueCorrupto(Properties p) {
        super(p);
    }

    @Override
    public void onPlace(BlockState s, Level level, BlockPos pos, BlockState viejo, boolean moviendo) {
        if (!viejo.is(this)) level.scheduleTick(pos, this, 120 + level.random.nextInt(240));
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, 0.6F, 0.5F);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
