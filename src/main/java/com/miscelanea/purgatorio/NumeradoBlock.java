package com.miscelanea.purgatorio;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Un bloque con varias caras pintadas (las columnas numeradas del garaje, los rótulos de las tiendas): cuál, en "numero". */
public class NumeradoBlock extends Block {
    public static final IntegerProperty NUMERO = IntegerProperty.create("numero", 0, 7);

    public NumeradoBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(NUMERO, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(NUMERO);
    }

    public BlockState con(int n) {
        return defaultBlockState().setValue(NUMERO, Math.floorMod(n, 8));
    }
}
