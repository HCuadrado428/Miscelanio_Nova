package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.dream.DreamRegistro;
import com.miscelanea.dream.VenenoDream;
import com.miscelanea.dream.VenenoDreamItem;
import com.miscelanea.dream.VenenoPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cliente del veneno de Dream:
 * <ul>
 *   <li>Mientras se está sin veneno (tras extraerlo), un frasco gris que se va llenando junto a la barra rápida, con el
 *       tiempo que falta.</li>
 *   <li>Con «Desangrado», los bordes de la pantalla se tiñen de rojo, laten con cada pérdida de sangre y chorrean
 *       gotas desde arriba.</li>
 * </ul>
 * Los overlays se registran en {@link VenenoClienteMod}.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VenenoCliente {
    private VenenoCliente() {}

    private static final ResourceLocation VINETA = new ResourceLocation(Miscelanea.MOD_ID, "textures/misc/dream_vineta.png");

    private static int restante, total;
    private static ItemStack icono = ItemStack.EMPTY;

    public static void recibir(VenenoPacket p) {
        restante = Math.max(0, p.restante);
        total = Math.max(1, p.total);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            restante = 0;
            return;
        }
        if (restante > 0 && !mc.isPaused()) restante--;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        restante = 0;
    }

    // ------------------------------------------------------------------ indicador «sin veneno»

    public static void hudVeneno(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (restante <= 0 || p == null || mc.options.hideGui || p.isSpectator()) return;
        if (icono.isEmpty()) icono = VenenoDreamItem.nuevo();
        // a la derecha de la barra rápida (más allá si la mano secundaria va a ese lado)
        int x = w / 2 + 91 + 6 + (p.getMainArm() == HumanoidArm.LEFT && !p.getOffhandItem().isEmpty() ? 29 : 0);
        int y = h - 21;
        float lleno = 1.0F - Mth.clamp((restante - pt) / (float) total, 0.0F, 1.0F);
        RenderSystem.enableBlend();
        g.fill(x, y, x + 20, y + 20, 0x88101410);
        g.fill(x, y + 19, x + 20, y + 20, 0xFF2E5A1C);
        g.renderItem(icono, x + 2, y + 2);
        // lo que falta por rellenar: velo oscuro desde arriba
        int alto = Math.round(16 * (1.0F - lleno));
        if (alto > 0) g.fill(RenderType.guiOverlay(), x + 2, y + 2, x + 18, y + 2 + alto, 300, 0xB0202020);
        String t = VenenoDream.tiempo(restante);
        g.drawString(mc.font, t, x + 23, y + 6, 0xFF8FC25A, true);
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ desangrado

    public static void hudDesangrado(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        MobEffectInstance ef = p.getEffect(DreamRegistro.DESANGRADO.get());
        if (ef == null) return;
        float t = p.tickCount + pt;
        float salida = ef.isInfiniteDuration() ? 1.0F : Mth.clamp((ef.getDuration() - pt) / 30.0F, 0.0F, 1.0F);
        float golpe = p.hurtTime > 0 ? (p.hurtTime - pt) / Math.max(1, p.hurtDuration) : 0.0F;
        golpe = Mth.clamp(golpe, 0.0F, 1.0F);
        float a = (0.32F + 0.06F * Mth.sin(t * 0.3F) + 0.45F * golpe) * salida;
        if (a <= 0.01F) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        g.setColor(0.55F, 0.0F, 0.02F, Math.min(1.0F, a));
        g.blit(VINETA, 0, 0, -90, 0.0F, 0.0F, w, h, w, h);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        // chorretones desde el borde de arriba: cada uno baja despacio, se para y vuelve a empezar
        int n = 9 + ef.getAmplifier() * 3;
        int alfa = (int) (Mth.clamp(0.75F * salida, 0.0F, 1.0F) * 255);
        for (int i = 0; i < n; i++) {
            float s1 = frac(Mth.sin(i * 12.9898F) * 43758.547F);
            float s2 = frac(Mth.sin(i * 78.233F) * 12543.12F);
            int x = (int) (s1 * w);
            float ciclo = frac(t / (140.0F + 80.0F * s2) + s2);
            float maxLargo = h * (0.06F + 0.16F * s2);
            float largo = maxLargo * Mth.clamp(ciclo * 1.6F, 0.0F, 1.0F);
            float desv = 1.0F - Mth.clamp((ciclo - 0.75F) / 0.25F, 0.0F, 1.0F); // se desvanece al final
            int ai = (int) (alfa * desv);
            if (ai < 8 || largo < 1) continue;
            int ancho = 1 + (int) (s2 * 2.5F);
            int col = ai << 24 | 0x6A0008;
            int colOsc = ai << 24 | 0x3A0004;
            g.fill(x, 0, x + ancho, (int) largo, col);
            g.fill(x, 0, x + 1, (int) largo, colOsc);
            // la gota gorda de la punta
            int yg = (int) largo;
            g.fill(x - 1, yg, x + ancho + 1, yg + 2 + ancho, col);
            g.fill(x, yg + 2 + ancho, x + ancho, yg + 3 + ancho, col);
        }
        RenderSystem.disableBlend();
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }
}
