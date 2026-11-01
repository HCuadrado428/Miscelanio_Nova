package com.miscelanea.client.arcade;

import com.miscelanea.arcade.ArcadeAbrirPacket;
import com.miscelanea.arcade.ArcadeAccionPacket;
import com.miscelanea.arcade.ArcadeFramePacket;
import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.client.consola.AstroPatrulla;
import com.miscelanea.client.consola.Botones;
import com.miscelanea.client.consola.JuegoGB;
import com.miscelanea.client.consola.Juegos;
import com.miscelanea.client.consola.Lcd;
import com.miscelanea.client.consola.LcdTextura;
import com.miscelanea.client.consola.MusicaGB;
import com.miscelanea.consola.Juego;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Map;

/** Pantalla de la recreativa: menú con récords del servidor, créditos, juegos a todo color y retransmisión a los que miran. */
public class ArcadeScreen extends Screen {
    private enum Estado {MENU, ESPERA, JUEGO}

    private static final int CW = 208, CH = 232, LX = 24, LY = 36;
    private static final Map<String, int[]> PALETAS = Map.of(
            "astro", AstroPatrulla.PALETA,
            "tetrominos", new int[]{0xFFF8F8F0, 0xFF88C8F8, 0xFFE05050, 0xFF181830},
            "serpiente", new int[]{0xFFE8F8C0, 0xFF98D058, 0xFF388030, 0xFF102810},
            "ladrillos", new int[]{0xFFF8E8D0, 0xFFF0A040, 0xFFC03030, 0xFF301010},
            "pong", new int[]{0xFF101018, 0xFF404858, 0xFFA0A8B8, 0xFFF8F8F8},
            "pajaro", new int[]{0xFFA8E0F8, 0xFF78C868, 0xFF308840, 0xFF102018},
            "invasores", new int[]{0xFF080818, 0xFF304080, 0xFF70D070, 0xFFF0F0F0});
    private static int ultimoSel;

    private final BlockPos pos;
    private int creditos;
    private final boolean libre;
    private final Botones bot = new Botones();
    private final Lcd lcd = new Lcd();
    private LcdTextura tex;
    private Estado estado = Estado.MENU;
    private int t, sel = ultimoSel, aviso, tFin, pasos;
    private String avisoTxt = "";
    private JuegoGB juego;
    private String juegoId;
    private boolean finVisto;
    private int puesto;
    private long ultimo;
    private double acum;
    private MusicaGB mus;

    public ArcadeScreen(BlockPos pos, int creditos, boolean libre) {
        super(Component.translatable("block.miscelanea.recreativa"));
        this.pos = pos;
        this.creditos = creditos;
        this.libre = libre;
        JuegoGB.sonido(ModSounds.GB_INICIO, 1.2F);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        int b = Botones.boton(key);
        if (b >= 0) {
            botonPulsar(b);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        int b = Botones.boton(key);
        if (b >= 0) {
            botonSoltar(b);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    // acceso a los métodos de paquete de Botones desde otro paquete
    private void botonPulsar(int b) {
        Botones.Acceso.pulsar(bot, b);
    }

    private void botonSoltar(int b) {
        Botones.Acceso.soltar(bot, b);
    }

    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.CERRAR, "", 0));
        pararMusica();
        if (tex != null) tex.cerrar();
        super.removed();
    }

    /** Respuestas del servidor a meter ficha / empezar. */
    void respuesta(ArcadeAbrirPacket m) {
        int antes = creditos;
        creditos = m.creditos;
        switch (m.modo) {
            case 1 -> {
                if (creditos <= antes) mostrarAviso(I18n.get("consola.miscelanea.sin_fichas"));
            }
            case 2 -> empezar();
            case 3 -> {
                estado = Estado.MENU;
                mostrarAviso(I18n.get("consola.miscelanea.inserta_ficha"));
                JuegoGB.sonido(ModSounds.GB_FIN, 1.6F);
            }
        }
    }

    private void mostrarAviso(String s) {
        avisoTxt = s;
        aviso = 100;
    }

