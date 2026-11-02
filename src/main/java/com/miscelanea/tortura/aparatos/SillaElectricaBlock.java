package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Silla eléctrica: la silla (parte principal) y, a su lado (a la derecha de quien la pone, mirándola de frente), el
 * cuadro de mando con el dial del voltaje y la palanca (anexo).
 */
public class SillaElectricaBlock extends AparatoBlock {
    private static final VoxelShape SILLA = Block.box(1, 0, 1, 15, 16, 15);
    private static final VoxelShape CUADRO = Block.box(2, 0, 3, 14, 16, 13);

    public SillaElectricaBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente.getCounterClockWise();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ANEXO) ? CUADRO : SILLA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new SillaElectricaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.SILLA_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.SILLA_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
