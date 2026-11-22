package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/** El frigorífico de muestras: un almacén con menú de cofre (27 huecos) para las muestras, los sueros y los tubos. */
public class FrigorificoBlock extends BaseEntityBlock {
    public FrigorificoBlock(Properties p) {
        super(p);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new FrigorificoBlockEntity(p, s);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean movido) {
        if (!s.is(nuevo.getBlock()) && l.getBlockEntity(p) instanceof FrigorificoBlockEntity be) {
            Containers.dropContents(l, p, be);
            l.updateNeighbourForOutputSignal(p, this);
        }
        super.onRemove(s, l, p, nuevo, movido);
    }

    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player pl, InteractionHand h, BlockHitResult r) {
        if (l.isClientSide) return InteractionResult.SUCCESS;
        if (l.getBlockEntity(p) instanceof FrigorificoBlockEntity be) pl.openMenu(be);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState s) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState s, Level l, BlockPos p) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(l.getBlockEntity(p));
    }
}
