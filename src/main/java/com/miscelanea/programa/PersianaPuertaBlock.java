package com.miscelanea.programa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/**
 * Persiana del parking: al levantarla se queda arriba muy poco (3 s) y vuelve a caer, salvo que alguien la siga sujetando
 * (manteniendo clic derecho).
 */
public class PersianaPuertaBlock extends Block {
    public static final BooleanProperty ABIERTA = BlockStateProperties.OPEN;
    private static final VoxelShape CERRADA = Block.box(0, 0, 6, 16, 16, 10);
    private static final VoxelShape ARRIBA = Block.box(0, 13, 5, 16, 16, 11);
    private static long ultimoUso;

    public PersianaPuertaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ABIERTA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ABIERTA);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ABIERTA) ? ARRIBA : CERRADA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ABIERTA) ? Shapes.empty() : CERRADA;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ultimoUso = level.getGameTime();
        if (!s.getValue(ABIERTA)) {
            todas(level, pos, true);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 1.5F, 0.5F);
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
            level.scheduleTick(pos, this, 60);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource r) {
        if (!s.getValue(ABIERTA)) return;
        if (level.getGameTime() - ultimoUso < 55) {
            level.scheduleTick(pos, this, 10);
            return;
        }
        todas(level, pos, false);
        level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.5F, 0.5F);
        level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.5F, 0.6F);
    }

    private void todas(Level level, BlockPos pos, boolean abierta) {
        ArrayDeque<BlockPos> cola = new ArrayDeque<>();
        Set<BlockPos> vistos = new HashSet<>();
        cola.add(pos.immutable());
        vistos.add(pos.immutable());
        while (!cola.isEmpty() && vistos.size() < 64) {
            BlockPos p = cola.poll();
            BlockState s = level.getBlockState(p);
            level.setBlock(p, s.setValue(ABIERTA, abierta), Block.UPDATE_ALL);
            for (Direction d : Direction.values()) {
                BlockPos q = p.relative(d);
                if (!vistos.contains(q) && level.getBlockState(q).is(this)) {
                    vistos.add(q.immutable());
                    cola.add(q.immutable());
                }
            }
        }
    }
}
