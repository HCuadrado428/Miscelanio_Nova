package com.miscelanea.client.arcade;

import com.miscelanea.arcade.ArcadeAbrirPacket;
import com.miscelanea.arcade.ArcadeAccionPacket;
import com.miscelanea.arcade.ArcadeFramePacket;
import com.miscelanea.arcade.ArcadeInputPacket;
import com.miscelanea.arcade.ArcadeJuegos;
import com.miscelanea.client.consola.AstroPatrulla;
import com.miscelanea.client.consola.Botones;
import com.miscelanea.client.consola.JuegoGB;
import com.miscelanea.client.consola.Juegos;
import com.miscelanea.client.consola.Lcd;
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
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Pantalla de la recreativa: menú con récords, créditos, juegos a todo color (256x192) y retransmisión a los que miran.
 * En la recreativa doble, el segundo jugador se une y los dos simulan la misma partida paso a paso: cada uno envía
 * sus mandos unos pasos por adelantado y nadie avanza hasta tener los del otro.
 */
public class ArcadeScreen extends Screen {
    private enum Estado {MENU, ESPERA, JUEGO}

    private static final int RETRASO = 4;
    private static final Map<String, int[]> PALETAS = Map.of(
            "astro", AstroPatrulla.PALETA,
            "tetrominos", new int[]{0xFFF8F8F0, 0xFF88C8F8, 0xFFE05050, 0xFF181830},
            "serpiente", new int[]{0xFFE8F8C0, 0xFF98D058, 0xFF388030, 0xFF102810},
            "ladrillos", new int[]{0xFFF8E8D0, 0xFFF0A040, 0xFFC03030, 0xFF301010},
            "pong", new int[]{0xFF101018, 0xFF404858, 0xFFA0A8B8, 0xFFF8F8F8},
            "pajaro", new int[]{0xFFA8E0F8, 0xFF78C868, 0xFF308840, 0xFF102018},
            "invasores", new int[]{0xFF080818, 0xFF304080, 0xFF70D070, 0xFFF0F0F0});
    private static int ultimoSel;

    final BlockPos pos;
    private int creditos;
    private final boolean libre, doble, invitado;
    private String companero;
    private final int cw, ch, lx, ly;
    private final Botones bot = new Botones();
    private final Botones[] mandos = {new Botones(), new Botones()};
    private final Pantalla pant = new Pantalla();
    private final Lcd lcd = new Lcd();
    private PantallaTextura tex;
    private Estado estado = Estado.MENU;
    private int t, sel = ultimoSel, aviso, tFin, pasos;
    private String avisoTxt = "";
    private JuegoGB clasico;
    private JuegoArcade juego;
    private String juegoId;
    private int jugadores = 1;
    private boolean finVisto;
    private int puesto;
    private long ultimo;
    private double acum;
    private MusicaGB mus;
    // partida a dos
    private boolean enRed;
    private int pasoSim, enviadoHasta = -1, atasco, mascaraOtro;
    private final Map<Integer, Integer> remotas = new HashMap<>(), locales = new HashMap<>();
    private ArcadeCliente.Remota remota;

    public ArcadeScreen(BlockPos pos, int creditos, boolean libre, boolean doble, boolean invitado, String companero) {
        super(Component.translatable("block.miscelanea.recreativa"));
        this.pos = pos;
        this.creditos = creditos;
        this.libre = libre;
        this.doble = doble;
        this.invitado = invitado;
        this.companero = companero;
        cw = doble ? 380 : 304;
        ch = 300;
        lx = (cw - Pantalla.W) / 2;
        ly = 36;
        while (sel >= ArcadeJuegos.IDS.length) sel = 0;
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
            Botones.Acceso.pulsar(bot, b);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        int b = Botones.boton(key);
        if (b >= 0) {
            Botones.Acceso.soltar(bot, b);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void removed() {
        ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.CERRAR, "", 0));
        pararMusica();
        if (tex != null) tex.cerrar();
        super.removed();
    }

