package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.movil.ClipCliente;
import com.miscelanea.client.movil.MovilCaptura;
import com.miscelanea.client.movil.NotasVozCliente;
import com.miscelanea.rave.PantallaVideoBlockEntity;
import com.miscelanea.rave.RaveRed;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Los vídeos de las pantallas de vídeo en este cliente: se piden al servidor la primera vez, se guardan y se reproducen en
 * bucle a la vez para todos (la imagen que toca sale de la hora del mundo desde que se puso). El sonido del vídeo suena si
 * estás a menos de 20 bloques, cada vez que vuelve a empezar.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class VideosPantalla {
    private VideosPantalla() {}

    private record Clave(UUID dueno, int clip) {}

    private static final Map<Clave, ClipCliente.Clip> VIDEOS = new HashMap<>();
    private static final Map<Clave, Long> PEDIDOS = new HashMap<>();
    private static final java.util.Set<Clave> FALTAN = new java.util.HashSet<>();

    /** La imagen que enseña cada pantalla (por posición del origen). */
    static final class Imagen {
        DynamicTexture tex;
        ResourceLocation rl;
        int mostrada = -1, w, h;
        Clave de;
        long vuelta = -1;
    }

    private static final Map<BlockPos, Imagen> IMAGENES = new HashMap<>();

    public static void recibir(UUID dueno, int clip, byte[] datos) {
        Clave k = new Clave(dueno, clip);
        PEDIDOS.remove(k);
        ClipCliente.Clip c = datos.length == 0 ? null : ClipCliente.leer(datos);
        if (c == null) FALTAN.add(k);
        else VIDEOS.put(k, c);
    }

    /** ¿Tiene ya el vídeo esa pantalla? (para las pruebas) */
    public static boolean cargado(PantallaVideoBlockEntity be) {
        return be.dueno() != null && VIDEOS.containsKey(new Clave(be.dueno(), be.clip()));
    }

    /** Textura con la imagen que toca ahora en esa pantalla (origen), o null si no hay vídeo. Devuelve también su tamaño. */
    static Imagen imagen(PantallaVideoBlockEntity be, float pt) {
        BlockPos p = be.getBlockPos();
        if (be.dueno() == null || be.clip() < 0) {
            soltar(p);
            return null;
        }
        Clave k = new Clave(be.dueno(), be.clip());
        ClipCliente.Clip c = VIDEOS.get(k);
        if (c == null) {
            Long t = PEDIDOS.get(k);
            if (!FALTAN.contains(k) && (t == null || Util.getMillis() - t > 10000)) {
                PEDIDOS.put(k, Util.getMillis());
                RaveRed.CANAL.sendToServer(new RaveRed.PedirVideo(p));
            }
            return null;
        }
        if (c.imagenes.isEmpty()) return null;
        Imagen im = IMAGENES.computeIfAbsent(p.immutable(), q -> new Imagen());
        if (!k.equals(im.de)) {
            im.de = k;
            im.mostrada = -1;
            im.vuelta = -1;
        }
        long ms = (long) ((be.getLevel().getGameTime() - be.inicio() + pt) * 50);
        int dur = Math.max(1, c.ms());
        long vuelta = ms / dur;
        int i = (int) Math.min(c.imagenes.size() - 1, (ms % dur) * c.fps / 1000);
        // el sonido, cada vez que empieza la vuelta, si estás cerca
        if (vuelta != im.vuelta) {
            im.vuelta = vuelta;
            var pl = Minecraft.getInstance().player;
            if (c.audio != null && c.audio.length > 0 && pl != null && pl.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5) < 20 * 20)
                NotasVozCliente.sonarBlob(c.audio);
        }
        if (i != im.mostrada || im.tex == null) {
            NativeImage img = MovilCaptura.decodificar(c.imagenes.get(i));
            if (img != null) {
                if (im.tex != null && im.tex.getPixels() != null && im.tex.getPixels().getWidth() == img.getWidth() && im.tex.getPixels().getHeight() == img.getHeight()) {
                    im.tex.getPixels().copyFrom(img);
                    img.close();
                } else {
                    if (im.rl != null) Minecraft.getInstance().getTextureManager().release(im.rl);
                    im.tex = new DynamicTexture(img);
                    im.tex.setFilter(true, true);
                    im.rl = Minecraft.getInstance().getTextureManager().register("miscelanea_pantalla_video", im.tex);
                }
                im.tex.upload();
                org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);
                im.w = c.w;
                im.h = c.h;
                im.mostrada = i;
            }
        }
        return im.rl == null ? null : im;
    }

    private static void soltar(BlockPos p) {
        Imagen im = IMAGENES.remove(p);
        if (im != null && im.rl != null) Minecraft.getInstance().getTextureManager().release(im.rl);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        for (Imagen im : IMAGENES.values()) if (im.rl != null) Minecraft.getInstance().getTextureManager().release(im.rl);
        IMAGENES.clear();
        VIDEOS.clear();
        PEDIDOS.clear();
        FALTAN.clear();
    }
}
