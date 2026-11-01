package com.miscelanea.tanques;

import com.miscelanea.bichos.BichoItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** Acuario (peces) o terrario (bichos). Los tanques iguales de al lado se unen en uno grande. */
public class TanqueBlock extends BaseEntityBlock {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, SOUTH = BlockStateProperties.SOUTH,
            EAST = BlockStateProperties.EAST, WEST = BlockStateProperties.WEST;
    public final boolean acuario;

    public TanqueBlock(Properties p, boolean acuario) {
        super(p);
        this.acuario = acuario;
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(SOUTH, false).setValue(EAST, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NORTH, SOUTH, EAST, WEST);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    private BlockState unir(BlockState s, BlockGetter l, BlockPos p) {
        return s.setValue(NORTH, l.getBlockState(p.north()).is(this)).setValue(SOUTH, l.getBlockState(p.south()).is(this))
                .setValue(EAST, l.getBlockState(p.east()).is(this)).setValue(WEST, l.getBlockState(p.west()).is(this));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return unir(defaultBlockState(), c.getLevel(), c.getClickedPos());
    }

    @Override
    public BlockState updateShape(BlockState s, Direction d, BlockState o, LevelAccessor l, BlockPos p, BlockPos po) {
        return d.getAxis().isHorizontal() ? unir(s, l, p) : s;
    }

    @Override
    public boolean skipRendering(BlockState s, BlockState otro, Direction d) {
        return otro.is(this) || super.skipRendering(s, otro, d);
    }

    @Override
    public float getShadeBrightness(BlockState s, BlockGetter l, BlockPos p) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new TanqueBlockEntity(p, s);
    }

    @Override
    public InteractionResult use(BlockState s, Level level, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TanqueBlockEntity be)) return InteractionResult.PASS;
        ItemStack mano = p.getItemInHand(hand);
        if (mano.getItem() instanceof BichoItem bi) {
            if (bi.especie.esPez() != acuario) {
                if (!level.isClientSide) p.displayClientMessage(Component.translatable(acuario ? "msg.miscelanea.tanque.solo_peces" : "msg.miscelanea.tanque.solo_bichos").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (be.animales.size() >= TanqueBlockEntity.MAX) {
                if (!level.isClientSide) p.displayClientMessage(Component.translatable("msg.miscelanea.tanque.lleno").withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                be.animales.add(mano.copyWithCount(1));
                be.cambiado();
                if (!p.getAbilities().instabuild) mano.shrink(1);
                level.playSound(null, pos, acuario ? SoundEvents.BUCKET_EMPTY_FISH : SoundEvents.BEEHIVE_ENTER, SoundSource.BLOCKS, 0.8F, 1.2F);
                ((ServerLevel) level).sendParticles(acuario ? ParticleTypes.BUBBLE_POP : ParticleTypes.HAPPY_VILLAGER,
                        pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 8, 0.25, 0.2, 0.25, 0.02);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (mano.isEmpty() && p.isShiftKeyDown() && !be.animales.isEmpty()) {
            if (!level.isClientSide) {
                p.setItemInHand(hand, be.animales.remove(be.animales.size() - 1));
                be.cambiado();
                level.playSound(null, pos, acuario ? SoundEvents.BUCKET_FILL_FISH : SoundEvents.BEEHIVE_EXIT, SoundSource.BLOCKS, 0.8F, 1.0F);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean mov) {
        if (!s.is(n.getBlock()) && l.getBlockEntity(p) instanceof TanqueBlockEntity be)
            for (ItemStack st : be.animales) Containers.dropItemStack(l, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, st);
        super.onRemove(s, l, p, n, mov);
    }
}
