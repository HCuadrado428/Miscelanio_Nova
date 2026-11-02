package com.miscelanea.client.armas;

import com.miscelanea.Miscelanea;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Sacudida de cámara que se apaga poco a poco. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TemblorCliente {
    private static float fuerza;
    private static long inicio, fin;

    private TemblorCliente() {}

    public static void empezar(float f, int duracion) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long t = mc.level.getGameTime();
        if (t < fin && fuerza * (fin - t) / Math.max(1F, fin - inicio) > f) return;
        fuerza = Math.min(f, 3F);
        inicio = t;
        fin = t + duracion;
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float t = mc.level.getGameTime() + (float) e.getPartialTick();
        if (t >= fin) return;
        float k = fuerza * (fin - t) / Math.max(1F, fin - inicio);
        e.setPitch(e.getPitch() + Mth.sin(t * 2.7F) * k);
        e.setYaw(e.getYaw() + Mth.sin(t * 3.3F + 1.1F) * k);
        e.setRoll(e.getRoll() + Mth.sin(t * 4.1F + 2.2F) * k * 0.7F);
    }
}
