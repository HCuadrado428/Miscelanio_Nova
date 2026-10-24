package com.miscelanea.carcel;

import com.miscelanea.registry.ModDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Cadáver en una bolsa que se puede registrar (mantener clic derecho ~2 s).
 * Puede llevar un objeto (por ejemplo una llave) y puede ser una trampa que da una descarga.
 * Creativo: clic derecho con un objeto lo mete dentro; Shift + clic derecho con la mano vacía
 * activa/desactiva la trampa; clic derecho con la mano vacía lo deja sin registrar otra vez.
 */
public class CadaverBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty REGISTRADO = BooleanProperty.create("registrado");
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 5, 16);
    /** Clics (repetidos al mantener) necesarios para registrarlo. */
    private static final int PASOS = 10;

    public CadaverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(REGISTRADO, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, REGISTRADO);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return FORMA;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CadaverBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof CadaverBlockEntity be)) return InteractionResult.PASS;
        ItemStack mano = player.getItemInHand(hand);

        // ---- Configuración en creativo
        if (player.isCreative()) {
            if (!mano.isEmpty()) {
                if (!level.isClientSide) {
                    be.setContenido(mano.copy());
                    level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (player.isShiftKeyDown()) {
                if (!level.isClientSide) {
                    be.setTrampa(!be.esTrampa());
                    player.displayClientMessage(Component.translatable(be.esTrampa()
                            ? "msg.miscelanea.cadaver_trampa" : "msg.miscelanea.cadaver_normal").withStyle(ChatFormatting.GRAY), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (s.getValue(REGISTRADO)) {
                if (!level.isClientSide) level.setBlock(pos, s.setValue(REGISTRADO, false), Block.UPDATE_ALL);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        if (s.getValue(REGISTRADO)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        // ---- Registrar (al mantener el clic, se llama varias veces)
        int paso = be.avanzar(player, level.getGameTime());
        ServerLevel sl = (ServerLevel) level;
        if (paso % 3 == 1) {
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.BLOCKS, 0.6F, 0.7F + level.random.nextFloat() * 0.2F);
        }
        // Barra de progreso (solo símbolos)
        int lleno = Math.min(10, paso);
        player.displayClientMessage(Component.literal("▮".repeat(lleno)).withStyle(ChatFormatting.WHITE)
                .append(Component.literal("▯".repeat(10 - lleno)).withStyle(ChatFormatting.DARK_GRAY)), true);
        if (paso < PASOS) return InteractionResult.CONSUME;

        level.setBlock(pos, s.setValue(REGISTRADO, true), Block.UPDATE_ALL);
        player.displayClientMessage(Component.empty(), true);
        if (be.esTrampa()) {
            // Llave falsa: descarga
            player.hurt(ModDamageTypes.descarga(level, player), 5.0F);
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 25, 0.4, 0.2, 0.4, 0.2);
            level.playSound(null, pos, com.miscelanea.registry.ModSounds.DESCARGA.get(), SoundSource.BLOCKS, 1.0F, 1.2F);
        }
        ItemStack contenido = be.sacarContenido();
        if (!contenido.isEmpty()) {
            if (!player.getInventory().add(contenido)) player.drop(contenido, false);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
        return InteractionResult.CONSUME;
    }
}
