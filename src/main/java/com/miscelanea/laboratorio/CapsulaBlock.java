package com.miscelanea.laboratorio;

import com.miscelanea.multibloque.MultiBloque;
import com.miscelanea.tortura.aparatos.AparatoBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Base de las cabinas del laboratorio: una cápsula de cristal de dos bloques de alto (el sujeto va de pie dentro) con la
 * consola en el bloque de detrás (el anexo de {@link AparatoBlock}). El hueco de encima de la cápsula se rellena con una
 * parte invisible (ver {@link MultiBloque}).
 */
public abstract class CapsulaBlock extends AparatoBlock implements MultiBloque {
    private static final VoxelShape CAPSULA = Block.box(0, 0, 0, 16, 32, 16), CONSOLA = Block.box(1, 0, 1, 15, 16, 15);
    private static final List<BlockPos> ENCIMA = List.of(BlockPos.ZERO.above());

    protected CapsulaBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente.getOpposite();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ANEXO) ? CONSOLA : CAPSULA;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return s.getValue(ANEXO) ? CONSOLA : Shapes.block();
    }

    /** Clic derecho en la consola: abre el panel (en el cliente); lo demás lo hace el aparato en el servidor. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState s, Level l, BlockPos pos, net.minecraft.world.entity.player.Player p,
                                                     net.minecraft.world.InteractionHand mano, net.minecraft.world.phys.BlockHitResult hit) {
        ConsolaLab.abrirPanel(s, l, pos, p, mano);
        return super.use(s, l, pos, p, mano, hit);
    }

    @Override
    public List<BlockPos> partes(BlockState s) {
        return s.getValue(ANEXO) ? List.of() : ENCIMA;
    }

    @Override
    public VoxelShape formaCompleta(BlockState s) {
        return CAPSULA;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext c) {
        BlockState s = super.getStateForPlacement(c);
        return s != null && MultiBloque.cabe(this, s, c) ? s : null;
    }

    @Override
    public void onPlace(BlockState s, Level l, BlockPos p, BlockState viejo, boolean mov) {
        super.onPlace(s, l, p, viejo, mov);
        if (!s.getValue(ANEXO) && !viejo.is(this)) MultiBloque.colocar(this, l, p, s);
    }

    @Override
    public void onRemove(BlockState s, Level l, BlockPos p, BlockState nuevo, boolean mov) {
        if (!s.getValue(ANEXO) && !nuevo.is(this)) MultiBloque.quitar(this, l, p, s);
        super.onRemove(s, l, p, nuevo, mov);
    }
}
