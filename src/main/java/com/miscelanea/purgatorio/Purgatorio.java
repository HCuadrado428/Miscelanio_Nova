package com.miscelanea.purgatorio;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * El Purgatorio: adonde van los errores purgados. Se cae ahí "saliéndose del mapa": asfixiándose dentro de un bloque
 * (a veces, como un noclip) o cayendo al vacío del mundo normal. Se sale cruzando una puerta con el cartel verde de
 * SALIDA (hay más o menos una cada 60 chunks), y se vuelve justo a donde se estaba. Morir allí es como morir normal.
 * Si se cae por debajo del suelo del Purgatorio, se aparece en otra parte de él.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Purgatorio {
    private Purgatorio() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Miscelanea.MOD_ID, "purgatorio"));
    private static final String TAG = "miscelanea_purgatorio";
    /** Lo lejos (en chunks) que se puede aparecer del centro. */
    private static final int RADIO = 120;

    public static boolean en(Level l) {
        return l.dimension() == DIMENSION;
    }

    @Nullable
    static ServerLevel nivel(ServerPlayer p) {
        return p.server.getLevel(DIMENSION);
    }

    private static CompoundTag persistente(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    // ------------------------------------------------------------------ cómo se llega
    @SubscribeEvent
    public static void danio(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || en(p.level())) return;
        if (e.getSource().is(DamageTypes.IN_WALL) && PurgatorioConfig.activado() && p.getRandom().nextFloat() < PurgatorioConfig.probabilidadPared()) {
            e.setCanceled(true);
            entrar(p, "purgatorio.miscelanea.noclip");
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        Level l = p.level();
        if (en(l)) {
            // con el Purgatorio desactivado en la configuración, a quien esté dentro se le saca
            if (p.tickCount % 20 == 0 && !PurgatorioConfig.activado()) {
                salir(p);
                return;
            }
            Habitantes.tick(p);
            // la puerta de salida también se mira aquí (si el jugador no se mueve, el bloque no se entera)
            if (p.tickCount % 5 == 0) {
                var puerta = com.miscelanea.registry.ModBlocks.PUERTA_SALIDA.get();
                if (l.getBlockState(p.blockPosition()).is(puerta) || l.getBlockState(BlockPos.containing(p.getEyePosition())).is(puerta)) {
                    salir(p);
                    return;
                }
            }
            // por debajo del suelo: se aparece en otra parte (cayendo desde arriba)
            if (p.getY() < -24) {
                // si se ha caído nada más llegar (el suelo aún no le había llegado al cliente), vuelve al mismo sitio;
                // si no, aparece en otra parte cayendo del techo
                ServerLevel l2 = (ServerLevel) l;
                long ahora = l2.getGameTime();
                boolean recien = ahora - persistente(p).getLong(TAG + "_t") < 400;
                // por los agujeros del Mundo sin Texturas se cae fuera del Purgatorio: de vuelta a casa
                // (también nada más llegar: si encima hay un agujero de verdad, no es que el suelo no hubiera cargado)
                boolean agujero = l.getBlockState(new BlockPos(p.getBlockX(), PurgatorioGenerador.SUELO - 1, p.getBlockZ())).isAir()
                        && l.getBlockState(new BlockPos(p.getBlockX(), PurgatorioGenerador.SUELO - 2, p.getBlockZ())).isAir();
                if ((!recien || agujero) && PurgatorioGenerador.zona(p.getBlockX() >> 4, p.getBlockZ() >> 4) == PurgatorioGenerador.Zona.SIN_TEXTURA) {
                    salir(p);
                    return;
                }
                BlockPos b = recien ? new BlockPos(p.getBlockX(), PurgatorioGenerador.SUELO, p.getBlockZ()) : sitio(p, PurgatorioGenerador.SUELO + 18);
                preparar(l2, b);
                persistente(p).putLong(TAG + "_t", ahora);
                p.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                p.setDeltaMovement(0, 0, 0);
                p.fallDistance = 0;
            }
            return;
        }
        if (l.dimension() == Level.OVERWORLD && p.getY() < l.getMinBuildHeight() - 40 && !p.getAbilities().flying
                && PurgatorioConfig.activado() && PurgatorioConfig.entrarAlCaer())
            entrar(p, "purgatorio.miscelanea.vacio");
    }

    /** Al irse del servidor se olvida lo que se sabía de él (para no ir guardando jugadores que ya no están). */
    @SubscribeEvent
    public static void fuera(PlayerEvent.PlayerLoggedOutEvent e) {
        Habitantes.olvidar(e.getEntity().getUUID());
    }

    /** Lleva al jugador al Purgatorio (guardando de dónde viene). */
    public static void entrar(ServerPlayer p, @Nullable String aviso) {
        ServerLevel dest = nivel(p);
        if (dest == null || en(p.level())) return;
        CompoundTag vuelta = new CompoundTag();
        BlockPos de = p.getY() < p.level().getMinBuildHeight() ? null : p.blockPosition();
        vuelta.putString("Dim", p.level().dimension().location().toString());
        if (de != null) {
            vuelta.putDouble("X", p.getX());
            vuelta.putDouble("Y", p.getY());
            vuelta.putDouble("Z", p.getZ());
        }
        persistente(p).put(TAG, vuelta);
        soltar(p);
        Habitantes.olvidar(p.getUUID());
        BlockPos b = sitio(p, PurgatorioGenerador.SUELO);
        preparar(dest, b);
        persistente(p).putLong(TAG + "_t", dest.getGameTime());
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.teleportTo(dest, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, p.getYRot(), 0);
        dest.playSound(null, b, SoundEvents.BEACON_DEACTIVATE, SoundSource.AMBIENT, 1F, 0.5F);
        if (aviso != null) p.displayClientMessage(Component.translatable(aviso), true);
        Miscelanea.LOGGER.debug("Purgatorio: entra {} en {}", p.getGameProfile().getName(), b);
    }

    /**
     * Sale por una puerta de SALIDA (o por un agujero): a donde estaba. Si se coló por una pared, al hueco libre más
     * cercano (no otra vez dentro del bloque); si se cayó al vacío, a su cama o al punto de aparición del mundo.
     */
    public static void salir(ServerPlayer p) {
        if (!en(p.level())) return;
        CompoundTag datos = persistente(p);
        CompoundTag v = datos.getCompound(TAG);
        datos.remove(TAG);
        ServerLevel dest = null;
        Vec3 b = null;
        ResourceLocation rl = ResourceLocation.tryParse(v.getString("Dim"));
        if (rl != null) dest = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (dest == null || dest.dimension() == DIMENSION) dest = p.server.overworld();
        if (v.contains("X")) {
            BlockPos g = BlockPos.containing(v.getDouble("X"), v.getDouble("Y"), v.getDouble("Z"));
            BlockPos h = hueco(dest, g);
            if (h == null && !dest.dimensionType().hasCeiling()) h = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, g);
            if (h != null) b = Vec3.atBottomCenterOf(h);
        }
        if (b == null) {
            // a su cama (o ancla) si la tiene, como al reaparecer, pero sin gastar la carga del ancla
            ServerLevel cama = p.server.getLevel(p.getRespawnDimension());
            BlockPos rp = p.getRespawnPosition();
            if (cama != null && rp != null && !cama.dimension().equals(DIMENSION)) {
                var o = Player.findRespawnPositionAndUseSpawnBlock(cama, rp, p.getRespawnAngle(), p.isRespawnForced(), true);
                if (o.isPresent()) {
                    dest = cama;
                    b = o.get();
                }
            }
        }
        if (b == null) {
            dest = p.server.overworld();
            b = Vec3.atBottomCenterOf(dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dest.getSharedSpawnPos()));
        }
        soltar(p);
        Habitantes.olvidar(p.getUUID());
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.teleportTo(dest, b.x, b.y, b.z, p.getYRot(), p.getXRot());
        dest.playSound(null, b.x, b.y, b.z, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1F, 1.4F);
        p.displayClientMessage(Component.translatable("purgatorio.miscelanea.salida"), true);
        Miscelanea.LOGGER.info("Purgatorio: sale {} a {} {}", p.getGameProfile().getName(), dest.dimension().location(), BlockPos.containing(b));
    }

    /** Le baja de lo que lleve montado (también del tren de la montaña rusa) y le quita lo que lleve encima. */
    static void soltar(ServerPlayer p) {
        if (p.isPassenger()) com.miscelanea.montana.MontanaEventos.bajarSiOSi(p);
        if (p.isVehicle()) p.ejectPassengers();
        if (p.isSleeping()) p.stopSleeping();
    }

    /**
     * El hueco de dos de alto más cercano a g donde se pueda estar (sin chocar con nada, sin lava ni fuego), mejor con
     * suelo debajo: primero en la misma columna (arriba antes que abajo) y luego en anillos de hasta 3 bloques.
     */
    @Nullable
    public static BlockPos hueco(ServerLevel l, BlockPos g) {
        int[] dys = {0, 1, -1, 2, -2, 3, 4, -3, 5, 6, -4, 7, 8};
        BlockPos sinSuelo = null;
        BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
        for (int r = 0; r <= 3; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : dys) {
                        q.set(g.getX() + dx, g.getY() + dy, g.getZ() + dz);
                        if (q.getY() <= l.getMinBuildHeight() || q.getY() + 1 >= l.getMaxBuildHeight()) continue;
                        if (!cabe(l, q) || !cabe(l, q.above())) continue;
                        BlockPos abajo = q.below();
                        if (!l.getBlockState(abajo).getCollisionShape(l, abajo).isEmpty()) return q.immutable();
                        if (sinSuelo == null) sinSuelo = q.immutable();
                    }
                }
        return sinSuelo;
    }

    private static boolean cabe(ServerLevel l, BlockPos q) {
        var s = l.getBlockState(q);
        return s.getCollisionShape(l, q).isEmpty() && !l.getFluidState(q).is(net.minecraft.tags.FluidTags.LAVA)
                && !s.is(net.minecraft.tags.BlockTags.FIRE) && !s.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)
                && !s.is(net.minecraft.world.level.block.Blocks.SWEET_BERRY_BUSH) && !s.is(net.minecraft.world.level.block.Blocks.CACTUS)
                && !s.is(net.minecraft.world.level.block.Blocks.COBWEB);
    }

    /** Lo lleva a otra parte del Purgatorio (cuando le pilla un habitante). */
    public static void recolocar(ServerPlayer p) {
        ServerLevel l = nivel(p);
        if (l == null || !en(p.level())) return;
        BlockPos b = sitio(p, PurgatorioGenerador.SUELO);
        preparar(l, b);
        persistente(p).putLong(TAG + "_t", l.getGameTime());
        soltar(p);
        Habitantes.olvidar(p.getUUID());
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.teleportTo(l, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, p.getYRot(), 0);
    }

    /** Genera ya los chunks de alrededor del sitio (son muy baratos), para que el suelo esté al llegar. */
    public static void preparar(ServerLevel l, BlockPos b) {
        int cx = b.getX() >> 4, cz = b.getZ() >> 4;
        for (int x = -1; x <= 1; x++)
            for (int z = -1; z <= 1; z++) l.getChunk(cx + x, cz + z);
    }

    /** Un sitio al azar en una calle del Purgatorio (el cruce de un chunk siempre está libre). */
    static BlockPos sitio(ServerPlayer p, int y) {
        var r = p.getRandom();
        int cx = r.nextInt(RADIO * 2) - RADIO, cz = r.nextInt(RADIO * 2) - RADIO;
        return new BlockPos(cx * 16 + 8, y, cz * 16 + 8);
    }

    /** Para la prueba: el centro de un chunk de esa zona cerca del origen. */
    public static BlockPos buscar(PurgatorioGenerador.Zona z) {
        for (int r = 0; r < 40; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && PurgatorioGenerador.zona(cx, cz) == z && !PurgatorioGenerador.salida(cx, cz))
                        return new BlockPos(cx * 16 + 8, PurgatorioGenerador.SUELO, cz * 16 + 8);
        return new BlockPos(8, PurgatorioGenerador.SUELO, 8);
    }

    /**
     * Para la prueba: el rincón raro de esa zona más cercano al origen: {dónde ponerse, adónde mirar} (o null). Sala: la
     * silla de cara a la pared; recreativa: la única máquina encendida; feria: la caseta abierta con el peluche; hotel:
     * la habitación de la tele.
     */
    @Nullable
    public static BlockPos[] buscarRaro(PurgatorioGenerador.Zona z) {
        int f = PurgatorioGenerador.SUELO;
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int r = 0; r < 80; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r || PurgatorioGenerador.zona(cx, cz) != z) continue;
                    int k = PurgatorioGenerador.raro(cx, cz);
                    if (k < 0) continue;
                    int bx = cx * 16, bz = cz * 16;
                    switch (z) {
                        case SALA -> {
                            return new BlockPos[]{new BlockPos(bx + 12, f, bz + 6), new BlockPos(bx + 12, f, bz + 1)};
                        }
                        case RECREATIVA -> {
                            return new BlockPos[]{new BlockPos(bx + 11, f, bz + 6), new BlockPos(bx + 11, f + 1, bz + 11)};
                        }
                        case FERIA -> {
                            boolean norte = q[k][1] == 0;
                            return new BlockPos[]{new BlockPos(bx + q[k][0] + 3, f, bz + (norte ? 9 : 6)), new BlockPos(bx + q[k][0] + 3, f + 1, bz + (norte ? 5 : 10))};
                        }
                        case HOTEL -> {
                            int x0 = q[k][0], z0 = q[k][1], sx = x0 == 0 ? 1 : -1, dz = z0 == 0 ? 1 : -1;
                            int xa = x0 == 0 ? 1 : 14, zc = z0 == 0 ? 1 : 14;
                            return new BlockPos[]{new BlockPos(bx + xa + 2 * sx, f, bz + zc + 2 * dz), new BlockPos(bx + xa, f + 1, bz + zc + 3 * dz)};
                        }
                        default -> {
                            return null;
                        }
                    }
                }
        return null;
    }

    /** Para la prueba: el agujero del Mundo sin Texturas más cercano al origen (su centro, a la altura del suelo). */
    public static BlockPos buscarAgujero() {
        int[][] q = {{0, 0}, {9, 0}, {0, 9}, {9, 9}};
        for (int r = 0; r < 60; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r || PurgatorioGenerador.zona(cx, cz) != PurgatorioGenerador.Zona.SIN_TEXTURA) continue;
                    for (int k = 0; k < 4; k++)
                        if (!(k == 0 && PurgatorioGenerador.salida(cx, cz)) && PurgatorioGenerador.h(cx, cz, 80 + k) % 10 == 8)
                            return new BlockPos(cx * 16 + q[k][0] + 3, PurgatorioGenerador.SUELO, cz * 16 + q[k][1] + 3);
                }
        return null;
    }

    /** Para la prueba: el chunk con puerta de salida más cercano al origen. */
    public static BlockPos buscarSalida() {
        for (int r = 0; r < 60; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && PurgatorioGenerador.salida(cx, cz))
                        return new BlockPos(cx * 16 + 3, PurgatorioGenerador.SUELO, cz * 16 + 6);
        return new BlockPos(8, PurgatorioGenerador.SUELO, 8);
    }
}
