package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.miscelanea.movil.MovilRed;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * La pantalla del móvil: el aparato (carcasa teñida con el color del objeto, bisel y cámara frontal), la barra de estado
 * (hora del juego, cobertura y batería), la barra de inicio y una pila de {@link Vista vistas} (las apps).
 * <p>
 * Se dibuja a escala entera de píxel (la de la interfaz, o menos si no cabe) para que el pixel art salga nítido.
 * Navegación: clic en los botones; arrastrar para desplazar listas; deslizar hacia arriba desde la barra de inicio o
 * clic en ella = inicio; deslizar desde el borde izquierdo hacia la derecha, Esc o la flecha = atrás.
 */
public class MovilScreen extends Screen {
    public static final int W = 148, H = 256, SX = 8, SY = 14, SW = 132, SH = 228;
    private static final ResourceLocation CARCASA = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/movil/carcasa.png"),
            MARCO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/movil/marco.png");

    private final Deque<Vista> pila = new ArrayDeque<>();
    private Vista saliente;
    private long tAnim, tAbierto;
    private boolean animAtras;
    private float esc = 1, ox, oy;
    // ratón
    private double pulX, pulY;
    private boolean pulsado, arrastrado;
    // banner de aviso dentro del móvil
    private String banTitulo, banTexto;
    private int banApp, banChat;
    private long banT;
    // cámara frontal (selfie)
    private CameraType camaraAntes;

    public MovilScreen() {
        super(Component.translatable("movil.miscelanea.titulo"));
    }

    @Override
    protected void init() {
        double gs = minecraft.getWindow().getGuiScale();
        int fis = Math.max(1, Math.min((int) Math.round(gs), (minecraft.getWindow().getHeight() - 16) / H));
        esc = (float) (fis / gs);
        ox = (float) (Math.round((width - W * esc) / 2 * gs) / gs);
        oy = (float) (Math.round((height - H * esc) / 2 * gs) / gs);
        if (pila.isEmpty()) {
            tAbierto = Util.getMillis();
            abrir(new VistasApps.Inicio(), false);
            MovilRed.alServidor(MovilRed.C_SYNC, null);
            MovilCliente.pose(MovilRed.POSE_MIRAR);
            if (MovilCliente.llEstado != MovilRed.LL_FIN) abrir(new VistasTelefono.Llamada(), false);
        }
    }

    // ------------------------------------------------------------------ navegación
    void abrir(Vista v, boolean animar) {
        Vista antes = pila.peek();
        if (antes != null) antes.cerrada();
        v.m = this;
        pila.push(v);
        v.abierta();
        if (animar && antes != null) {
            saliente = antes;
            animAtras = false;
            tAnim = Util.getMillis();
        }
    }

    void abrir(Vista v) {
        abrir(v, true);
    }

    /** Sustituye la vista de arriba (sin volver a ella con atrás). */
    void cambiar(Vista v) {
        Vista q = pila.pop();
        q.cerrada();
        v.m = this;
        pila.push(v);
        v.abierta();
        saliente = q;
        animAtras = false;
        tAnim = Util.getMillis();
    }

    void atras() {
        if (pila.size() <= 1) {
            onClose();
            return;
        }
        Vista q = pila.pop();
        q.cerrada();
        pila.peek().abierta();
        saliente = q;
        animAtras = true;
        tAnim = Util.getMillis();
    }

    void inicio() {
        if (pila.size() <= 1) return;
        Vista q = pila.peek();
        while (pila.size() > 1) pila.pop().cerrada();
        pila.peek().abierta();
        saliente = q;
        animAtras = true;
        tAnim = Util.getMillis();
    }

    Vista arriba() {
        return pila.peek();
    }

    boolean viendoChat(int id) {
        return pila.peek() instanceof VistasChat.Chat c && c.id == id;
    }

    boolean hayLlamadaEnPila() {
        for (Vista v : pila) if (v instanceof VistasTelefono.Llamada) return true;
        return false;
    }

    void banner(int app, String titulo, String texto, int chat) {
        banApp = app;
        banTitulo = titulo;
        banTexto = texto;
        banChat = chat;
        banT = Util.getMillis();
    }

