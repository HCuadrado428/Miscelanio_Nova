package com.miscelanea.purgatorio.eventos;

import com.miscelanea.purgatorio.Purgatorio;
import com.miscelanea.purgatorio.PurgatorioGenerador;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

import javax.annotation.Nullable;

/**
 * Dónde está la puerta de SALIDA más cercana. Se pregunta al generador qué chunks tienen salida
 * ({@link PurgatorioGenerador#salida}) y, si el chunk ya está cargado, se busca dentro la puerta de verdad (así no
 * depende de en qué parte del chunk la ponga el generador).
 */
public final class Salidas {
    private Salidas() {}

    /** Hasta dónde se busca (en chunks): hay más o menos una salida de cada 60 chunks. */
    static final int RADIO = 32;

    /** El chunk con salida más cercano a ese sitio (o null si no hay ninguno en el radio). */
    @Nullable
    public static long[] chunkCercano(BlockPos de) {
        int pcx = de.getX() >> 4, pcz = de.getZ() >> 4;
        for (int r = 0; r <= RADIO; r++) {
            long mejor = Long.MAX_VALUE;
            long[] res = null;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r || !PurgatorioGenerador.salida(pcx + dx, pcz + dz)) continue;
                    long d = (long) dx * dx + (long) dz * dz;
                    if (d < mejor) {
                        mejor = d;
                        res = new long[]{pcx + dx, pcz + dz};
                    }
                }
            // en el anillo r puede haber una más lejos (en diagonal) que otra del anillo r+1: con mirar uno más basta casi siempre
            if (res != null) {
                for (int dx = -(r + 1); dx <= r + 1; dx++)
                    for (int dz = -(r + 1); dz <= r + 1; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r + 1 || !PurgatorioGenerador.salida(pcx + dx, pcz + dz)) continue;
                        long d = (long) dx * dx + (long) dz * dz;
                        if (d < mejor) {
                            mejor = d;
                            res = new long[]{pcx + dx, pcz + dz};
                        }
                    }
                return res;
            }
        }
        return null;
    }

    /**
     * La puerta de SALIDA más cercana (su bloque de abajo). Si el chunk está cargado (o {@code cargar}), la puerta de
     * verdad; si no, una estimación en ese chunk a la altura del suelo.
     */
    @Nullable
    public static BlockPos cercana(ServerLevel l, BlockPos de, boolean cargar) {
        long[] c = chunkCercano(de);
        if (c == null) return null;
        int cx = (int) c[0], cz = (int) c[1];
        BlockPos estimada = new BlockPos(cx * 16 + 3, PurgatorioGenerador.SUELO, cz * 16 + 3);
        if (!Purgatorio.en(l)) return estimada;
        if (!l.hasChunk(cx, cz)) {
            if (!cargar) return estimada;
            Purgatorio.preparar(l, estimada);
        }
        BlockPos p = buscarPuerta(l.getChunk(cx, cz), de.getY());
        return p != null ? p : estimada;
    }

    /** Busca el bloque de puerta de salida de más abajo en el chunk (primero cerca de la altura dada). */
    @Nullable
    static BlockPos buscarPuerta(LevelChunk ch, int yCerca) {
        var puerta = com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get();
        int x0 = ch.getPos().getMinBlockX(), z0 = ch.getPos().getMinBlockZ();
        int min = ch.getMinBuildHeight(), max = ch.getMaxBuildHeight();
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        BlockPos mejor = null;
        int dm = Integer.MAX_VALUE;
        // las secciones vacías se saltan enteras: casi todo el chunk es aire
        for (int s = 0; s < ch.getSections().length; s++) {
            var sec = ch.getSections()[s];
            if (sec == null || sec.hasOnlyAir()) continue;
            int yb = ch.getSectionYFromSectionIndex(s) << 4;
            if (yb + 15 < min || yb > max) continue;
            for (int y = 0; y < 16; y++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        if (sec.getBlockState(x, y, z).is(puerta)) {
                            q.set(x0 + x, yb + y, z0 + z);
                            // la de abajo de cada puerta
                            if (ch.getBlockState(q.below()).is(puerta)) continue;
                            int d = Math.abs(q.getY() - yCerca);
                            if (d < dm) {
                                dm = d;
                                mejor = q.immutable();
                            }
                        }
        }
        return mejor;
    }

    /**
     * Un sitio libre delante de la puerta (a dos bloques), para que te "llamen" a ella sin cruzarla todavía: {dónde, el
     * yaw para mirarla}.
     */
    public static double[] delante(ServerLevel l, BlockPos puerta) {
        for (Direction d : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
            BlockPos q = puerta.relative(d, 2);
            if (libre(l, q) && libre(l, q.above()) && !l.getBlockState(q.below()).getCollisionShape(l, q.below()).isEmpty()
                    && libre(l, puerta.relative(d))) {
                float yaw = d.getOpposite().toYRot();
                return new double[]{q.getX() + 0.5, q.getY(), q.getZ() + 0.5, yaw};
            }
        }
        return new double[]{puerta.getX() + 0.5, puerta.getY(), puerta.getZ() + 2.5, 180};
    }

    private static boolean libre(ServerLevel l, BlockPos q) {
        return l.getBlockState(q).getCollisionShape(l, q).isEmpty() && !l.getBlockState(q).is(com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get());
    }
}
