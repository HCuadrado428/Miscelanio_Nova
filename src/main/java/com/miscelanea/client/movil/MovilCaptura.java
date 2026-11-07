package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBIWriteCallback;
import org.lwjgl.stb.STBImage;
import org.lwjgl.stb.STBImageWrite;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Cámara del móvil. Cuando hace falta (visor abierto o videollamada con cámara), justo después de dibujar el mundo y
 * antes de la mano y el HUD ({@code AFTER_PARTICLES}), copia en la GPU el centro de la pantalla a una textura: en
 * vertical una franja 9:16 (360x640) y con el móvil girado una 16:9 (640x360). De ahí salen el visor, la foto (180x320
 * o 320x180) y las imágenes de vídeo (72x128 por defecto, siempre en vertical), que se comprimen en JPEG con STB.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MovilCaptura {
    private MovilCaptura() {}

    public static final int MW = 360, MH = 640, FW = 180, FH = 320;
    private static TextureTarget medio, medioH, foto, fotoH, video;
    private static long pedidoHasta;
    private static boolean hay, hayH, horizontal;

    /** Pedirlo en cada fotograma mientras se necesite (en vertical). */
    public static void pedir() {
        pedir(false);
    }

    /** Pedirlo en cada fotograma mientras se necesite; horizontal = con el móvil girado (16:9). */
    public static void pedir(boolean horiz) {
        if (horiz != horizontal) {
            // al girar, el visor no enseña la imagen vieja de la otra forma
            if (horiz) hayH = false;
            else hay = false;
        }
        horizontal = horiz;
        pedidoHasta = Util.getMillis() + 300;
    }

    public static boolean lista() {
        return (horizontal ? hayH && medioH != null : hay && medio != null) && Util.getMillis() <= pedidoHasta;
    }

    private static TextureTarget nuevo(int w, int h) {
        TextureTarget t = new TextureTarget(w, h, false, Minecraft.ON_OSX);
        t.setFilterMode(GL11.GL_LINEAR);
        return t;
    }

    @SubscribeEvent
    public static void nivel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || Util.getMillis() > pedidoHasta) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        int sw = main.width, sh = main.height;
        if (horizontal) {
            if (medioH == null) medioH = nuevo(MH, MW);
            int cw = Math.min(sw, sh * 16 / 9), ch = Math.min(sh, cw * 9 / 16);
            int x0 = (sw - cw) / 2, y0 = (sh - ch) / 2;
            copiar(main, x0, y0, x0 + cw, y0 + ch, medioH);
            hayH = true;
        } else {
            if (medio == null) medio = nuevo(MW, MH);
            int cw = Math.min(sw, sh * 9 / 16), ch = Math.min(sh, cw * 16 / 9);
            int x0 = (sw - cw) / 2, y0 = (sh - ch) / 2;
            copiar(main, x0, y0, x0 + cw, y0 + ch, medio);
            hay = true;
        }
        main.bindWrite(false);
    }

    private static void copiar(RenderTarget de, int x0, int y0, int x1, int y1, RenderTarget a) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, de.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, a.frameBufferId);
        GlStateManager._glBlitFrameBuffer(x0, y0, x1, y1, 0, 0, a.width, a.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    /** Textura GL del visor (al revés en vertical, como todas las de framebuffer). */
    public static int texturaVisor() {
        TextureTarget t = horizontal ? medioH : medio;
        return t == null ? -1 : t.getColorTextureId();
    }

    /** Foto de 180x320 (o 320x180 en horizontal) en JPEG de como mucho maxBytes (baja la calidad si hace falta), o null. */
    public static byte[] foto(int maxBytes) {
        boolean h = horizontal;
        TextureTarget de = h ? medioH : medio;
        if (!(h ? hayH : hay) || de == null) return null;
        TextureTarget a;
        if (h) {
            if (fotoH == null) fotoH = nuevo(FH, FW);
            a = fotoH;
        } else {
            if (foto == null) foto = nuevo(FW, FH);
            a = foto;
        }
        copiar(de, 0, 0, de.width, de.height, a);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        try (NativeImage img = descargar(a)) {
            for (int q : new int[]{88, 78, 66, 54, 42, 30}) {
                byte[] b = jpeg(img, q);
                if (b != null && b.length <= maxBytes) return b;
            }
        }
        return null;
    }

    /** Imagen de vídeo w x h en JPEG, o null. */
    public static byte[] video(int w, int h, int maxBytes) {
        if (horizontal || !hay || medio == null) return null;
        if (video == null || video.width != w || video.height != h) {
            if (video != null) video.destroyBuffers();
            video = nuevo(w, h);
            if (foto == null) foto = nuevo(FW, FH);
        }
        // en dos pasos (640 → 320 → w) para que no salga tan dentado
        copiar(medio, 0, 0, MW, MH, foto);
        copiar(foto, 0, 0, FW, FH, video);
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        try (NativeImage img = descargar(video)) {
            for (int q : new int[]{58, 42, 28}) {
                byte[] b = jpeg(img, q);
                if (b != null && b.length <= maxBytes) return b;
            }
        }
        return null;
    }

    private static NativeImage descargar(RenderTarget t) {
        NativeImage img = new NativeImage(t.width, t.height, false);
        RenderSystem.bindTexture(t.getColorTextureId());
        img.downloadTexture(0, true);
        return img;
    }

    /** JPEG con STB; la imagen viene de un framebuffer (de abajo arriba), así que se le da la vuelta. */
    private static byte[] jpeg(NativeImage img, int calidad) {
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer px = MemoryUtil.memAlloc(w * h * 3);
        ByteArrayOutputStream out = new ByteArrayOutputStream(16384);
        try {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = img.getPixelRGBA(x, h - 1 - y);
                    px.put((byte) c).put((byte) (c >> 8)).put((byte) (c >> 16));
                }
            px.flip();
            try (STBIWriteCallback cb = STBIWriteCallback.create((ctx, data, size) -> {
                ByteBuffer b = STBIWriteCallback.getData(data, size);
                byte[] arr = new byte[size];
                b.get(arr);
                out.write(arr, 0, size);
            })) {
                if (STBImageWrite.stbi_write_jpg_to_func(cb, 0L, w, h, 3, px, calidad) == 0) return null;
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            MemoryUtil.memFree(px);
        }
    }

    /** JPEG/PNG → NativeImage (RGBA), o null. */
    public static NativeImage decodificar(byte[] datos) {
        if (datos == null || datos.length == 0) return null;
        ByteBuffer in = MemoryUtil.memAlloc(datos.length);
        try (MemoryStack st = MemoryStack.stackPush()) {
            in.put(datos).flip();
            IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
            ByteBuffer px = STBImage.stbi_load_from_memory(in, w, h, c, 4);
            if (px == null) return null;
            try {
                int iw = w.get(0), ih = h.get(0);
                if (iw <= 0 || ih <= 0 || iw > 1024 || ih > 1024) return null;
                NativeImage img = new NativeImage(iw, ih, false);
                for (int y = 0; y < ih; y++)
                    for (int x = 0; x < iw; x++) {
                        int i = (y * iw + x) * 4;
                        int r = px.get(i) & 0xFF, gg = px.get(i + 1) & 0xFF, b = px.get(i + 2) & 0xFF;
                        img.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (gg << 8) | r);
                    }
                return img;
            } finally {
                STBImage.stbi_image_free(px);
            }
        } catch (Throwable t) {
            return null;
        } finally {
            MemoryUtil.memFree(in);
        }
    }

    /** Dibuja una textura GL (de framebuffer: voltearV) en un rectángulo, con recorte de UV. */
    public static void dibujar(GuiGraphics g, int tex, float x, float y, float w, float h, float u0, float v0, float u1, float v1, boolean voltearV) {
        if (tex < 0) return;
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.disableBlend();
        Matrix4f m = g.pose().last().pose();
        if (voltearV) {
            v0 = 1 - v0;
            v1 = 1 - v1;
        }
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bb.vertex(m, x, y, 0).uv(u0, v0).endVertex();
        bb.vertex(m, x, y + h, 0).uv(u0, v1).endVertex();
        bb.vertex(m, x + w, y + h, 0).uv(u1, v1).endVertex();
        bb.vertex(m, x + w, y, 0).uv(u1, v0).endVertex();
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableBlend();
    }

    /** El visor llenando (recortando) un rectángulo de proporción w:h. */
    public static void dibujarVisor(GuiGraphics g, int x, int y, int w, int h) {
        float ar = (float) w / h, src = horizontal ? (float) MH / MW : (float) MW / MH;
        float u0 = 0, u1 = 1, v0 = 0, v1 = 1;
        if (ar > src) {
            float k = src / ar;
            v0 = (1 - k) / 2;
            v1 = 1 - v0;
        } else {
            float k = ar / src;
            u0 = (1 - k) / 2;
            u1 = 1 - u0;
        }
        dibujar(g, texturaVisor(), x, y, w, h, u0, v0, u1, v1, true);
    }
}
