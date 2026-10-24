package com.miscelanea.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.EsconditePacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

/**
 * Jugadores escondidos en taquillas. Si un monstruo te ve entrar, irá a sacarte;
 * si no, te pierde la pista y no puede volver a fijarte como objetivo mientras sigas dentro.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Escondites {
    private record Escondite(BlockPos base, Direction mira, Set<UUID> testigos, int[] ticks) {}

    private static final Map<UUID, Escondite> ESCONDIDOS = new HashMap<>();
    /** También lo rellena el cliente (para la colisión de la taquilla). */
    public static final Set<UUID> OCULTOS = Collections.synchronizedSet(new HashSet<>());

    private Escondites() {}

    public static boolean escondido(Player p) {
        return OCULTOS.contains(p.getUUID());
    }

    public static void entrar(ServerPlayer p, BlockPos base, Direction mira) {
        for (Escondite e : ESCONDIDOS.values()) if (e.base().equals(base)) return; // ocupada
        ServerLevel level = p.serverLevel();

        // ¿Algún monstruo le está viendo meterse?
        Set<UUID> testigos = new HashSet<>();
        for (Mob m : level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(16), m -> m.getTarget() == p)) {
            if (m.hasLineOfSight(p) && m.distanceTo(p) < 10) testigos.add(m.getUUID());
            else m.setTarget(null);
        }

        ESCONDIDOS.put(p.getUUID(), new Escondite(base, mira, testigos, new int[]{0}));
        OCULTOS.add(p.getUUID());
        float yaw = mira.toYRot();
        p.connection.teleport(base.getX() + 0.5, base.getY(), base.getZ() + 0.5, yaw, 0.0F);
        p.setDeltaMovement(Vec3.ZERO);
        TaquillaBlock.puerta(level, base, true);
        level.playSound(null, base, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.7F);
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EsconditePacket(true, yaw));
    }

    public static void salir(ServerPlayer p) {
        Escondite e = ESCONDIDOS.remove(p.getUUID());
        OCULTOS.remove(p.getUUID());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new EsconditePacket(false, 0.0F));
        if (e == null) return;
        ServerLevel level = p.serverLevel();
        BlockPos fuera = e.base().relative(e.mira());
        p.connection.teleport(fuera.getX() + 0.5, fuera.getY(), fuera.getZ() + 0.5, p.getYRot(), p.getXRot());
        TaquillaBlock.puerta(level, e.base(), true);
        level.playSound(null, e.base(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.8F);
        cerrarLuego.put(e.base().immutable(), level.getGameTime() + 6);
    }

    private static final Map<BlockPos, Long> cerrarLuego = new HashMap<>();

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            Escondite e = ESCONDIDOS.get(p.getUUID());
            if (e == null) continue;
            ServerLevel level = p.serverLevel();
            if (!(level.getBlockState(e.base()).getBlock() instanceof TaquillaBlock) || !p.isAlive()) {
                salir(p);
                continue;
            }
            e.ticks()[0]++;
            if (e.ticks()[0] == 6) {
                TaquillaBlock.puerta(level, e.base(), false);
                level.playSound(null, e.base(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 0.8F);
            }
            // Quieto dentro
            Vec3 centro = new Vec3(e.base().getX() + 0.5, e.base().getY(), e.base().getZ() + 0.5);
            if (p.position().distanceToSqr(centro) > 0.04) p.connection.teleport(centro.x, centro.y, centro.z, p.getYRot(), p.getXRot());
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0.0F;

            // Un monstruo que le vio entrar llega a la taquilla: le saca de un tirón
            for (UUID id : e.testigos()) {
                if (level.getEntity(id) instanceof Mob m && m.isAlive() && m.distanceToSqr(centro) < 4.0) {
                    salir(p);
                    m.setTarget(p);
                    Vec3 tiron = m.position().subtract(p.position()).normalize().scale(0.6);
                    p.setDeltaMovement(tiron.x, 0.3, tiron.z);
                    p.hurtMarked = true;
                    level.playSound(null, e.base(), SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, SoundSource.HOSTILE, 1.0F, 0.9F);
                    break;
                }
            }
        }
        // Cerrar puertas de taquillas vacías
        if (!cerrarLuego.isEmpty()) {
            var server = event.getServer();
            cerrarLuego.entrySet().removeIf(en -> {
                for (ServerLevel l : server.getAllLevels()) {
                    if (l.getGameTime() >= en.getValue()) {
                        if (l.getBlockState(en.getKey()).getBlock() instanceof TaquillaBlock) {
                            TaquillaBlock.puerta(l, en.getKey(), false);
                        }
                        return true;
                    }
                }
                return false;
            });
        }
    }

    /** Los monstruos no pueden fijar como objetivo a alguien escondido (salvo los que le vieron entrar). */
    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity nuevo = event.getNewTarget();
        if (!(nuevo instanceof Player p) || !escondido(p) || p.level().isClientSide) return;
        Escondite e = ESCONDIDOS.get(p.getUUID());
        if (e != null && e.testigos().contains(event.getEntity().getUUID())) return;
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ESCONDIDOS.remove(event.getEntity().getUUID());
        OCULTOS.remove(event.getEntity().getUUID());
    }
}
