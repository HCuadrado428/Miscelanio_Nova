package com.miscelanea.tortura.aparatos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Rueda de Catalina: una rueda de carro grande (2,5 bloques) sobre un poste con pies. La parte principal es la de abajo
 * (el pie y el eje) y el anexo, el bloque de encima. Todo se dibuja en {@code RuedaRenderer} (la rueda gira), así que
 * las dos partes son invisibles como bloque.
 */
public class RuedaBlock extends AparatoBlock {
    private static final VoxelShape ABAJO = Shapes.or(Block.box(0, 0, 0, 16, 3, 16), Block.box(3, 3, 3, 13, 16, 13));
    private static final VoxelShape ARRIBA = Block.box(1, 0, 3, 15, 14, 13);

    public RuedaBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return Direction.UP;
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ANEXO) ? ARRIBA : ABAJO;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new RuedaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, RuedaRegistro.RUEDA_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, RuedaRegistro.RUEDA_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
