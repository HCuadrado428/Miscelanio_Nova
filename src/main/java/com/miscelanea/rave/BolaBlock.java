package com.miscelanea.rave;

import com.miscelanea.muebles.Muebles;
import com.miscelanea.multibloque.MultiBloque;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Bola de discoteca: una bola de espejos grande (más de bloque y medio) que cuelga del techo con su motor, gira y reparte manchas
 * de luz (del color que le pongas con un tinte) por todo el local. Ocupa su hueco y los dos de debajo (ver {@link MultiBloque}),
 * pero no choca: se pasa por debajo y a través.
 */
public class BolaBlock extends LuzRaveBlock implements MultiBloque {
    private static final List<BlockPos> PARTES = List.of(BlockPos.ZERO.below(), BlockPos.ZERO.below(2));
    /** El motor y la cadena, y debajo la bola (centro 8 px por debajo del bloque, 13 px de radio). */
    private static final VoxelShape FORMA = Shapes.or(Block.box(4, 4.5, 4, 12, 16, 12), Block.box(-3, -21, -3, 19, 5, 19));

    public BolaBlock(Properties p) {
        super(p, BolaBlockEntity::new, RenderShape.ENTITYBLOCK_ANIMATED);
        registerDefaultState(stateDefinition.any().setValue(LucesRave.ENCENDIDA, true).setValue(LucesRave.MODO, 0).setValue(Muebles.COLOR, DyeColor.WHITE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(LucesRave.ENCENDIDA, LucesRave.MODO, LucesRave.CON_SENAL, Muebles.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        return MultiBloque.cabe(this, defaultBlockState(), c) ? defaultBlockState() : null;
    }

    @Override
    protected VoxelShape caja(BlockState s) {
        return FORMA;
    }

    @Override
    public List<BlockPos> partes(BlockState s) {
        return PARTES;
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        return FORMA;
    }

    @Override
    public boolean solido() {
        return false;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState viejo, boolean mov) {
        super.onPlace(s, l, p, viejo, mov);
        if (!viejo.is(this)) MultiBloque.colocar(this, l, p, s);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        if (!nuevo.is(this)) MultiBloque.quitar(this, l, p, s);
        super.onRemove(s, l, p, nuevo, mov);
    }
}
