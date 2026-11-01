package com.miscelanea.ocarina;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Efectos de las canciones: se aplican al acabar de sonar la melodía. El paso del tiempo se ve acelerado en el cielo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Ocarina {
    private static final String CLAVE = "MiscelaneaOcarina";
    private static final int RETARDO = 70;           // lo que dura la melodía grabada
    private static final List<Object[]> PENDIENTES = new ArrayList<>();   // {jugador, cancion, ticks}
    private static final Map<ServerLevel, Long> AVANCE = new HashMap<>();  // nivel → dayTime objetivo

    private Ocarina() {}

    static void tocada(ServerPlayer p, Cancion c) {
        if (!OcarinaItem.tocando(p) && !(p.getMainHandItem().getItem() instanceof OcarinaItem) && !(p.getOffhandItem().getItem() instanceof OcarinaItem))
            return;
        CompoundTag d = p.getPersistentData().getCompound(CLAVE);
        long ahora = p.level().getGameTime();
        long listo = d.getLong(c.id);
        if (ahora < listo) {
            p.displayClientMessage(Component.translatable("msg.miscelanea.ocarina.espera", (listo - ahora + 19) / 20).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        String fallo = motivoFallo(p, c);
        if (fallo != null) {
            p.displayClientMessage(Component.translatable(fallo).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        d.putLong(c.id, ahora + c.espera);
        p.getPersistentData().put(CLAVE, d);
        p.level().playSound(null, p.getX(), p.getEyeY(), p.getZ(), ModSounds.CANCION[c.ordinal()].get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        PENDIENTES.add(new Object[]{p, c, RETARDO});
    }

    private static String motivoFallo(ServerPlayer p, Cancion c) {
        ServerLevel l = p.serverLevel();
        return switch (c) {
            case SOL, LUNA -> l.dimensionType().hasFixedTime() ? "msg.miscelanea.ocarina.sin_cielo" : null;
            case TORMENTA, DESPEJAR -> l.dimension() != Level.OVERWORLD ? "msg.miscelanea.ocarina.sin_cielo" : null;
            default -> null;
        };
    }

    private static void efecto(ServerPlayer p, Cancion c) {
        ServerLevel l = p.serverLevel();
        p.displayClientMessage(Component.translatable("msg.miscelanea.ocarina." + c.id).withStyle(ChatFormatting.GOLD), true);
        l.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.9, 0.6, 0.05);
        switch (c) {
            case SOL -> avanzarHasta(l, 23500 + 1500);       // amanecer → mañana
            case LUNA -> avanzarHasta(l, 13000);
            case TORMENTA -> l.setWeatherParameters(0, 6000, true, true);
            case DESPEJAR -> l.setWeatherParameters(12000, 0, false, false);
            case REGRESO -> regresar(p);
        }
    }

    /** Hace correr el tiempo deprisa hasta la próxima vez que la hora del día valga {@code hora} (mod 24000). */
    private static void avanzarHasta(ServerLevel l, int hora) {
        long t = l.getDayTime();
        long base = t - Math.floorMod(t, 24000L);
        long objetivo = base + Math.floorMod(hora, 24000);
        if (objetivo <= t + 200) objetivo += 24000;
        AVANCE.put(l, objetivo);
    }

    private static void regresar(ServerPlayer p) {
        ServerLevel destino = p.server.getLevel(p.getRespawnDimension());
        BlockPos cama = p.getRespawnPosition();
        Vec3 donde = null;
        if (destino != null && cama != null) {
            Optional<Vec3> o = Player.findRespawnPositionAndUseSpawnBlock(destino, cama, p.getRespawnAngle(), p.isRespawnForced(), true);
            if (o.isPresent()) donde = o.get();
        }
        if (donde == null) {
            destino = p.server.overworld();
            BlockPos s = destino.getSharedSpawnPos();
            BlockPos arriba = destino.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, s);
            donde = Vec3.atBottomCenterOf(arriba);
        }
        p.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, p.getX(), p.getY() + 1, p.getZ(), 60, 0.4, 0.9, 0.4, 0.1);
        p.teleportTo(destino, donde.x, donde.y, donde.z, p.getYRot(), p.getXRot());
        p.fallDistance = 0;
        destino.sendParticles(ParticleTypes.REVERSE_PORTAL, donde.x, donde.y + 1, donde.z, 60, 0.4, 0.9, 0.4, 0.1);
        destino.playSound(null, donde.x, donde.y, donde.z, ModSounds.OCARINA_EXITO.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        for (Iterator<Object[]> it = PENDIENTES.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            int t = (int) o[2] - 1;
            o[2] = t;
            ServerPlayer p = (ServerPlayer) o[0];
            if (p.isRemoved() || !p.isAlive()) {
                it.remove();
                continue;
            }
            if (t <= 0) {
                it.remove();
                efecto(p, (Cancion) o[1]);
            }
        }
        for (Iterator<Map.Entry<ServerLevel, Long>> it = AVANCE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            ServerLevel l = en.getKey();
            long t = l.getDayTime(), obj = en.getValue();
            if (t >= obj) {
                it.remove();
                continue;
            }
            l.setDayTime(Math.min(obj, t + 240));
        }
    }
}
