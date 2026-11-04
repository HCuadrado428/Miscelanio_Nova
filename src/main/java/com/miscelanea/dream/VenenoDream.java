package com.miscelanea.dream;

import com.miscelanea.ConfigServidor;
import com.miscelanea.Miscelanea;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * Extraer el veneno de Dream: con «Mordisco venenoso», agachado y clic derecho con una botella vacía en la mano, la
 * botella se llena de veneno (objeto {@link VenenoDreamItem}). Después se queda sin veneno un rato
 * ({@link ConfigServidor#venenoEnfriamientoTicks()}, 5 minutos por defecto): ni puede volver a extraer ni puede morder.
 * <p>
 * La espera se guarda en los datos persistentes del jugador (tick del Overworld en que acaba), así que sigue al salir y
 * volver a entrar, al morir y al cambiar de dimensión. El cliente la recibe con {@link VenenoPacket} para el indicador.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class VenenoDream {
    private VenenoDream() {}

    public static final String CLAVE = "MiscelaneaVenenoDream";
    private static final String FIN = "Fin", TOTAL = "Total";

    // ------------------------------------------------------------------ espera

    private static long ahora(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    /** Ticks que le quedan sin veneno (0 = tiene). */
    public static int restante(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        if (!pd.contains(CLAVE)) return 0;
        CompoundTag t = pd.getCompound(CLAVE);
        long r = t.getLong(FIN) - ahora(p);
        if (r <= 0) {
            pd.remove(CLAVE);
            return 0;
        }
        // por si se ha bajado la espera en la configuración (o el mundo ha cambiado de hora de juego)
        int max = ConfigServidor.venenoEnfriamientoTicks();
        if (r > max) {
            if (max <= 0) {
                pd.remove(CLAVE);
                return 0;
            }
            r = max;
            t.putLong(FIN, ahora(p) + max);
        }
        return (int) r;
    }

    private static int total(ServerPlayer p) {
        CompoundTag pd = p.getPersistentData();
        int t = pd.contains(CLAVE) ? pd.getCompound(CLAVE).getInt(TOTAL) : 0;
        return t > 0 ? t : Math.max(1, ConfigServidor.venenoEnfriamientoTicks());
    }

    public static void empezarEspera(ServerPlayer p) {
        int ticks = ConfigServidor.venenoEnfriamientoTicks();
        if (ticks <= 0) {
            p.getPersistentData().remove(CLAVE);
        } else {
            CompoundTag t = new CompoundTag();
            t.putLong(FIN, ahora(p) + ticks);
            t.putInt(TOTAL, ticks);
            p.getPersistentData().put(CLAVE, t);
        }
        sincronizar(p);
    }

    public static void sincronizar(ServerPlayer p) {
        int r = restante(p);
        DreamRed.a(p, new VenenoPacket(r, r > 0 ? total(p) : 0));
    }

    /** "m:ss" */
    public static String tiempo(int ticks) {
        int s = (ticks + 19) / 20;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    /** «Te has quedado sin veneno (3:12)» */
    public static Component agotado(int ticks) {
        return Component.translatable("miscelanea.veneno.agotado", tiempo(ticks));
    }

    // ------------------------------------------------------------------ extraer

    private static boolean intento(Player p, ItemStack st) {
        return st.is(Items.GLASS_BOTTLE) && p.isShiftKeyDown() && !p.isSpectator() && p.hasEffect(DreamRegistro.MORDISCO.get());
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (!intento(p, e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (p instanceof ServerPlayer sp) extraer(sp, e.getHand());
    }

    /** Agachado mirando a un bloque, la botella no usa el bloque: se extrae igual (y no se coge agua). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!intento(p, e.getItemStack())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        if (p instanceof ServerPlayer sp) extraer(sp, e.getHand());
    }

    private static void extraer(ServerPlayer p, InteractionHand mano) {
        if (!ConfigServidor.mordiscoActivado()) {
            p.displayClientMessage(Component.translatable("miscelanea.mordisco.desactivado"), true);
            return;
        }
        if (Mordiscos.mordiendo(p) || Mordiscos.esVictima(p)) return;
        int r = restante(p);
        if (r > 0) {
            p.displayClientMessage(agotado(r), true);
            return;
        }
        ItemStack st = p.getItemInHand(mano);
        if (!st.is(Items.GLASS_BOTTLE)) return;
        p.awardStat(Stats.ITEM_USED.get(Items.GLASS_BOTTLE));
        p.setItemInHand(mano, ItemUtils.createFilledResult(st, p, VenenoDreamItem.nuevo()));
        p.swing(mano, true);
        p.gameEvent(GameEvent.FLUID_PICKUP);
        empezarEspera(p);

        ServerLevel l = p.serverLevel();
        // de la boca a la botella
        Vec3 boca = p.getEyePosition().add(p.getLookAngle().scale(0.35)).subtract(0, 0.2, 0);
        l.sendParticles(new DustParticleOptions(new Vector3f(0.16F, 0.36F, 0.08F), 0.9F), boca.x, boca.y, boca.z, 12, 0.06, 0.12, 0.06, 0.0);
        l.sendParticles(ParticleTypes.ITEM_SLIME, boca.x, boca.y - 0.1, boca.z, 6, 0.05, 0.08, 0.05, 0.02);
        l.sendParticles(ParticleTypes.DRIPPING_WATER, boca.x, boca.y, boca.z, 2, 0.04, 0.02, 0.04, 0.0);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.POINTED_DRIPSTONE_DRIP_WATER_INTO_CAULDRON, SoundSource.PLAYERS, 1.0F, 0.7F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.8F, 0.75F);
        l.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 0.4F, 0.6F);
        p.displayClientMessage(Component.translatable("miscelanea.veneno.extraido"), true);
    }

    // ------------------------------------------------------------------ ciclo de vida

    /** Morir o volver del End no devuelve el veneno. */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        CompoundTag viejo = e.getOriginal().getPersistentData();
        if (viejo.contains(CLAVE)) e.getEntity().getPersistentData().put(CLAVE, viejo.getCompound(CLAVE).copy());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sincronizar(p);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sincronizar(p);
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) sincronizar(p);
    }
}
