package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilItem;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estado del móvil en el cliente (lo que manda el servidor), su tecla, los avisos con el móvil cerrado (notificación,
 * sonido y vibración), el tono de espera al llamar, la alarma, la linterna y el indicador de llamada en el HUD.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MovilCliente {
    private MovilCliente() {}

    public static final KeyMapping ABRIR = new KeyMapping("key.miscelanea.movil", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B, com.miscelanea.client.combate.LlaveCliente.CATEGORIA);

    // ------------------------------------------------------------------ datos del servidor
    public record Contacto(UUID id, String nombre, String numero, boolean conectado) {}

    public record Msg(long sec, UUID de, String nombreDe, long t, String texto, int adjunto, int voz, int vozMs, byte[] onda) {}

    /** Alguien que me comparte su ubicación (posición de hace como mucho ~2 s). */
    public record UbiAmigo(UUID id, String nombre, String dim, double x, double y, double z, float yaw, int restanS, long recibido) {
        public int restan() {
            return Math.max(0, restanS - (int) ((Util.getMillis() - recibido) / 1000));
        }
    }

    /** Con quién comparto yo la mía. */
    public record UbiMia(UUID id, String nombre, int restanS, long recibido) {
        public int restan() {
            return Math.max(0, restanS - (int) ((Util.getMillis() - recibido) / 1000));
        }
    }

    public record Registro(UUID otro, String nombre, long t, int tipo, int dur, boolean video) {}

    public static final class ChatInfo {
        public int id, noLeidos;
        public boolean grupo;
        public String nombre = "";
        public final List<UUID> miembros = new ArrayList<>();
        public Msg ultimo;
    }

    public static final class ChatAbierto {
        public int id;
        public boolean grupo;
        public String nombre = "";
        public final Map<UUID, String> miembros = new HashMap<>();
        public final List<Msg> msgs = new ArrayList<>();
    }

    public record Foto(int id, long t) {}

    public static String miNumero = "---";
    public static boolean silencio, vozServidor, videoServidor, sincronizado;
    public static int tono, fondo, alarma = -1;
    public static final List<Contacto> CONTACTOS = new ArrayList<>();
    public static final List<ChatInfo> CHATS = new ArrayList<>();
    public static final List<Registro> REGISTRO = new ArrayList<>();
    public static ChatAbierto chat;
    public static int chatPendiente = -1;
    public static final List<Foto> FOTOS = new ArrayList<>();
    public static int maxFotos = 40, maxBytesFoto = 30000;
    public static boolean fotosCargadas;
    /** Llamadas perdidas desde la última vez que se abrió el teléfono. */
    public static long registroVisto;

    // llamada
    public static int llEstado = MovilRed.LL_FIN, llMotivo;
    public static UUID llOtro;
    public static String llNombre = "";
    public static boolean llVideoPermitido, miVideo, otroVideo, frontal = true;
    public static long llInicio, llFin;
    public static int vFps = 5, vW = 72, vH = 128, vMax = 9000;

    public static final int MG_SEGUIR = com.miscelanea.movil.MinegramDatos.N_SEGUIR;
    public static boolean linterna;
    // funciones del protocolo 2 (las activa el servidor)
    public static boolean notasVozServidor, minegram, ubicacion, banco, bancoObjetos, linternaReal;
    public static int vozMaxSeg = 30, vozMaxBytes = 160000;
    public static final List<UbiAmigo> UBI_LE = new ArrayList<>();
    public static final List<UbiMia> UBI_MIAS = new ArrayList<>();
    /** Jugadores (id de entidad) con la linterna encendida, para dibujar su haz. */
    public static final java.util.Set<Integer> LINTERNAS = new java.util.HashSet<>();
    public static int colorAbierto = MovilItem.COLOR_BASE;
    private static final Map<Integer, Integer> POSES = new HashMap<>();
    private static int poseLocal;
    private static long ultEspera, alarmaSonando, alarmaProx;
    private static int minutoAntes = -1;

    // ------------------------------------------------------------------ utilidades
    public static boolean vozLocal() {
        return ModList.get().isLoaded("voicechat");
    }

    /** Se pueden grabar y oír notas de voz (voicechat en los dos lados y su códec cargado). */
    public static boolean notasVoz() {
        return notasVozServidor && vozLocal() && com.miscelanea.movil.MovilVoz.codecDisponible();
    }

    public static UbiAmigo ubiDe(UUID id) {
        for (UbiAmigo u : UBI_LE) if (u.id().equals(id)) return u;
        return null;
    }

    public static UbiMia ubiMia(UUID id) {
        for (UbiMia u : UBI_MIAS) if (u.id().equals(id)) return u;
        return null;
    }

    /** Enciende o apaga la linterna (y se lo dice al servidor, que pone la luz de verdad). */
    public static void linterna(boolean on) {
        if (linterna == on) return;
        linterna = on;
        MovilRed.alServidor(MovilRed.C_LINTERNA, b -> b.writeBoolean(on));
    }

    /** Traduce los textos que manda el servidor como {@code \u0001clave\u0001arg...}. */
    public static String traducir(String s) {
        if (s.isEmpty() || s.charAt(0) != '\u0001') return s;
        String[] p = s.substring(1).split("\u0001", -1);
        Object[] args = new Object[p.length - 1];
        System.arraycopy(p, 1, args, 0, args.length);
        return Component.translatable("movil.miscelanea." + p[0], args).getString();
    }

    /** Notificación genérica del servidor (Minegram, Bizum, ubicación...). */
    static void noti(int app, String titulo, String texto, boolean sonar) {
        Minecraft mc = Minecraft.getInstance();
        String tit = traducir(titulo), txt = traducir(texto);
        if (app == Ui.APP_MINEGRAM) MinegramCliente.noVistas++;
        if (sonar && !silencio) sonido(MovilRegistro.MENSAJE.get(), 0.7F, app == Ui.APP_BANCO ? 1.3F : 1.1F);
        if (mc.screen instanceof MovilScreen s) s.banner(app, tit, txt, -1);
        else mc.getToasts().addToast(new MovilToast(app, tit, txt));
    }

    public static Contacto contacto(UUID id) {
        for (Contacto c : CONTACTOS) if (c.id().equals(id)) return c;
        return null;
    }

    public static ChatInfo chatInfo(int id) {
        for (ChatInfo c : CHATS) if (c.id == id) return c;
        return null;
    }

    public static int noLeidos() {
        int n = 0;
        for (ChatInfo c : CHATS) n += c.noLeidos;
        return n;
    }

    public static int perdidas() {
        int n = 0;
        for (Registro r : REGISTRO) if (r.tipo() == com.miscelanea.movil.MovilDatos.R_PERDIDA && r.t() > registroVisto) n++;
        return n;
    }

    public static ItemStack movilDe(Player p) {
        if (MovilRegistro.esMovil(p.getMainHandItem())) return p.getMainHandItem();
        if (MovilRegistro.esMovil(p.getOffhandItem())) return p.getOffhandItem();
        for (ItemStack s : p.getInventory().items) if (MovilRegistro.esMovil(s)) return s;
        return ItemStack.EMPTY;
    }

    public static boolean tengoMovil() {
        Player p = Minecraft.getInstance().player;
        return p != null && !movilDe(p).isEmpty();
    }

    public static void sonido(SoundEvent s, float vol, float tono) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, tono, vol));
    }

    public static void clic() {
        sonido(MovilRegistro.CLIC.get(), 0.45F, 0.95F + (float) Math.random() * 0.1F);
    }

    /** Hora del juego en minutos (0..1439). */
    public static int minutoDelDia() {
        var l = Minecraft.getInstance().level;
        if (l == null) return 0;
        long t = Math.floorMod(l.getDayTime() + 6000L, 24000L);
        return (int) (t * 1440 / 24000);
    }

    public static String hora() {
        int m = minutoDelDia();
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    public static int dia() {
        var l = Minecraft.getInstance().level;
        return l == null ? 1 : (int) ((l.getDayTime() + 6000L) / 24000L) + 1;
    }

    /** Batería de mentira: se gasta a lo largo del día y "se carga" de noche. */
    public static int bateria() {
        var l = Minecraft.getInstance().level;
        if (l == null) return 100;
        long t = Math.floorMod(l.getDayTime(), 24000L);
        return t < 13000 ? 100 - (int) (t * 72 / 13000) : 28 + (int) ((t - 13000) * 72 / 11000);
    }

    // ------------------------------------------------------------------ abrir / pose
    public static void abrir(int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MovilScreen) return;
        colorAbierto = color;
        mc.setScreen(new MovilScreen());
    }

    public static void pose(int modo) {
        if (modo == poseLocal) return;
        poseLocal = modo;
        MovilRed.alServidor(MovilRed.C_POSE, b -> b.writeByte(modo));
    }

    public static int poseDe(AbstractClientPlayer p) {
        if (p == Minecraft.getInstance().player) return poseLocal;
        return POSES.getOrDefault(p.getId(), MovilRed.POSE_NADA);
    }

    static void poseRemota(int id, int modo) {
        POSES.put(id, modo);
    }

    /** Al cerrar la pantalla: si sigue la llamada, se sigue sujetando el móvil. */
    static void cerrado() {
        pose(llEstado == MovilRed.LL_ACTIVA ? MovilRed.POSE_MIRAR : MovilRed.POSE_NADA);
        if (llEstado == MovilRed.LL_ACTIVA && miVideo) MovilRed.alServidor(MovilRed.C_VIDEO, b -> b.writeByteArray(new byte[0]));
    }

    public static void ajustes() {
        MovilRed.alServidor(MovilRed.C_AJUSTES, b -> {
            b.writeBoolean(silencio);
            b.writeByte(tono);
            b.writeByte(fondo);
            b.writeInt(alarma);
        });
    }

    public static void llamar(UUID otro, boolean video) {
        if (!vozLocal()) {
            aviso(Component.translatable("movil.miscelanea.necesitas_svc"));
            return;
        }
        MovilRed.alServidor(MovilRed.C_LLAMAR, b -> {
            b.writeUUID(otro);
            b.writeBoolean(video);
        });
        miVideo = video;
        frontal = true;
    }

    /** Aviso corto: dentro del móvil si está abierto; si no, en la barra de acción. */
    public static void aviso(Component c) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MovilScreen s) s.banner(Ui.APP_AJUSTES, Component.translatable("movil.miscelanea.titulo").getString(), c.getString(), -1);
        else if (mc.player != null) mc.player.displayClientMessage(c, true);
    }

    // ------------------------------------------------------------------ llegadas
    static void mensaje(int chatId, boolean grupo, String nombreChat, Msg m, boolean avisar) {
        Minecraft mc = Minecraft.getInstance();
        ChatInfo ci = chatInfo(chatId);
        if (ci == null) {
            ci = new ChatInfo();
            ci.id = chatId;
            ci.grupo = grupo;
            ci.nombre = nombreChat;
        } else CHATS.remove(ci);
        CHATS.add(0, ci);
        ci.ultimo = m;
        MovilScreen s = mc.screen instanceof MovilScreen ms ? ms : null;
        boolean viendo = s != null && s.viendoChat(chatId);
        if (chat != null && chat.id == chatId) {
            chat.msgs.add(m);
            if (viendo) MovilRed.alServidor(MovilRed.C_CHAT_LEIDO, b -> b.writeVarInt(chatId));
        }
        boolean mio = mc.player != null && m.de().equals(mc.player.getUUID());
        if (!mio && !viendo) ci.noLeidos++;
        if (!avisar || viendo) return;
        String texto = m.adjunto() >= 0 ? Component.translatable("movil.miscelanea.foto_adjunta").getString()
                : m.voz() >= 0 ? Component.translatable("movil.miscelanea.nota_voz", duracion(m.vozMs() / 1000)).getString() : m.texto();
        String titulo = grupo ? m.nombreDe() + " @ " + nombreChat : m.nombreDe();
        if (!silencio) sonido(MovilRegistro.MENSAJE.get(), 0.8F, 1.0F);
        if (s != null) s.banner(Ui.APP_CHATS, titulo, texto, chatId);
        else mc.getToasts().addToast(new MovilToast(Ui.APP_CHATS, titulo, texto));
    }

    static void llamada(int estado, UUID otro, String nombre, boolean video, int motivo) {
        Minecraft mc = Minecraft.getInstance();
        int antes = llEstado;
        llEstado = estado;
        llOtro = otro;
        llNombre = nombre;
        llMotivo = motivo;
        if (estado == MovilRed.LL_ENTRANTE) {
            miVideo = video && videoServidor;
            frontal = true;
            if (!(mc.screen instanceof MovilScreen))
                mc.getToasts().addToast(new MovilToast(Ui.APP_LLAMAR, nombre, Component.translatable(video ? "movil.miscelanea.videollamada_entrante_tecla"
                        : "movil.miscelanea.llamada_entrante_tecla", ABRIR.getTranslatedKeyMessage()).getString()));
        } else if (estado == MovilRed.LL_ACTIVA) {
            llInicio = Util.getMillis();
            otroVideo = false;
            if (!(mc.screen instanceof MovilScreen)) pose(MovilRed.POSE_MIRAR);
        } else if (estado == MovilRed.LL_FIN) {
            llFin = Util.getMillis();
            FotoCache.quitarVideo();
            otroVideo = false;
            if (antes != MovilRed.LL_FIN) sonido(MovilRegistro.COLGAR.get(), 0.7F, 1.0F);
            if (!(mc.screen instanceof MovilScreen)) {
                pose(MovilRed.POSE_NADA);
                if (antes == MovilRed.LL_ENTRANTE && motivo == com.miscelanea.movil.Llamadas.FIN_SIN_RESPUESTA)
                    mc.getToasts().addToast(new MovilToast(Ui.APP_LLAMAR, nombre, Component.translatable("movil.miscelanea.llamada_perdida").getString()));
            }
        }
    }

    static void videoParams(boolean permitido, int fps, int w, int h, int max) {
        llVideoPermitido = permitido;
        vFps = Math.max(1, fps);
        vW = Math.max(16, w);
        vH = Math.max(16, h);
        vMax = max;
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null) return;
        while (ABRIR.consumeClick()) {
            if (mc.screen != null) continue;
            ItemStack s = movilDe(p);
            if (s.isEmpty()) p.displayClientMessage(Component.translatable("msg.miscelanea.movil_no"), true);
            else abrir(MovilItem.color(s));
        }
        long ms = Util.getMillis();
        if (llEstado == MovilRed.LL_SALIENTE && ms - ultEspera > 3000) {
            ultEspera = ms;
            sonido(MovilRegistro.ESPERA.get(), 0.55F, 1.0F);
        }
        // alarma
        int min = minutoDelDia();
        if (alarma >= 0 && minutoAntes >= 0 && min != minutoAntes) {
            boolean cruza = minutoAntes < min ? (alarma > minutoAntes && alarma <= min) : (alarma > minutoAntes || alarma <= min);
            if (cruza && min - minutoAntes < 120 && tengoMovil()) {
                alarmaSonando = ms;
                alarmaProx = ms;
                mc.getToasts().addToast(new MovilToast(Ui.APP_RELOJ, Component.translatable("movil.miscelanea.alarma").getString(),
                        String.format("%02d:%02d", alarma / 60, alarma % 60)));
            }
        }
        minutoAntes = min;
        if (alarmaSonando > 0) {
            long t = ms - alarmaSonando;
            if (t > 9000 || mc.screen instanceof MovilScreen) alarmaSonando = 0;
            else if (ms >= alarmaProx) {
                alarmaProx = ms + 1600;
                sonido(silencio ? MovilRegistro.VIBRAR.get() : MovilRegistro.ALARMA.get(), 0.9F, 1.0F);
            }
        }
        if (linterna && movilEnMano(p) == null && !(mc.screen instanceof MovilScreen)) linterna(false);
        NotasVozCliente.tick();
        MapaCache.tick();
    }

    private static ItemStack movilEnMano(Player p) {
        if (MovilRegistro.esMovil(p.getMainHandItem())) return p.getMainHandItem();
        if (MovilRegistro.esMovil(p.getOffhandItem())) return p.getOffhandItem();
        return null;
    }

    private static final ResourceLocation LUZ = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/movil/luz.png");

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (linterna && mc.screen == null) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
            int t = (int) (Math.min(w, h) * 1.1F);
            g.setColor(1F, 0.97F, 0.85F, 0.55F);
            g.blit(LUZ, w / 2 - t / 2, h / 2 - t / 2, t, t, 0, 0, 128, 128, 128, 128);
            g.setColor(1, 1, 1, 1);
            g.flush();
            RenderSystem.defaultBlendFunc();
        }
        if (llEstado != MovilRed.LL_FIN && !(mc.screen instanceof MovilScreen)) {
            String s = llEstado == MovilRed.LL_ACTIVA ? duracion((Util.getMillis() - llInicio) / 1000) + "  " + llNombre
                    : Component.translatable(llEstado == MovilRed.LL_ENTRANTE ? "movil.miscelanea.entrante_corto" : "movil.miscelanea.llamando_corto", llNombre).getString();
            int ancho = Ui.ancho(s) + 18, x = w / 2 - ancho / 2;
            Ui.redondo(g, x, 3, ancho, 13, 6, 0xD0101418);
            boolean parpadeo = llEstado != MovilRed.LL_ACTIVA && (Util.getMillis() / 400) % 2 == 0;
            Ui.circulo(g, x + 7, 9, 3, parpadeo ? 0xFF7FF0B0 : Ui.ACENTO);
            Ui.texto(g, s, x + 13, 6, 0xFFFFFFFF);
        }
    }

    public static String duracion(long s) {
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60) : String.format("%02d:%02d", s / 60, s % 60);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        CONTACTOS.clear();
        CHATS.clear();
        REGISTRO.clear();
        FOTOS.clear();
        POSES.clear();
        chat = null;
        chatPendiente = -1;
        fotosCargadas = false;
        sincronizado = false;
        llEstado = MovilRed.LL_FIN;
        poseLocal = 0;
        linterna = false;
        FotoCache.limpiar();
        UBI_LE.clear();
        UBI_MIAS.clear();
        LINTERNAS.clear();
        NotasVozCliente.limpiar();
        MinegramCliente.limpiar();
        BancoCliente.limpiar();
        MapaCache.salir();
    }
}
