package com.miscelanea.client.tortura.efectos;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.efectos.EfectosRegistro;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * La nube que deja la granada del miedo: unas volutas de gas por tick, a ras de suelo, dentro de un radio que se va
 * cerrando igual que la nube de efecto del servidor (3,8 bloques durante 13 s).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class GasMiedoCliente {
    private GasMiedoCliente() {}

    private static final int DURA = 266;

    private record Nube(double x, double y, double z, long desde) {}

    private static final List<Nube> NUBES = new ArrayList<>();

    static void nube(double x, double y, double z) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        NUBES.add(new Nube(x, y, z, l.getGameTime()));
        if (NUBES.size() > 12) NUBES.remove(0);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || NUBES.isEmpty()) return;
        var mc = Minecraft.getInstance();
        var l = mc.level;
        if (l == null) {
            NUBES.clear();
            return;
        }
        if (mc.isPaused()) return;
        long t = l.getGameTime();
        NUBES.removeIf(n -> t - n.desde > DURA || t < n.desde);
        var r = l.getRandom();
        for (Nube n : NUBES) {
            long edad = t - n.desde;
            double rad = Math.max(0.8, 3.8 - 0.004 * edad);
            // menos volutas al final, cuando la nube se deshace
            int cuantas = edad > DURA - 40 ? 1 : 3;
            for (int i = 0; i < cuantas; i++) {
                double an = r.nextDouble() * Math.PI * 2, d = Math.sqrt(r.nextDouble()) * rad;
                l.addParticle(EfectosRegistro.GAS.get(), n.x + Math.cos(an) * d, n.y + 0.05 + r.nextDouble() * 0.5, n.z + Math.sin(an) * d,
                        (r.nextDouble() - 0.5) * 0.02, 0.004, (r.nextDouble() - 0.5) * 0.02);
            }
        }
    }
}
