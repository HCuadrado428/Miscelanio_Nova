package com.miscelanea.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.qte.Qte;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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

/** Eventos del servidor (y comunes) de la trampa, los grilletes, el lazo y los minijuegos. */
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
        Lazo.limpiarMemoria();
    }

    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            // en plena ejecución de la trampa no se escapa nadie: muere ya (antes de que se guarde)
            TrampaOso.ejecutarYa(sp);
            Lazo.cortarTodo(sp);   // antes de que se guarde: vuelve libre
            Qte.desconectar(sp);
        }
    }

    // ------------------------------------------------------------------ sincronizar estado
    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        // el lazo no sobrevive a desconectarse (por si el servidor se cerró de golpe)
        if (Lazo.estado(e.getEntity()) != 0) Lazo.limpiar(e.getEntity());
        if (Cautivos.tieneAlgo(e.getEntity())) Cautivos.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        Cautivos.sincronizar(e.getEntity());
        // el cuerpo nuevo tiene el mismo id: que todos vuelvan a dibujarle la cabeza
        TrampaOso.enviarEjecucion(e.getEntity(), false, 0);
    }

    @SubscribeEvent
    public static void cambiarDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) Lazo.cortarTodo(sp);
        Cautivos.sincronizar(e.getEntity());
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof LivingEntity le && Cautivos.tieneAlgo(le) && e.getEntity() instanceof ServerPlayer sp) {
            Cautivos.sincronizarCon(le, sp);
            if (TrampaOso.ejecutando(le)) TrampaOso.enviarEjecucionA(le, sp);
        }
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
        if (v.isAlive() && Lazo.estado(v) != 0) Lazo.tick(v);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        if (e.getEntity().level().isClientSide) return;
        TrampaOso.alMorir(e.getEntity());
        Lazo.alMorir(e.getEntity());
        if (e.getEntity() instanceof ServerPlayer sp) Qte.cancelar(sp);
    }

    // ------------------------------------------------------------------ usar los objetos sobre entidades
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        Player p = e.getEntity();
        if (!(e.getTarget() instanceof LivingEntity t)) return;
        if (Cautivos.atado(p) || Lazo.sinManos(p) || TrampaOso.ejecutando(p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        ItemStack st = e.getItemStack();
        Item it = st.getItem();
        boolean servidor = !p.level().isClientSide;
        // lazo: soltar (creativo), maniatar al derribado, cargarlo al hombro, subirlo al caballo / montar con él
        if (servidor ? usoLazo(p, t, st, e.getHand()) : usoLazoCliente(p, t, st, e.getHand())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }
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

    private static boolean usoLazo(Player p, LivingEntity t, ItemStack st, InteractionHand mano) {
        // (un caballo enlazado o derribado se trata como cualquier otro mob)
        if (t instanceof AbstractHorse caballo && Lazo.estado(caballo) == 0) return mano == InteractionHand.MAIN_HAND && Lazo.caballo(p, caballo, st);
        return Lazo.interactuar(p, t, st, mano);
    }

    /** Lo mismo con el estado que conoce el cliente (para que no haga otra cosa con ese clic). */
    private static boolean usoLazoCliente(Player p, LivingEntity t, ItemStack st, InteractionHand mano) {
        Boolean r = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.tortura.LazoCliente.interactuaria(p, t, st, mano));
        return r != null && r;
    }

    // ------------------------------------------------------------------ manos atadas
    private static boolean atado(Entity e) {
        return e != null && (Cautivos.atado(e) || Lazo.sinManos(e) || TrampaOso.ejecutando(e));
    }

    /** Con grilletes se puede usar la ganzúa y la llave; con el lazo, nada. */
    private static boolean bloqueado(Entity e, ItemStack s) {
        if (e == null) return false;
        if (Lazo.sinManos(e) || TrampaOso.ejecutando(e)) return true;
        return Cautivos.atado(e) && !permitido(s);
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
        if (atado(e.getSource().getEntity())) {
            e.setCanceled(true);
            return;
        }
        // en plena ejecución solo la trampa (o /kill, el vacío...) puede matarla
        if (!e.getEntity().level().isClientSide && TrampaOso.ejecutando(e.getEntity())
                && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        if (bloqueado(e.getEntity(), e.getItemStack())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (bloqueado(e.getEntity(), e.getItemStack())) {
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
        if (bloqueado(e.getEntity(), e.getItem())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void cambiarObjetivo(LivingChangeTargetEvent e) {
        if (e.getNewTarget() != null && atado(e.getEntity())) e.setCanceled(true);
    }
}
