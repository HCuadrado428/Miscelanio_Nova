package com.miscelanea.feria;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Peluche de una persona. A diferencia del osito de la caseta de tiro, este gira en 16 direcciones, chilla al apretarlo
 * y se cansa si abusas. Agachado se abraza.
 */
public class PeluchePersonaBlock extends Block {

    /** Hacia dónde mira, en 16 pasos como las cabezas. */
    public static final IntegerProperty ROTACION = BlockStateProperties.ROTATION_16;
    /** Cuánto lo has apretado: a más cansancio, más grave suena. Se recupera solo. */
    public static final IntegerProperty CANSANCIO = IntegerProperty.create("cansancio", 0, 3);

    private static final VoxelShape FORMA = Block.box(3.0D, 0.0D, 4.0D, 13.0D, 14.0D, 12.0D);
    /** Cada cuántos ticks descansa un punto de cansancio. */
    private static final int DESCANSO = 200;

    /** Cuál de los peluches es: solo sirve para que cada uno chille en su tono. */
    private final int indice;

    public PeluchePersonaBlock(Properties p, int indice) {
        super(p);
        this.indice = indice;
        registerDefaultState(stateDefinition.any()
                .setValue(ROTACION, 0)
                .setValue(CANSANCIO, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ROTACION, CANSANCIO);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        int giro = Mth.floor((180.0F + c.getRotation()) * 16.0F / 360.0F + 0.5D) & 15;
        return defaultBlockState().setValue(ROTACION, giro);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (j.isShiftKeyDown()) return abrazar(s, l, p, j);
        return apretar(s, l, p, j);
    }

    /** Clic derecho: chilla. Cuanto más cansado, más grave. */
    private InteractionResult apretar(BlockState s, Level l, BlockPos p, Player j) {
        int cansancio = s.getValue(CANSANCIO);
        float tono = 1.35F - cansancio * 0.18F + (indice % 4 - 1.5F) * 0.06F;
        l.playSound(null, p, ModSounds.FE_CUAC.get(), SoundSource.BLOCKS, 0.7F, tono);
        if (!l.isClientSide) {
            if (cansancio < 3) {
                l.setBlock(p, s.setValue(CANSANCIO, cansancio + 1), 3);
                l.scheduleTick(p, this, DESCANSO);
            }
            if (l instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.NOTE, p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D,
                        1, 0.2D, 0.0D, 0.2D, 0.0D);
            }
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    /** Agachado y clic derecho: lo abrazas. Sale un corazón y te quedas mejor un rato. */
    private InteractionResult abrazar(BlockState s, Level l, BlockPos p, Player j) {
        l.playSound(null, p, ModSounds.FE_POP.get(), SoundSource.BLOCKS, 0.4F, 1.6F);
        if (!l.isClientSide) {
            if (l instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.HEART, p.getX() + 0.5D, p.getY() + 1.1D, p.getZ() + 0.5D,
                        3, 0.25D, 0.1D, 0.25D, 0.0D);
            }
            j.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0, true, false, true));
            // un abrazo también descansa al peluche
            if (s.getValue(CANSANCIO) > 0) l.setBlock(p, s.setValue(CANSANCIO, 0), 3);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        int cansancio = s.getValue(CANSANCIO);
        if (cansancio > 0) {
            l.setBlock(p, s.setValue(CANSANCIO, cansancio - 1), 3);
            if (cansancio - 1 > 0) l.scheduleTick(p, this, DESCANSO);
        }
    }

    @Override
    public BlockState rotate(BlockState s, Rotation r) {
        return s.setValue(ROTACION, r.rotate(s.getValue(ROTACION), 16));
    }

    @Override
    public BlockState mirror(BlockState s, Mirror m) {
        return s.setValue(ROTACION, m.mirror(s.getValue(ROTACION), 16));
    }
}
