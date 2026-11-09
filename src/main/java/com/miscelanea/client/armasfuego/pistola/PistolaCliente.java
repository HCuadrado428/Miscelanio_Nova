package com.miscelanea.client.armasfuego.pistola;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.pistola.Carga;
import com.miscelanea.armasfuego.pistola.GirarPacket;
import com.miscelanea.armasfuego.pistola.RevolverItem;
import com.miscelanea.armasfuego.pistola.Tambor;
import com.miscelanea.network.ModNetwork;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cliente del revólver: agachado + clic izquierdo gira el tambor, la pose del brazo (apuntando, como la ballesta
 * cargada) y el tambor pequeño junto a la mira (abajo a la derecha), con lo que hay en cada recámara y una muesca
 * arriba en la que toca. Tras girarlo no se sabe cuál toca: el tambor se queda donde estaba y la muesca es un "?".
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class PistolaCliente {
    private PistolaCliente() {}

    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/revolver_tambor.png");

    public static final HumanoidModel.ArmPose POSE = HumanoidModel.ArmPose.create("MISCELANEA_REVOLVER", false, (model, entity, arm) -> {
        ModelPart b = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        b.xRot = model.head.xRot - Mth.HALF_PI + 0.08F;
        b.yRot = model.head.yRot + (arm == HumanoidArm.RIGHT ? -0.1F : 0.1F);
        b.zRot = 0;
    });

    private static long ultimoGiro;
    /** Ángulo con el que se ve el tambor (se queda quieto tras girarlo para no chivar cuál toca). */
    private static float angulo, anguloVisto;

    public static void init() {}

    @SubscribeEvent
    public static void clic(InputEvent.InteractionKeyMappingTriggered e) {
        var mc = Minecraft.getInstance();
        var p = mc.player;
        if (p == null || !e.isAttack() || !p.isShiftKeyDown() || !(p.getMainHandItem().getItem() instanceof RevolverItem)) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        long t = p.level().getGameTime();
        if (t - ultimoGiro > 10) {
            ultimoGiro = t;
            ModNetwork.CHANNEL.sendToServer(new GirarPacket());
        }
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null && !(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) return;
        ItemStack s = mc.player.getMainHandItem();
        if (!(s.getItem() instanceof RevolverItem)) return;
        dibujar(g, s, w / 2 + 17, h / 2 + 15, mc.level.getGameTime(), pt);
    }

    /** El tambor con centro en (cx, cy). */
    public static void dibujar(GuiGraphics g, ItemStack s, int cx, int cy, long ahora, float pt) {
        boolean girado = Tambor.girado(s);
        int pos = Tambor.pos(s);
        if (!girado) anguloVisto = -pos * 60F;
        float objetivo = anguloVisto;
        float giro = (ahora - Tambor.giroT(s)) + pt;
        float a = objetivo;
        if (giro >= 0 && giro < Tambor.GIRO_TICKS) {
            // gira rápido y frena (ease-out) hasta quedarse donde estaba
            float k = 1 - giro / Tambor.GIRO_TICKS;
            a = objetivo + 720F * k * k;
        }
        angulo = a;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        // el tambor (21x21, sin agujeros: las recámaras se dibujan donde caen al girar, sin rotar los píxeles)
        g.blit(TEX, -10, -10, 0, 0, 21, 21, 32, 32);
        for (int i = 0; i < Tambor.N; i++) {
            double r = Math.toRadians(angulo + i * 60);
            int x = (int) Math.round(Math.sin(r) * 6.0) - 2, y = (int) Math.round(-Math.cos(r) * 6.0) - 2;
            // recámara vacía: un agujero negro con el borde oscuro
            g.fill(x, y, x + 4, y + 4, 0xFF3A1E14);
            g.fill(x + 1, y + 1, x + 3, y + 3, 0xFF0C0806);
            Carga c = Tambor.en(s, i);
            if (c == Carga.VACIA) continue;
            // cargada: el culote de latón con el pistón del color de la munición en el centro
            g.fill(x, y, x + 4, y + 4, 0xFF8A6114);
            g.fill(x, y, x + 3, y + 3, 0xFFD9A92B);
            g.fill(x, y, x + 1, y + 1, 0xFFFAE27C);
            g.fill(x + 1, y + 1, x + 3, y + 3, 0xFF000000 | oscuro(c.color));
            g.fill(x + 1, y + 1, x + 2, y + 2, c.color);
        }
        // la muesca de la que toca (o "?" si se ha girado)
        if (girado) {
            var f = Minecraft.getInstance().font;
            g.drawString(f, "?", -f.width("?") / 2, -21, 0xFFC27BFF, true);
        } else {
            g.blit(TEX, -2, -14, 22, 0, 5, 4, 32, 32);
        }
        g.pose().popPose();
    }

    private static int claro(int c) {
        int r = Math.min(255, (c >> 16 & 255) + 70), gg = Math.min(255, (c >> 8 & 255) + 70), b = Math.min(255, (c & 255) + 70);
        return r << 16 | gg << 8 | b;
    }

    private static int oscuro(int c) {
        int r = (c >> 16 & 255) / 2, gg = (c >> 8 & 255) / 2, b = (c & 255) / 2;
        return r << 16 | gg << 8 | b;
    }
}
