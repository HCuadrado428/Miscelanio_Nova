package com.miscelanea.client.rave;

import com.miscelanea.rave.EstroboBlockEntity;
import com.miscelanea.rave.LucesRave;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Destello del estroboscopio en pantalla: si hay uno encendido a menos de 24 bloques, a la vista y por delante, en cada
 * destello la pantalla se aclara (más cuanto más cerca). Respeta la opción de accesibilidad "Ocultar destellos".
 */
public final class EstroboDestello {
    private EstroboDestello() {}

    private static long ultimoTick = -1;
    private static float fuerza;

    public static void dibujar(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideLightningFlash().get()) return;
        long t = mc.level.getGameTime();
        if (t != ultimoTick) {
            ultimoTick = t;
            fuerza = calcular(mc, t);
        }
        if (fuerza <= 0.01F) return;
        int a = (int) (Math.min(0.6F, fuerza) * 255);
        g.fill(0, 0, w, h, (a << 24) | 0xF4F8FF);
    }

    private static float calcular(Minecraft mc, long t) {
        Vec3 ojo = mc.player.getEyePosition();
        Vec3 mira = mc.player.getViewVector(1F);
        float max = 0;
        for (EstroboBlockEntity e : EstroboBlockEntity.CLIENTE) {
            if (e.isRemoved() || e.getLevel() != mc.level) continue;
            var s = e.getBlockState();
            if (!s.hasProperty(LucesRave.ENCENDIDA) || !s.getValue(LucesRave.ENCENDIDA)) continue;
            if (!LucesRave.destello(t, s.getValue(LucesRave.MODO), e.getBlockPos())) continue;
            Vec3 c = Vec3.atCenterOf(e.getBlockPos());
            double d = c.distanceTo(ojo);
            if (d > 24) continue;
            Vec3 hacia = c.subtract(ojo).normalize();
            double frente = hacia.dot(mira);
            if (frente < 0.2) continue;
            var r = mc.level.clip(new ClipContext(ojo, c.subtract(hacia.scale(0.7)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (r.getType() == HitResult.Type.BLOCK) continue;
            max = Math.max(max, (float) ((1 - d / 24) * (0.4 + 0.6 * frente)));
        }
        return max * 0.7F;
    }
}
