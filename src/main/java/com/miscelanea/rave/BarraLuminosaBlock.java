package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.Direction;

/**
 * Una barra luminosa tirada en el suelo: alumbra (nivel 9) en su color y se apaga sola a los 5 minutos (desaparece). Se
 * atraviesa y se rompe de un golpe; no se recupera (ya está gastada).
 */
public class BarraLuminosaBlock extends Block {
    public static final EnumProperty<Direction.Axis> EJE = BlockStateProperties.HORIZONTAL_AXIS;
    public static final int DURA = 6000;
    private static final VoxelShape X = Block.box(2, 0, 7, 14, 2, 9), Z = Block.box(7, 0, 2, 9, 2, 14);

    public BarraLuminosaBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(EJE, Direction.Axis.X).setValue(Muebles.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(EJE, Muebles.COLOR);
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(EJE) == Direction.Axis.X ? X : Z;
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState antes, boolean mov) {
        super.onPlace(s, l, p, antes, mov);
        if (!l.isClientSide && !antes.is(this)) l.scheduleTick(p, this, DURA);
    }

    @Override
    public void tick(BlockState s, ServerLevel l, BlockPos p, RandomSource r) {
        l.removeBlock(p, false);
    }
}
