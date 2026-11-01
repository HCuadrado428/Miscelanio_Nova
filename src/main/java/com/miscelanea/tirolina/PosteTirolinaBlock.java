package com.miscelanea.tirolina;

import com.miscelanea.registry.ModEntities;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/** Poste de tirolina: únelo a otro con un carrete de cable; clic derecho con la mano vacía para lanzarte. */
public class PosteTirolinaBlock extends BaseEntityBlock {
    private static final VoxelShape FORMA = Shapes.or(Block.box(3, 0, 3, 13, 1, 13), Block.box(4.5, 1, 4.5, 11.5, 32, 11.5));

    public PosteTirolinaBlock(Properties p) {
        super(p);
    }

    /** El poste mide tres bloques: hace falta sitio libre encima. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext c) {
        BlockPos p = c.getClickedPos();
        Level l = c.getLevel();
        for (int k = 1; k <= 2; k++) if (!l.getBlockState(p.above(k)).canBeReplaced()) return null;
        return defaultBlockState();
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new PosteTirolinaBlockEntity(p, s);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos pos, Player p, InteractionHand hand, BlockHitResult hit) {
        ItemStack mano = p.getItemInHand(hand);
        if (mano.getItem() instanceof CableTirolinaItem || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(l.getBlockEntity(pos) instanceof PosteTirolinaBlockEntity be)) return InteractionResult.PASS;
        BlockPos otro = be.otro();
        if (otro == null) {
            if (!l.isClientSide) p.displayClientMessage(Component.translatable("msg.miscelanea.tirolina.sin_cable").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.sidedSuccess(l.isClientSide);
        }
        if (p.isPassenger()) return InteractionResult.PASS;
        if (!l.isClientSide) {
            BlockPos a = be.destino != null ? pos : otro, b = be.destino != null ? otro : pos;
            boolean desdeA = be.destino != null;
            Polea po = new Polea(ModEntities.POLEA.get(), l);
            po.preparar(a, b, desdeA ? 0.0F : 1.0F, desdeA ? 0.12F : -0.12F);
            l.addFreshEntity(po);
            p.startRiding(po, true);
            l.playSound(null, pos, ModSounds.TIROLINA_ENGANCHAR.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(l.isClientSide);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState n, boolean mov) {
        if (!s.is(n.getBlock()) && l.getBlockEntity(p) instanceof PosteTirolinaBlockEntity be && be.otro() != null) {
            if (l.getBlockEntity(be.otro()) instanceof PosteTirolinaBlockEntity o) {
                if (p.equals(o.destino)) o.destino = null;
                if (p.equals(o.origen)) o.origen = null;
                o.cambiado();
            }
            Containers.dropItemStack(l, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, new ItemStack(ModItems.CABLE_TIROLINA.get()));
        }
        super.onRemove(s, l, p, n, mov);
    }
}
