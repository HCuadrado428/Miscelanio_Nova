package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Máquina de humo y fuente de chispas frías (las dos solo echan partículas). La de humo mira hacia delante y echa humo del
 * color que le pongas con un tinte; la de chispas echa hacia arriba una fuente de chispas que no queman. Modo = cantidad.
 */
public class HumoBlock extends LuzRaveBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private final boolean chispas;
    private static final VoxelShape[] HUMO = new VoxelShape[6];
    private static final VoxelShape CHISPAS = Block.box(2.8, 0, 2.8, 13.2, 9, 13.2);

    public HumoBlock(Properties p, boolean chispas) {
        super(p, EfectoBlockEntity::new, RenderShape.MODEL);
        this.chispas = chispas;
        registerDefaultState(stateDefinition.any().setValue(LucesRave.ENCENDIDA, true).setValue(LucesRave.MODO, 1)
                .setValue(FACING, Direction.NORTH).setValue(Muebles.COLOR, chispas ? DyeColor.YELLOW : DyeColor.MAGENTA));
    }

    public boolean chispas() {
        return chispas;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LucesRave.ENCENDIDA, LucesRave.MODO, FACING, Muebles.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return defaultBlockState().setValue(FACING, c.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape caja(BlockState s) {
        if (chispas) return CHISPAS;
        Direction d = s.getValue(FACING);
        VoxelShape f = HUMO[d.get3DDataValue()];
        if (f == null) f = HUMO[d.get3DDataValue()] = com.miscelanea.camaras.Montaje.forma(d, 2, 0, 0.5, 14, 13.5, 14);
        return f;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return l.isClientSide ? createTickerHelper(t, RaveRegistro.EFECTO_BE.get(), EfectoBlockEntity::tickCliente) : null;
    }
}
