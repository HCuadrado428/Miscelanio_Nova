package com.miscelanea.fiesta;

import com.miscelanea.Miscelanea;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * La dimensión de la fiesta (el juego de tablero con minijuegos). Hito 1: entrar y salir.
 * <ul>
 *   <li>Al entrar se guarda todo lo del jugador (dónde estaba, el inventario, el modo de juego, la vida y el hambre) en
 *       sus datos propios (sobreviven a reiniciar el servidor) y se le deja con las manos vacías, en modo aventura,
 *       en el lobby.</li>
 *   <li>Al salir (con el comando, al desconectarse o al volver a conectar estando dentro) se le devuelve todo tal cual.</li>
 *   <li>Dentro no se hace daño, no hay hambre y si se cae por el borde vuelve al lobby.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Fiesta {
    private Fiesta() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Miscelanea.MOD_ID, "party"));
    private static final String CLAVE = "miscelanea_fiesta";

    public static boolean en(Level l) {
        return l.dimension() == DIMENSION;
    }

    // ------------------------------------------------------------------ guardar y devolver al jugador
    private static CompoundTag datos(Player p) {
        CompoundTag raiz = p.getPersistentData();
        if (!raiz.contains(Player.PERSISTED_NBT_TAG)) raiz.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return raiz.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** ¿Tiene guardado lo que llevaba antes de entrar? */
    public static boolean tieneGuardado(Player p) {
        return datos(p).contains(CLAVE, Tag.TAG_COMPOUND);
    }

    private static void guardar(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.putString("dim", p.level().dimension().location().toString());
        d.putDouble("x", p.getX());
        d.putDouble("y", p.getY());
        d.putDouble("z", p.getZ());
        d.putFloat("yaw", p.getYRot());
        d.putFloat("pitch", p.getXRot());
        d.putInt("modo", p.gameMode.getGameModeForPlayer().getId());
        d.putFloat("vida", p.getHealth());
        d.putInt("hambre", p.getFoodData().getFoodLevel());
        d.putFloat("saturacion", p.getFoodData().getSaturationLevel());
        d.put("inventario", p.getInventory().save(new ListTag()));
        datos(p).put(CLAVE, d);
    }

    // ------------------------------------------------------------------ entrar y salir
    /** Lo lleva al lobby. Devuelve false si no se puede (apagada, ya está dentro o no existe la dimensión). */
    public static boolean entrar(ServerPlayer p) {
        if (!FiestaConfig.activada() || en(p.level())) return false;
        ServerLevel l = p.server.getLevel(DIMENSION);
        if (l == null) {
            Miscelanea.LOGGER.warn("[Fiesta] no existe la dimensión {}", DIMENSION.location());
            return false;
        }
        Lobby.preparar(l);
        guardar(p);
        p.unRide();
        p.getInventory().clearContent();
        p.removeAllEffects();
        p.setHealth(p.getMaxHealth());
        p.getFoodData().setFoodLevel(20);
        p.setGameMode(GameType.ADVENTURE);
        BlockPos a = Lobby.aparicion();
        p.teleportTo(l, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 0, 0);
        p.displayClientMessage(Component.translatable("fiesta.miscelanea.entras"), true);
        Miscelanea.LOGGER.info("[Fiesta] entra {}", p.getGameProfile().getName());
        return true;
    }

    /** Lo devuelve a donde estaba, con todo lo suyo. Devuelve false si no estaba dentro ni tenía nada guardado. */
    public static boolean salir(ServerPlayer p) {
        boolean dentro = en(p.level());
        CompoundTag d = tieneGuardado(p) ? datos(p).getCompound(CLAVE) : null;
        if (!dentro && d == null) return false;
        p.unRide();
        if (d != null) {
            ResourceKey<Level> clave = ResourceKey.create(Registries.DIMENSION, new ResourceLocation(d.getString("dim")));
            ServerLevel destino = p.server.getLevel(clave);
            if (destino == null) destino = p.server.overworld();
            // primero lo suyo (así, si algo falla al teletransportar, ya lo tiene)
            p.getInventory().clearContent();
            p.getInventory().load(d.getList("inventario", Tag.TAG_COMPOUND));
            p.setGameMode(GameType.byId(d.getInt("modo")));
            p.setHealth(Math.max(1F, Math.min(p.getMaxHealth(), d.getFloat("vida"))));
            p.getFoodData().setFoodLevel(d.getInt("hambre"));
            p.getFoodData().setSaturation(d.getFloat("saturacion"));
            p.inventoryMenu.broadcastChanges();
            p.teleportTo(destino, d.getDouble("x"), d.getDouble("y"), d.getDouble("z"), d.getFloat("yaw"), d.getFloat("pitch"));
            datos(p).remove(CLAVE);
        } else {
            // dentro sin nada guardado (no debería pasar): al sitio donde nace
            ServerLevel o = p.server.overworld();
            BlockPos s = o.getSharedSpawnPos();
            p.setGameMode(p.server.getDefaultGameType());
            p.teleportTo(o, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
        }
        p.displayClientMessage(Component.translatable("fiesta.miscelanea.sales"), true);
        Miscelanea.LOGGER.info("[Fiesta] sale {}", p.getGameProfile().getName());
        return true;
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p) || p.tickCount % 10 != 0) return;
        if (!en(p.level())) return;
        if (!FiestaConfig.activada()) {
            salir(p);
            return;
        }
        if (p.getFoodData().getFoodLevel() < 20) p.getFoodData().setFoodLevel(20);
        if (p.getY() < Lobby.SUELO - 20) {
            BlockPos a = Lobby.aparicion();
            p.teleportTo(p.serverLevel(), a.getX() + 0.5, a.getY(), a.getZ() + 0.5, p.getYRot(), 0);
            p.fallDistance = 0;
        }
    }

    /** Dentro nadie se hace daño. */
    @SubscribeEvent
    public static void sinAtaques(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && en(p.level())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void sinDanio(LivingHurtEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && en(p.level())) e.setCanceled(true);
    }

    /** Si se desconecta dentro, al volver lo suyo vuelve con él. */
    @SubscribeEvent
    public static void alConectar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (en(p.level()) || tieneGuardado(p))) salir(p);
    }

    /** Si muere de algún modo raro estando dentro, se le devuelve lo suyo al reaparecer. */
    @SubscribeEvent
    public static void alReaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && tieneGuardado(p) && !en(p.level())) salir(p);
    }
}
