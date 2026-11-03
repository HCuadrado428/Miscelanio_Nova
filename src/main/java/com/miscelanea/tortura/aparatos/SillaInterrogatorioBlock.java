package com.miscelanea.tortura.aparatos;

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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * Silla de interrogatorio: la silla de metal con correas (parte principal) y, a su lado (a la derecha de quien la pone,
 * mirándola de frente, como el cuadro de la silla eléctrica), la mesita de las herramientas con la batería del coche y el
 * monitor cardíaco (anexo).
 */
public class SillaInterrogatorioBlock extends AparatoBlock {
    private static final VoxelShape SILLA = Shapes.or(Block.box(1, 0, 1, 15, 9, 15), Block.box(1, 9, 12, 15, 22, 15));
    private static final VoxelShape MESA = Block.box(2, 0, 2, 14, 14, 14);
    /** Por FACING (índice 2D: sur, oeste, norte, este). */
    private static final VoxelShape[] SILLAS = new VoxelShape[4];

    static {
        for (Direction d : Direction.Plane.HORIZONTAL) SILLAS[d.get2DDataValue()] = rotar(SILLA, d);
    }

    public SillaInterrogatorioBlock(Properties p) {
        super(p);
    }

    @Override
    public Direction haciaAnexo(Direction frente) {
        return frente.getCounterClockWise();
    }

    @Override
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        if (s.getValue(ANEXO)) return MESA;
        return SILLAS[s.getValue(FACING).get2DDataValue()];
    }

    /** La forma de la silla se diseña mirando al norte (respaldo en +z). */
    private static VoxelShape rotar(VoxelShape forma, Direction f) {
        if (f == Direction.NORTH) return forma;
        VoxelShape[] out = {Shapes.empty()};
        forma.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double a0, b0, a1, b1;
            switch (f) {
                case SOUTH -> {
                    a0 = 1 - x1; b0 = 1 - z1; a1 = 1 - x0; b1 = 1 - z0;
                }
                case EAST -> {
                    a0 = 1 - z1; b0 = x0; a1 = 1 - z0; b1 = x1;
                }
                default -> {   // WEST
                    a0 = z0; b0 = 1 - x1; a1 = z1; b1 = 1 - x0;
                }
            }
            out[0] = Shapes.or(out[0], Shapes.box(a0, y0, b0, a1, y1, b1));
        });
        return out[0];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return s.getValue(ANEXO) ? null : new SillaInterrogatorioBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        if (l.isClientSide) return createTickerHelper(t, AparatosRegistro.INTERROGATORIO_BE.get(), AparatoBlockEntity::tickCliente);
        return createTickerHelper(t, AparatosRegistro.INTERROGATORIO_BE.get(), AparatoBlockEntity::tickServidor);
    }
}
