package com.miscelanea.client.imperio;

import com.miscelanea.Miscelanea;
import com.miscelanea.imperio.ImperioBlockEntity;
import com.miscelanea.imperio.ImperioClicPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Apuntar al tablero del juego de propiedades (casillas y botones) y tocarlo con clic derecho. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ImperioCliente {
    public static ImperioBlockEntity apuntado;
    public static double tx, ty;

    private ImperioCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        apuntado = null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        for (ImperioBlockEntity be : ImperioBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(p.position()) > 400) continue;
            com.miscelanea.imperio.Partida q = be.p;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, q.fase != com.miscelanea.imperio.Partida.LOBBY && q.fase != com.miscelanea.imperio.Partida.FIN
                    && q.actual() != null && p.getUUID().equals(q.actual().uuid), "block.miscelanea.imperio");
        }
        if (mc.screen != null) return;
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        double mejor = 6.0;
        double tope = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceTo(ojo) : 99;
        for (ImperioBlockEntity be : ImperioBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(ojo) > 100 || Math.abs(dir.y) < 1e-4) continue;
            double t = (be.getBlockPos().getY() + 1.5 / 16 - ojo.y) / dir.y;
            if (t <= 0 || t > mejor || t > tope + 0.15) continue;
            double[] c = be.textura(ojo.add(dir.scale(t)));
            if (c[0] < 0 || c[1] < 0 || c[0] >= 512 || c[1] >= 512) continue;
            mejor = t;
            apuntado = be;
            tx = c[0];
            ty = c[1];
        }
    }

    /** Botón de velocidad de la partida, si lo hay ahora. */
    public static com.miscelanea.imperio.ImperioBotones.Boton botonVelocidad(com.miscelanea.imperio.Partida p) {
        for (com.miscelanea.imperio.ImperioBotones.Boton b : com.miscelanea.imperio.ImperioBotones.de(p))
            if (b.id() == com.miscelanea.imperio.ImperioBotones.VELOCIDAD) return b;
        return null;
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || p == null) return;
        // agachado + clic derecho: opción de la mesa (cambiar la velocidad), como en el resto de juegos de mesa
        com.miscelanea.imperio.ImperioBotones.Boton vel = p.isShiftKeyDown() ? botonVelocidad(apuntado.p) : null;
        if (vel != null) ModNetwork.CHANNEL.sendToServer(new ImperioClicPacket(apuntado.getBlockPos(), vel.x(), vel.y()));
        else ModNetwork.CHANNEL.sendToServer(new ImperioClicPacket(apuntado.getBlockPos(), tx, ty));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
