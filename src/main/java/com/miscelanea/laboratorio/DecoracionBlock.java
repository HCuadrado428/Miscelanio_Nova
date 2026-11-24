package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bloque de decoración del laboratorio sin orientación (probetas, microscopio...): solo tiene forma. */
public class DecoracionBlock extends Block {
    private final VoxelShape forma;

    public DecoracionBlock(Properties p, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(p);
        this.forma = Block.box(x0, y0, z0, x1, y1, z1);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return forma;
    }
}