    // ------------------------------------------------------------------ ciclo
    @Override
    public void tick() {
        Vista v = pila.peek();
        if (v == null) return;
        int ll = MovilCliente.llEstado;
        if (ll != MovilRed.LL_FIN && !hayLlamadaEnPila()) abrir(new VistasTelefono.Llamada());
        if (MovilCliente.chatPendiente >= 0) {
            int id = MovilCliente.chatPendiente;
            MovilCliente.chatPendiente = -1;
            if (v instanceof VistasChat.NuevoChat) cambiar(new VistasChat.Chat(id));
            else abrir(new VistasChat.Chat(id));
        }
        v.tick();
        MovilCliente.pose(camaraAntes != null ? MovilRed.POSE_SELFIE : MovilRed.POSE_MIRAR);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        frontal(false);
        for (Vista v : pila) v.cerrada();
        MovilCliente.cerrado();
    }

    private void frontal(boolean si) {
        var o = minecraft.options;
        if (si && camaraAntes == null) {
            camaraAntes = o.getCameraType();
            o.setCameraType(CameraType.THIRD_PERSON_FRONT);
        } else if (!si && camaraAntes != null) {
            o.setCameraType(camaraAntes);
            camaraAntes = null;
        }
    }

    // ------------------------------------------------------------------ dibujo
    private float subida() {
        float k = Mth.clamp((Util.getMillis() - tAbierto) / 220F, 0, 1);
        k = 1 - (1 - k) * (1 - k) * (1 - k);
        return (1 - k) * (height - oy + 4);
    }

    /** Recorte en coordenadas locales de la pantalla del móvil. */
    void recortar(GuiGraphics g, int x0, int y0, int x1, int y1) {
        float sy = subida();
        int gx0 = Mth.floor(ox + (SX + x0) * esc), gy0 = Mth.floor(oy + sy + (SY + y0) * esc);
        int gx1 = Mth.ceil(ox + (SX + x1) * esc), gy1 = Mth.ceil(oy + sy + (SY + y1) * esc);
        g.enableScissor(gx0, gy0, gx1, gy1);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Vista v = pila.peek();
        if (v == null) return;
        frontal(v.quiereFrontal());
        float sy = subida();
        int lx = (int) Math.floor((mx - ox) / esc - SX), ly = (int) Math.floor((my - oy - sy) / esc - SY);
        // sombra suave detrás del móvil
        g.fillGradient(0, 0, width, height, 0x00000000, 0x30000000);

        g.pose().pushPose();
        g.pose().translate(ox, oy + sy, 0);
        g.pose().scale(esc, esc, 1);
        int c = MovilCliente.colorAbierto;
        RenderSystem.enableBlend();
        g.setColor(((c >> 16) & 0xFF) / 255F, ((c >> 8) & 0xFF) / 255F, (c & 0xFF) / 255F, 1);
        g.blit(CARCASA, 0, 0, 0, 0, W, H, 256, 256);
        g.setColor(1, 1, 1, 1);

        recortar(g, 0, 0, SW, SH);
        g.pose().pushPose();
        g.pose().translate(SX, SY, 0);
        long dt = Util.getMillis() - tAnim;
        if (saliente != null && dt < 180) {
            float k = dt / 180F;
            k = 1 - (1 - k) * (1 - k);
            Vista abajo = animAtras ? v : saliente, encima = animAtras ? saliente : v;
            float desp = animAtras ? k * SW : (1 - k) * SW;
            g.pose().pushPose();
            g.pose().translate((int) (animAtras ? -(1 - k) * SW * 0.3F : -k * SW * 0.3F), 0, 0);
            abajo.dibujar(g, -999, -999, pt);
            g.pose().popPose();
            g.pose().pushPose();
            g.pose().translate((int) desp, 0, 100);
            Ui.rect(g, -2, 0, 2, SH, 0x40000000);
            encima.dibujar(g, animAtras ? -999 : lx - (int) desp, ly, pt);
            g.pose().popPose();
        } else {
            saliente = null;
            v.dibujar(g, lx, ly, pt);
        }
        g.pose().translate(0, 0, 200);
        barraEstado(g, v);
        bannerDibujar(g, lx, ly);
        // barra de inicio
        int ci = v.barraOscura() ? 0xC0000000 : 0xD0FFFFFF;
        Ui.redondo(g, SW / 2 - 18, SH - 5, 36, 2, 1, ci);
        g.pose().popPose();
        g.disableScissor();

        g.pose().translate(0, 0, 300);
        g.blit(MARCO, 0, 0, 0, 0, W, H, 256, 256);
        g.pose().popPose();
    }

