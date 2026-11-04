package com.miscelanea.estatica;

import com.miscelanea.ConfigServidor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Meterse en pantallas: se entra por una recreativa (o cualquier bloque de la etiqueta miscelanea:pantallas), el cuerpo
 * desaparece (lo esconden los clientes con la bandera DENTRO; no recibe daño), y se sale por otra pantalla cercana que
 * elige el cliente (o por la misma, o sola al pasar el tiempo máximo). Nunca se mueve ni se rompe ningún bloque.
 */
public final class PantallasServidor {
    private PantallasServidor() {}

    private record Dentro(BlockPos entrada, List<BlockPos> destinos, long desde, Vec3 fijo, float yaw, float pitch) {}

    private static final Map<UUID, Dentro> DENTRO = new HashMap<>();

    public static boolean dentro(ServerPlayer p) {
        return !DENTRO.isEmpty() && DENTRO.containsKey(p.getUUID());
    }

    /** La parte de abajo de una pantalla de dos bloques. */
    public static BlockPos base(ServerLevel l, BlockPos pos) {
        BlockState s = l.getBlockState(pos);
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && s.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return pos.below();
        return pos;
    }

    public static boolean esPantalla(ServerLevel l, BlockPos pos) {
        if (!l.isLoaded(pos)) return false;
        BlockEntity be = l.getBlockEntity(pos);
        return be instanceof com.miscelanea.arcade.RecreativaBlockEntity || l.getBlockState(pos).is(EstaticaRegistro.PANTALLAS);
    }

    public static Direction cara(ServerLevel l, BlockPos pos) {
        BlockState s = l.getBlockState(pos);
        return s.hasProperty(BlockStateProperties.HORIZONTAL_FACING) ? s.getValue(BlockStateProperties.HORIZONTAL_FACING) : Direction.NORTH;
    }

    /** Pantallas con entidad de bloque en el radio (ordenadas por distancia). */
    private static List<BlockPos> buscar(ServerLevel l, BlockPos centro, int radio) {
        List<BlockPos> out = new ArrayList<>();
        int c0x = (centro.getX() - radio) >> 4, c1x = (centro.getX() + radio) >> 4, c0z = (centro.getZ() - radio) >> 4, c1z = (centro.getZ() + radio) >> 4;
        double r2 = (double) radio * radio;
        for (int cx = c0x; cx <= c1x; cx++)
            for (int cz = c0z; cz <= c1z; cz++) {
                LevelChunk ch = l.getChunkSource().getChunkNow(cx, cz);
                if (ch == null) continue;
                for (BlockPos bp : ch.getBlockEntities().keySet()) {
                    if (bp.equals(centro) || bp.distSqr(centro) > r2) continue;
                    if (esPantalla(l, bp) && base(l, bp).equals(bp)) out.add(bp.immutable());
                }
            }
        out.sort(Comparator.comparingDouble(b -> b.distSqr(centro)));
        return out.size() > 32 ? new ArrayList<>(out.subList(0, 32)) : out;
    }

    public static boolean entrar(ServerPlayer p, BlockPos pos) {
        ServerLevel l = p.serverLevel();
        if (dentro(p) || !l.isLoaded(pos)) return false;
        pos = base(l, pos);
        if (!esPantalla(l, pos) || p.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > 6 * 6) {
            Estatica.aviso(p, Component.translatable("estatica.miscelanea.no_pantalla"));
            return false;
        }
        List<BlockPos> destinos = buscar(l, pos, ConfigServidor.estaticaPantallasRadio());
        byte[] caras = new byte[destinos.size()];
        for (int i = 0; i < caras.length; i++) caras[i] = (byte) cara(l, destinos.get(i)).get3DDataValue();
        DENTRO.put(p.getUUID(), new Dentro(pos, destinos, l.getGameTime(), p.position(), p.getYRot(), p.getXRot()));
        Vec3 c = p.getBoundingBox().getCenter();
        EstaticaRed.cerca(l, c.x, c.y, c.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ESTELA, p.getId(), c.x, c.y - 0.9, c.z,
                pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5, Math.round(p.yBodyRot)));
        Estatica.sonido(l, Vec3.atCenterOf(pos), EstaticaRegistro.PANTALLA.get(), 1F, 1F);
        EstaticaRed.a(p, new EstaticaRed.Pantallas(pos, destinos, caras, ConfigServidor.estaticaPantallasMaxTicks()));
        Estatica.sincronizar(p);
        return true;
    }

    /** Sale por el destino 'indice' (o por la entrada si es -1 o no vale). */
    public static void salir(ServerPlayer p, int indice) {
        Dentro d = DENTRO.remove(p.getUUID());
        if (d == null) return;
        ServerLevel l = p.serverLevel();
        BlockPos bp = indice >= 0 && indice < d.destinos.size() ? d.destinos.get(indice) : d.entrada;
        if (!esPantalla(l, bp)) bp = d.entrada;
        Vec3 dest = salida(l, p, bp);
        if (dest == null && bp != d.entrada) {
            bp = d.entrada;
            dest = salida(l, p, bp);
        }
        float yaw = d.yaw;
        if (dest == null) dest = d.fijo;
        else yaw = cara(l, bp).toYRot();
        Estatica.mover(p, dest, yaw, 0F);
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        Vec3 c = Vec3.atCenterOf(bp);
        EstaticaRed.cerca(l, c.x, c.y, c.z, 64, new EstaticaRed.Fx(EstaticaRed.Fx.ESTELA, p.getId(), c.x, c.y - 0.2, c.z, dest.x, dest.y, dest.z, Math.round(yaw)));
        EstaticaRed.cerca(l, dest.x, dest.y, dest.z, 64, EstaticaRed.Fx.en(EstaticaRed.Fx.ESTALLIDO, p.getId(), dest.add(0, 1, 0), 1));
        Estatica.sonido(l, c, EstaticaRegistro.PANTALLA.get(), 1F, 0.8F);
        Estatica.sonido(l, dest, EstaticaRegistro.ESTALLIDO.get(), 0.7F, 1.2F);
        EstaticaRed.a(p, new EstaticaRed.Pantallas(d.entrada, List.of(), new byte[0], 0));
        Estatica.gastar(p, Poder.PANTALLA);
        Estatica.sincronizar(p);
    }

    /** El hueco delante de la pantalla (o encima) donde cabe el jugador. */
    private static Vec3 salida(ServerLevel l, ServerPlayer p, BlockPos bp) {
        Direction f = cara(l, bp);
        BlockPos delante = bp.relative(f);
        for (int dy = 0; dy <= 1; dy++) {
            Vec3 q = new Vec3(delante.getX() + 0.5, delante.getY() + dy, delante.getZ() + 0.5);
            if (Estatica.cabe(l, p, q)) return q;
        }
        return null;
    }

    /** Mientras está dentro: quieto en su sitio (escondido) y fuera al acabarse el tiempo. */
    static void tick(ServerPlayer p) {
        Dentro d = DENTRO.get(p.getUUID());
        if (d == null) return;
        if (p.serverLevel().getGameTime() - d.desde > ConfigServidor.estaticaPantallasMaxTicks() || !p.isAlive()) {
            salir(p, -1);
            return;
        }
        if (p.position().distanceToSqr(d.fijo) > 0.04) Estatica.mover(p, d.fijo, p.getYRot(), p.getXRot());
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }
}
