package com.miscelanea.client.flota;

import com.miscelanea.Miscelanea;
import com.miscelanea.flota.FlotaBlockEntity;
import com.miscelanea.flota.FlotaClicPacket;
import com.miscelanea.flota.FlotaPartida;
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

/** Apuntar al tablero de hundir la flota, tocarlo con clic derecho y los sonidos de las fichas al saltar. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class FlotaCliente {
    public static FlotaBlockEntity apuntado;
    public static double tx, ty;
    private static final Map<FlotaBlockEntity, Long> VISTO = new WeakHashMap<>();

    private FlotaCliente() {}

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
        for (FlotaBlockEntity be : FlotaBlockEntity.Visibles.SET) {
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

    private static void sonidos(Minecraft mc) {
        long ahora = mc.level.getGameTime();
        for (FlotaBlockEntity be : FlotaBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(mc.player.position()) > 400) continue;
            long antes = VISTO.getOrDefault(be, ahora);
            VISTO.put(be, ahora);
            if (antes >= ahora) continue;
            FlotaPartida p = be.p;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, p.fase == FlotaPartida.JUGAR && p.actual() != null
                    && mc.player.getUUID().equals(p.actual().uuid), "block.miscelanea.flota");
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
            long llega = p.tDisparo + FlotaPartida.VUELO;
            if (p.ultCelda >= 0 && llega > antes && llega <= ahora) {
                double[] q = com.miscelanea.flota.FlotaGeo.centro(1 - p.ultTirador, p.ultCelda);
                Vec3 w = be.mundo(q[0], q[1]);
                if (p.ultResultado == FlotaPartida.AGUA) {
                    mc.level.playLocalSound(w.x, w.y, w.z, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.6F, 1.4F, false);
                    for (int k = 0; k < 14; k++)
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.SPLASH, w.x + (Math.random() - 0.5) * 0.08, w.y + 0.02, w.z + (Math.random() - 0.5) * 0.08,
                                0, 0.05, 0);
                } else {
                    mc.level.playLocalSound(w.x, w.y, w.z, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 0.35F, 1.6F, false);
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.EXPLOSION, w.x, w.y + 0.05, w.z, 0, 0, 0);
                    for (int k = 0; k < 8; k++)
                        mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME, w.x + (Math.random() - 0.5) * 0.06, w.y + 0.04, w.z + (Math.random() - 0.5) * 0.06,
                                0, 0.02, 0);
                    if (p.ultHundido >= 0) mc.level.playLocalSound(w.x, w.y, w.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F, false);
                }
            }
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || p == null) return;
        ModNetwork.CHANNEL.sendToServer(new FlotaClicPacket(apuntado.getBlockPos(), tx, ty));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
