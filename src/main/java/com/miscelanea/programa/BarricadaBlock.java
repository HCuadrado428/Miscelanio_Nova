package com.miscelanea.programa;

import com.miscelanea.carcel.Ruido;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Puerta atrancada con tablas: se rompe a golpes (3 embestidas). Cada golpe hace mucho ruido. Los enemigos también la rompen. */
public class BarricadaBlock extends Block {
    public static final IntegerProperty GOLPES = IntegerProperty.create("golpes", 0, 2);
    private static final Map<UUID, Long> ULTIMO = new HashMap<>();

    public BarricadaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(GOLPES, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(GOLPES);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        long t = level.getGameTime();
        Long u = ULTIMO.get(player.getUUID());
        if (u != null && t - u < 14) return InteractionResult.CONSUME;
        ULTIMO.put(player.getUUID(), t);
        if (player instanceof ServerPlayer sp) Acciones.poner(sp, Acciones.Accion.EMBESTIR, 10);
        golpear((ServerLevel) level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** Un golpe (de un jugador o de un enemigo). Rompe también las barricadas de encima/debajo al final. */
    public static void golpear(ServerLevel level, BlockPos pos, Entity quien) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof BarricadaBlock)) return;
        level.playSound(null, pos, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.8F + level.random.nextFloat() * 0.2F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SPRUCE_PLANKS.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 15, 0.3, 0.4, 0.3, 0.1);
        Ruido.alertar(level, Vec3.atCenterOf(pos), 18, quien);
        int g = s.getValue(GOLPES);
        if (g < 2) {
            for (BlockPos p : new BlockPos[]{pos, pos.above(), pos.below()}) {
                BlockState o = level.getBlockState(p);
                if (o.getBlock() instanceof BarricadaBlock) level.setBlock(p, o.setValue(GOLPES, g + 1), Block.UPDATE_ALL);
            }
            return;
        }
        level.playSound(null, pos, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 1.2F, 0.9F);
        for (BlockPos p : new BlockPos[]{pos, pos.above(), pos.below()}) {
            if (level.getBlockState(p).getBlock() instanceof BarricadaBlock) level.removeBlock(p, false);
        }
    }
}
