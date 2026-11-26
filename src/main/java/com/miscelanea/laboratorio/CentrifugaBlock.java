package com.miscelanea.laboratorio;

import com.miscelanea.tortura.aparatos.AparatoBlock;
import com.miscelanea.tortura.aparatos.AparatoBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * La centrífuga: la base con el eje en un bloque y el puesto de mando (la palanca) en el de detrás (el anexo). El brazo
 * con la silla gira por encima, así que hace falta espacio libre alrededor (3 x 3 y dos de alto).
 */
public class CentrifugaBlock extends AparatoBlock {
    private static final VoxelShape FORMA = Block.box(0, 0, 0, 16, 12, 16);

    public CentrifugaBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente.getOpposite();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return FORMA;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new CentrifugaBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, LabRegistro.CENTRIFUGA_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, LabRegistro.CENTRIFUGA_BE.get(), AparatoBlockEntity::tickServidor);
    }

    /** Clic derecho en la consola: abre el panel (en el cliente); lo demás lo hace el aparato en el servidor. */
    @Override
    public net.minecraft.world.InteractionResult use(BlockState s, Level l, BlockPos pos, net.minecraft.world.entity.player.Player p,
                                                     net.minecraft.world.InteractionHand mano, net.minecraft.world.phys.BlockHitResult hit) {
        ConsolaLab.abrirPanel(s, l, pos, p, mano);
        return super.use(s, l, pos, p, mano, hit);
    }
}
