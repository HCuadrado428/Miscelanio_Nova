package com.miscelanea.armasfuego.comun;

import com.miscelanea.Miscelanea;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Limpieza del estado de las armas de fuego nuevas al salir un jugador o cerrar el mundo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class ArmasFuegoEventos {
    private ArmasFuegoEventos() {}

    @SubscribeEvent
    public static void sale(PlayerEvent.PlayerLoggedOutEvent e) {
        ArmaFuegoItem.olvidar(e.getEntity().getUUID());
        com.miscelanea.armasfuego.oeste.DerringerItem.olvidarManga(e.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void para(net.minecraftforge.event.server.ServerStoppedEvent e) {
        ArmaFuegoItem.limpiar();
    }

    /**
     * La Gatling en una vagoneta o en un barco: clic derecho con ella en la mano sobre una vagoneta vacía (normal) o un
     * barco con sitio. En la vagoneta, uno se sube a la Gatling; en el barco va en el asiento de atrás y la dispara el
     * que lo lleva.
     */
    @SubscribeEvent
    public static void montarGatling(net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract e) {
        var p = e.getEntity();
        var s = e.getItemStack();
        if (!(s.getItem() instanceof com.miscelanea.armasfuego.gatling.GatlingItem)) return;
        var v = e.getTarget();
        boolean vagoneta = v instanceof net.minecraft.world.entity.vehicle.AbstractMinecart c
                && c.getMinecartType() == net.minecraft.world.entity.vehicle.AbstractMinecart.Type.RIDEABLE && !c.isVehicle();
        boolean barco = v instanceof net.minecraft.world.entity.vehicle.Boat b && !(b instanceof net.minecraft.world.entity.vehicle.ChestBoat)
                && b.getPassengers().size() < 2 && b.getPassengers().stream().noneMatch(x -> x instanceof com.miscelanea.armasfuego.gatling.GatlingEntity);
        if (!vagoneta && !barco) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.sidedSuccess(p.level().isClientSide));
        if (p.level().isClientSide) return;
        montar(p, s, v);
    }

    /** Pone la Gatling del objeto montada en {@code v} (vagoneta o barco). */
    public static com.miscelanea.armasfuego.gatling.GatlingEntity montar(net.minecraft.world.entity.player.Player p, net.minecraft.world.item.ItemStack s,
                                                                         net.minecraft.world.entity.Entity v) {
        var l = p.level();
        var g = com.miscelanea.registry.ModEntities.GATLING.get().create(l);
        if (g == null) return null;
        g.moveTo(v.getX(), v.getY(), v.getZ(), v.getYRot(), 0);
        g.desde(s);
        g.ponerDueno(p);
        g.apuntar(p.getYRot(), 0);
        l.addFreshEntity(g);
        g.startRiding(v, true);
        l.playSound(null, v.getX(), v.getY(), v.getZ(), net.minecraft.sounds.SoundEvents.ANVIL_PLACE, net.minecraft.sounds.SoundSource.BLOCKS, 0.4F, 1.4F);
        if (!p.getAbilities().instabuild) s.shrink(1);
        return g;
    }
}
