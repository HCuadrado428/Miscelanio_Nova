package com.miscelanea.client.camaras;

import com.miscelanea.Miscelanea;
import com.miscelanea.camaras.CamarasRed;
import com.miscelanea.client.movil.MovilCaptura;
import com.miscelanea.mixin.MinecraftAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.*;

/**
 * Lado del cliente de las cámaras. Hace dos cosas:
 * <ul>
 *   <li><b>Ver</b>: las teles que están a la vista avisan de qué canal enseñan ({@link #visto}); cada medio segundo se lo
 *   decimos al servidor y los fotogramas JPEG que nos reenvía se guardan en una textura por canal.</li>
 *   <li><b>Transmitir</b>: si el servidor nos manda dibujar una cámara (somos quien tiene su chunk cargado), unas diez veces
 *   por segundo se dibuja el mundo desde su posición en una textura pequeña, se comprime en JPEG y se manda al servidor.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class CamarasCliente {
    private CamarasCliente() {}

    /** Se está dibujando una cámara de vigilancia (la cámara del móvil no debe copiar esa imagen). */
    public static volatile boolean dibujando;

    // ------------------------------------------------------------------ ver
    public static final class Senal {
        public DynamicTexture tex;
        public ResourceLocation rl;
        public long ultimo, primero;
        public int total;
    }

    private static final Map<Integer, Senal> SENALES = new HashMap<>();
    private static final Map<Integer, Long> VISTOS = new HashMap<>();
    private static List<Integer> lista = List.of();
    private static Map<Integer, String> NOMBRES = Map.of();

    /** Nombre de la cámara de un canal (el de la cámara renombrada), o "". */
    public static String nombre(int canal) {
        return NOMBRES.getOrDefault(canal, "");
    }

    /** La tele enseña este canal (se llama al dibujarla). 0 = automático: se pide la lista y se rota. */
    public static void visto(int canal) {
        VISTOS.put(canal, Util.getMillis());
    }

    private static long listaPedida;

    /** Pedir la lista de cámaras con sus nombres (rótulos, flechas de la pantalla grande y del móvil). */
    public static void pedirLista() {
        listaPedida = Util.getMillis();
    }

    /** Canal que enseña de verdad una tele: el suyo, o en automático el que toca (rota cada 6 s entre los que existen). */
    public static int efectivo(int canal) {
        if (canal != 0) return canal;
        if (lista.isEmpty()) return 0;
        return lista.get((int) ((Util.getMillis() / 6000) % lista.size()));
    }

    public static Senal senal(int canal) {
        return SENALES.get(canal);
    }

    /** ¿Llega señal? (un fotograma en el último segundo y medio) */
    public static boolean hayEnvio(int canal) {
        Senal s = SENALES.get(canal);
        return s != null && Util.getMillis() - s.ultimo < 1500;
    }

    /** Fotogramas recibidos de un canal (para las pruebas). */
    public static int recibidos(int canal) {
        Senal s = SENALES.get(canal);
        return s == null ? 0 : s.total;
    }

    /** Cuántas cámaras estoy dibujando ahora (para las pruebas). */
    public static int dibujandoAhora() {
        return ORDENES.size();
    }

    public static long enviados() {
        return enviados;
    }

    private static long enviados;

    // ------------------------------------------------------------------ transmitir
    private record Orden(String dim, double x, double y, double z, float yaw, float pitch, int calidad, long hasta) {}

    private static final Map<Integer, Orden> ORDENES = new LinkedHashMap<>();
    private static int turno;
    private static long ultTx;
    private static boolean avisadoShaders;
    private static TextureTarget grande, pequena;
    private static ArmorStand falsa;

    public static void recibir(int tipo, FriendlyByteBuf b) {
        try {
            switch (tipo) {
                case CamarasRed.S_ORDEN -> {
                    int canal = b.readVarInt();
                    ORDENES.put(canal, new Orden(b.readUtf(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(),
                            b.readUnsignedByte(), Util.getMillis() + 5000));
                }
                case CamarasRed.S_PARAR -> ORDENES.remove(b.readVarInt());
                case CamarasRed.S_FRAME -> {
                    int canal = b.readVarInt();
                    fotograma(canal, b.readByteArray(CamarasRed.MAX_FRAME));
                }
                case CamarasRed.S_LISTA -> {
                    int n = Math.min(100, b.readVarInt());
                    List<Integer> l = new ArrayList<>();
                    Map<Integer, String> nombres = new HashMap<>();
                    for (int i = 0; i < n; i++) {
                        int c = b.readVarInt();
                        l.add(c);
                        nombres.put(c, b.readUtf(32));
                    }
                    lista = l;
                    NOMBRES = nombres;
                }
                default -> {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private static void fotograma(int canal, byte[] jpeg) {
        NativeImage img = MovilCaptura.decodificar(jpeg);
        if (img == null) return;
        try {
            Senal s = SENALES.computeIfAbsent(canal, k -> new Senal());
            if (s.tex == null || s.tex.getPixels() == null || s.tex.getPixels().getWidth() != img.getWidth() || s.tex.getPixels().getHeight() != img.getHeight()) {
                if (s.rl != null) Minecraft.getInstance().getTextureManager().release(s.rl);
                s.tex = new DynamicTexture(img.getWidth(), img.getHeight(), false);
                // con sitio para los mipmaps: DynamicTexture reserva solo un nivel y glGenerateMipmap no hacía nada
                int niveles = Math.max(0, Math.min(4, 31 - Integer.numberOfLeadingZeros(Math.min(img.getWidth(), img.getHeight()))));
                com.mojang.blaze3d.platform.TextureUtil.prepareImage(s.tex.getId(), niveles, img.getWidth(), img.getHeight());
                s.tex.setFilter(true, true);    // suavizado al ampliarla y, al verla pequeña en la tele, reducida con mipmaps (si no, sale granulada)
                s.rl = Minecraft.getInstance().getTextureManager().register("miscelanea_camara", s.tex);
            }
            s.tex.getPixels().copyFrom(img);
            s.tex.upload();
            org.lwjgl.opengl.GL30.glGenerateMipmap(org.lwjgl.opengl.GL11.GL_TEXTURE_2D);   // la textura sigue enlazada tras subirla
            long ahora = Util.getMillis();
            if (s.total == 0) s.primero = ahora;
            s.ultimo = ahora;
            s.total++;
        } finally {
            img.close();
        }
    }

    /** Al salir del mundo: se olvida todo. */
    public static void limpiar() {
        for (Senal s : SENALES.values()) if (s.rl != null) Minecraft.getInstance().getTextureManager().release(s.rl);
        SENALES.clear();
        VISTOS.clear();
        ORDENES.clear();
        VISTAS.clear();
        lista = List.of();
        NOMBRES = Map.of();
    }

    @SubscribeEvent
    public static void salir(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        limpiar();
        falsa = null;
    }

    // ------------------------------------------------------------------ cada tick: decir qué se mira
    private static int tick;
    private static boolean mirabaAlgo;

    /*
     * Mientras se dibuja una cámara, lo que le pasa al jugador que transmite (catalejo, correr, borrachera, sustos, retroceso
     * de las armas...) no puede salir en la imagen: el ángulo de visión y el giro son los de la cámara, y nada más.
     */
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void fovCamara(net.minecraftforge.client.event.ViewportEvent.ComputeFov e) {
        if (dibujando && e.usedConfiguredFov()) e.setFOV(Minecraft.getInstance().options.fov().get());
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void giroCamara(net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles e) {
        if (!dibujando || falsa == null) return;
        e.setYaw(falsa.getYRot());
        e.setPitch(falsa.getXRot());
        e.setRoll(0);
    }

    @SubscribeEvent
    public static void tic(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (++tick % 10 != 0) return;
        long ahora = Util.getMillis();
        VISTOS.values().removeIf(t -> ahora - t > 700);
        // texturas de canales que ya no llegan y lo que veían cámaras que ya no dibujo: fuera (antes duraban hasta desconectar)
        if (tick % 200 == 0) {
            SENALES.entrySet().removeIf(en -> {
                if (VISTOS.containsKey(en.getKey()) || ahora - en.getValue().ultimo < 30_000) return false;
                if (en.getValue().rl != null) mc.getTextureManager().release(en.getValue().rl);
                return true;
            });
            VISTAS.values().removeIf(v -> ahora - (Long) v[2] > 30_000);
        }
        Set<Integer> c = new TreeSet<>(VISTOS.keySet());
        // en automático también interesan los canales que rota la tele (el 0 en sí solo pide la lista de cámaras)
        if (c.contains(0)) c.add(efectivo(0));
        if (ahora - listaPedida < 700) c.add(0);
        if (c.isEmpty() && !mirabaAlgo) return;
        mirabaAlgo = !c.isEmpty();
        CamarasRed.alServidor(CamarasRed.C_VER, b -> {
            b.writeVarInt(c.size());
            for (int x : c) b.writeVarInt(x);
        });
    }

    // ------------------------------------------------------------------ cada fotograma: dibujar lo que me han mandado
    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || ORDENES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        long ahora = Util.getMillis();
        ORDENES.values().removeIf(o -> o.hasta() < ahora);
        if (ORDENES.isEmpty() || mc.level == null || mc.player == null || ahora - ultTx < 95) return;
        ultTx = ahora;
        if (MovilCaptura.shaders() || mc.options.graphicsMode().get() == GraphicsStatus.FABULOUS) {
            if (!avisadoShaders) {
                avisadoShaders = true;
                Miscelanea.LOGGER.warn("[Cámaras] Con shaders o gráficos fabulosos no se puede dibujar desde una cámara: no se transmite");
            }
            return;
        }
        List<Integer> canales = new ArrayList<>(ORDENES.keySet());
        int canal = canales.get(turno++ % canales.size());
        Orden o = ORDENES.get(canal);
        if (!mc.level.dimension().location().toString().equals(o.dim())) return;
        byte[] jpeg = dibujar(mc, canal, o);
        if (jpeg == null) return;
        enviados++;
        CamarasRed.alServidor(CamarasRed.C_FRAME, b -> {
            b.writeVarInt(canal);
            b.writeByteArray(jpeg);
        });
    }

    /** Dibuja el mundo desde la cámara en una textura y devuelve el JPEG de 128x72, o null. */
    /** Lo que ve cada cámara (qué trozos del mundo), de una pasada para la siguiente; y de qué zona de dibujado es. */
    private static final Map<Integer, Object[]> VISTAS = new HashMap<>();
    private static net.minecraft.client.Camera camaraPropia;
    /** Calidad del JPEG: se queda en la que cabe (si fuera cambiando de un fotograma a otro, la imagen "temblaría"). */
    private static int calidadJpeg = 78, fotogramasBien;

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static byte[] dibujar(Minecraft mc, int canal, Orden o) {
        var gr = mc.gameRenderer;
        var lr = (com.miscelanea.mixin.LevelRendererAccessor) mc.levelRenderer;
        // si el juego aún está calculando qué ve el jugador, no se toca: se intenta en el fotograma siguiente
        java.util.concurrent.Future<?> pendiente = lr.miscelanea$calculo();
        if (pendiente != null && !pendiente.isDone()) {
            ultTx = 0;
            return null;
        }
        RenderTarget original = mc.getMainRenderTarget();
        Entity camaraAntes = mc.getCameraEntity();
        CameraType tipoAntes = mc.options.getCameraType();
        net.minecraft.client.Camera camJugador = gr.getMainCamera();
        var miraAntes = mc.hitResult;
        var entidadMiraAntes = mc.crosshairPickEntity;
        Matrix4f proyAntes = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting ordenAntes = RenderSystem.getVertexSorting();
        // lo que ve el jugador (se devuelve tal cual al acabar)
        Object almacenJugador = lr.miscelanea$almacen().get();
        double px = lr.miscelanea$x(), py = lr.miscelanea$y(), pz = lr.miscelanea$z(), prx = lr.miscelanea$rx(), pry = lr.miscelanea$ry();
        boolean recalcularAntes = lr.miscelanea$recalcular();
        Object[] vista = VISTAS.get(canal);
        if (vista != null && vista[1] != lr.miscelanea$zona()) vista = null;      // se ha recargado el dibujado: lo guardado ya no vale
        boolean primera = vista == null;
        float aspecto = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        int sw = o.calidad() <= 0 ? 256 : o.calidad() == 1 ? 384 : 512, sh = sw * 9 / 16;
        int th = sh * 2, tw = Math.max(sw, Math.min(sw * 4, Math.round(th * aspecto)));
        boolean bien = false;
        try {
            if (grande == null || grande.width != tw || grande.height != th) {
                if (grande != null) grande.destroyBuffers();
                grande = new TextureTarget(tw, th, true, Minecraft.ON_OSX);
            }
            if (pequena == null || pequena.width != sw) {
                if (pequena != null) pequena.destroyBuffers();
                pequena = new TextureTarget(sw, sh, false, Minecraft.ON_OSX);
            }
            if (falsa == null || falsa.level() != mc.level) falsa = new ArmorStand(mc.level, o.x(), o.y(), o.z());
            falsa.moveTo(o.x(), o.y() - falsa.getEyeHeight(), o.z(), o.yaw(), o.pitch());
            falsa.setYHeadRot(o.yaw());
            falsa.yHeadRotO = o.yaw();
            if (camaraPropia == null) camaraPropia = new net.minecraft.client.Camera();
            dibujando = true;
            if (!primera) lr.miscelanea$almacen().set(vista[0]);
            lr.miscelanea$recalcular(true);
            ((MinecraftAccessor) mc).miscelanea$setMainRenderTarget(grande);
            ((MinecraftAccessor) mc).miscelanea$setCameraEntity(falsa);
            ((com.miscelanea.mixin.GameRendererAccessor) gr).miscelanea$setMainCamera(camaraPropia);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            gr.setRenderHand(false);
            grande.setClearColor(0.05F, 0.05F, 0.07F, 1.0F);
            grande.clear(Minecraft.ON_OSX);
            grande.bindWrite(true);
            gr.renderLevel(mc.getFrameTime(), Util.getNanos(), new PoseStack());
            bien = true;
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[Cámaras] No se pudo dibujar desde la cámara: {}", t.toString());
            ORDENES.clear();
        } finally {
            // se espera a que acabe el cálculo de lo que ve la cámara (unos milisegundos) y se guarda para la próxima pasada
            boolean acabado = true;
            java.util.concurrent.Future<?> f = lr.miscelanea$calculo();
            if (f != null && !f.isDone()) {
                try {
                    f.get(250, java.util.concurrent.TimeUnit.MILLISECONDS);
                } catch (Throwable t) {
                    acabado = false;
                }
            }
            if (bien && acabado) VISTAS.put(canal, new Object[]{lr.miscelanea$almacen().get(), lr.miscelanea$zona(), Util.getMillis()});
            // y todo vuelve a como lo tenía el jugador
            lr.miscelanea$almacen().set(almacenJugador);
            lr.miscelanea$x(px);
            lr.miscelanea$y(py);
            lr.miscelanea$z(pz);
            lr.miscelanea$rx(prx);
            lr.miscelanea$ry(pry);
            lr.miscelanea$recalcular(recalcularAntes || !acabado);
            lr.miscelanea$recortar().set(true);
            ((MinecraftAccessor) mc).miscelanea$setMainRenderTarget(original);
            ((MinecraftAccessor) mc).miscelanea$setCameraEntity(camaraAntes);
            ((com.miscelanea.mixin.GameRendererAccessor) gr).miscelanea$setMainCamera(camJugador);
            mc.options.setCameraType(tipoAntes);
            mc.hitResult = miraAntes;
            mc.crosshairPickEntity = entidadMiraAntes;
            gr.setRenderHand(true);
            RenderSystem.setProjectionMatrix(proyAntes, ordenAntes);
            original.bindWrite(true);
            dibujando = false;
        }
        // la primera pasada de una cámara aún usa lo que veía el jugador (le faltarían trozos): no se manda
        if (!bien || primera) {
            if (primera) ultTx = 0;
            return null;
        }
        // solo la franja central 16:9: con la ventana en 16:10, 4:3 o ultrapanorámica, la imagen salía aplastada o estirada
        int cx0 = 0, cy0 = 0, cx1 = grande.width, cy1 = grande.height;
        if (grande.width * 9 > grande.height * 16) {
            int cw = grande.height * 16 / 9;
            cx0 = (grande.width - cw) / 2;
            cx1 = cx0 + cw;
        } else {
            int ch = grande.width * 9 / 16;
            cy0 = (grande.height - ch) / 2;
            cy1 = cy0 + ch;
        }
        MovilCaptura.copiar(grande, cx0, cy0, cx1, cy1, pequena);
        original.bindWrite(true);
        try (NativeImage img = MovilCaptura.descargar(pequena)) {
            int max = CamarasRed.MAX_FRAME - 500;
            for (int intento = 0; intento < 6; intento++) {
                byte[] b = jpegGris(img, calidadJpeg);
                if (b != null && b.length <= max) {
                    // con mucho margen durante un buen rato, se sube un punto la calidad
                    if (b.length < max * 0.6 && ++fotogramasBien > 50 && calidadJpeg < 84) {
                        calidadJpeg += 6;
                        fotogramasBien = 0;
                    }
                    return b;
                }
                calidadJpeg = Math.max(30, calidadJpeg - 8);
                fotogramasBien = 0;
            }
        }
        return null;
    }

    /**
     * JPEG en escala de grises (un solo canal: pesa tres veces menos que en color, y se puede ir a mucha más resolución) con un
     * toque de contraste; la imagen viene de un framebuffer (de abajo arriba), así que se le da la vuelta. La tele la tiñe de
     * verde frío al dibujarla (aspecto de cámara de laboratorio).
     */
    private static byte[] jpegGris(NativeImage img, int calidad) {
        int w = img.getWidth(), h = img.getHeight();
        java.nio.ByteBuffer px = org.lwjgl.system.MemoryUtil.memAlloc(w * h);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(16384);
        try {
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int c = img.getPixelRGBA(x, h - 1 - y);
                    float l = 0.299F * (c & 0xFF) + 0.587F * ((c >> 8) & 0xFF) + 0.114F * ((c >> 16) & 0xFF);
                    l = (l - 128F) * 1.12F + 128F;
                    px.put((byte) Math.max(0, Math.min(255, (int) l)));
                }
            px.flip();
            try (org.lwjgl.stb.STBIWriteCallback cb = org.lwjgl.stb.STBIWriteCallback.create((ctx, data, size) -> {
                java.nio.ByteBuffer b = org.lwjgl.stb.STBIWriteCallback.getData(data, size);
                byte[] arr = new byte[size];
                b.get(arr);
                out.write(arr, 0, size);
            })) {
                if (org.lwjgl.stb.STBImageWrite.stbi_write_jpg_to_func(cb, 0L, w, h, 1, px, calidad) == 0) return null;
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(px);
        }
    }

    public static List<Integer> lista() {
        return lista;
    }

    /** Fija el canal de una tele (lo comprueba el servidor: hay que estar cerca). */
    public static void fijarCanal(BlockPos pos, int canal) {
        CamarasRed.alServidor(CamarasRed.C_CANAL, b -> {
            b.writeBlockPos(pos);
            b.writeVarInt(canal);
        });
    }

    // ------------------------------------------------------------------ imagen de estática (sin señal)
    private static DynamicTexture estatica;
    private static ResourceLocation estaticaRl;
    private static long ultEstatica;
    private static final Random RND = new Random();

    /** Textura de ruido que se rehace unas 12 veces por segundo. */
    public static ResourceLocation estatica() {
        if (estatica == null || estatica.getPixels() == null) {
            estatica = new DynamicTexture(128, 72, false);
            estatica.setFilter(true, false);
            estaticaRl = Minecraft.getInstance().getTextureManager().register("miscelanea_estatica_tele", estatica);
            ultEstatica = 0;
        }
        long ahora = Util.getMillis();
        if (ahora - ultEstatica > 80) {
            ultEstatica = ahora;
            NativeImage p = estatica.getPixels();
            for (int y = 0; y < 72; y++)
                for (int x = 0; x < 128; x++) {
                    int v = 30 + RND.nextInt(150);
                    if (RND.nextInt(40) == 0) v = 230;
                    p.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            estatica.upload();
        }
        return estaticaRl;
    }
}
