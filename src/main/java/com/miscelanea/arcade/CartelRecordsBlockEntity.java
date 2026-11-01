package com.miscelanea.arcade;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Entidad del cartel de récords: solo sirve para dibujarlo (la esquina de cada grupo dibuja el cartel entero). */
public class CartelRecordsBlockEntity extends BlockEntity {
    public CartelRecordsBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.CARTEL_RECORDS_BE.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12, 8, 12);
    }
}
