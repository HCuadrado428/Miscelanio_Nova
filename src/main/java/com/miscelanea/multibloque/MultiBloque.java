package com.miscelanea.multibloque;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/**
 * Un bloque que ocupa más de un hueco (una cabina de 3 de ancho, una torre de 3 de alto...). El bloque de verdad ("maestro")
 * está en un hueco y los demás se rellenan con {@link ParteBlock}: bloques invisibles que tienen el trozo de forma que les toca
 * y pasan los clics y los golpes al maestro. El modelo entero lo dibuja el maestro.
 * <p>El maestro llama a {@link #colocar} en su {@code onPlace}, a {@link #quitar} en su {@code onRemove} y a {@link #cabe} en su
 * {@code getStateForPlacement}.
 */
public interface MultiBloque {
    /** Huecos que ocupa además del suyo, relativos al maestro (según su estado: hacia dónde mira). */
    List<BlockPos> partes(BlockState s);

    /** La forma entera, en coordenadas del maestro (puede salirse de su hueco). */
    VoxelShape formaCompleta(BlockState s);

    /** ¿Choca? (las partes copian esto: una bola colgada se atraviesa, una cabina no). */
    default boolean solido() {
        return true;
    }

    /** Lo que cae dentro del hueco del maestro (para chocar: los demás huecos los ponen las partes). */
    static VoxelShape trozo(VoxelShape completa) {
        return Shapes.join(completa, Shapes.block(), BooleanOp.AND);
    }

    static boolean cabe(MultiBloque b, BlockState s, BlockPlaceContext c) {
        for (BlockPos r : b.partes(s))
            if (!c.getLevel().getBlockState(c.getClickedPos().offset(r)).canBeReplaced(c)) return false;
        return true;
    }

    static void colocar(MultiBloque b, Level l, BlockPos p, BlockState s) {
        if (l.isClientSide) return;
        for (BlockPos r : b.partes(s)) {
            BlockPos q = p.offset(r);
            if (!l.getBlockState(q).canBeReplaced()) continue;
            l.setBlock(q, MultiRegistro.PARTE.get().defaultBlockState(), 3);
            if (l.getBlockEntity(q) instanceof ParteBlockEntity be) be.maestro(p);
        }
    }

    static void quitar(MultiBloque b, Level l, BlockPos p, BlockState s) {
        if (l.isClientSide) return;
        for (BlockPos r : b.partes(s)) {
            BlockPos q = p.offset(r);
            if (l.getBlockState(q).is(MultiRegistro.PARTE.get()) && l.getBlockEntity(q) instanceof ParteBlockEntity be && p.equals(be.maestro()))
                l.removeBlock(q, false);
        }
    }
}
