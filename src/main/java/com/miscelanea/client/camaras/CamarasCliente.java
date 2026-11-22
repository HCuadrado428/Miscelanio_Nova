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
                s.tex.setFilter(true, false);   // suavizado al ampliarla en la pantalla
                s.rl = Minecraft.getInstance().getTextureManager().register("miscelanea_camara", s.tex);
            }
            s.tex.getPixels().copyFrom(img);
            s.tex.upload();
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

    @SubscribeEvent
    public static void tic(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (++tick % 10 != 0) return;
        long ahora = Util.getMillis();
        VISTOS.values().removeIf(t -> ahora - t > 700);
        Set<Integer> c = new TreeSet<>(VISTOS.keySet());
        // en automático también interesan los canales que rota la tele
        if (c.contains(0)) c.add(efectivo(0));
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
        byte[] jpeg = dibujar(mc, o);
        if (jpeg == null) return;
        enviados++;
        CamarasRed.alServidor(CamarasRed.C_FRAME, b -> {
            b.writeVarInt(canal);
            b.writeByteArray(jpeg);
        });
    }

    /** Dibuja el mundo desde la cámara en una textura y devuelve el JPEG de 128x72, o null. */
    private static byte[] dibujar(Minecraft mc, Orden o) {
        var gr = mc.gameRenderer;
        RenderTarget original = mc.getMainRenderTarget();
        Entity camaraAntes = mc.getCameraEntity();
        CameraType tipoAntes = mc.options.getCameraType();
        Matrix4f proyAntes = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting ordenAntes = RenderSystem.getVertexSorting();
        float aspecto = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        int sw = o.calidad() <= 0 ? 128 : o.calidad() == 1 ? 192 : 256, sh = sw * 9 / 16;
        int th = sh * 2, tw = Math.max(sw, Math.min(sw * 4, Math.round(th * aspecto)));
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
            dibujando = true;
            ((MinecraftAccessor) mc).miscelanea$setMainRenderTarget(grande);
            mc.setCameraEntity(falsa);
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            gr.setRenderHand(false);
            grande.setClearColor(0.05F, 0.05F, 0.07F, 1.0F);
            grande.clear(Minecraft.ON_OSX);
            grande.bindWrite(true);
            gr.renderLevel(mc.getFrameTime(), Util.getNanos(), new PoseStack());
        } catch (Throwable t) {
            Miscelanea.LOGGER.warn("[Cámaras] No se pudo dibujar desde la cámara: {}", t.toString());
            ORDENES.clear();
            return null;
        } finally {
            ((MinecraftAccessor) mc).miscelanea$setMainRenderTarget(original);
            mc.setCameraEntity(camaraAntes);
            mc.options.setCameraType(tipoAntes);
            gr.setRenderHand(true);
            RenderSystem.setProjectionMatrix(proyAntes, ordenAntes);
            original.bindWrite(true);
            dibujando = false;
        }
        MovilCaptura.copiar(grande, 0, 0, grande.width, grande.height, pequena);
        original.bindWrite(true);
        try (NativeImage img = MovilCaptura.descargar(pequena)) {
            for (int q : new int[]{82, 72, 60, 48, 36, 26}) {
                byte[] b = MovilCaptura.jpeg(img, q);
                if (b != null && b.length <= CamarasRed.MAX_FRAME - 500) return b;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ imagen de estática (sin señal)
    private static DynamicTexture estatica;
    private static ResourceLocation estaticaRl;
    private static long ultEstatica;
    private static final Random RND = new Random();

    /** Textura de ruido que se rehace unas 12 veces por segundo. */
    public static ResourceLocation estatica() {
        if (estatica == null || estatica.getPixels() == null) {
            estatica = new DynamicTexture(64, 36, false);
            estaticaRl = Minecraft.getInstance().getTextureManager().register("miscelanea_estatica_tele", estatica);
            ultEstatica = 0;
        }
        long ahora = Util.getMillis();
        if (ahora - ultEstatica > 80) {
            ultEstatica = ahora;
            NativeImage p = estatica.getPixels();
            for (int y = 0; y < 36; y++)
                for (int x = 0; x < 64; x++) {
                    int v = 30 + RND.nextInt(150);
                    if (RND.nextInt(40) == 0) v = 230;
                    p.setPixelRGBA(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
                }
            estatica.upload();
        }
        return estaticaRl;
    }
}
