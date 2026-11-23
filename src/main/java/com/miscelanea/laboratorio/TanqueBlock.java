package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** El tanque de privación sensorial: una cápsula oscura (ver {@link CapsulaBlock}) con el intercomunicador en la consola de detrás. */
public class TanqueBlock extends CapsulaBlock {
    public TanqueBlock(Properties p) {
        super(p);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new TanqueBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, LabRegistro.TANQUE_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, LabRegistro.TANQUE_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
