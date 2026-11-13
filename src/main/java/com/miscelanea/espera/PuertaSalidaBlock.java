package com.miscelanea.espera;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** El hueco de la puerta de salida del Espera: se cruza (no tiene colisión) y te devuelve a donde estabas. */
public class PuertaSalidaBlock extends Block {
    private static final VoxelShape FORMA = Block.box(0, 0, 6, 16, 16, 10);

    public PuertaSalidaBlock(Properties p) {
        super(p);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public void entityInside(BlockState s, Level l, BlockPos p, Entity e) {
        if (!l.isClientSide && e instanceof ServerPlayer sp && !sp.isSpectator() && !sp.isPassenger()) Espera.salir(sp);
    }
}
