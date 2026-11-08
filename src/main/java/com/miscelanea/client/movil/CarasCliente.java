package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.Caras;
import com.miscelanea.movil.MovilRed;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Caras de los jugadores para el móvil con Customizable Player Models. Cada medio minuto, si estás quieto con las manos
 * libres (sin el móvil delante), tu juego dibuja la cabeza de TU modelo de frente en una imagen de 64x64 y, si ha
 * cambiado, la manda al servidor, que la reparte ({@link Caras}). El móvil usa esas imágenes en contactos, chats y
 * llamadas: se ven aunque el otro esté lejos o desconectado, y nunca con el móvil en la mano.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CarasCliente {
    private CarasCliente() {}

    private static final int T = 64;

    private record Cara(DynamicTexture tex, ResourceLocation rl) {}

    private static final Map<UUID, Cara> CARAS = new HashMap<>();
    private static long ultimaFoto, entro = -1;
    private static byte[] ultimaMandada;
    private static TextureTarget lienzo;

    static void recibir(UUID id, byte[] png) {
        try {
            NativeImage img = NativeImage.read(png);
            if (img.getWidth() != T || img.getHeight() != T) {
                img.close();
                return;
            }
            quitar(id);
            DynamicTexture t = new DynamicTexture(img);
            ResourceLocation rl = Minecraft.getInstance().getTextureManager().register("miscelanea_cara", t);
            CARAS.put(id, new Cara(t, rl));
        } catch (Exception ignored) {
        }
    }

    private static void quitar(UUID id) {
        Cara c = CARAS.remove(id);
        if (c != null) Minecraft.getInstance().getTextureManager().release(c.rl());
    }

    /** Dibuja su cara si la tenemos. */
    static boolean dibujar(GuiGraphics g, UUID id, int x, int y, int tam) {
        Cara c = CARAS.get(id);
        if (c == null) return false;
        Ui.redondo(g, x, y, tam, tam, Math.max(1, tam / 8), 0xFF2A3640);
        RenderSystem.enableBlend();
        g.blit(c.rl(), x, y, tam, tam, 0, 0, T, T, T, T);
        RenderSystem.disableBlend();
        return true;
    }

    static boolean tiene(UUID id) {
        return CARAS.containsKey(id);
    }

    static void limpiar() {
        for (UUID id : new java.util.ArrayList<>(CARAS.keySet())) quitar(id);
        ultimaMandada = null;
        entro = -1;
    }

    // ------------------------------------------------------------------ mi cara
    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !Ui.cpm()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) {
            entro = -1;
            return;
        }
        long ahora = Util.getMillis();
        if (entro < 0) entro = ahora;
        if (ahora - entro < 6000 || ahora - ultimaFoto < 30000) return;
        // solo con las manos libres y sin nada raro encima
        if (mc.screen instanceof MovilScreen || MovilCliente.poseDe(p) != MovilRed.POSE_NADA || p.isSleeping() || p.isInvisible()
                || p.isSpectator() || p.isUsingItem() || p.swinging || p.isPassenger() || p.isFallFlying() || p.isVisuallySwimming()) return;
        ultimaFoto = ahora;
        byte[] png = foto(mc, p);
        if (png == null || png.length > Caras.MAX || java.util.Arrays.equals(png, ultimaMandada)) return;
        ultimaMandada = png;
        MovilRed.alServidor(MovilRed.C_CARA, b -> b.writeByteArray(png));
    }

    /** La cabeza de mi modelo, de frente, en PNG de 64x64 con fondo transparente. */
    private static byte[] foto(Minecraft mc, LocalPlayer p) {
        Matrix4f proyAntes = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting ordenAntes = RenderSystem.getVertexSorting();
        PoseStack mv = RenderSystem.getModelViewStack();
        boolean empujado = false;
        try {
            if (lienzo == null) lienzo = new TextureTarget(T, T, true, Minecraft.ON_OSX);
            lienzo.setClearColor(0, 0, 0, 0);
            lienzo.clear(Minecraft.ON_OSX);
            lienzo.bindWrite(true);
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, T, T, 0, 1000, 3000), VertexSorting.ORTHOGRAPHIC_Z);
            mv.pushPose();
            empujado = true;
            mv.setIdentity();
            mv.translate(0, 0, -2000);
            RenderSystem.applyModelViewMatrix();
            GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
            if (!Ui.cabeza(g, p, 0, 0, T, 0)) return null;
            g.flush();
            try (NativeImage img = new NativeImage(T, T, false)) {
                RenderSystem.bindTexture(lienzo.getColorTextureId());
                img.downloadTexture(0, false);
                img.flipY();
                // vacía (algo no se ha dibujado): no se manda
                boolean algo = false;
                for (int y = 0; y < T && !algo; y += 4)
                    for (int x = 0; x < T && !algo; x += 4) algo = (img.getPixelRGBA(x, y) >>> 24) > 0;
                return algo ? img.asByteArray() : null;
            }
        } catch (Exception ex) {
            Miscelanea.LOGGER.debug("[Móvil] No se pudo hacer la foto de la cara", ex);
            return null;
        } finally {
            if (empujado) mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(proyAntes, ordenAntes);
            mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
