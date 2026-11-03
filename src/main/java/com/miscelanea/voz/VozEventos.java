package com.miscelanea.voz;

import com.miscelanea.Miscelanea;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos del servidor de las voces: avisar a los clientes de qué voz tiene cada jugador (cualquier efecto apuntado en
 * {@link VozPorEfecto}), el chat tartamudo/farfullado y el tick de la borrachera de los jugadores.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class VozEventos {
    private VozEventos() {}

    // ------------------------------------------------------------------ sincronizar voces: cualquier cambio de efecto se manda en el tick siguiente
    @SubscribeEvent
    public static void efectoPuesto(MobEffectEvent.Added e) {
        if (e.getEntity() instanceof ServerPlayer p && conVoz(e.getEffectInstance())) VozSync.marcar(p);
    }

    @SubscribeEvent
    public static void efectoQuitado(MobEffectEvent.Remove e) {
        if (e.getEntity() instanceof ServerPlayer p && conVoz(e.getEffectInstance())) VozSync.marcar(p);
    }

    @SubscribeEvent
    public static void efectoAcabado(MobEffectEvent.Expired e) {
        if (e.getEntity() instanceof ServerPlayer p && conVoz(e.getEffectInstance())) VozSync.marcar(p);
    }

    private static boolean conVoz(MobEffectInstance m) {
        if (m == null) return false;
        var id = net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getKey(m.getEffect());
        return id != null && VozPorEfecto.vozDe(id) != null;
    }

    @SubscribeEvent
    public static void tickJugador(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (VozSync.pendiente(p)) VozSync.sincronizar(p);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) VozSync.sincronizar(p);
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) VozSync.olvidar(p);
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Borrachera.limpiar(p);
            VozSync.sincronizar(p);
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) VozSync.sincronizar(p);
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer quien) VozSync.sincronizarCon(t, quien);
    }

    // ------------------------------------------------------------------ borrachera (jugadores): hipo y vomitar
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !(v instanceof ServerPlayer p) || !p.isAlive()) return;
        MobEffectInstance b = p.getEffect(VozRegistro.BORRACHERA.get());
        if (b != null) Borrachera.tickJugador(p, b);
        else if (p.getPersistentData().contains("MiscelaneaBorrHipo")) Borrachera.limpiar(p);
    }

    // ------------------------------------------------------------------ chat: tartamudo y/o farfullado
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void chat(ServerChatEvent e) {
        ServerPlayer p = e.getPlayer();
        if (p == null) return;
        MobEffectInstance t = p.getEffect(VozRegistro.TARTAMUDEO.get());
        MobEffectInstance b = p.getEffect(VozRegistro.BORRACHERA.get());
        if (t == null && b == null) return;
        String texto = e.getMessage().getString();
        if (b != null) texto = Borrachera.farfullar(texto, b.getAmplifier(), p.getRandom());
        if (t != null) texto = Tartamudeo.tartamudear(texto, t.getAmplifier(), p.getRandom());
        e.setMessage(Component.literal(texto));
    }
}
