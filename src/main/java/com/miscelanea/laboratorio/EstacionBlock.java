package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Una estación del laboratorio (la cápsula y la consola de detrás, ver {@link CapsulaBlock}): la parte común de las estaciones nuevas. */
public class EstacionBlock extends CapsulaBlock {
    private final BiFunction<BlockPos, BlockState, BlockEntity> fabrica;
    private final Supplier<? extends BlockEntityType<?>> tipo;

    public EstacionBlock(Properties p, BiFunction<BlockPos, BlockState, BlockEntity> fabrica, Supplier<? extends BlockEntityType<?>> tipo) {
        super(p);
        this.fabrica = fabrica;
        this.tipo = tipo;
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
