package com.miscelanea.trampolin;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Solo existe para que el renderizador dibuje la lona deformable. */
public class TrampolinBlockEntity extends BlockEntity {
    public TrampolinBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.TRAMPOLIN_BE.get(), p, s);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(0.1);
    }
}
