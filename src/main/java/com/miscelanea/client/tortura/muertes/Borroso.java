package com.miscelanea.client.tortura.muertes;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/**
 * Desenfoque y visión doble de la víctima en las cinemáticas con canal "borroso" (el veneno de Dream). Usa los mismos
 * shaders de pantalla que la borrachera (shaders/post/borrachera_N.json, tres niveles), escalados con el ajuste de
 * accesibilidad "Efectos de distorsión". Si el shader falla, no hay desenfoque (el resto de la pantalla sigue).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Borroso {
    private Borroso() {}

    private static final ResourceLocation[] POST = {
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_1.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_2.json"),
            new ResourceLocation(Miscelanea.MOD_ID, "shaders/post/borrachera_3.json")};
    private static final PostChain[] CADENAS = new PostChain[3];
    private static final int[][] TAM = new int[3][2];
    private static boolean fallo;
    private static int sinUso;

    @SubscribeEvent
    public static void antesHud(RenderGuiEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        float dist = mc.options == null ? 1F : mc.options.screenEffectScale().get().floatValue();
        float k = MuertesCliente.borroso(e.getPartialTick()) * dist;
        if (k < 0.08F || fallo) return;
        sinUso = 0;
        int nivel = k < 0.4F ? 0 : k < 0.75F ? 1 : 2;
        try {
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            PostChain c = CADENAS[nivel];
            if (c == null) {
                c = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), POST[nivel]);
                c.resize(w, h);
                CADENAS[nivel] = c;
                TAM[nivel][0] = w;
                TAM[nivel][1] = h;
            } else if (TAM[nivel][0] != w || TAM[nivel][1] != h) {
                c.resize(w, h);
                TAM[nivel][0] = w;
                TAM[nivel][1] = h;
            }
            boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), prof = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            c.process(e.getPartialTick());
            mc.getMainRenderTarget().bindWrite(true);
            if (blend) RenderSystem.enableBlend();
            if (prof) RenderSystem.enableDepthTest();
        } catch (Exception ex) {
            fallo = true;
            cerrar();
            mc.getMainRenderTarget().bindWrite(true);
            Miscelanea.LOGGER.warn("[miscelanea] No se pudo usar el desenfoque de la cinemática del veneno", ex);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        // sin usar un rato: se liberan los shaders
        if (e.phase != TickEvent.Phase.END) return;
        boolean alguna = CADENAS[0] != null || CADENAS[1] != null || CADENAS[2] != null;
        if (alguna && ++sinUso > 100) cerrar();
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        cerrar();
        fallo = false;
    }

    private static void cerrar() {
        for (int i = 0; i < CADENAS.length; i++) {
            if (CADENAS[i] != null) {
                try {
                    CADENAS[i].close();
                } catch (Exception ignored) {
                    // ya estaba cerrado
                }
                CADENAS[i] = null;
            }
        }
        sinUso = 0;
    }
}
