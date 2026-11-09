package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Rizo de la montaña rusa: se pone en un solo bloque, pero la vuelta ocupa 5 bloques hacia delante, 4 de alto y se
 * desplaza 1 a la derecha para salir al lado de donde entró (ver {@link Via}). Necesita aire en ese espacio y que el
 * tren llegue con velocidad (si no, se queda a medias y vuelve hacia atrás).
 */
public class RizoBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final VoxelShape PLANA = Block.box(0, 0, 0, 16, 4, 16);

    public RizoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, Colocacion.dirRizo(c.getLevel(), c.getClickedPos(), c.getHorizontalDirection()));
    }

    /** Clic derecho con la mano vacía: lo gira. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState s, net.minecraft.world.level.Level l, BlockPos p, net.minecraft.world.entity.player.Player pl,
                                                     net.minecraft.world.InteractionHand h, net.minecraft.world.phys.BlockHitResult hit) {
        if (!pl.getItemInHand(h).isEmpty() || h != net.minecraft.world.InteractionHand.MAIN_HAND) return net.minecraft.world.InteractionResult.PASS;
        if (l.isClientSide) return net.minecraft.world.InteractionResult.SUCCESS;
        BlockState n = s.setValue(FACING, s.getValue(FACING).getClockWise());
        l.setBlock(p, n, Block.UPDATE_ALL);
        l.playSound(null, p, net.minecraft.sounds.SoundEvents.CHAIN_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.7F, 1.3F);
        pl.displayClientMessage(net.minecraft.network.chat.Component.translatable("montana.miscelanea.pieza",
                net.minecraft.network.chat.Component.translatable("block.miscelanea.rizo_montana"),
                net.minecraft.network.chat.Component.translatable("montana.miscelanea.dir." + n.getValue(FACING).getSerializedName())), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(net.minecraft.world.item.ItemStack st, @javax.annotation.Nullable BlockGetter l, java.util.List<net.minecraft.network.chat.Component> t, net.minecraft.world.item.TooltipFlag f) {
        t.add(net.minecraft.network.chat.Component.translatable("montana.miscelanea.ayuda.rizo").withStyle(net.minecraft.ChatFormatting.GRAY));
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return PLANA;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new RizoBlockEntity(p, s);
    }

    /** El bloque-entidad solo sirve para dibujar la vuelta (que se sale del bloque). */
    public static class RizoBlockEntity extends BlockEntity {
        public RizoBlockEntity(BlockPos p, BlockState s) {
            super(com.miscelanea.registry.ModBlocks.RIZO_BE.get(), p, s);
        }

        /** Lo que calcula el cliente para dibujar la vuelta (una vez). */
        public Object cache;

        @Override
        @SuppressWarnings("deprecation")
        public void setBlockState(BlockState s) {
            super.setBlockState(s);
            cache = null;
        }

        @Override
        public net.minecraft.world.phys.AABB getRenderBoundingBox() {
            return new net.minecraft.world.phys.AABB(worldPosition).inflate(6);
        }
    }
}
