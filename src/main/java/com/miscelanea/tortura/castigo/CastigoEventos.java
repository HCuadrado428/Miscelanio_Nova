package com.miscelanea.tortura.castigo;

import com.miscelanea.Miscelanea;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Eventos del servidor de los castigos: jaula colgante, cepo, tomates / huevos (manchas) y bola de preso. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class CastigoEventos {
    private CastigoEventos() {}

    /** Datos persistentes del jugador que se desconectó dentro de una jaula cerrada: vuelve a ella al entrar. */
    public static final String JAULA = "MiscelaneaJaula";
    private static final Map<UUID, Integer> VOLVER_JAULA = new HashMap<>();

    // ------------------------------------------------------------------ ticks
    @SubscribeEvent
    public static void tickVivo(LivingEvent.LivingTickEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !v.isAlive()) return;
        if (Bolas.tiene(v)) Bolas.tick(v);
    }

    @SubscribeEvent
    public static void tickJugador(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 10 == 0) Manchas.tick(p);
        Integer n = VOLVER_JAULA.get(p.getUUID());
        if (n != null && p.tickCount % 5 == 0) {
            if (volverAJaula(p) || n > 40) {
                VOLVER_JAULA.remove(p.getUUID());
                p.getPersistentData().remove(JAULA);
            } else VOLVER_JAULA.put(p.getUUID(), n + 1);
        }
    }

    private static boolean volverAJaula(ServerPlayer p) {
        CompoundTag t = p.getPersistentData().getCompound(JAULA);
        if (!t.hasUUID("Jaula") || !(p.level() instanceof ServerLevel sl)) return true;
        Entity e = sl.getEntity(t.getUUID("Jaula"));
        if (!(e instanceof JaulaEntity j)) return false;   // todavía no se ha cargado
        if (j.estado() == JaulaEntity.COLGANDO && j.preso() == null && !p.isPassenger() && p.isAlive()) {
            byte puerta = t.getByte("Puerta");
            j.meter(p, null);
            if (puerta == JaulaEntity.CON_LLAVE) j.cerrarConLlave();
        }
        return true;
    }

    @SubscribeEvent
    public static void pararServidor(ServerStoppedEvent e) {
        Escarnio.limpiarMemoria();
        Bolas.limpiarMemoria();
        VOLVER_JAULA.clear();
    }

    // ------------------------------------------------------------------ entrar, salir, morir, dimensión
    @SubscribeEvent
    public static void salir(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        VOLVER_JAULA.remove(p.getUUID());
        Entity veh = p.getVehicle();
        if (veh instanceof CepoEntity c) c.liberar(null, false);
        if (veh instanceof JaulaEntity j) {
            if (j.puerta() != JaulaEntity.ABIERTA && j.estado() == JaulaEntity.COLGANDO) {
                CompoundTag t = new CompoundTag();
                t.putUUID("Jaula", j.getUUID());
                t.putByte("Puerta", j.puerta());
                p.getPersistentData().put(JAULA, t);
            }
            j.sacar(p);
            // se guarda dentro de la jaula (no donde lo dejaría el desmontar)
            Vec3 b = j.position().add(0, JaulaEntity.SUELO, 0);
            p.setPos(b.x, b.y, b.z);
        }
        if (Bolas.tiene(p)) Bolas.guardarBola(p);
        Escarnio.olvidar(p);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (Manchas.tiene(p)) Manchas.enviar(p, Manchas.leer(p));
        if (p.getPersistentData().contains(JAULA)) VOLVER_JAULA.put(p.getUUID(), 0);
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Manchas.enviar(p, Manchas.leer(p));
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        VOLVER_JAULA.remove(p.getUUID());
        p.getPersistentData().remove(JAULA);
        Manchas.enviar(p, Manchas.leer(p));
    }

    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        if (e.isWasDeath()) return;
        Player viejo = e.getOriginal();
        if (viejo.getPersistentData().contains(Manchas.CLAVE))
            e.getEntity().getPersistentData().put(Manchas.CLAVE, viejo.getPersistentData().get(Manchas.CLAVE).copy());
    }

    @SubscribeEvent
    public static void verEntidad(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer t && e.getEntity() instanceof ServerPlayer quien && Manchas.tiene(t)) Manchas.enviarA(t, quien);
    }

    @SubscribeEvent
    public static void morir(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide) return;
        if (Bolas.tiene(v)) {
            BolaPresoEntity b = Bolas.bola(v);
            if (b != null) b.discard();
            Bolas.limpiar(v);
        }
        if (v instanceof ServerPlayer p) {
            Manchas.lavar(p, false);
            p.getPersistentData().remove(JAULA);
        }
        Escarnio.olvidar(v);
    }

    // ------------------------------------------------------------------ no bajarse de la jaula ni del cepo
    @SubscribeEvent
    public static void montar(EntityMountEvent e) {
        if (!e.isDismounting() || e.getEntityMounting().level().isClientSide) return;
        Entity veh = e.getEntityBeingMounted(), quien = e.getEntityMounting();
        if (!quien.isAlive() || quien.isRemoved() || veh.isRemoved() || quien.isSpectator()) return;
        if (veh instanceof JaulaEntity j && !j.dejaSalir()) {
            e.setCanceled(true);
            if (quien instanceof Player p && p.tickCount % 20 == 0)
                p.displayClientMessage(Component.translatable(j.puerta() == JaulaEntity.CON_LLAVE ? "msg.miscelanea.jaula.encerrado"
                        : "msg.miscelanea.jaula.puerta_cerrada", Component.keybind("key.left"), Component.keybind("key.right")).withStyle(ChatFormatting.RED), true);
        } else if (veh instanceof CepoEntity c && !c.dejaBajar()) {
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ objetos sobre entidades (después de los de la sala de juegos)
    @SubscribeEvent
    public static void interactuar(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getTarget() instanceof LivingEntity t)) return;
        Player p = e.getEntity();
        if (enCepo(p)) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            return;
        }
        boolean hecho = p.level().isClientSide ? Bolas.interactuaria(e.getItemStack()) : Bolas.interactuar(p, t, e.getItemStack(), e.getHand());
        if (hecho) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    // ------------------------------------------------------------------ en el cepo no se hace nada; en la jaula, nada con los bloques
    private static boolean enCepo(Entity e) {
        return e != null && e.getVehicle() instanceof CepoEntity;
    }

    private static boolean enJaula(Entity e) {
        return e != null && e.getVehicle() instanceof JaulaEntity;
    }

    private static void aviso(Player p) {
        if (!p.level().isClientSide && p.tickCount % 10 == 0)
            p.displayClientMessage(Component.translatable("msg.miscelanea.cepo.atrapado").withStyle(ChatFormatting.RED), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void atacar(AttackEntityEvent e) {
        if (enCepo(e.getEntity())) {
            e.setCanceled(true);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicBloque(PlayerInteractEvent.RightClickBlock e) {
        if (enCepo(e.getEntity()) || enJaula(e.getEntity())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void clicObjeto(PlayerInteractEvent.RightClickItem e) {
        if (enCepo(e.getEntity())) {
            e.setCanceled(true);
            e.setCancellationResult(InteractionResult.FAIL);
            aviso(e.getEntity());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void golpearBloque(PlayerInteractEvent.LeftClickBlock e) {
        if (enCepo(e.getEntity()) || enJaula(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void romperBloque(BlockEvent.BreakEvent e) {
        if (enCepo(e.getPlayer()) || enJaula(e.getPlayer())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void velocidadRomper(PlayerEvent.BreakSpeed e) {
        if (enCepo(e.getEntity()) || enJaula(e.getEntity())) e.setNewSpeed(0F);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void ponerBloque(BlockEvent.EntityPlaceEvent e) {
        if (enCepo(e.getEntity()) || enJaula(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void usarObjeto(LivingEntityUseItemEvent.Start e) {
        if (enCepo(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void saltar(LivingEvent.LivingJumpEvent e) {
        LivingEntity v = e.getEntity();
        if (!v.level().isClientSide && Bolas.tiene(v)) {
            Vec3 d = v.getDeltaMovement();
            v.setDeltaMovement(d.x, Math.min(d.y, 0.0), d.z);
        }
    }
}
