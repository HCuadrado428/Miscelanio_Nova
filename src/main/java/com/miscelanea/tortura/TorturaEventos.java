package com.miscelanea.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.qte.Qte;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
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

/** Eventos del servidor (y comunes) de la trampa, los grilletes y los minijuegos. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class TorturaEventos {
    private TorturaEventos() {}

    // ------------------------------------------------------------------ minijuegos
    @SubscribeEvent
    public static void tickServidor(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) Qte.tick(e.getServer());
    }

    @SubscribeEvent
    public static void pararServidor(ServerStoppedEvent e) {
        Qte.limpiar();
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Qte.desconectar(sp);
    }

    // ------------------------------------------------------------------ sincronizar estado
    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (Cautivos.tieneAlgo(e.getEntity())) Cautivos.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        Cautivos.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void cambiarDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        Cautivos.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity le && Cautivos.tieneAlgo(le) && e.getEntity() instanceof ServerPlayer sp)
            Cautivos.sincronizarCon(le, sp);
    }

    /** Volver del End crea otro jugador: se copia el estado. Al morir NO (la trampa y los grilletes se pierden). */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        Player viejo = e.getOriginal();
        if (viejo.getPersistentData().contains(Cautivos.RAIZ))
            e.getEntity().getPersistentData().put(Cautivos.RAIZ, viejo.getPersistentData().getCompound(Cautivos.RAIZ).copy());
    }

    // ------------------------------------------------------------------ cada tick de cada cautivo
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !Cautivos.tieneAlgo(v)) return;
        if (Cautivos.conTrampa(v)) TrampaOso.tick(v);
        if (v.isAlive() && Cautivos.atado(v)) Grilletes.tick(v);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide) return;
        if (e.getEntity() instanceof ServerPlayer sp) Qte.cancelar(sp);
    }

    // ------------------------------------------------------------------ usar los objetos sobre entidades
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        Player p = e.getEntity();
        if (!(e.getTarget() instanceof LivingEntity t)) return;
        if (Cautivos.atado(p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack st = e.getItemStack();
        Item it = st.getItem();
        boolean servidor = !p.level().isClientSide;
        if (it instanceof TrampaOsoItem) {
            if (servidor) TrampaOsoItem.usarSobre(p, st, t);
        } else if (it instanceof GrilletesItem) {
            if (servidor) Grilletes.usarGrilletes(p, st, t);
        } else if (it instanceof CadenaItem) {
            if (servidor) Grilletes.alternarCadena(p, t);
        } else if (it instanceof LlaveTrampaItem) {
            if (servidor) TrampaOso.abrirCon(p, t, st);
        } else if (st.isEmpty() && e.getHand() == InteractionHand.MAIN_HAND && p.isShiftKeyDown()) {
            // mano vacía + Shift: el captor o un creativo quita grilletes; un creativo quita la trampa
            if (!servidor) return;
            boolean hecho = Grilletes.manoVacia(p, t);
            if (!hecho && p.getAbilities().instabuild && Cautivos.conTrampa(t)) {
                TrampaOso.quitar(t, false);
                hecho = true;
            }
            if (!hecho) return;
        } else return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ------------------------------------------------------------------ manos atadas
    private static boolean atado(Entity e) {
        return e != null && Cautivos.atado(e);
    }

    private static boolean permitido(ItemStack s) {
        return s.getItem() instanceof GanzuaItem || s.getItem() instanceof LlaveTrampaItem;
    }

    private static void aviso(Player p) {
        if (!p.level().isClientSide && p.tickCount % 10 == 0)
            p.displayClientMessage(Component.translatable("msg.miscelanea.grilletes.manos").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void atacar(AttackEntityEvent e) {
        if (atado(e.getEntity())) {
            e.setCanceled(true);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void recibirAtaque(LivingAttackEvent e) {
        if (atado(e.getSource().getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        if (atado(e.getEntity()) && !permitido(e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (atado(e.getEntity()) && !permitido(e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpearBloque(PlayerInteractEvent.LeftClickBlock e) {
        if (atado(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void romperBloque(BlockEvent.BreakEvent e) {
        if (atado(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void velocidadRomper(PlayerEvent.BreakSpeed e) {
        if (atado(e.getEntity())) e.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void ponerBloque(BlockEvent.EntityPlaceEvent e) {
        if (atado(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void usarObjeto(LivingEntityUseItemEvent.Start e) {
        if (atado(e.getEntity()) && !permitido(e.getItem())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cambiarObjetivo(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && atado(e.getEntity())) e.setCanceled(true);
    }
}
