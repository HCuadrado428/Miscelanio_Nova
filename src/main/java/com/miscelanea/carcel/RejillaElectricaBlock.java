package com.miscelanea.carcel;

import com.miscelanea.registry.ModDamageTypes;
import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rejilla eléctrica en el suelo. Se enciende y se apaga cada 3 s. Encendida da descargas y bloquea
 * los ítems de uso de quien la pisa. Se puede saltar por encima o apagar 30 s tirándole una botella,
 * un ladrillo o cualquier proyectil.
 */
public class RejillaElectricaBlock extends Block {
    public static final BooleanProperty ACTIVA = BooleanProperty.create("activa");
    public static final BooleanProperty AVERIADA = BooleanProperty.create("averiada");
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 1, 16);
    private static final int CICLO = 60;
    private static final int APAGADA_POR_GOLPE = 600;

    public RejillaElectricaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVA, false).setValue(AVERIADA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ACTIVA, AVERIADA);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public void onPlace(BlockState s, Level level, BlockPos pos, BlockState viejo, boolean moviendo) {
        if (!viejo.is(this)) level.scheduleTick(pos, this, CICLO);
    }

    @Override
    public void tick(BlockState s, ServerLevel level, BlockPos pos, RandomSource random) {
        if (s.getValue(AVERIADA)) {
            level.setBlock(pos, s.setValue(AVERIADA, false).setValue(ACTIVA, false), Block.UPDATE_ALL);
            level.scheduleTick(pos, this, CICLO);
            return;
        }
        boolean activa = !s.getValue(ACTIVA);
        level.setBlock(pos, s.setValue(ACTIVA, activa), Block.UPDATE_ALL);
        // Las rejillas vecinas van sincronizadas porque se colocan a la vez; cada una lleva su ciclo
        level.scheduleTick(pos, this, CICLO);
        if (activa) level.playSound(null, pos, ModSounds.CHISPAZO.get(), SoundSource.BLOCKS, 0.6F, 0.7F);
    }

    @Override
    public void entityInside(BlockState s, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !s.getValue(ACTIVA) || !(entity instanceof LivingEntity le)) return;
        if (le.tickCount % 10 != 0) return;
        le.hurt(ModDamageTypes.descarga(level, le), 2.0F);
        if (le instanceof Player p) {
            for (int i = 0; i < 9; i++) bloquear(p, p.getInventory().getItem(i));
            bloquear(p, p.getOffhandItem());
        }
        ((ServerLevel) level).sendParticles(ParticleTypes.ELECTRIC_SPARK, le.getX(), le.getY() + 0.3, le.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        level.playSound(null, pos, ModSounds.DESCARGA.get(), SoundSource.BLOCKS, 0.7F, 1.3F);
    }

    private static void bloquear(Player p, ItemStack s) {
        if (!s.isEmpty()) p.getCooldowns().addCooldown(s.getItem(), 100);
    }

    @Override
    public void animateTick(BlockState s, Level level, BlockPos pos, RandomSource random) {
        if (s.getValue(ACTIVA) && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 0.1,
                    pos.getZ() + random.nextDouble(), 0, 0.05, 0);
        }
    }

    @Override
    public void onProjectileHit(Level level, BlockState s, BlockHitResult hit, Projectile proj) {
        if (level.isClientSide || s.getValue(AVERIADA)) return;
        BlockPos pos = hit.getBlockPos();
        apagar(level, pos);
    }

    /** Apaga esta rejilla y las conectadas (hasta 16 bloques). */
    public static void apagar(Level level, BlockPos origen) {
        java.util.ArrayDeque<BlockPos> cola = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> vistos = new java.util.HashSet<>();
        cola.add(origen);
        while (!cola.isEmpty() && vistos.size() < 64) {
            BlockPos p = cola.poll();
            if (!vistos.add(p)) continue;
            BlockState st = level.getBlockState(p);
            if (!(st.getBlock() instanceof RejillaElectricaBlock r)) continue;
            level.setBlock(p, st.setValue(AVERIADA, true).setValue(ACTIVA, false), Block.UPDATE_ALL);
            if (level instanceof ServerLevel sl) {
                sl.scheduleTick(p, r, APAGADA_POR_GOLPE);
                sl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.2, p.getZ() + 0.5, 3, 0.3, 0.1, 0.3, 0.01);
            }
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.Plane.HORIZONTAL) cola.add(p.relative(d));
        }
        level.playSound(null, origen, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 0.8F);
    }
}
