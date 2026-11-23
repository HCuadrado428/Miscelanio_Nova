package com.miscelanea.laboratorio;

import com.miscelanea.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** El dispensador de sueros: una ficha de recreativa por un suero sin etiquetar al azar. */
public class DispensadorBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape FORMA = Block.box(1, 0, 1, 15, 16, 15);

    public DispensadorBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> b) {
        b.add(FACING);
    }

    /** El frente (pantalla, puerta, boquillas) mira hacia quien lo pone. */
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (l.isClientSide) return InteractionResult.SUCCESS;
        ItemStack mano = pl.getMainHandItem();
        if (!mano.is(ModItems.FICHA.get())) {
            pl.displayClientMessage(Component.translatable("laboratorio.miscelanea.dispensador.ficha").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        if (!pl.getAbilities().instabuild) mano.shrink(1);
        ItemStack suero = Sueros.crear(l.random.nextInt(Sueros.TIPOS), false);
        if (!pl.getInventory().add(suero)) pl.drop(suero, false);
        l.playSound(null, p, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }
}
