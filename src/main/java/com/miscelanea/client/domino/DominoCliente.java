package com.miscelanea.client.domino;

import com.miscelanea.Miscelanea;
import com.miscelanea.domino.DominoBlockEntity;
import com.miscelanea.domino.DominoClicPacket;
import com.miscelanea.domino.DominoPartida;
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

/** Apuntar al la mesa de dominó, tocarlo con clic derecho y los sonidos de las fichas al saltar. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class DominoCliente {
    public static DominoBlockEntity apuntado;
    public static double tx, ty;
    private static final Map<DominoBlockEntity, Long> VISTO = new WeakHashMap<>();

    private DominoCliente() {}

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
        for (DominoBlockEntity be : DominoBlockEntity.Visibles.SET) {
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
        for (DominoBlockEntity be : DominoBlockEntity.Visibles.SET) {
            if (be.isRemoved() || be.getLevel() != mc.level || be.getBlockPos().distToCenterSqr(mc.player.position()) > 400) continue;
            long antes = VISTO.getOrDefault(be, ahora);
            VISTO.put(be, ahora);
            if (antes >= ahora) continue;
            DominoPartida p = be.p;
            com.miscelanea.client.juegos.AvisoTurno.revisar(be, p.fase == DominoPartida.JUGAR && p.actual() != null
                    && mc.player.getUUID().equals(p.actual().uuid), "block.miscelanea.domino");
            Vec3 c = Vec3.atCenterOf(be.getBlockPos());
        }
    }

    @SubscribeEvent
    public static void usar(InputEvent.InteractionKeyMappingTriggered e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (!e.isUseItem() || apuntado == null || p == null) return;
        ModNetwork.CHANNEL.sendToServer(new DominoClicPacket(apuntado.getBlockPos(), tx, ty));
        e.setCanceled(true);
        e.setSwingHand(true);
    }
}
