package com.miscelanea.glitch;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * Bloque glitcheado de paso (la textura animada del bloque corrupto, a brillo completo). Lo pone la onda de una muestra
 * de glitch encima de cada bloque que se come y, tras un rato al azar, se resuelve: se queda en aire o, si el servidor
 * tiene {@code destruir_bloques = false}, vuelve a ser el bloque de antes. No se puede picar ni empujar.
 */
public class GlitchTemporalBlock extends Block implements EntityBlock {
    public GlitchTemporalBlock(Properties p) {
        super(p);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new GlitchTemporalBlockEntity(pos, st);
    }

    /**
     * Pone el bloque glitcheado en {@code pos} (sin avisar a los vecinos, para que nada se caiga mientras dura) y lo deja
     * programado para resolverse dentro de {@code espera} ticks.
     */
    public static void corromper(ServerLevel level, BlockPos pos, BlockState original, boolean destruir, int espera) {
        Block b = GlitchRegistro.GLITCH_TEMPORAL.get();
        if (!level.setBlock(pos, b.defaultBlockState(), Block.UPDATE_CLIENTS)) return;
        if (level.getBlockEntity(pos) instanceof GlitchTemporalBlockEntity be) be.preparar(original, destruir);
        level.scheduleTick(pos, b, Math.max(1, espera));
    }

    @Override
    public void tick(BlockState st, ServerLevel level, BlockPos pos, RandomSource r) {
        resolver(level, pos, r.nextInt(4) == 0);
    }

    /** Se acaba el glitch en este bloque: aire o el bloque de antes. */
    public static void resolver(ServerLevel level, BlockPos pos, boolean conSonido) {
        if (!level.getBlockState(pos).is(GlitchRegistro.GLITCH_TEMPORAL.get())) return;
        BlockState nuevo = Blocks.AIR.defaultBlockState();
        if (level.getBlockEntity(pos) instanceof GlitchTemporalBlockEntity be && !be.destruir()) nuevo = be.original(level);
        level.sendParticles(GlitchRegistro.PARTICULA.get(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.0);
        if (conSonido)
            level.playSound(null, pos, GlitchRegistro.CRUJIDO.get(), SoundSource.BLOCKS, 0.35F, 0.7F + level.random.nextFloat() * 0.8F);
        level.setBlock(pos, nuevo, Block.UPDATE_ALL);
    }

    @Override
    public void animateTick(BlockState st, Level level, BlockPos pos, RandomSource r) {
        if (r.nextInt(3) == 0)
            level.addParticle(GlitchRegistro.PARTICULA.get(), pos.getX() + r.nextDouble(), pos.getY() + r.nextDouble() * 1.1, pos.getZ() + r.nextDouble(), 0, 0, 0);
    }
}
