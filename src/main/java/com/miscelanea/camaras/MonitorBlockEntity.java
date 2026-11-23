package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Un monitor de laboratorio: como la tele, guarda su canal; en una pared de monitores manda el de abajo a la izquierda. */
public class MonitorBlockEntity extends TeleBlockEntity {
    public MonitorBlockEntity(BlockPos p, BlockState s) {
        super(CamarasRegistro.MONITOR_BE.get(), p, s);
    }

    /** La pared entera (hasta 8 de lado) la dibuja el de abajo a la izquierda: se dibuja aunque ese bloque no esté a la vista. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(MonitorBlock.MAX);
    }
}
