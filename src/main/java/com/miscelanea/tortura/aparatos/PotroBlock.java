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
 * Potro: mesa larga de dos bloques. La parte principal (la de la manivela, los pies de la víctima) queda donde se pone;
 * el anexo (la cabecera) se alarga hacia atrás, en el sentido contrario al frente.
 */
public class PotroBlock extends AparatoBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 12, 16);

    public PotroBlock(Properties p) {
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
        return s.getValue(ANEXO) ? null : new PotroBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.POTRO_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.POTRO_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
