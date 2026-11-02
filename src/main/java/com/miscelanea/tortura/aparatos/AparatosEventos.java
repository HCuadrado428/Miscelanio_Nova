package com.miscelanea.tortura.aparatos;

import com.miscelanea.Miscelanea;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.EntityTravelToDimensionEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Eventos de los aparatos: soltar a la víctima al morir, desconectarse o cambiar de dimensión; que no se baje sola del
 * asiento; que dentro no pueda pegar, usar objetos ni tocar bloques; y que las correas y el hierro de marcar funcionen
 * también con mobs que tienen su propio clic derecho (aldeanos...).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class AparatosEventos {
    private AparatosEventos() {}

    private static void soltar(Entity e) {
        AparatoBlockEntity be = Aparatos.de(e);
        if (be != null) be.sacar(false);
        e.getPersistentData().remove(Aparatos.CLAVE);
    }

    // ------------------------------------------------------------------ limpieza
    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.getPersistentData().contains(Aparatos.CLAVE)) soltar(sp);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        // por si el servidor se cerró de golpe con él dentro
        Player p = e.getEntity();
        if (p.getPersistentData().contains(Aparatos.CLAVE)) soltar(p);
        if (p.getVehicle() instanceof AsientoAparato a) {
            Aparatos.LIBERANDO.add(p.getUUID());
            try {
                p.stopRiding();
            } finally {
                Aparatos.LIBERANDO.remove(p.getUUID());
            }
            a.discard();
        }
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide && e.getEntity().getPersistentData().contains(Aparatos.CLAVE)) soltar(e.getEntity());
    }

    @SubscribeEvent
    public static void viajar(EntityTravelToDimensionEvent e) {
        if (!e.getEntity().level().isClientSide && e.getEntity().getPersistentData().contains(Aparatos.CLAVE)) soltar(e.getEntity());
    }

    @SubscribeEvent
    public static void parar(ServerStoppedEvent e) {
        Aparatos.LIBERANDO.clear();
    }

    /** Nadie se baja del asiento por su cuenta (Shift): solo cuando el aparato lo suelta. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void montar(EntityMountEvent e) {
        Entity v = e.getEntityMounting();
        if (!e.isDismounting() || v == null || v.level().isClientSide || !(e.getEntityBeingMounted() instanceof AsientoAparato a)) return;
        if (Aparatos.LIBERANDO.contains(v.getUUID()) || !v.isAlive() || a.isRemoved()) return;
        if (Aparatos.de(v) != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        // dentro de un aparato no se le puede enlazar (el lazo lo intentaría bajar del asiento)
        if (com.miscelanea.tortura.Lazo.estado(v) == com.miscelanea.tortura.Lazo.ENLAZADO && v.getPersistentData().contains(Aparatos.CLAVE))
            com.miscelanea.tortura.Lazo.romper(v, true);
        if (v.tickCount % 40 != 7) return;
        if (v.getPersistentData().contains(Aparatos.CLAVE)) {
            if (Aparatos.de(v) == null) v.getPersistentData().remove(Aparatos.CLAVE);   // marca vieja
        } else if (v.getVehicle() instanceof AsientoAparato a
                && !(v.level().getBlockEntity(a.aparato()) instanceof AparatoBlockEntity be && be.esVictima(v))) {
            Aparatos.LIBERANDO.add(v.getUUID());
            try {
                v.stopRiding();
            } finally {
                Aparatos.LIBERANDO.remove(v.getUUID());
            }
            a.discard();
        }
    }

    // ------------------------------------------------------------------ la víctima no hace nada
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void atacar(AttackEntityEvent e) {
        if (Aparatos.en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void recibirAtaque(LivingAttackEvent e) {
        if (e.getSource().getEntity() != null && e.getSource().getEntity() != e.getEntity() && Aparatos.en(e.getSource().getEntity()))
            e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        if (Aparatos.en(e.getEntity())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (Aparatos.en(e.getEntity())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpearBloque(PlayerInteractEvent.LeftClickBlock e) {
        if (Aparatos.en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void romperBloque(BlockEvent.BreakEvent e) {
        if (Aparatos.en(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void velocidadRomper(PlayerEvent.BreakSpeed e) {
        if (Aparatos.en(e.getEntity())) e.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void ponerBloque(BlockEvent.EntityPlaceEvent e) {
        if (Aparatos.en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void usarObjeto(LivingEntityUseItemEvent.Start e) {
        if (Aparatos.en(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cambiarObjetivo(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && e.getEntity() instanceof Mob && Aparatos.en(e.getEntity())) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ correas y hierro sobre cualquier mob
    @SubscribeEvent
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        if (e.isCanceled() || !(e.getTarget() instanceof LivingEntity t)) return;
        Player p = e.getEntity();
        if (Aparatos.en(p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack st = e.getItemStack();
        InteractionResult r;
        if (st.getItem() instanceof CorreasItem c) r = c.interactLivingEntity(st, p, t, e.getHand());
        else if (st.getItem() instanceof HierroMarcarItem h) r = h.interactLivingEntity(st, p, t, e.getHand());
        else return;
        if (r == InteractionResult.PASS) return;
        e.setCanceled(true);
        e.setCancellationResult(r);
    }
}
