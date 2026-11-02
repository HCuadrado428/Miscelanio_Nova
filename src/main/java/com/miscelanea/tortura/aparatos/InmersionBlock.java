package com.miscelanea.tortura.aparatos;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * Silla de inmersión: el poste con la viga basculante (parte principal, en la orilla) y, hacia quien la pone, el apoyo
 * del contrapeso (anexo). La viga sale hacia atrás, sobre el agua: la silla se hunde en el bloque de detrás del poste,
 * que tiene que ser agua (mejor de dos de hondo). Si no la hay, avisa al ponerla.
 */
public class InmersionBlock extends AparatoBlock {
    private static final VoxelShape POSTE = Shapes.or(Block.box(5, 0, 5, 11, 21, 11), Block.box(1, 0, 6.5, 15, 2, 9.5),
            Block.box(6.5, 0, 1, 9.5, 2, 15));
    private static final Map<Direction, VoxelShape> APOYO = new EnumMap<>(Direction.class);

    static {
        // el apoyo del contrapeso está 3 px hacia el poste (hacia atrás desde el anexo)
        for (Direction f : Direction.Plane.HORIZONTAL) {
            double cx = 8 - f.getStepX() * 3, cz = 8 - f.getStepZ() * 3;
            APOYO.put(f, Block.box(cx - 2.5, 0, cz - 2.5, cx + 2.5, 7.5, cz + 2.5));
        }
    }

    public InmersionBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ANEXO) ? APOYO.get(s.getValue(FACING)) : POSTE;
    }

    @Override
    public void setPlacedBy(Level l, BlockPos p, BlockState s, @Nullable LivingEntity e, ItemStack st) {
        super.setPlacedBy(l, p, s, e, st);
        if (l.isClientSide || !(e instanceof Player pl) || s.getValue(ANEXO)) return;
        if (!(l.getBlockEntity(p) instanceof InmersionBlockEntity be)) return;
        if (!be.hayAgua())
            pl.displayClientMessage(Component.translatable("msg.miscelanea.inmersion.sin_agua").withStyle(ChatFormatting.RED), false);
        else if (!be.hondo())
            pl.displayClientMessage(Component.translatable("msg.miscelanea.inmersion.poco_hondo").withStyle(ChatFormatting.GOLD), false);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new InmersionBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.INMERSION_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.INMERSION_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
