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
 * El péndulo: mesa de dos bloques (la parte principal, con la palanca, en los pies de la víctima; el anexo, con la
 * manivela, en la cabecera, hacia atrás) bajo un pórtico de cuatro bloques de alto del que cuelga la cuchilla.
 * Solo la mesa tiene forma; el pórtico y la cuchilla son de adorno (los dibuja el renderer).
 */
public class PenduloBlock extends AparatoBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 11, 16);

    public PenduloBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente.getOpposite();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new PenduloBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.PENDULO_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.PENDULO_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
