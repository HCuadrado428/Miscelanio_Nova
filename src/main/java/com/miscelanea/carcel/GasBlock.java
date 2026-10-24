package com.miscelanea.carcel;

import com.miscelanea.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rejilla de gas en el suelo. Suelta gas de psicosis (la nube es el humo de fogata) en un radio de 3 bloques.
 * Con señal de redstone se apaga; sin redstone está siempre soltando gas.
 */
public class GasBlock extends Block {
    public static final BooleanProperty APAGADA = BlockStateProperties.POWERED;
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 2, 16);

    public GasBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(APAGADA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(APAGADA);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public void onPlace(BlockState s, Level level, BlockPos pos, BlockState viejo, boolean moviendo) {
        if (!viejo.is(this)) level.scheduleTick(pos, this, 10);
    }

    @Override
    public void neighborChanged(BlockState s, Level level, BlockPos pos, Block b, BlockPos from, boolean moving) {
        boolean p = level.hasNeighborSignal(pos);
        if (p != s.getValue(APAGADA)) level.setBlock(pos, s.setValue(APAGADA, p), Block.UPDATE_CLIENTS);
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        level.scheduleTick(pos, this, 10);
        if (s.getValue(APAGADA)) return;
        AABB nube = new AABB(pos).inflate(3, 2, 3).move(0, 1, 0);
        for (Player p : level.getEntitiesOfClass(Player.class, nube, pl -> !pl.isSpectator() && !pl.isCreative())) {
            // Tapa el antídoto: mientras dure la protección no te afecta
            if (p.hasEffect(ModEffects.INMUNIDAD_GAS.get())) continue;
            MobEffectInstance act = p.getEffect(ModEffects.PSICOSIS.get());
            int dur = act == null ? 0 : act.getDuration();
            int nivel = Math.min(2, dur / 400);
            p.addEffect(new MobEffectInstance(ModEffects.PSICOSIS.get(), Math.min(dur + 30, 1600), nivel, false, false, true));
        }
        if (random.nextInt(4) == 0) level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.25F, 0.5F);
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource random) {
        if (s.getValue(APAGADA)) return;
        for (int i = 0; i < 2; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, true,
                    pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.15, pos.getZ() + 0.2 + random.nextDouble() * 0.6,
                    (random.nextDouble() - 0.5) * 0.03, 0.02 + random.nextDouble() * 0.02, (random.nextDouble() - 0.5) * 0.03);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ENTITY_EFFECT, pos.getX() + random.nextDouble(), pos.getY() + 0.5 + random.nextDouble(),
                    pos.getZ() + random.nextDouble(), 0.45, 0.75, 0.2);
        }
    }
}
