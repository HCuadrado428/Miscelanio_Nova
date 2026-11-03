package com.miscelanea.tortura.saw;

import com.miscelanea.Miscelanea;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;

/** Eventos del servidor de los juegos de Jigsaw: tick de la caja de agua, limpieza, sincronización y uso sobre entidades. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class SawEventos {
    private SawEventos() {}

    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !Saw.tieneAlgo(v)) return;
        if (CajaAgua.tiene(v) && v.isAlive()) CajaAgua.tick(v);
    }

    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.getServer().getTickCount() % 200 == 0)
            Alambre.limpiar(e.getServer().overworld().getGameTime(), false);
    }

    @SubscribeEvent
    public static void pararServidor(ServerStoppedEvent e) {
        Alambre.limpiar(0, true);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide) return;
        CajaAgua.limpiar(e.getEntity());
    }

    /** Volver del End crea otro jugador: se copia el estado. Al morir NO. */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        Player viejo = e.getOriginal();
        if (viejo.getPersistentData().contains(Saw.RAIZ))
            e.getEntity().getPersistentData().put(Saw.RAIZ, viejo.getPersistentData().getCompound(Saw.RAIZ).copy());
    }

    @SubscribeEvent
    public static void cambiarDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        // la manguera no llega a otra dimensión
        CajaAgua.quitar(e.getEntity(), false);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (CajaAgua.tiene(e.getEntity())) CajaAgua.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        CajaAgua.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity le && CajaAgua.tiene(le) && e.getEntity() instanceof ServerPlayer sp)
            CajaAgua.sincronizarCon(le, sp);
    }

    // ------------------------------------------------------------------ usar sobre entidades
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        Player p = e.getEntity();
        if (!(e.getTarget() instanceof LivingEntity t)) return;
        ItemStack st = e.getItemStack();
        boolean servidor = !p.level().isClientSide;
        if (st.getItem() instanceof CajaAguaItem) {
            if (servidor) CajaAguaItem.usarSobre(p, st, t);
        } else if (st.isEmpty() && e.getHand() == InteractionHand.MAIN_HAND && p.isShiftKeyDown() && p.getAbilities().instabuild
                && (servidor ? CajaAgua.tiene(t) : cajaCliente(t))) {
            // creativo: Shift + mano vacía quita la caja de agua
            if (servidor) CajaAgua.quitar(t, false);
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    private static boolean cajaCliente(LivingEntity t) {
        Boolean r = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.saw.SawCliente.caja(t.getId()) != null);
        return r != null && r;
    }
}
