package com.miscelanea.bichos;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Vitrina del museo: dona un bicho o un pez con clic derecho y queda expuesto con su placa.
 * Agachado y con la mano vacía lo recuperas.
 */
public class VitrinaBlock extends BaseEntityBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 16, 16);

    public VitrinaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(HorizontalDirectionalBlock.FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState st) {
        return new VitrinaBlockEntity(pos, st);
    }

    @Override
    public InteractionResult use(BlockState st, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof VitrinaBlockEntity v)) return InteractionResult.PASS;
        ItemStack mano = player.getItemInHand(hand);
        if (v.ejemplar().isEmpty() && mano.getItem() instanceof BichoItem bi) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                ItemStack uno = mano.copyWithCount(1);
                v.poner(uno, sp.getGameProfile().getName());
                if (!sp.getAbilities().instabuild) mano.shrink(1);
                boolean nuevo = Coleccion.donar(sp, bi.especie);
                sp.displayClientMessage(Component.translatable(nuevo ? "msg.miscelanea.donado" : "msg.miscelanea.expuesto",
                        Component.translatable("item.miscelanea." + bi.especie.item())).withStyle(nuevo ? ChatFormatting.GOLD : ChatFormatting.GRAY), false);
                level.playSound(null, pos, nuevo ? SoundEvents.PLAYER_LEVELUP : SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
                ((ServerLevel) level).sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!v.ejemplar().isEmpty() && mano.isEmpty() && player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, v.quitar());
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState st, Level level, BlockPos pos, BlockState nuevo, boolean mov) {
        if (!st.is(nuevo.getBlock()) && level.getBlockEntity(pos) instanceof VitrinaBlockEntity v && !v.ejemplar().isEmpty())
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, v.ejemplar());
        super.onRemove(st, level, pos, nuevo, mov);
    }

    public static net.minecraft.world.level.block.entity.BlockEntityType<VitrinaBlockEntity> tipo() {
        return ModBlocks.VITRINA_BE.get();
    }
}
