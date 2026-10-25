package com.miscelanea.programa;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Archivador / cajonera: mantén clic derecho para registrarlo. A veces hay algo, a veces nada. */
public class ArchivadorBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty REGISTRADO = BooleanProperty.create("registrado");
    private static final Map<BlockPos, long[]> PASOS = new HashMap<>();   // {pasos, último tick}
    private static final Map<BlockPos, UUID> QUIEN = new HashMap<>();

    public ArchivadorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(REGISTRADO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, REGISTRADO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (s.getValue(REGISTRADO)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        long t = level.getGameTime();
        long[] st = PASOS.computeIfAbsent(pos.immutable(), k -> new long[]{0, 0});
        if (!player.getUUID().equals(QUIEN.get(pos)) || t - st[1] > 8) st[0] = 0;
        QUIEN.put(pos.immutable(), player.getUUID());
        st[1] = t;
        st[0]++;
        if (player instanceof ServerPlayer sp) Acciones.poner(sp, Acciones.Accion.REGISTRAR, 8);
        if (st[0] % 3 == 1) level.playSound(null, pos, SoundEvents.CHISELED_BOOKSHELF_PICKUP, SoundSource.BLOCKS, 0.7F, 0.7F + level.random.nextFloat() * 0.3F);
        if (st[0] < 6) return InteractionResult.CONSUME;
        PASOS.remove(pos);
        QUIEN.remove(pos);
        level.setBlock(pos, s.setValue(REGISTRADO, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.BARREL_OPEN, SoundSource.BLOCKS, 0.8F, 1.1F);
        CajaCerradaBlock.soltar(level, pos.relative(s.getValue(FACING)), Botin.cajon(level.random));
        return InteractionResult.CONSUME;
    }
}
