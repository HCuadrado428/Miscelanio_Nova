package com.miscelanea.carcel;

import com.miscelanea.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Reja con cerradura de símbolo. Se abre con la llave del mismo símbolo (se gasta) y abre toda la reja (todas las conectadas).
 * Cerrada bloquea también el carro del chivato. Abierta emite señal de redstone.
 * Creativo: Shift + clic derecho con la mano vacía cambia el símbolo; clic derecho con la mano vacía la cierra.
 */
public class RejaBlock extends HorizontalDirectionalBlock {
    public static final BooleanProperty ABIERTA = BlockStateProperties.OPEN;

    private static final VoxelShape NS = Block.box(0, 0, 6.5, 16, 16, 9.5);
    private static final VoxelShape EW = Block.box(6.5, 0, 0, 9.5, 16, 16);
    private static final VoxelShape NS_ABIERTA = Block.box(0, 14, 6.5, 16, 16, 9.5);
    private static final VoxelShape EW_ABIERTA = Block.box(6.5, 14, 0, 9.5, 16, 16);

    public RejaBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ABIERTA, false)
                .setValue(Simbolo.PROPIEDAD, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, ABIERTA, Simbolo.PROPIEDAD);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    private static boolean ejeX(BlockState s) {
        return s.getValue(FACING).getAxis() == Direction.Axis.X;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (s.getValue(ABIERTA)) return ejeX(s) ? EW_ABIERTA : NS_ABIERTA;
        return ejeX(s) ? EW : NS;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return s.getValue(ABIERTA) ? Shapes.empty() : getShape(s, level, pos, ctx);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack mano = player.getItemInHand(hand);
        if (player.isCreative() && mano.isEmpty()) {
            if (!level.isClientSide) {
                if (player.isShiftKeyDown()) {
                    int nuevo = (s.getValue(Simbolo.PROPIEDAD) + 1) % Simbolo.TOTAL;
                    columna(level, pos, st -> st.setValue(Simbolo.PROPIEDAD, nuevo));
                } else {
                    columna(level, pos, st -> st.setValue(ABIERTA, !s.getValue(ABIERTA)));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (s.getValue(ABIERTA)) return InteractionResult.PASS;
        if (!mano.is(ModItems.LLAVE.get())) {
            // Las llaves no ocupan hueco: se usa la que lleves que coincida con el símbolo
            for (ItemStack it : player.getInventory().items) {
                if (it.is(ModItems.LLAVE.get()) && LlaveItem.simbolo(it) == s.getValue(Simbolo.PROPIEDAD)) {
                    mano = it;
                    break;
                }
            }
        }
        if (mano.is(ModItems.LLAVE.get())) {
            if (!level.isClientSide) {
                if (LlaveItem.simbolo(mano) == s.getValue(Simbolo.PROPIEDAD)) {
                    columna(level, pos, st -> st.setValue(ABIERTA, true));
                    if (!player.isCreative()) mano.shrink(1);
                    level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.6F);
                    level.playSound(null, pos, SoundEvents.CHAIN_BREAK, SoundSource.BLOCKS, 1.0F, 0.8F);
                    if (level instanceof ServerLevel sl) {
                        Ruido.alertar(sl, net.minecraft.world.phys.Vec3.atCenterOf(pos), 12, player);
                    }
                } else {
                    level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.7F, 1.6F);
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) level.playSound(null, pos, SoundEvents.CHAIN_HIT, SoundSource.BLOCKS, 0.8F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Aplica un cambio a todas las rejas conectadas (la puerta entera, columnas y filas). */
    private void columna(Level level, BlockPos pos, java.util.function.UnaryOperator<BlockState> cambio) {
        java.util.ArrayDeque<BlockPos> cola = new java.util.ArrayDeque<>();
        java.util.Set<BlockPos> vistos = new java.util.HashSet<>();
        cola.add(pos.immutable());
        vistos.add(pos.immutable());
        while (!cola.isEmpty() && vistos.size() < 96) {
            BlockPos p = cola.poll();
            level.setBlock(p, cambio.apply(level.getBlockState(p)), Block.UPDATE_ALL);
            for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                BlockPos q = p.relative(d);
                if (!vistos.contains(q) && level.getBlockState(q).is(this)) {
                    vistos.add(q.immutable());
                    cola.add(q.immutable());
                }
            }
        }
    }

    @Override
    public boolean isSignalSource(BlockState s) {
        return true;
    }

    @Override
    public int getSignal(BlockState s, BlockGetter level, BlockPos pos, Direction dir) {
        return s.getValue(ABIERTA) ? 15 : 0;
    }
}
