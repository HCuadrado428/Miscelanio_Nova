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
    /** Probabilidad de colarse cada vez que hace daño estar dentro de un bloque (cada medio segundo). */
    private static final float NOCLIP = 0.12F;
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
        if (e.getSource().is(DamageTypes.IN_WALL) && p.getRandom().nextFloat() < NOCLIP) {
            e.setCanceled(true);
            entrar(p, "purgatorio.miscelanea.noclip");
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.isSpectator()) return;
        Level l = p.level();
        if (en(l)) {
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
                BlockPos b = recien ? new BlockPos(p.getBlockX(), PurgatorioGenerador.SUELO, p.getBlockZ()) : sitio(p, PurgatorioGenerador.SUELO + 18);
                preparar(l2, b);
                persistente(p).putLong(TAG + "_t", ahora);
                p.teleportTo(b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
                p.setDeltaMovement(0, 0, 0);
                p.fallDistance = 0;
            }
            return;
        }
        if (l.dimension() == Level.OVERWORLD && p.getY() < l.getMinBuildHeight() - 40 && !p.getAbilities().flying)
            entrar(p, "purgatorio.miscelanea.vacio");
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
        if (p.isPassenger()) p.stopRiding();
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

    /** Sale por una puerta de SALIDA: a donde estaba (o a su cama / el punto de aparición). */
    public static void salir(ServerPlayer p) {
        if (!en(p.level())) return;
        CompoundTag datos = persistente(p);
        CompoundTag v = datos.getCompound(TAG);
        datos.remove(TAG);
        ServerLevel dest = null;
        BlockPos b = null;
        ResourceLocation rl = ResourceLocation.tryParse(v.getString("Dim"));
        if (rl != null) dest = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
        if (dest == null || dest.dimension() == DIMENSION) dest = p.server.overworld();
        if (v.contains("X")) {
            BlockPos g = BlockPos.containing(v.getDouble("X"), v.getDouble("Y"), v.getDouble("Z"));
            // si se coló por una pared, sale encima de ella (no otra vez dentro)
            b = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, g);
            if (!dest.dimensionType().hasCeiling() || b.getY() <= g.getY() + 2) {
                if (dest.getBlockState(g).isAir() && dest.getBlockState(g.above()).isAir()) b = g;
            } else b = g.above();
        }
        if (b == null) {
            BlockPos s = p.getRespawnPosition() != null && p.getRespawnDimension() == dest.dimension() ? p.getRespawnPosition() : dest.getSharedSpawnPos();
            b = dest.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s);
        }
        p.fallDistance = 0;
        p.setDeltaMovement(0, 0, 0);
        p.teleportTo(dest, b.getX() + 0.5, b.getY(), b.getZ() + 0.5, p.getYRot(), p.getXRot());
        dest.playSound(null, b, SoundEvents.BEACON_ACTIVATE, SoundSource.AMBIENT, 1F, 1.4F);
        p.displayClientMessage(Component.translatable("purgatorio.miscelanea.salida"), true);
        Miscelanea.LOGGER.info("Purgatorio: sale {} a {} {}", p.getGameProfile().getName(), dest.dimension().location(), b);
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
