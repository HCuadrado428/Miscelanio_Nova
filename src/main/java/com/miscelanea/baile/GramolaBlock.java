package com.miscelanea.baile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gramola de videoclip: eliges canción y, mientras suena, todo lo que ves a tu alrededor se convierte en el decorado
 * del videoclip (el vestíbulo del hotel de Weapon of Choice, la discoteca de los setenta de Boogie Wonderland...) y
 * lo bailas con las flechas, como en la pista. Los demás pueden unirse antes de cada canción y bailan detrás en
 * formación. El decorado solo existe en la pantalla de los que bailan: el mundo de verdad no se toca.
 */
public class GramolaBlock extends PistaBaileBlock {
    private static final VoxelShape FORMA = Shapes.or(Block.box(1, 0, 2, 15, 13, 14), Block.box(2, 13, 3, 14, 16, 13));

    public GramolaBlock(Properties p) {
        super(p);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }
}