    private void barraEstado(GuiGraphics g, Vista v) {
        int fb = v.fondoBarra();
        if (fb != 0) Ui.rect(g, 0, 0, SW, Vista.BARRA, fb);
        int col = v.barraOscura() ? 0xFF101418 : 0xFFFFFFFF;
        Ui.texto(g, MovilCliente.hora(), 6, 2, col);
        // batería
        int bat = MovilCliente.bateria(), bx = SW - 18;
        Ui.rect(g, bx, 3, 12, 6, col);
        Ui.rect(g, bx + 1, 4, 10, 4, v.barraOscura() ? 0xFFF2F2F2 : 0xFF000000);
        Ui.rect(g, bx + 12, 5, 1, 2, col);
        int lleno = Math.max(1, bat * 10 / 100);
        Ui.rect(g, bx + 1, 4, lleno, 4, bat <= 20 ? Ui.ROJO : col);
        String pc = bat + "%";
        Ui.mini(g, pc, bx - 2 - Ui.anchoMini(pc), 4, col);
        // cobertura
        int sx = bx - 6 - Ui.anchoMini(pc) - 10;
        for (int i = 0; i < 4; i++) Ui.rect(g, sx + i * 2, 8 - i * 2 + 1, 1, i * 2 + 1, col);
        if (MovilCliente.silencio) Ui.glifo(g, Ui.G_SILENCIO, sx - 11, 1, col);
        if (MovilCliente.llEstado == MovilRed.LL_ACTIVA && !(v instanceof VistasTelefono.Llamada))
            Ui.redondo(g, 38, 2, 24, 8, 4, Ui.ACENTO);
    }

    private void bannerDibujar(GuiGraphics g, int mx, int my) {
        if (banTitulo == null) return;
        long t = Util.getMillis() - banT;
        if (t > 3500) {
            banTitulo = null;
            return;
        }
        float k = t < 200 ? t / 200F : t > 3200 ? (3500 - t) / 300F : 1;
        int y = (int) (-34 + 47 * k);
        Ui.redondo(g, 4, y, SW - 8, 30, 6, 0xF2252D36);
        Ui.icono(g, banApp, 7, y + 3);
        Ui.texto(g, Ui.cortar(banTitulo, SW - 44), 34, y + 5, 0xFFFFFFFF);
        Ui.texto(g, Ui.cortar(banTexto, SW - 44), 34, y + 16, Ui.TEXTO2);
    }

    private boolean clicBanner(double x, double y) {
        if (banTitulo == null || !Ui.dentro(x, y, 4, 13, SW - 8, 30)) return false;
        banTitulo = null;
        if (banChat >= 0 && !viendoChat(banChat)) abrir(new VistasChat.Chat(banChat));
        return true;
    }

    // ------------------------------------------------------------------ entrada
    private double lx(double mx) {
        return (mx - ox) / esc - SX;
    }

    private double ly(double my) {
        return (my - oy - subida()) / esc - SY;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int b) {
        double x = lx(mx), y = ly(my);
        if (b != 0) {
            if (b == 1 && Ui.dentro(x, y, 0, 0, SW, SH)) atras();
            return true;
        }
        pulsado = Ui.dentro(x, y, 0, 0, SW, SH);
        arrastrado = false;
        pulX = x;
        pulY = y;
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int b, double dx, double dy) {
        if (!pulsado || b != 0) return true;
        double x = lx(mx), y = ly(my);
        if (Math.abs(x - pulX) + Math.abs(y - pulY) > 4) arrastrado = true;
        if (arrastrado && pila.peek() != null && pulY < SH - 12 && pulX > 10) pila.peek().arrastrar(x, y, dx / esc, dy / esc);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int b) {
        if (!pulsado || b != 0) return true;
        pulsado = false;
        double x = lx(mx), y = ly(my);
        Vista v = pila.peek();
        if (v == null) return true;
        if (pulY >= SH - 12 && (y - pulY < -24 || !arrastrado)) {
            if (pila.size() > 1) MovilCliente.clic();
            inicio();
            return true;
        }
        if (pulX <= 10 && x - pulX > 34) {
            atras();
            return true;
        }
        if (!arrastrado && !clicBanner(x, y)) v.clic(x, y);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double d) {
        Vista v = pila.peek();
        if (v != null) v.rueda(d);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        Vista v = pila.peek();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            atras();
            return true;
        }
        if (v != null && v.tecla(key, scan, mods)) return true;
        if ((v == null || !v.escribiendo()) && (MovilCliente.ABRIR.matches(key, scan) || minecraft.options.keyInventory.matches(key, scan))) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        Vista v = pila.peek();
        return v != null && v.caracter(c);
    }
}
