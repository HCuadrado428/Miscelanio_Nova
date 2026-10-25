package com.miscelanea.programa;

import com.miscelanea.carcel.Ruido;
import com.miscelanea.registry.ModEffects;
import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Caja de herramientas o botiquín con candado: se abre con una ganzúa (minijuego de la rueda).
 * Algunas cajas de herramientas llevan una trampa de gas (te baja la cordura).
 */
public class CajaCerradaBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ABIERTA = BlockStateProperties.OPEN;
    private static final VoxelShape FORMA = Block.box(2, 0, 4, 14, 8, 12);
    private static final VoxelShape FORMA_X = Block.box(4, 0, 2, 12, 8, 14);
    private final boolean botiquin;

    public CajaCerradaBlock(Properties p, boolean botiquin) {
        super(p);
        this.botiquin = botiquin;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ABIERTA, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ABIERTA);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(FACING).getAxis() == Direction.Axis.X ? FORMA_X : FORMA;
    }

    public boolean esBotiquin() {
        return botiquin;
    }

    public static boolean tieneGanzua(Player p) {
        for (ItemStack s : p.getInventory().items) if (s.is(ModItems.GANZUA.get())) return true;
        return false;
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (s.getValue(ABIERTA)) return InteractionResult.PASS;
        if (!tieneGanzua(player)) {
            if (!level.isClientSide) level.playSound(null, pos, SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 0.8F, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> com.miscelanea.client.programa.GanzuaScreen.abrir(pos));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.CONSUME;
    }

    public static void resultadoGanzua(ServerPlayer p, BlockPos pos, int res) {
        Level level = p.level();
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CajaCerradaBlock caja) || s.getValue(ABIERTA)) return;
        if (p.distanceToSqr(Vec3.atCenterOf(pos)) > 36 || !tieneGanzua(p)) return;
        ServerLevel sl = (ServerLevel) level;
        if (res == GanzuaPacket.EMPEZAR) {
            Acciones.poner(p, Acciones.Accion.GANZUA, 220);
            return;
        }
        Acciones.parar(p);
        if (res == GanzuaPacket.FALLO) {
            level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 1.4F);
            Ruido.alertar(sl, Vec3.atCenterOf(pos), 10, p);
            return;
        }
        for (ItemStack it : p.getInventory().items) {
            if (it.is(ModItems.GANZUA.get())) {
                it.shrink(1);
                break;
            }
        }
        level.setBlock(pos, s.setValue(ABIERTA, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.2F);
        if (!caja.botiquin && Prueba.esTrampa(pos)) {
            sl.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 120, 1.5, 0.8, 1.5, 0.02);
            sl.sendParticles(ParticleTypes.SNEEZE, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 50, 1.2, 0.6, 1.2, 0.02);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 1.4F, 0.5F);
            for (ServerPlayer o : sl.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(pos).inflate(3))) {
                o.addEffect(new MobEffectInstance(ModEffects.PSICOSIS.get(), 100, 0, false, false, true));
                Jugadores.drenar(o, 130);
            }
        }
        ItemStack b = caja.botiquin ? Botin.botiquin(level.random) : Botin.caja(level.random);
        soltar(level, pos, b);
    }

    static void soltar(Level level, BlockPos pos, ItemStack b) {
        if (b.isEmpty()) return;
        ItemEntity ie = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5, b);
        ie.setDeltaMovement(0, 0.15, 0);
        ie.setPickUpDelay(5);
        level.addFreshEntity(ie);
    }
}
