package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Bola de discoteca: cuelga del techo, gira y reparte manchas de luz (del color que le pongas con un tinte) por todo el local. */
public class BolaBlock extends LuzRaveBlock {
    private static final VoxelShape FORMA = Block.box(3, 1, 3, 13, 16, 13);

    public BolaBlock(Properties p) {
        super(p, BolaBlockEntity::new, RenderShape.ENTITYBLOCK_ANIMATED);
        registerDefaultState(stateDefinition.any().setValue(LucesRave.ENCENDIDA, true).setValue(LucesRave.MODO, 0).setValue(Muebles.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LucesRave.ENCENDIDA, LucesRave.MODO, Muebles.COLOR);
    }

    @Override
    protected VoxelShape caja(BlockState s) {
        return FORMA;
    }
}
