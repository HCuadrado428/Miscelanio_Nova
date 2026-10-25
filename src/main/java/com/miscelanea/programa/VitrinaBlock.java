package com.miscelanea.programa;

import com.miscelanea.carcel.Ruido;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Vitrina de emergencia en la pared: rompes el cristal (hace ruido) y coges lo de dentro:
 * una pastilla de reaparición jugando solo, o una jeringuilla para reanimar si sois varios.
 */
public class VitrinaBlock extends ParedBlock {
    public static final BooleanProperty ROTA = BooleanProperty.create("rota");

    public VitrinaBlock(Properties p) {
        super(p, 3, 2, 13, 14, 4);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROTA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ROTA);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (s.getValue(ROTA)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        level.setBlock(pos, s.setValue(ROTA, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 1.3F, 0.8F);
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 30, 0.25, 0.3, 0.25, 0.1);
        Ruido.alertar(sl, Vec3.atCenterOf(pos), 14, player);
        ItemStack it = new ItemStack(Prueba.jugadoresDentro() > 1 ? ModItems.JERINGA_REANIMAR.get() : ModItems.PASTILLA.get());
        CajaCerradaBlock.soltar(level, pos.relative(s.getValue(FACING)), it);
        return InteractionResult.CONSUME;
    }
}
