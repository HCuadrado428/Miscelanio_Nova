package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.function.Predicate;

/**
 * Qué forma y dirección coge una pieza de vía al ponerla, mirando lo que hay alrededor (como los raíles de Minecraft):
 * <ol>
 *     <li>Si hay una vía suelta que acaba justo en este bloque, la sigue (con su dirección); si acaba un bloque más
 *     arriba, esta es una bajada.</li>
 *     <li>Si no, si al lado hay una recta suelta, la convierte en curva hacia aquí; y si está un bloque más abajo y en
 *     línea, en subida.</li>
 *     <li>Si no, va hacia donde mira el jugador.</li>
 * </ol>
 * Después, si en vez de recta una curva o una subida enlaza con una vía suelta que ya hay delante, se pone así.
 * Son unas pocas lecturas de bloques al colocar, nada más.
 */
public final class Colocacion {
    private Colocacion() {}

    /** Dirección y forma de la pieza nueva y, si hace falta, el nuevo estado de una pieza vecina. */
    public record Plan(Direction dir, ViaMontanaBlock.Forma forma, @Nullable BlockPos otroPos, @Nullable BlockState otro) {}

    private static Via.Clase clase(ViaMontanaBlock.Forma f) {
        return switch (f) {
            case RECTA -> Via.Clase.RECTA;
            case CURVA_IZQ -> Via.Clase.CURVA_IZQ;
            case CURVA_DER -> Via.Clase.CURVA_DER;
            case SUBIDA -> Via.Clase.SUBIDA;
            case BAJADA -> Via.Clase.BAJADA;
        };
    }

    /** ¿La vía acaba ahí, sin nada detrás que la siga? */
    private static boolean suelta(BlockGetter l, Via.Pieza q) {
        return Via.siguiente(l, q) == null;
    }

    public static Plan planear(BlockGetter l, BlockPos pos, Direction mira, Predicate<ViaMontanaBlock.Forma> admite, ViaMontanaBlock.Tipo tipo) {
        // 1) una vía suelta que llega aquí (o un bloque más arriba: bajada)
        Plan llega = null;
        for (Direction x : Direction.Plane.HORIZONTAL) {
            BlockPos atras = pos.relative(x.getOpposite());
            BlockPos rizo = pos.relative(x.getOpposite(), (int) Via.RIZO_AVANCE).relative(x.getCounterClockWise(), (int) Via.RIZO_LADO);
            for (BlockPos c : new BlockPos[]{atras, atras.below(), atras.above(), rizo, rizo.above()}) {
                Via.Pieza q = Via.en(l, c);
                if (q == null || q.salidaDir() != x || !suelta(l, q)) continue;
                Plan p = null;
                if (q.salida().equals(pos)) p = new Plan(x, ViaMontanaBlock.Forma.RECTA, null, null);
                else if (q.salida().equals(pos.above()) && admite.test(ViaMontanaBlock.Forma.BAJADA))
                    p = new Plan(x, ViaMontanaBlock.Forma.BAJADA, null, null);
                if (p == null) continue;
                if (x == mira) return delante(l, pos, p, admite, tipo);
                if (llega == null) llega = p;
            }
        }
        if (llega != null) return delante(l, pos, llega, admite, tipo);
        // 2) una recta suelta al lado que se puede girar (curva) o inclinar (subida) para llegar aquí
        Plan girar = null;
        for (Direction x : Direction.Plane.HORIZONTAL) {
            Plan p = null;
            BlockPos lado = pos.relative(x.getOpposite());
            BlockState st = l.getBlockState(lado);
            Via.Pieza q = Via.en(l, lado);
            if (q != null && q.clase() == Via.Clase.RECTA && st.getBlock() instanceof ViaMontanaBlock vb && suelta(l, q)
                    && q.dir().getAxis() != x.getAxis()) {
                ViaMontanaBlock.Forma f = x == q.dir().getCounterClockWise() ? ViaMontanaBlock.Forma.CURVA_IZQ : ViaMontanaBlock.Forma.CURVA_DER;
                if (vb.admite(f)) p = new Plan(x, ViaMontanaBlock.Forma.RECTA, lado, st.setValue(ViaMontanaBlock.FORMA, f));
            }
            BlockPos abajo = lado.below();
            BlockState sb = l.getBlockState(abajo);
            Via.Pieza qb = Via.en(l, abajo);
            if (p == null && qb != null && qb.clase() == Via.Clase.RECTA && sb.getBlock() instanceof ViaMontanaBlock vb && suelta(l, qb)
                    && qb.dir() == x && vb.admite(ViaMontanaBlock.Forma.SUBIDA))
                p = new Plan(x, ViaMontanaBlock.Forma.RECTA, abajo, sb.setValue(ViaMontanaBlock.FORMA, ViaMontanaBlock.Forma.SUBIDA));
            if (p == null) continue;
            if (x == mira) return delante(l, pos, p, admite, tipo);
            if (girar == null) girar = p;
        }
        if (girar != null) return delante(l, pos, girar, admite, tipo);
        // 3) hacia donde mira
        return delante(l, pos, new Plan(mira, ViaMontanaBlock.Forma.RECTA, null, null), admite, tipo);
    }

    /** Si la recta no enlaza con nada delante pero una curva o una subida sí (con una vía que espera), se pone esa. */
    private static Plan delante(BlockGetter l, BlockPos pos, Plan p, Predicate<ViaMontanaBlock.Forma> admite, ViaMontanaBlock.Tipo tipo) {
        if (p.forma() != ViaMontanaBlock.Forma.RECTA) return p;
        for (ViaMontanaBlock.Forma f : new ViaMontanaBlock.Forma[]{ViaMontanaBlock.Forma.RECTA, ViaMontanaBlock.Forma.CURVA_IZQ,
                ViaMontanaBlock.Forma.CURVA_DER, ViaMontanaBlock.Forma.SUBIDA}) {
            if (!admite.test(f)) continue;
            Via.Pieza hip = new Via.Pieza(pos.immutable(), p.dir(), clase(f), tipo, false);
            Via.Pieza n = Via.siguiente(l, hip);
            if (n == null) continue;
            // enlaza con una vía que no tiene a nadie detrás (si ya lo tiene, no se le roba)
            Via.Pieza antes = Via.anterior(l, n);
            if (antes == null || antes.pos().equals(pos)) return f == p.forma() ? p : new Plan(p.dir(), f, p.otroPos(), p.otro());
        }
        return p;
    }

    /** Dirección para un rizo: la de la vía suelta que llega a él, o hacia donde mira. */
    public static Direction dirRizo(BlockGetter l, BlockPos pos, Direction mira) {
        Direction mejor = null;
        for (Direction x : Direction.Plane.HORIZONTAL) {
            BlockPos atras = pos.relative(x.getOpposite());
            for (BlockPos c : new BlockPos[]{atras, atras.below()}) {
                Via.Pieza q = Via.en(l, c);
                if (q != null && q.salidaDir() == x && q.salida().equals(pos) && suelta(l, q)) {
                    if (x == mira) return x;
                    if (mejor == null) mejor = x;
                }
            }
        }
        return mejor != null ? mejor : mira;
    }
}
