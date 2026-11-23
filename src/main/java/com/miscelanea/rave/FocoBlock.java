package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Foco móvil (cabeza robótica): en el suelo o colgado del techo, barre el local con un haz de luz de color. */
public class FocoBlock extends LuzRaveBlock {
    private static final VoxelShape SUELO = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5), TECHO = Block.box(1.5, 0, 1.5, 14.5, 16, 14.5);

    public FocoBlock(Properties p) {
        super(p, FocoBlockEntity::new, RenderShape.ENTITYBLOCK_ANIMATED);
        registerDefaultState(stateDefinition.any().setValue(LucesRave.ENCENDIDA, true).setValue(LucesRave.MODO, 0)
                .setValue(LucesRave.COLGADO, false).setValue(Muebles.COLOR, DyeColor.MAGENTA));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LucesRave.ENCENDIDA, LucesRave.MODO, LucesRave.COLGADO, Muebles.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(LucesRave.COLGADO, c.getClickedFace() == Direction.DOWN);
    }

    @Override
    protected VoxelShape caja(BlockState s) {
        return s.getValue(LucesRave.COLGADO) ? TECHO : SUELO;
    }
}
