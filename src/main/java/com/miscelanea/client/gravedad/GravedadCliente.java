package com.miscelanea.client.gravedad;

import com.miscelanea.Miscelanea;
import com.miscelanea.gravedad.GravedadItem;
import com.miscelanea.gravedad.GravedadPacket;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/** Clic izquierdo = lanzar; y el haz naranja entre la pistola y lo que sostiene (lo ven todos). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class GravedadCliente {
    private GravedadCliente() {}

    private static ItemStack pistola(Player p) {
        if (p.getMainHandItem().getItem() instanceof GravedadItem) return p.getMainHandItem();
        if (p.getOffhandItem().getItem() instanceof GravedadItem) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        var p = Minecraft.getInstance().player;
        if (p == null || !e.isAttack() || pistola(p).isEmpty()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        ModNetwork.CHANNEL.sendToServer(new GravedadPacket());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        var mc = Minecraft.getInstance();
        if (e.phase != TickEvent.Phase.END || mc.level == null) return;
        for (Player p : mc.level.players()) {
            ItemStack s = pistola(p);
            if (s.isEmpty() || !s.hasTag() || !s.getTag().contains(GravedadItem.AGARRADO)) continue;
            Entity obj = mc.level.getEntity(s.getTag().getInt(GravedadItem.AGARRADO));
            if (obj == null) continue;
            Vec3 a = p.getEyePosition().add(p.getViewVector(1.0F).scale(0.8)).add(0, -0.3, 0);
            Vec3 b = obj.position().add(0, obj.getBbHeight() * 0.5, 0);
            int n = 10;
            for (int i = 0; i < n; i++) {
                double t = (i + mc.level.random.nextDouble()) / n;
                Vec3 q = a.lerp(b, t).add((mc.level.random.nextDouble() - 0.5) * 0.08, Math.sin(t * Math.PI) * 0.1, (mc.level.random.nextDouble() - 0.5) * 0.08);
                mc.level.addParticle(new DustParticleOptions(new Vector3f(1.0F, 0.55F + 0.2F * (float) t, 0.1F), 0.7F), q.x, q.y, q.z, 0, 0, 0);
            }
        }
    }
}
