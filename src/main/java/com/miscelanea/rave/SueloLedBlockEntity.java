package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Baldosa del suelo LED: solo hace falta para dibujar su luz (ver SueloLedRenderer del cliente). */
public class SueloLedBlockEntity extends BlockEntity {
    public SueloLedBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.SUELO_LED_BE.get(), p, s);
    }
}
