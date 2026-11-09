package com.miscelanea.dream;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;

/**
 * El viaje del anillo de medianoche: lleva a la dimensión de «The Midnight» (ConfigServidor.anilloDimension) y, desde
 * allí, de vuelta al sitio exacto del que se salió (o al punto de aparición del mundo si no hay ninguno guardado).
 * El punto de salida va en los datos persistentes del jugador (sobreviven a morir y a reiniciar).
 */
public final class Medianoche {
    private Medianoche() {}

    private static final String TAG = "miscelanea_anillo";
    /** Radio (bloques) alrededor del punto pedido en el que se busca dónde aterrizar. */
    private static final int RADIO_BUSQUEDA = 24;

    private static CompoundTag persistente(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    @Nullable
    public static ResourceKey<Level> dimension() {
        ResourceLocation rl = ResourceLocation.tryParse(ConfigServidor.anilloDimension().trim());
        return rl == null ? null : ResourceKey.create(Registries.DIMENSION, rl);
    }

    /** Nombres que ha tenido la dimensión de The Midnight (en la 0.6.x es midnight:the_midnight). */
    private static final String[] CONOCIDAS = {"midnight:the_midnight", "midnight:midnight"};

    /**
     * La dimensión de la Medianoche que hay de verdad en este servidor: la de la config si existe; si no (la config
     * antigua decía midnight:midnight, que no existe), cualquiera de los nombres conocidos de The Midnight.
     */
    @Nullable
    static ServerLevel nivelMedianoche(net.minecraft.server.MinecraftServer srv) {
        ResourceKey<Level> k = dimension();
        ServerLevel l = k == null ? null : srv.getLevel(k);
        if (l != null) return l;
        for (String n : CONOCIDAS) {
            l = srv.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(n)));
            if (l != null) return l;
        }
        return null;
    }

    /** ¿Está ya en la Medianoche? (para la descripción y el cliente: la de la config o un nombre conocido). */
    public static boolean enMedianoche(Level l) {
        ResourceKey<Level> k = dimension();
        if (k != null && l.dimension() == k) return true;
        String d = l.dimension().location().toString();
        for (String n : CONOCIDAS) if (n.equals(d)) return true;
        return false;
    }

    /** El anillo ha terminado de cargar: viaja (en el siguiente tick, fuera del tick del propio jugador). */
    public static void viajar(ServerPlayer p) {
        com.miscelanea.tortura.qte.Qte.despues(1, () -> {
            if (p.isAlive() && !p.hasDisconnected()) hacerViaje(p);
        });
    }

    private static void hacerViaje(ServerPlayer p) {
        ServerLevel medianoche = nivelMedianoche(p.server);
        if (medianoche == null) {
            p.displayClientMessage(Component.translatable("miscelanea.anillo.no_instalado"), true);
            fallo(p);
            return;
        }
        CompoundTag datos = persistente(p);
        boolean ida = p.level() != medianoche;
        ServerLevel destino;
        BlockPos llegada;
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (ida) {
            destino = medianoche;
            llegada = buscar(destino, p.blockPosition());
            if (llegada == null) {
                p.displayClientMessage(Component.translatable("miscelanea.anillo.sin_sitio"), true);
                fallo(p);
                return;
            }
            CompoundTag salida = new CompoundTag();
            salida.putString("Dim", p.level().dimension().location().toString());
            salida.putDouble("X", p.getX());
            salida.putDouble("Y", p.getY());
            salida.putDouble("Z", p.getZ());
            salida.putFloat("Yaw", yaw);
            salida.putFloat("Pitch", pitch);
            datos.put(TAG, salida);
            com.miscelanea.logros.LogrosMiscelanea.dar(p, "medianoche");
        } else {
            destino = null;
            llegada = null;
            if (datos.contains(TAG)) {
                CompoundTag s = datos.getCompound(TAG);
                ResourceLocation rl = ResourceLocation.tryParse(s.getString("Dim"));
                ServerLevel l = rl == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
                if (l != null && l != medianoche) {
                    BlockPos guardada = BlockPos.containing(s.getDouble("X"), s.getDouble("Y") + 0.01, s.getDouble("Z"));
                    destino = l;
                    llegada = seguro(l, guardada) ? guardada : buscar(l, guardada);
                    yaw = s.getFloat("Yaw");
                    pitch = s.getFloat("Pitch");
                }
            }
            if (llegada == null) {
                // sin sitio guardado (o ya no es seguro): al punto de aparición del mundo
                destino = p.server.overworld();
                BlockPos spawn = destino.getSharedSpawnPos();
                llegada = buscar(destino, spawn);
                if (llegada == null) llegada = destino.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn);
            }
            datos.remove(TAG);
        }
        ServerLevel origen = p.serverLevel();
        origen.playSound(null, p.getX(), p.getY() + 1, p.getZ(), DreamRegistro.ANILLO_SALTO.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
        origen.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.35, 0.8, 0.35, 0.15);
        if (p.isPassenger()) p.stopRiding();
        p.stopUsingItem();
        p.fallDistance = 0;
        p.teleportTo(destino, llegada.getX() + 0.5, llegada.getY(), llegada.getZ() + 0.5, yaw, pitch);
        p.setDeltaMovement(0, 0, 0);
        destino.playSound(null, p.getX(), p.getY() + 1, p.getZ(), DreamRegistro.ANILLO_SALTO.get(), SoundSource.PLAYERS, 1.0F, 0.7F);
        destino.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.35, 0.8, 0.35, 0.05);
        p.getCooldowns().addCooldown(DreamRegistro.ANILLO.get(), ConfigServidor.anilloEnfriamientoTicks());
        DreamRed.a(p, new AnilloPacket(ida));
        p.displayClientMessage(Component.translatable(ida ? "miscelanea.anillo.ida" : "miscelanea.anillo.vuelta"), true);
        Miscelanea.LOGGER.debug("Anillo de medianoche: {} viaja a {} {}", p.getGameProfile().getName(), destino.dimension().location(), llegada);
    }

    private static void fallo(ServerPlayer p) {
        p.level().playSound(null, p.getX(), p.getY(), p.getZ(), net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.4F);
        p.getCooldowns().addCooldown(DreamRegistro.ANILLO.get(), 40);
    }

    // ------------------------------------------------------------------ dónde aterrizar

    /** Un sitio seguro cerca de esa x/z (primero la misma columna, luego en anillos cada vez más anchos), o null. */
    @Nullable
    static BlockPos buscar(ServerLevel l, BlockPos cerca) {
        int x0 = cerca.getX(), z0 = cerca.getZ();
        if (!l.getWorldBorder().isWithinBounds(cerca)) {
            x0 = Mth.floor(Mth.clamp(x0, l.getWorldBorder().getMinX() + 2, l.getWorldBorder().getMaxX() - 2));
            z0 = Mth.floor(Mth.clamp(z0, l.getWorldBorder().getMinZ() + 2, l.getWorldBorder().getMaxZ() - 2));
        }
        for (int r = 0; r <= RADIO_BUSQUEDA; r += 4) {
            for (int dx = -r; dx <= r; dx += 4)
                for (int dz = -r; dz <= r; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue; // solo el borde del anillo
                    BlockPos p = columna(l, x0 + dx, z0 + dz, cerca.getY());
                    if (p != null) return p;
                }
        }
        return null;
    }

    @Nullable
    private static BlockPos columna(ServerLevel l, int x, int z, int yPref) {
        l.getChunk(x >> 4, z >> 4); // genera el trozo si hace falta
        int min = l.getMinBuildHeight() + 1;
        int max = l.getMinBuildHeight() + l.dimensionType().logicalHeight() - 2;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        if (!l.dimensionType().hasCeiling()) {
            // al aire libre: la superficie primero y luego hacia abajo (cuevas)
            int top = Math.min(max, l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z));
            for (int y = top; y >= min; y--)
                if (seguro(l, m.set(x, y, z))) return m.immutable();
            return null;
        }
        // con techo (como el Nether): lo más cerca posible de la altura de partida, arriba y abajo
        int y0 = Mth.clamp(yPref, min, max);
        for (int d = 0; d <= max - min; d++) {
            if (y0 + d <= max && seguro(l, m.set(x, y0 + d, z))) return m.immutable();
            if (d > 0 && y0 - d >= min && seguro(l, m.set(x, y0 - d, z))) return m.immutable();
        }
        return null;
    }

    /** Suelo firme debajo y dos bloques libres (sin líquidos, fuego, telarañas...) donde van los pies y la cabeza. */
    static boolean seguro(ServerLevel l, BlockPos pies) {
        if (pies.getY() <= l.getMinBuildHeight() || pies.getY() + 1 >= l.getMaxBuildHeight()) return false;
        BlockPos abajo = pies.below();
        BlockState suelo = l.getBlockState(abajo);
        if (!suelo.isFaceSturdy(l, abajo, Direction.UP)) return false;
        if (suelo.is(Blocks.MAGMA_BLOCK) || suelo.is(BlockTags.CAMPFIRES) || suelo.is(BlockTags.FIRE) || !l.getFluidState(abajo).isEmpty())
            return false;
        return libre(l, pies) && libre(l, pies.above());
    }

    private static boolean libre(ServerLevel l, BlockPos p) {
        BlockState s = l.getBlockState(p);
        if (!s.getCollisionShape(l, p).isEmpty() || !l.getFluidState(p).isEmpty()) return false;
        return !s.is(BlockTags.FIRE) && !s.is(Blocks.COBWEB) && !s.is(Blocks.SWEET_BERRY_BUSH) && !s.is(Blocks.WITHER_ROSE)
                && !s.is(Blocks.POWDER_SNOW) && !s.is(Blocks.END_PORTAL) && !s.is(Blocks.NETHER_PORTAL);
    }
}