    // ------------------------------------------------------------------ red
    void respuesta(ArcadeAbrirPacket m) {
        int antes = creditos;
        creditos = m.creditos;
        switch (m.modo) {
            case 1 -> {
                if (creditos <= antes) mostrarAviso(I18n.get("consola.miscelanea.sin_fichas"));
            }
            case 2 -> empezar(m.juego, m.semilla, m.jugadores);
            case 3 -> {
                estado = Estado.MENU;
                mostrarAviso(I18n.get(doble && companero.length() > 0 && ArcadeJuegos.dosJugadores(ArcadeJuegos.IDS[sel])
                        ? "consola.miscelanea.dos_fichas" : "consola.miscelanea.inserta_ficha"));
                JuegoGB.sonido(ModSounds.GB_FIN, 1.6F);
            }
            case 5 -> {
                String antesC = companero;
                companero = m.companero;
                if (companero.isEmpty() && !antesC.isEmpty()) {
                    if (invitado) {
                        Minecraft.getInstance().setScreen(null);
                        if (Minecraft.getInstance().player != null)
                            Minecraft.getInstance().player.displayClientMessage(Component.translatable("msg.miscelanea.arcade.anfitrion_fuera").withStyle(ChatFormatting.GRAY), true);
                        return;
                    }
                    enRed = false;
                    mostrarAviso(I18n.get("consola.miscelanea.companero_fuera", antesC));
                } else if (!companero.isEmpty() && !companero.equals(antesC) && !invitado) {
                    mostrarAviso(I18n.get("consola.miscelanea.companero_llega", companero));
                    JuegoGB.sonido(ModSounds.ARCADE_FICHA, 1.5F);
                }
            }
            default -> {}
        }
    }

    void entradaRemota(int paso, int mascara) {
        remotas.put(paso, mascara);
        mascaraOtro = mascara;
    }

    void frameRemoto(ArcadeCliente.Remota r) {
        remota = r;
    }

    private void mostrarAviso(String s) {
        avisoTxt = s;
        aviso = 120;
    }

