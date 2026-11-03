package com.miscelanea.client.oca;

import com.miscelanea.Miscelanea;
import com.miscelanea.oca.OcaBlockEntity;
import com.miscelanea.oca.OcaClicPacket;
import com.miscelanea.oca.OcaPartida;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/** Apuntar al tablero de la Oca, tocarlo con clic derecho y los sonidos de las fichas al saltar. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class OcaCliente {
    public static OcaBlockEntity apuntado;
    public static double tx, ty;
    private static final Map<OcaBlockEntity, Long> VISTO = new WeakHashMap<>();

    private OcaCliente() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        apuntado = null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        sonidos(mc);
        if (mc.screen != null) return;
        Vec3 ojo = p.getEyePosition(), dir = p.getViewVector(1F);
        double mejor = 6.0;
        double tope = mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS ? mc.hitResult.getLocation().distanceTo(ojo) : 99;
        for (OcaBlockEntity be : OcaBlockEntity.Visibles.SET) {
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
    public static com.miscelanea.oca.OcaBotones.Boton botonVelocidad(com.miscelanea.oca.OcaPartida p) {
        for (com.miscelanea.oca.OcaBotones.Boton b : com.miscelanea.oca.OcaBotones.de(p))
            if (b.id() == com.miscelanea.oca.OcaBotones.VELOCIDAD) return b;
        return null;
    }

    private static void sonidos(Minecraft mc) {
        long ahora = mc.level.getGameTime();
        for (OcaBlockEntity be : OcaBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(mc.player.position()) > 400) continue;
            long antes = VISTO.getOrDefault(be, ahora);
            VISTO.put(be, ahora);
            if (antes >= ahora) continue;
            OcaPartida p = be.p;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, p.fase != OcaPartida.LOBBY && p.fase != OcaPartida.FIN && p.actual() != null
                    && mc.player.getUUID().equals(p.actual().uuid), "block.miscelanea.oca");
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
            if (p.movJ >= 0 && p.camino.length > 1) {
                for (int k = 1; k < p.camino.length; k++) {
                    long tk = p.tMover + (long) k * p.paso();
                    if (tk > antes && tk <= ahora)
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.35F, 1.6F + (k % 2) * 0.15F, false);
                }
                long llega = p.tMover + (long) (p.camino.length - 1) * p.paso() + 2;
                if (llega > antes && llega <= ahora) {
                    var snd = switch (p.casillaEspecial) {
                        case "oca" -> SoundEvents.CHICKEN_AMBIENT;
                        case "puente", "dados" -> SoundEvents.SLIME_JUMP;
                        case "posada" -> SoundEvents.WOODEN_DOOR_OPEN;
                        case "pozo" -> SoundEvents.GENERIC_SPLASH;
                        case "laberinto" -> SoundEvents.ENDERMAN_TELEPORT;
                        case "carcel" -> SoundEvents.IRON_DOOR_CLOSE;
                        case "muerte" -> SoundEvents.WITHER_AMBIENT;
                        case "jardin" -> SoundEvents.PLAYER_LEVELUP;
                        default -> null;
                    };
                    if (snd != null) mc.level.playLocalSound(c.x, c.y, c.z, snd, SoundSource.BLOCKS, p.casillaEspecial.equals("muerte") ? 0.35F : 0.8F,
                            p.casillaEspecial.equals("oca") ? 0.7F : 1.0F, false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || p == null) return;
        // agachado + clic derecho: opción de la mesa (cambiar la velocidad), como en el resto de juegos de mesa
        com.miscelanea.oca.OcaBotones.Boton vel = p.isShiftKeyDown() ? botonVelocidad(apuntado.p) : null;
        if (vel != null) ModNetwork.CHANNEL.sendToServer(new OcaClicPacket(apuntado.getBlockPos(), vel.x(), vel.y()));
        else ModNetwork.CHANNEL.sendToServer(new OcaClicPacket(apuntado.getBlockPos(), tx, ty));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
