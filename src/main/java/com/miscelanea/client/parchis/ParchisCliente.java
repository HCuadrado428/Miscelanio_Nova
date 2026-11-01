package com.miscelanea.client.parchis;

import com.miscelanea.Miscelanea;
import com.miscelanea.parchis.ParchisBlockEntity;
import com.miscelanea.parchis.ParchisClicPacket;
import com.miscelanea.parchis.ParchisPartida;
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

/** Apuntar al tablero de parchís, tocarlo con clic derecho y los sonidos de las fichas al saltar. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ParchisCliente {
    public static ParchisBlockEntity apuntado;
    public static double tx, ty;
    private static final Map<ParchisBlockEntity, Long> VISTO = new WeakHashMap<>();

    private ParchisCliente() {}

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
        for (ParchisBlockEntity be : ParchisBlockEntity.Visibles.SET) {
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
        for (ParchisBlockEntity be : ParchisBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(mc.player.position()) > 400) continue;
            long antes = VISTO.getOrDefault(be, ahora);
            VISTO.put(be, ahora);
            if (antes >= ahora) continue;
            ParchisPartida p = be.p;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, p.fase != ParchisPartida.LOBBY && p.fase != ParchisPartida.FIN && p.actual() != null
                    && mc.player.getUUID().equals(p.actual().uuid), "block.miscelanea.parchis");
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
            if (p.movColor >= 0) {
                int pasos = p.movDesde == ParchisPartida.CASA ? 2 : p.movHasta - p.movDesde;
                for (int k = 1; k <= pasos; k++) {
                    long tk = p.tMover + (long) k * p.movPaso;
                    if (tk > antes && tk <= ahora)
                        mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.WOOD_HIT, SoundSource.BLOCKS, 0.35F, 1.7F + (k % 2) * 0.15F, false);
                }
            }
            if (p.capColor >= 0 && p.tCap > antes && p.tCap <= ahora)
                mc.level.playLocalSound(c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.BLOCKS, 0.9F, 1.3F, false);
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || p == null) return;
        ModNetwork.CHANNEL.sendToServer(new ParchisClicPacket(apuntado.getBlockPos(), tx, ty));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
