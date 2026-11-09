package com.miscelanea.purgatorio;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Un dedo de agua en el fondo de la piscina vacía: una lámina transparente de un píxel que no moja, no corre y no se
 * puede coger con el cubo (es un bloque normal: no hay agua que calcular).
 */
public class AguaPocaBlock extends Block {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 1, 16);

    public AguaPocaBlock(Properties p) {
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
    public boolean propagatesSkylightDown(BlockState s, BlockGetter l, BlockPos p) {
        return true;
    }
}
