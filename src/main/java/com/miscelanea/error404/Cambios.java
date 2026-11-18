package com.miscelanea.error404;

import com.miscelanea.Miscelanea;
import com.miscelanea.error404.liminal.LiminalRegistro;
import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que "no cuadra" en la 404: cosas que cambian cuando no miras. Cada cierto tiempo, a la espalda de cada jugador
 * (fuera de su vista, a más de 6 bloques), cambia algo pequeño:
 * <ul>
 *   <li>el reloj del aula marca otra hora;</li>
 *   <li>un número del garaje o de una tienda ya no es el que era;</li>
 *   <li>un panel de fluorescentes se apaga (o se vuelve a encender);</li>
 *   <li>un trozo de pared del pasillo es de otro color;</li>
 *   <li>aparece una puerta donde antes solo había pared.</li>
 * </ul>
 * Nunca cierra un paso: solo cambia o añade cosas en las paredes y el techo, así que la 404 sigue siendo recorrible.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Cambios {
    private Cambios() {}

    /** Segundos que faltan para el siguiente cambio de cada jugador. */
    private static final Map<UUID, Integer> ESPERA = new HashMap<>();

    /** Cuánto hay que estar mirando hacia otro lado (producto escalar con la mirada) para que un sitio cuente como "fuera de vista". */
    private static final double DETRAS = -0.05;
    private static final double DISTANCIA_MIN = 6.0;

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.level().isClientSide) return;
        if (p.tickCount % 20 != 0 || !Error404.en(p.level())) return;
        if (!Error404Config.activado() || !Error404Config.cambios()) return;
        int quedan = ESPERA.getOrDefault(p.getUUID(), 12) - 1;
        if (quedan > 0) {
            ESPERA.put(p.getUUID(), quedan);
            return;
        }
        RandomSource r = p.getRandom();
        int min = Error404Config.cambiosMin(), max = Error404Config.cambiosMax();
        ESPERA.put(p.getUUID(), min + r.nextInt(max - min + 1));
        hacer((ServerLevel) p.level(), p);
    }

    @SubscribeEvent
    public static void salio(PlayerEvent.PlayerLoggedOutEvent e) {
        ESPERA.remove(e.getEntity().getUUID());
    }

    private enum Tipo {RELOJ, NUMERO, LUZ, PARED, PUERTA}

    /** Hace un cambio (el primero que encuentre) a la espalda del jugador. Devuelve si lo ha hecho. */
    public static boolean hacer(ServerLevel l, ServerPlayer p) {
        RandomSource r = p.getRandom();
        for (int intento = 0; intento < 6; intento++) {
            Tipo tipo = switch (r.nextInt(20)) {
                case 0, 1, 2, 3, 4 -> Tipo.RELOJ;
                case 5, 6, 7, 8 -> Tipo.NUMERO;
                case 9, 10 -> Tipo.LUZ;
                case 11, 12, 13, 14, 15, 16 -> Tipo.PARED;
                default -> Tipo.PUERTA;
            };
            Vec3 atras = Vec3.directionFromRotation(0, p.getYRot() + 180);
            double d = 8 + r.nextInt(17);
            double lado = r.nextInt(11) - 5;
            Vec3 lat = Vec3.directionFromRotation(0, p.getYRot() + 90);
            BlockPos c = BlockPos.containing(p.getX() + atras.x * d + lat.x * lado, p.getY(), p.getZ() + atras.z * d + lat.z * lado);
            if (cambiar(l, p, c, tipo, r)) return true;
        }
        return false;
    }

    private static boolean cambiar(ServerLevel l, ServerPlayer p, BlockPos c, Tipo tipo, RandomSource r) {
        List<BlockPos> sitios = new ArrayList<>();
        List<Direction> caras = new ArrayList<>();
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-8, -1, -8), c.offset(8, 3, 8))) {
            if (!l.isLoaded(q)) continue;
            BlockState s = l.getBlockState(q);
            switch (tipo) {
                case RELOJ -> {
                    if (s.is(ModBlocks.RELOJ_PARADO.get())) sitios.add(q.immutable());
                }
                case NUMERO -> {
                    if (s.getBlock() instanceof NumeradoBlock) sitios.add(q.immutable());
                }
                case LUZ -> {
                    if (s.getBlock() instanceof FluorescenteBlock) sitios.add(q.immutable());
                }
                case PARED, PUERTA -> {
                    if (!esPared(s)) break;
                    for (Direction dir : Direction.Plane.HORIZONTAL) {
                        BlockPos f = q.relative(dir);
                        if (l.isLoaded(f) && l.getBlockState(f).isAir() && l.getBlockState(f.above()).isAir()) {
                            sitios.add(q.immutable());
                            caras.add(dir);
                            break;
                        }
                    }
                }
            }
        }
        if (sitios.isEmpty()) return false;
        // los que no se ven desde donde está el jugador
        List<Integer> validos = new ArrayList<>();
        for (int i = 0; i < sitios.size(); i++) if (fueraDeVista(p, sitios.get(i))) validos.add(i);
        if (validos.isEmpty()) return false;
        int i = validos.get(r.nextInt(validos.size()));
        BlockPos x = sitios.get(i);
        BlockState s = l.getBlockState(x);
        switch (tipo) {
            case RELOJ -> {
                int ahora = s.getValue(RelojParadoBlock.HORA);
                l.setBlock(x, s.setValue(RelojParadoBlock.HORA, (ahora + 1 + r.nextInt(4)) % 5), 3);
            }
            case NUMERO -> {
                int ahora = s.getValue(NumeradoBlock.NUMERO);
                l.setBlock(x, s.setValue(NumeradoBlock.NUMERO, (ahora + 1 + r.nextInt(7)) % 8), 3);
            }
            case LUZ -> l.setBlock(x, s.setValue(FluorescenteBlock.ENCENDIDO, !s.getValue(FluorescenteBlock.ENCENDIDO)), 3);
            case PARED -> pintarTramo(l, x, caras.get(i), r);
            case PUERTA -> {
                if (!ponerPuerta(l, x, caras.get(i), r)) return false;
            }
        }
        Miscelanea.LOGGER.debug("404: cambio {} en {} (a la espalda de {})", tipo, x, p.getGameProfile().getName());
        return true;
    }

    /** ¿Se ve (más o menos) desde donde está mirando el jugador? Solo cuenta lo que queda a su espalda y lejos. */
    static boolean fueraDeVista(ServerPlayer p, BlockPos x) {
        Vec3 a = Vec3.atCenterOf(x).subtract(p.getEyePosition());
        double d = a.length();
        return d >= DISTANCIA_MIN && a.scale(1.0 / d).dot(p.getLookAngle()) < DETRAS;
    }

    private static List<Block> paredes() {
        return List.of(ModBlocks.PARED_SALA.get(), ModBlocks.PARED_COLEGIO.get(), ModBlocks.PARED_APARCAMIENTO.get(),
                ModBlocks.PAPEL_HOTEL.get(), LiminalRegistro.PARED_BACKROOMS.get());
    }

    static boolean esPared(BlockState s) {
        return paredes().contains(s.getBlock());
    }

    /** Un tramo de pared (de 3 a 6 bloques de largo y 3 de alto) pasa a ser de otra pared. */
    private static void pintarTramo(ServerLevel l, BlockPos x, Direction cara, RandomSource r) {
        Block actual = l.getBlockState(x).getBlock();
        List<Block> otras = new ArrayList<>(paredes());
        otras.remove(actual);
        Block nueva = otras.get(r.nextInt(otras.size()));
        Direction t = cara.getClockWise();
        int largo = 3 + r.nextInt(4), ini = -r.nextInt(largo);
        for (int i = ini; i < ini + largo; i++)
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos q = x.relative(t, i).above(dy);
                if (l.isLoaded(q) && esPared(l.getBlockState(q))) l.setBlock(q, nueva.defaultBlockState(), 3);
            }
    }

    /** Una puerta cerrada donde había pared (solo si no hay ya una por aquí). */
    private static boolean ponerPuerta(ServerLevel l, BlockPos x, Direction cara, RandomSource r) {
        if (!esPared(l.getBlockState(x.above())) || !l.getBlockState(x.below()).isFaceSturdy(l, x.below(), Direction.UP)) return false;
        for (BlockPos q : BlockPos.betweenClosed(x.offset(-6, -1, -6), x.offset(6, 2, 6)))
            if (l.isLoaded(q) && l.getBlockState(q).getBlock() instanceof DoorBlock) return false;
        BlockState abajo = Blocks.SPRUCE_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, cara.getOpposite())
                .setValue(DoorBlock.HINGE, r.nextBoolean() ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT)
                .setValue(DoorBlock.OPEN, false).setValue(DoorBlock.POWERED, false)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        l.setBlock(x, abajo, 3);
        l.setBlock(x.above(), abajo.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        return true;
    }
}