    private void pedirPartida() {
        estado = Estado.ESPERA;
        ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.EMPEZAR, "", 0));
    }

    private void empezar() {
        juegoId = ArcadeJuegos.IDS[sel];
        if (juegoId.equals("astro")) juego = new AstroPatrulla(bot);
        else {
            Juego j = Juego.TETROMINOS;
            for (Juego x : Juego.values()) if (x.id.equals(juegoId)) j = x;
            juego = Juegos.crear(j, bot);
        }
        juego.reiniciar();
        estado = Estado.JUEGO;
        finVisto = false;
        puesto = 0;
        JuegoGB.sonido(ModSounds.ARCADE_FICHA, 1.3F);
        pararMusica();
        mus = new MusicaGB(juegoId.equals("astro") ? ModSounds.ARCADE_MUSICA.get() : ModSounds.GB_MUSICA.get());
        Minecraft.getInstance().getSoundManager().play(mus);
    }

    private void pararMusica() {
        if (mus != null) {
            Minecraft.getInstance().getSoundManager().stop(mus);
            mus = null;
        }
    }

    private void paso() {
        t++;
        if (aviso > 0) aviso--;
        switch (estado) {
            case MENU -> {
                int n = ArcadeJuegos.IDS.length;
                if (bot.repite(Botones.ABAJO, 14, 5)) {
                    sel = (sel + 1) % n;
                    JuegoGB.sonido(ModSounds.GB_MOVER);
                }
                if (bot.repite(Botones.ARRIBA, 14, 5)) {
                    sel = (sel + n - 1) % n;
                    JuegoGB.sonido(ModSounds.GB_MOVER);
                }
                ultimoSel = sel;
                if (bot.pulsa(Botones.START) && creditos <= 0 && !libre)
                    ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.FICHA, "", 0));
                else if (bot.pulsa(Botones.A) || bot.pulsa(Botones.START)) pedirPartida();
            }
            case ESPERA -> {}
            case JUEGO -> {
                if (juego.fin) {
                    if (!finVisto) {
                        finVisto = true;
                        tFin = 0;
                        pararMusica();
                        JuegoGB.sonido(ModSounds.GB_FIN);
                        ListTag tb = ArcadeCliente.tabla(juegoId);
                        puesto = 0;
                        if (juego.puntos > 0) {
                            puesto = tb.size() + 1;
                            for (int i = 0; i < tb.size(); i++)
                                if (juego.puntos > tb.getCompound(i).getInt("p")) {
                                    puesto = i + 1;
                                    break;
                                }
                            if (puesto > 10) puesto = 0;
                            ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.RECORD, juegoId, juego.puntos));
                        }
                    }
                    tFin++;
                    if (tFin > 40 && (bot.pulsa(Botones.A) || bot.pulsa(Botones.START))) {
                        if (creditos <= 0 && !libre) ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.FICHA, "", 0));
                        else pedirPartida();
                    } else if (tFin > 40 && bot.pulsa(Botones.B)) {
                        estado = Estado.MENU;
                        t = 0;
                    }
                } else juego.paso();
            }
        }
        // retransmitir la pantalla a los que miran (10 imágenes por segundo)
        if (++pasos % 6 == 0) ModNetwork.CHANNEL.sendToServer(new ArcadeFramePacket(pos, Trama.codificar(lcd)));
    }

    // ------------------------------------------------------------------ dibujo de la pantalla
    private void pantalla() {
        Lcd l = lcd;
        l.font = font;
        l.empezar();
        if (estado == Estado.JUEGO) {
            l.paleta = PALETAS.getOrDefault(juegoId, Lcd.VERDE);
            l.rect(0, 0, Lcd.W, Lcd.H, 0);
            juego.dibujar(l);
            if (juego.fin) fin(l);
            return;
        }
        l.paleta = AstroPatrulla.PALETA;
        l.rect(0, 0, Lcd.W, Lcd.H, 1);
        l.rect(0, 0, Lcd.W, 14, 12);
        l.textoC("MISCELÁNEA ARCADE", 80, 3, 15);
        for (int i = 0; i < ArcadeJuegos.IDS.length; i++) {
            String id = ArcadeJuegos.IDS[i];
            int y = 18 + i * 15;
            boolean s = i == sel;
            if (s) l.rect(2, y - 2, 156, 13, 2);
            if (s && (t / 15) % 2 == 0) l.sprite(new String[]{"9...", "99..", "999.", "99..", "9..."}, 5, y + 1);
            l.texto(I18n.get("juego.miscelanea." + id), 12, y, id.equals("astro") ? 13 : (s ? 4 : 5));
            ListTag tb = ArcadeCliente.tabla(id);
            if (!tb.isEmpty()) l.textoD(Integer.toString(tb.getCompound(0).getInt("p")), 155, y, s ? 9 : 6);
        }
        l.rect(0, 124, Lcd.W, 20, 0);
        if (aviso > 0) {
            if ((aviso / 8) % 2 == 0) l.textoC(avisoTxt, 80, 130, 7);
        } else if (estado == Estado.ESPERA) l.textoC("...", 80, 130, 4);
        else if (libre) l.textoC(I18n.get("consola.miscelanea.libre"), 80, 130, 10);
        else if (creditos > 0) l.textoC(I18n.get("consola.miscelanea.creditos", creditos), 80, 130, 9);
        else if ((t / 20) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.start_ficha"), 80, 130, 8);
        // mejor jugador del juego seleccionado
        ListTag tb = ArcadeCliente.tabla(ArcadeJuegos.IDS[sel]);
        if (!tb.isEmpty() && aviso == 0 && estado == Estado.MENU) {
            CompoundTag c = tb.getCompound(0);
            l.textoC("♛ " + c.getString("n"), 80, 122 - 9, 3);
        }
    }

    private void fin(Lcd l) {
        boolean color16 = l.paleta.length == 16;
        int tx = color16 ? 4 : 3, ac = color16 ? 9 : 2;
        l.caja(8, 8, 144, 128);
        l.textoC(I18n.get("consola.miscelanea.fin"), 80, 14, tx);
        l.textoC(I18n.get("consola.miscelanea.puntos") + " " + juego.puntos, 80, 26, tx);
        ListTag tb = ArcadeCliente.tabla(juegoId);
        for (int i = 0; i < Math.min(7, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int y = 40 + i * 10;
            boolean yo = c.getString("n").equals(Minecraft.getInstance().getUser().getName()) && c.getInt("p") == juego.puntos;
            int col = yo && (tFin / 8) % 2 == 0 ? ac : tx;
            l.texto((i + 1) + ". " + recorta(c.getString("n")), 16, y, col);
            l.textoD(Integer.toString(c.getInt("p")), 144, y, col);
        }
        if (puesto == 1 && (tFin / 10) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.record_mundial"), 80, 112, ac);
        else if (puesto > 1) l.textoC(I18n.get("consola.miscelanea.top", puesto), 80, 112, ac);
        if (tFin > 40) l.textoC(I18n.get("consola.miscelanea.otra_ficha"), 80, 124, tx);
    }

    private static String recorta(String s) {
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long ahora = Util.getMillis();
        if (ultimo == 0) ultimo = ahora;
        acum = Math.min(acum + (ahora - ultimo), 150);
        ultimo = ahora;
        while (acum >= 1000.0 / 60.0) {
            acum -= 1000.0 / 60.0;
            paso();
            Botones.Acceso.paso(bot);
        }
        renderBackground(g);
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        float k = Math.min(width * 0.96F / CW, (height - 16) * 0.96F / CH);
        float kr = (float) (Math.floor(k * gs) / gs);
        if (kr >= 0.5F) k = kr;
        float ox = (width - CW * k) / 2F, oy = (height - 16 - CH * k) / 2F;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1);
        mueble(g);
        g.pose().pushPose();
        g.pose().translate(LX, LY, 0);
        pantalla();
        if (tex == null) tex = new LcdTextura("arcade_local");
        tex.subir(lcd);
        tex.dibujar(g, lcd);
        // brillo de cristal y líneas de barrido
        for (int y = 0; y < Lcd.H; y += 2) g.fill(0, y, Lcd.W, y + 1, 0x14000000);
        g.fill(4, 4, 60, 6, 0x18FFFFFF);
        g.pose().popPose();
        g.pose().popPose();
        String ayuda = I18n.get("consola.miscelanea.controles_arcade");
        float s = Math.min(1F, (width - 8F) / font.width(ayuda));
        g.pose().pushPose();
        g.pose().translate(width / 2F, height - 12, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, ayuda, -font.width(ayuda) / 2, 0, 0xFFB0B0B0, false);
        g.pose().popPose();
    }

    private static void circulo(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy < r; dy++) {
            float yy = dy + 0.5F;
            int w = Math.round((float) Math.sqrt(r * r - yy * yy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, col);
        }
    }

    private void mueble(GuiGraphics g) {
        boolean[] ab = Botones.Acceso.abajo(bot);
        g.fill(-4, -4, CW + 4, CH + 4, 0x80000000);
        g.fill(0, 0, CW, CH, 0xFF2C1648);
        // franjas de neón de los laterales
        g.fill(2, 0, 5, CH, 0xFFFF50BE);
        g.fill(7, 0, 9, CH, 0xFF50DCFF);
        g.fill(CW - 5, 0, CW - 2, CH, 0xFFFF50BE);
        g.fill(CW - 9, 0, CW - 7, CH, 0xFF50DCFF);
        // marquesina luminosa
        int pulso = (int) (40 * (0.5 + 0.5 * Math.sin(t * 0.08)));
        g.fill(12, 4, CW - 12, 30, 0xFF000000 | ((200 + pulso / 2) << 16) | (40 << 8) | (140 + pulso));
        g.fill(14, 6, CW - 14, 28, 0xFF3C1470);
        Component titulo = Component.literal("MISCELÁNEA ARCADE").withStyle(ChatFormatting.BOLD);
        int tw = font.width(titulo);
        g.drawString(font, titulo, CW / 2 - tw / 2 + 1, 13, 0xFFFF50BE, false);
        g.drawString(font, titulo, CW / 2 - tw / 2, 12, 0xFFFFF8D0, false);
        for (int x = 18; x < CW - 18; x += 6) g.fill(x, 7, x + 2, 8, (x / 6 + t / 8) % 2 == 0 ? 0xFFFFE070 : 0xFF806030);
        // bisel de la pantalla
        g.fill(LX - 6, LY - 4, LX + Lcd.W + 6, LY + Lcd.H + 4, 0xFF0A0A0E);
        g.fill(LX - 1, LY - 1, LX + Lcd.W + 1, LY + Lcd.H + 1, 0xFF202028);
        // panel de mandos
        int py = LY + Lcd.H + 6;
        g.fill(6, py, CW - 6, CH - 4, 0xFF1E1C24);
        g.fill(6, py, CW - 6, py + 2, 0xFF9AA0B0);
        g.fill(CW / 2 - 1, py + 2, CW / 2 + 1, CH - 4, 0xFFFF50BE);
        // palanca: se inclina con la cruceta
        int jx = 46, jy = py + 26;
        circulo(g, jx, jy, 11, 0xFF101014);
        int dx = (ab[Botones.DER] ? 1 : 0) - (ab[Botones.IZQ] ? 1 : 0), dy = (ab[Botones.ABAJO] ? 1 : 0) - (ab[Botones.ARRIBA] ? 1 : 0);
        g.fill(jx - 1 + dx * 2, jy - 10 + dy * 2, jx + 2 + dx * 2, jy, 0xFF2A2A30);
        circulo(g, jx + dx * 5, jy - 12 + dy * 4, 7, 0xFF8A1818);
        circulo(g, jx + dx * 5, jy - 13 + dy * 4, 6, 0xFFE22828);
        circulo(g, jx + dx * 5 - 2, jy - 15 + dy * 4, 2, 0xFFFF9090);
        // botones A (rojo) y B (azul)
        boton(g, 150, py + 22, ab[Botones.A], 0xFFE22828, 0xFFFF8080, "A");
        boton(g, 178, py + 16, ab[Botones.B], 0xFF2870E2, 0xFF80B0FF, "B");
        g.fill(86, py + 30, 98, py + 34, ab[Botones.SELECT] ? 0xFFE0E0E0 : 0xFF606068);
        g.fill(110, py + 30, 122, py + 34, ab[Botones.START] ? 0xFFE0E0E0 : 0xFF606068);
        String cr = libre ? I18n.get("consola.miscelanea.libre") : I18n.get("consola.miscelanea.creditos", creditos);
        g.drawString(font, cr, CW / 2 - font.width(cr) / 2, py + 10, 0xFFFFE070, false);
    }

    private void boton(GuiGraphics g, int x, int y, boolean on, int col, int luz, String txt) {
        circulo(g, x, y + 2, 9, 0xFF0A0A0E);
        circulo(g, x, y + (on ? 1 : 0), 8, on ? luz : col);
        if (!on) circulo(g, x - 3, y - 3, 2, 0x60FFFFFF);
        g.drawString(font, txt, x - 2, y + 11, 0xFFB0B0C0, false);
    }
}
