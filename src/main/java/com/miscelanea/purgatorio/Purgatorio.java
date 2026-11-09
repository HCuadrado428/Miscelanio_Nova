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
            // el pasillo que vuelve al mismo sitio: pasada la raya, 20 bloques atrás sin que se note (relativo: no se
            // pierde ni la velocidad ni hacia dónde se mira)
            int dz = PurgatorioPlano.bucle(p.getX(), p.getY(), p.getZ());
            if (dz != 0 && !p.isPassenger())
                p.connection.teleport(p.getX(), p.getY(), p.getZ() + dz, p.getYRot(), p.getXRot(), java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
            ascensor(p);
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
                // (a otra parte, en el suelo de algún piso: si se dejara caer desde arriba, en las zonas con techo se
                // quedaría encima del tejado)
                BlockPos b = recien ? new BlockPos(p.getBlockX(), PurgatorioPlano.base(persistente(p).getInt(TAG + "_n")), p.getBlockZ()) : sitio(p);
                preparar(l2, b);
                llegado(p, ahora, b);
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
        VIAJES.remove(e.getEntity().getUUID());
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
        BlockPos b = sitio(p);
        preparar(dest, b);
        llegado(p, dest.getGameTime(), b);
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

    // ------------------------------------------------------------------ ascensor
    private record Viaje(long t, int dy, BlockPos boton) {}

    private static final java.util.Map<java.util.UUID, Viaje> VIAJES = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Han apretado la botonera de un ascensor: se oyen las puertas y el motor y, en un segundo y medio, el ascensor
     * llega a otro piso (al azar, nunca el mismo). La cabina es igual en todos los pisos y está en el mismo sitio, así
     * que se le mueve solo en altura y no se nota el salto: al salir, otro piso y otra zona.
     */
    public static void llamarAscensor(ServerPlayer p, BlockPos boton) {
        if (!en(p.level()) || VIAJES.containsKey(p.getUUID())) return;
        int n = PurgatorioPlano.nivel(boton.getY());
        int m = (n + 1 + p.getRandom().nextInt(PurgatorioPlano.NIVELES - 1)) % PurgatorioPlano.NIVELES;
        VIAJES.put(p.getUUID(), new Viaje(p.serverLevel().getGameTime() + 30, PurgatorioPlano.base(m) - PurgatorioPlano.base(n), boton.immutable()));
        p.serverLevel().playSound(null, boton, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 0.6F);
        p.serverLevel().playSound(null, boton, SoundEvents.MINECART_INSIDE, SoundSource.BLOCKS, 0.25F, 0.5F);
    }

    /** Cada tic: si el ascensor ha llegado y sigue dentro de la cabina, al otro piso (ding). */
    private static void ascensor(ServerPlayer p) {
        Viaje v = VIAJES.get(p.getUUID());
        if (v == null || p.serverLevel().getGameTime() < v.t()) return;
        VIAJES.remove(p.getUUID());
        // dentro de la cabina: a menos de 2,5 bloques de su centro (la botonera está a un lado) y en el mismo piso
        double cx = v.boton().getX() - 0.5, cz = v.boton().getZ();
        if (Math.abs(p.getX() - cx) > 2.2 || Math.abs(p.getZ() - cz) > 2.2 || Math.abs(p.getY() - v.boton().getY()) > 2.5) return;
        ServerLevel l = p.serverLevel();
        preparar(l, p.blockPosition().offset(0, v.dy(), 0));
        soltar(p);
        p.fallDistance = 0;
        p.connection.teleport(p.getX(), p.getY() + v.dy(), p.getZ(), p.getYRot(), p.getXRot(), java.util.EnumSet.allOf(net.minecraft.world.entity.RelativeMovement.class));
        llegado(p, l.getGameTime(), p.blockPosition());
        BlockPos b = v.boton().offset(0, v.dy(), 0);
        l.playSound(null, b, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.7F, 1.5F);
        l.playSound(null, b, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.4F, 0.7F);
        int piso = PurgatorioPlano.nivel(b.getY());
        p.displayClientMessage(Component.translatable("purgatorio.miscelanea.planta", piso == 0 ? "0" : "-" + piso), true);
    }

    /** Para la prueba: ¿va en el ascensor? */
    public static boolean enAscensor(ServerPlayer p) {
        return VIAJES.containsKey(p.getUUID());
    }

    /** Lo lleva a otra parte del Purgatorio (cuando le pilla un habitante). */
    public static void recolocar(ServerPlayer p) {
        ServerLevel l = nivel(p);
        if (l == null || !en(p.level())) return;
        BlockPos b = sitio(p);
        preparar(l, b);
        llegado(p, l.getGameTime(), b);
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

    /** Apunta cuándo y a qué piso ha llegado (si se cae nada más llegar, se le devuelve ahí). */
    static void llegado(Player p, long t, BlockPos b) {
        persistente(p).putLong(TAG + "_t", t);
        persistente(p).putInt(TAG + "_n", PurgatorioPlano.nivel(b.getY()));
    }

    /** ¿Es un chunk normal en ese piso (sin sala grande, ni escalera, ni ascensor, ni pilar)? */
    static boolean normal(int n, int cx, int cz) {
        return PurgatorioGenerador.entero(n, cx, cz);
    }

    /**
     * Un sitio al azar en una calle del Purgatorio (el cruce de un chunk normal siempre está libre), en el piso de arriba
     * más o menos la mitad de las veces y si no en uno de los de abajo.
     */
    static BlockPos sitio(ServerPlayer p) {
        var r = p.getRandom();
        for (int i = 0; ; i++) {
            int cx = r.nextInt(RADIO * 2) - RADIO, cz = r.nextInt(RADIO * 2) - RADIO;
            int n = r.nextInt(2) == 0 ? 0 : r.nextInt(PurgatorioPlano.NIVELES);
            if (i < 30 && !normal(n, cx, cz)) continue;
            return new BlockPos(cx * 16 + 8, PurgatorioPlano.base(n), cz * 16 + 8);
        }
    }

    /** Para la prueba: el centro de un chunk normal de esa zona cerca del origen (en el piso de más arriba que la tenga). */
    public static BlockPos buscar(PurgatorioGenerador.Zona z) {
        for (int n = 0; n < PurgatorioPlano.NIVELES; n++) {
            BlockPos b = buscar(z, n, 40);
            if (b != null) return b;
        }
        return new BlockPos(8, PurgatorioGenerador.SUELO, 8);
    }

    @Nullable
    public static BlockPos buscar(PurgatorioGenerador.Zona z, int n, int radio) {
        for (int r = 0; r < radio; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && PurgatorioPlano.zona(n, cx, cz) == z && normal(n, cx, cz) && !PurgatorioPlano.salida(n, cx, cz))
                        return new BlockPos(cx * 16 + 8, PurgatorioPlano.base(n), cz * 16 + 8);
        return null;
    }

    /** Para la prueba: la sala grande de ese tipo más cercana al origen (en cualquier piso), o null. */
    @Nullable
    public static PurgatorioPlano.Grande buscarGrande(PurgatorioPlano.Tipo t) {
        PurgatorioPlano.Grande mejor = null;
        long dm = Long.MAX_VALUE;
        for (int n = 0; n < PurgatorioPlano.NIVELES; n++)
            for (int sx = -8; sx <= 8; sx++)
                for (int sz = -8; sz <= 8; sz++) {
                    var g = PurgatorioPlano.grandeDe(n, sx, sz);
                    if (g == null || g.tipo() != t) continue;
                    long d = (long) g.cx0() * g.cx0() + (long) g.cz0() * g.cz0() + n * 40L;
                    if (d < dm) {
                        dm = d;
                        mejor = g;
                    }
                }
        return mejor;
    }

    /** Para la prueba: el chunk (x, z) con escalera del piso n al n + 1 más cercano al origen. */
    @Nullable
    public static int[] buscarEscalera(int n) {
        for (int r = 0; r < 60; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && PurgatorioPlano.escalera(n, cx, cz)
                            && PurgatorioPlano.grande(n, cx, cz - 1) == null && PurgatorioPlano.grande(n + 1, cx, cz + 1) == null)
                        return new int[]{cx, cz};
        return null;
    }

    /** Para la prueba: el chunk (x, z) con ascensor más cercano al origen. */
    @Nullable
    public static int[] buscarAscensor() {
        for (int r = 0; r < 60; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++)
                    if (Math.max(Math.abs(cx), Math.abs(cz)) == r && PurgatorioPlano.ascensor(cx, cz)) return new int[]{cx, cz};
        return null;
    }

    /**
     * Para la prueba: el rincón raro de esa zona más cercano al origen: {dónde ponerse, adónde mirar} (o null). Sala: la
     * silla de cara a la pared; recreativa: la única máquina encendida; feria: la caseta abierta con el peluche; hotel:
     * la habitación de la tele.
     */
    @Nullable
    public static BlockPos[] buscarRaro(PurgatorioGenerador.Zona z) {
        int[][] q = PurgatorioPlano.PARCELAS;
        for (int n = 0; n < PurgatorioPlano.NIVELES; n++) {
            int f = PurgatorioPlano.base(n);
            for (int r = 0; r < 80; r++)
                for (int cx = -r; cx <= r; cx++)
                    for (int cz = -r; cz <= r; cz++) {
                        if (Math.max(Math.abs(cx), Math.abs(cz)) != r || PurgatorioPlano.zona(n, cx, cz) != z) continue;
                        int k = PurgatorioGenerador.raro(n, cx, cz);
                        if (k < 0) continue;
                        int bx = cx * 16, bz = cz * 16;
                        switch (z) {
                            case SALA -> {
                                return new BlockPos[]{new BlockPos(bx + 12, f, bz + 4), new BlockPos(bx + 12, f, bz + 1)};
                            }
                            case RECREATIVA -> {
                                return new BlockPos[]{new BlockPos(bx + 11, f, bz + 6), new BlockPos(bx + 11, f + 1, bz + 11)};
                            }
                            case FERIA -> {
                                boolean norte = q[k][1] == 0;
                                return new BlockPos[]{new BlockPos(bx + q[k][0] + 3, f, bz + (norte ? 9 : 6)), new BlockPos(bx + q[k][0] + 3, f + 1, bz + (norte ? 5 : 10))};
                            }
                            case HOTEL -> {
                                int x0 = q[k][0], z0 = q[k][1], dz = z0 == 0 ? 1 : -1;
                                int xa = x0 == 0 ? 1 : 14, zc = z0 == 0 ? 1 : 14;
                                // tumbado en la cama (de pie encima), mirando la tele de los pies
                                return new BlockPos[]{new BlockPos(bx + xa, f + 1, bz + zc), new BlockPos(bx + xa, f + 1, bz + zc + 3 * dz)};
                            }
                            default -> {
                                return null;
                            }
                        }
                    }
        }
        return null;
    }

    /** Para la prueba: el agujero del Mundo sin Texturas más cercano al origen (su centro, a la altura del suelo). */
    public static BlockPos buscarAgujero() {
        int[][] q = PurgatorioPlano.PARCELAS;
        for (int r = 0; r < 60; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r) continue;
                    for (int k = 0; k < 4; k++)
                        if (PurgatorioPlano.agujero(cx, cz, k))
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
