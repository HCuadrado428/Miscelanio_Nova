package com.miscelanea.client.feria;

import com.miscelanea.Miscelanea;
import com.miscelanea.feria.SustoBlockEntity;
import com.miscelanea.feria.VagonetaBruja;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Destello y sacudida de cámara cuando te salta un susto yendo en el tren de la bruja. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class FeriaHud {
    private FeriaHud() {}

    /** 1 justo al saltar, 0 a los 20 tics; 0 si no vas en el tren o el susto queda lejos. */
    static float fuerza(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !(mc.player.getVehicle() instanceof VagonetaBruja)) return 0;
        float dt = mc.level.getGameTime() + pt - SustoBlockEntity.ultimoT;
        if (dt < 0 || dt > 20) return 0;
        if (mc.player.blockPosition().distSqr(SustoBlockEntity.ultimoPos) > 36) return 0;
        return 1 - dt / 20;
    }

    /** Temblor del suelo en el final de la mascletà (más fuerte cuanto más cerca). */
    static float terremoto(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        float t = mc.level.getGameTime() + pt - com.miscelanea.feria.MaquinaFeriaBlockEntity.mascletaIni;
        if (t < 250 || t > 410) return 0;
        double d = Math.sqrt(mc.player.blockPosition().distSqr(com.miscelanea.feria.MaquinaFeriaBlockEntity.mascletaPos));
        if (d > 40) return 0;
        float k = t < 380 ? (t - 250) / 130F : (t > 384 && t < 410 ? 1.4F : 0.3F);
        return k * (float) Math.max(0, 1 - d / 40);
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        float tm = terremoto((float) e.getPartialTick());
        if (tm > 0) {
            float t = (float) (Minecraft.getInstance().level.getGameTime() + e.getPartialTick());
            e.setRoll(e.getRoll() + Mth.sin(t * 4.3F) * 1.2F * tm);
            e.setPitch(e.getPitch() + Mth.sin(t * 5.7F + 1) * 1.0F * tm);
            e.setYaw(e.getYaw() + Mth.cos(t * 3.9F) * 0.6F * tm);
        }
        float f = fuerza((float) e.getPartialTick());
        if (f <= 0) return;
        float t = (float) (Minecraft.getInstance().level.getGameTime() + e.getPartialTick());
        e.setRoll(e.getRoll() + Mth.sin(t * 2.7F) * 4 * f);
        e.setPitch(e.getPitch() + Mth.cos(t * 3.1F) * 2.5F * f);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        float f = fuerza(e.getPartialTick());
        if (f <= 0) return;
        var g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int a = (int) (f * f * 140);
        g.fill(0, 0, w, h, a << 24 | 0x500010);
        int borde = (int) (f * 200) << 24 | 0x200000;
        g.fillGradient(0, 0, w, h / 4, borde, 0);
        g.fillGradient(0, h * 3 / 4, w, h, 0, borde);
    }
}