    private void pedirPartida() {
        estado = Estado.ESPERA;
        ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.EMPEZAR, ArcadeJuegos.IDS[sel], 0));
    }

    private void empezar(String id, long semilla, int jug) {
        juegoId = ArcadeJuegos.valido(id) ? id : ArcadeJuegos.IDS[sel];
        jugadores = jug;
        clasico = null;
        juego = null;
        for (Botones b : mandos) Botones.Acceso.aplicar(b, 0);
        Botones[] bs = jug == 2 ? mandos : new Botones[]{invitado ? mandos[1] : mandos[0]};
        switch (juegoId) {
            case "chatarra" -> juego = new ComandoChatarra(bs, semilla, jug);
            case "astro" -> clasico = new AstroPatrulla(bot);
            default -> {
                Juego j = Juego.TETROMINOS;
                for (Juego x : Juego.values()) if (x.id.equals(juegoId)) j = x;
                clasico = Juegos.crear(j, bot);
            }
        }
        if (juego != null) juego.reiniciar();
        else clasico.reiniciar();
        enRed = jug == 2;
        pasoSim = 0;
        enviadoHasta = RETRASO - 1;
        remotas.clear();
        locales.clear();
        atasco = 0;
        estado = Estado.JUEGO;
        finVisto = false;
        puesto = 0;
        JuegoGB.sonido(ModSounds.ARCADE_FICHA, 1.3F);
        pararMusica();
        SoundEvent m = juego != null ? juego.musica() : juegoId.equals("astro") ? ModSounds.ARCADE_MUSICA.get() : ModSounds.GB_MUSICA.get();
        if (m != null) {
            mus = new MusicaGB(m);
            Minecraft.getInstance().getSoundManager().play(mus);
        }
    }

    private void pararMusica() {
        if (mus != null) {
            Minecraft.getInstance().getSoundManager().stop(mus);
            mus = null;
        }
    }

    private boolean terminado() {
        return juego != null ? juego.fin : clasico != null && clasico.fin;
    }

    private int puntos() {
        return juego != null ? juego.puntos : clasico != null ? clasico.puntos : 0;
    }

    // ------------------------------------------------------------------ lógica (60 pasos por segundo)
    private void paso() {
        t++;
        if (aviso > 0) aviso--;
        switch (estado) {
            case MENU -> {
                if (invitado) break;
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
                if (terminado()) {
                    alTerminar();
                    break;
                }
                if (enRed) pasoEnRed();
                else if (juego != null) {
                    Botones.Acceso.aplicar(invitado ? mandos[1] : mandos[0], Botones.Acceso.mascara(bot));
                    juego.paso();
                    for (Botones b : mandos) Botones.Acceso.paso(b);
                } else clasico.paso();
            }
        }
        // retransmitir la pantalla a los que miran (10 imágenes por segundo); el invitado no retransmite
        if (!invitado && ++pasos % 6 == 0) ModNetwork.CHANNEL.sendToServer(new ArcadeFramePacket(pos, Trama.codificar(pant)));
    }

    /** Un paso de la partida a dos: manda los mandos de un paso futuro y avanza solo si ya tiene los del compañero. */
    private void pasoEnRed() {
        for (int vuelta = 0; vuelta < 3; vuelta++) {
            int objetivo = pasoSim + RETRASO;
            while (enviadoHasta < objetivo) {
                enviadoHasta++;
                int m = Botones.Acceso.mascara(bot);
                locales.put(enviadoHasta, m);
                ModNetwork.CHANNEL.sendToServer(new ArcadeInputPacket(pos, enviadoHasta, m));
            }
            Integer otro = pasoSim < RETRASO ? Integer.valueOf(0) : remotas.remove(pasoSim);
            if (otro == null) {
                atasco++;
                return;
            }
            atasco = 0;
            int mio = locales.getOrDefault(pasoSim, 0);
            locales.remove(pasoSim);
            Botones.Acceso.aplicar(mandos[0], invitado ? otro : mio);
            Botones.Acceso.aplicar(mandos[1], invitado ? mio : otro);
            juego.paso();
            for (Botones b : mandos) Botones.Acceso.paso(b);
            pasoSim++;
            if (juego.fin) return;
            // ponerse al día si el compañero va por delante
            if (!remotas.containsKey(pasoSim + 2)) return;
        }
    }

    private void alTerminar() {
        if (!finVisto) {
            finVisto = true;
            tFin = 0;
            pararMusica();
            JuegoGB.sonido(ModSounds.GB_FIN);
            ListTag tb = ArcadeCliente.tabla(juegoId);
            puesto = 0;
            if (puntos() > 0 && !invitado) {
                puesto = tb.size() + 1;
                for (int i = 0; i < tb.size(); i++)
                    if (puntos() > tb.getCompound(i).getInt("p")) {
                        puesto = i + 1;
                        break;
                    }
                if (puesto > 10) puesto = 0;
                ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.RECORD, juegoId, puntos()));
            }
        }
        tFin++;
        if (invitado) {
            if (tFin > 60 && bot.pulsa(Botones.B)) estado = Estado.MENU;
            return;
        }
        if (tFin > 40 && (bot.pulsa(Botones.A) || bot.pulsa(Botones.START))) {
            if (creditos <= 0 && !libre) ModNetwork.CHANNEL.sendToServer(new ArcadeAccionPacket(pos, ArcadeAccionPacket.FICHA, "", 0));
            else pedirPartida();
        } else if (tFin > 40 && bot.pulsa(Botones.B)) {
            estado = Estado.MENU;
            t = 0;
        }
    }

    // ------------------------------------------------------------------ dibujo de la pantalla
    private void pantalla() {
        Pantalla p = pant;
        p.font = font;
        p.empezar();
        if (estado == Estado.JUEGO) {
            if (juego != null) juego.dibujar(p);
            else {
                lcd.font = font;
                lcd.empezar();
                lcd.paleta = PALETAS.getOrDefault(juegoId, Lcd.VERDE);
                lcd.rect(0, 0, Lcd.W, Lcd.H, 0);
                clasico.dibujar(lcd);
                marcoClasico(p);
                p.clasica(lcd, (Pantalla.W - Lcd.W) / 2, (Pantalla.H - Lcd.H) / 2);
            }
            if (terminado()) fin(p);
            else if (enRed && atasco > 20 && (t / 10) % 2 == 0) p.textoC(I18n.get("consola.miscelanea.esperando_red"), 128, 92, 20);
            return;
        }
        if (invitado) {
            // el jugador 2 ve lo que tiene el jugador 1 en pantalla, con su aviso encima
            if (remota != null && System.currentTimeMillis() - remota.ultimo < 3000) {
                System.arraycopy(remota.pantalla.buf, 0, p.buf, 0, p.buf.length);
                p.textos.addAll(remota.pantalla.textos);
            } else p.limpiar(1);
            p.rect(0, 176, Pantalla.W, 16, 0);
            p.textoC(I18n.get("consola.miscelanea.eres_j2", companero), 128, 180, 39);
            return;
        }
        p.limpiar(33);
        for (int y = 0; y < 24; y++) p.rect(0, y, Pantalla.W, 1, y < 22 ? 42 : 45);
        p.textoC("MISCELÁNEA ARCADE", 128, 7, 20);
        for (int i = 0; i < ArcadeJuegos.IDS.length; i++) {
            String id = ArcadeJuegos.IDS[i];
            int y = 30 + i * 16;
            boolean s = i == sel;
            if (s) p.rect(4, y - 3, 248, 15, 35);
            if (s && (t / 15) % 2 == 0) for (int k = 0; k < 5; k++) p.rect(8, y + k, 3 - Math.abs(k - 2), 1, 20);
            boolean nuevo = id.equals("chatarra") || id.equals("astro");
            p.texto(I18n.get("juego.miscelanea." + id), 16, y, nuevo ? (s ? 20 : 19) : (s ? 7 : 5));
            if (ArcadeJuegos.dosJugadores(id)) p.texto("1-2P", 150, y, s ? 39 : 37);
            ListTag tb = ArcadeCliente.tabla(id);
            if (!tb.isEmpty()) p.textoD(Integer.toString(tb.getCompound(0).getInt("p")), 248, y, s ? 20 : 6);
        }
        p.rect(0, 166, Pantalla.W, 26, 1);
        if (aviso > 0) {
            if ((aviso / 8) % 2 == 0) p.textoC(avisoTxt, 128, 176, 12);
        } else if (estado == Estado.ESPERA) p.textoC("...", 128, 176, 6);
        else {
            String cr = libre ? I18n.get("consola.miscelanea.libre") : creditos > 0 ? I18n.get("consola.miscelanea.creditos", creditos)
                    : (t / 20) % 2 == 0 ? I18n.get("consola.miscelanea.start_ficha") : "";
            p.textoC(cr, 128, 170, creditos > 0 || libre ? 19 : 15);
            if (doble) p.textoC(companero.isEmpty() ? I18n.get("consola.miscelanea.sin_companero") : I18n.get("consola.miscelanea.con_companero", companero),
                    128, 181, companero.isEmpty() ? 4 : 29);
        }
        ListTag tb = ArcadeCliente.tabla(ArcadeJuegos.IDS[sel]);
        if (!tb.isEmpty() && aviso == 0 && estado == Estado.MENU && !doble) {
            CompoundTag c = tb.getCompound(0);
            p.textoC("♛ " + c.getString("n"), 128, 181, 20);
        }
    }

    private void marcoClasico(Pantalla p) {
        p.limpiar(1);
        for (int y = 0; y < Pantalla.H; y += 4) p.rect(0, y, Pantalla.W, 1, 2);
        p.marco((Pantalla.W - Lcd.W) / 2 - 2, (Pantalla.H - Lcd.H) / 2 - 2, Lcd.W + 4, Lcd.H + 4, 45);
    }

    private void fin(Pantalla p) {
        p.rect(28, 16, 200, 160, 1);
        p.marco(28, 16, 200, 160, 45);
        p.marco(30, 18, 196, 156, 42);
        p.textoC(juego != null && juego.mensajeFin != null ? juego.mensajeFin : I18n.get("consola.miscelanea.fin"), 128, 24, 20);
        p.textoC(I18n.get("consola.miscelanea.puntos") + " " + puntos(), 128, 38, 7);
        ListTag tb = ArcadeCliente.tabla(juegoId);
        for (int i = 0; i < Math.min(8, tb.size()); i++) {
            CompoundTag c = tb.getCompound(i);
            int y = 54 + i * 11;
            boolean yo = c.getString("n").equals(Minecraft.getInstance().getUser().getName()) && c.getInt("p") == puntos();
            int col = yo && (tFin / 8) % 2 == 0 ? 20 : 6;
            p.texto((i + 1) + ". " + recorta(c.getString("n")), 40, y, col);
            p.textoD(Integer.toString(c.getInt("p")), 216, y, col);
        }
        if (puesto == 1 && (tFin / 10) % 2 == 0) p.textoC(I18n.get("consola.miscelanea.record_mundial"), 128, 146, 20);
        else if (puesto > 1) p.textoC(I18n.get("consola.miscelanea.top", puesto), 128, 146, 20);
        if (tFin > 40) p.textoC(I18n.get(invitado ? "consola.miscelanea.espera_j1" : "consola.miscelanea.otra_ficha"), 128, 160, 7);
    }

    private static String recorta(String s) {
        return s.length() > 16 ? s.substring(0, 16) : s;
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
        float k = Math.min(width * 0.96F / cw, (height - 16) * 0.96F / ch);
        float kr = (float) (Math.floor(k * gs) / gs);
        if (kr >= 0.5F) k = kr;
        float ox = (width - cw * k) / 2F, oy = (height - 16 - ch * k) / 2F;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1);
        mueble(g);
        g.pose().pushPose();
        g.pose().translate(lx, ly, 0);
        pantalla();
        if (tex == null) tex = new PantallaTextura("local");
        tex.subir(pant);
        tex.dibujar(g, pant);
        for (int y = 0; y < Pantalla.H; y += 2) g.fill(0, y, Pantalla.W, y + 1, 0x12000000);
        g.fill(6, 5, 90, 7, 0x18FFFFFF);
        g.pose().popPose();
        g.pose().popPose();
        String ayuda = I18n.get("consola.miscelanea.controles_arcade2");
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
        g.fill(-4, -4, cw + 4, ch + 4, 0x80000000);
        g.fill(0, 0, cw, ch, 0xFF2C1648);
        g.fill(2, 0, 5, ch, 0xFFFF50BE);
        g.fill(7, 0, 9, ch, 0xFF50DCFF);
        g.fill(cw - 5, 0, cw - 2, ch, 0xFFFF50BE);
        g.fill(cw - 9, 0, cw - 7, ch, 0xFF50DCFF);
        int pulso = (int) (40 * (0.5 + 0.5 * Math.sin(t * 0.08)));
        g.fill(12, 4, cw - 12, 30, 0xFF000000 | ((200 + pulso / 2) << 16) | (40 << 8) | (140 + pulso));
        g.fill(14, 6, cw - 14, 28, 0xFF3C1470);
        Component titulo = Component.literal(doble ? "MISCELÁNEA ARCADE · 2P" : "MISCELÁNEA ARCADE").withStyle(ChatFormatting.BOLD);
        int tw = font.width(titulo);
        g.drawString(font, titulo, cw / 2 - tw / 2 + 1, 13, 0xFFFF50BE, false);
        g.drawString(font, titulo, cw / 2 - tw / 2, 12, 0xFFFFF8D0, false);
        for (int x = 18; x < cw - 18; x += 6) g.fill(x, 7, x + 2, 8, (x / 6 + t / 8) % 2 == 0 ? 0xFFFFE070 : 0xFF806030);
        g.fill(lx - 6, ly - 4, lx + Pantalla.W + 6, ly + Pantalla.H + 4, 0xFF0A0A0E);
        g.fill(lx - 1, ly - 1, lx + Pantalla.W + 1, ly + Pantalla.H + 1, 0xFF202028);
        int py = ly + Pantalla.H + 6;
        g.fill(6, py, cw - 6, ch - 4, 0xFF1E1C24);
        g.fill(6, py, cw - 6, py + 2, 0xFF9AA0B0);
        boolean[] mio = Botones.Acceso.abajo(bot);
        if (doble) {
            boolean[] otro = new boolean[Botones.N];
            for (int i = 0; i < Botones.N; i++) otro[i] = (mascaraOtro >> i & 1) != 0;
            g.fill(cw / 2 - 1, py + 2, cw / 2 + 1, ch - 4, 0xFFFF50BE);
            mandos(g, 0, py, invitado ? otro : mio, 0xFFE22828, "1P");
            mandos(g, cw / 2, py, invitado ? mio : otro, 0xFF2870E2, "2P");
        } else mandos(g, (cw - 190) / 2 - 0, py, mio, 0xFFE22828, "");
        String cr = libre ? I18n.get("consola.miscelanea.libre") : I18n.get("consola.miscelanea.creditos", creditos);
        g.drawString(font, cr, cw / 2 - font.width(cr) / 2, ch - 14, 0xFFFFE070, false);
    }

    private void mandos(GuiGraphics g, int x0, int py, boolean[] ab, int bola, String etiqueta) {
        int jx = x0 + 48, jy = py + 30;
        circulo(g, jx, jy, 12, 0xFF101014);
        int dx = (ab[Botones.DER] ? 1 : 0) - (ab[Botones.IZQ] ? 1 : 0), dy = (ab[Botones.ABAJO] ? 1 : 0) - (ab[Botones.ARRIBA] ? 1 : 0);
        g.fill(jx - 1 + dx * 2, jy - 10 + dy * 2, jx + 2 + dx * 2, jy, 0xFF2A2A30);
        circulo(g, jx + dx * 5, jy - 12 + dy * 4, 7, mezcla(bola, 0xFF000000, 0.4F));
        circulo(g, jx + dx * 5, jy - 13 + dy * 4, 6, bola);
        circulo(g, jx + dx * 5 - 2, jy - 15 + dy * 4, 2, 0x90FFFFFF);
        boton(g, x0 + 104, py + 30, ab[Botones.A], 0xFFE22828, 0xFFFF8080, "A");
        boton(g, x0 + 128, py + 24, ab[Botones.B], 0xFF2870E2, 0xFF80B0FF, "B");
        boton(g, x0 + 152, py + 30, ab[Botones.C], 0xFFE2C028, 0xFFFFE080, "C");
        if (!etiqueta.isEmpty()) g.drawString(font, etiqueta, x0 + 14, py + 8, 0xFFB0B0C0, false);
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) (((a >> 16) & 255) * (1 - k) + ((b >> 16) & 255) * k), gg = (int) (((a >> 8) & 255) * (1 - k) + ((b >> 8) & 255) * k),
                bb = (int) ((a & 255) * (1 - k) + (b & 255) * k);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    private void boton(GuiGraphics g, int x, int y, boolean on, int col, int luz, String txt) {
        circulo(g, x, y + 2, 9, 0xFF0A0A0E);
        circulo(g, x, y + (on ? 1 : 0), 8, on ? luz : col);
        if (!on) circulo(g, x - 3, y - 3, 2, 0x60FFFFFF);
        g.drawString(font, txt, x - 2, y + 11, 0xFFB0B0C0, false);
    }
}
