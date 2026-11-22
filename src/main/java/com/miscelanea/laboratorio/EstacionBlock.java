package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
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
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Una estación del laboratorio de dos bloques (la cabina y el panel de detrás): la parte común de las estaciones nuevas. */
public class EstacionBlock extends AparatoBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 16, 16);

    private final BiFunction<BlockPos, BlockState, BlockEntity> fabrica;
    private final Supplier<? extends BlockEntityType<?>> tipo;

    public EstacionBlock(Properties p, BiFunction<BlockPos, BlockState, BlockEntity> fabrica, Supplier<? extends BlockEntityType<?>> tipo) {
        super(p);
        this.fabrica = fabrica;
        this.tipo = tipo;
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
        return s.getValue(ANEXO) ? null : fabrica.apply(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (t != tipo.get()) return null;
        return (lv, pos, st, be) -> {
            if (be instanceof AparatoBlockEntity ab) {
                if (lv.isClientSide) AparatoBlockEntity.tickCliente(lv, pos, st, ab);
                else AparatoBlockEntity.tickServidor(lv, pos, st, ab);
            }
        };
    }
}
