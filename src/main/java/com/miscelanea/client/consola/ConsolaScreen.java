package com.miscelanea.client.consola;

import com.miscelanea.consola.Juego;
import com.miscelanea.consola.RecordPacket;
import com.miscelanea.consola.RecordsPacket;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModSounds;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.lwjgl.glfw.GLFW;

/** La consola de bolsillo: carcasa dibujada, LCD verde de 160x144 y los juegos. */
public class ConsolaScreen extends Screen {
    private enum Estado {ARRANQUE, MENU, JUEGO}

    private static final int CW = 372, CH = 204, LX = 106, LY = 24;
    private static boolean musica = true;
    private static int ultimoSel = 0;

    private final Botones bot = new Botones();
    private final Lcd lcd = new Lcd();
    private Estado estado = Estado.ARRANQUE;
    private int t, sel = ultimoSel, aviso;
    private JuegoGB juego;
    private Juego actual;
    private boolean pausa, finVisto, nuevoRecord;
    private int tFin;
    private long ultimo;
    private double acum;
    private MusicaGB mus;

    public ConsolaScreen() {
        super(Component.translatable("item.miscelanea.consola"));
    }

    public static void abrir() {
        Minecraft.getInstance().setScreen(new ConsolaScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ entrada
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        int b = Botones.boton(key);
        if (b >= 0) {
            bot.pulsar(b);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        int b = Botones.boton(key);
        if (b >= 0) {
            bot.soltar(b);
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public void removed() {
        pararMusica();
        super.removed();
    }

    // ------------------------------------------------------------------ lógica
    private boolean desbloqueado(Juego j) {
        Player p = Minecraft.getInstance().player;
        if (j.incorporado || p == null || p.getAbilities().instabuild) return true;
        Item it = ModItems.cartucho(j);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) if (inv.getItem(i).is(it)) return true;
        return false;
    }

    private static int record(Juego j) {
        return RecordsPacket.cliente.getInt(j.id);
    }

    private void paso() {
        t++;
        if (aviso > 0) aviso--;
        if (bot.pulsa(Botones.SELECT)) {
            musica = !musica;
            JuegoGB.sonido(ModSounds.GB_MOVER, musica ? 1.5F : 0.7F);
            if (estado == Estado.JUEGO && !pausa && !juego.fin && musica) empezarMusica();
            else pararMusica();
        }
        switch (estado) {
            case ARRANQUE -> {
                if (t == 70) JuegoGB.sonido(ModSounds.GB_INICIO);
                if (t > 120 || (t > 10 && (bot.pulsa(Botones.A) || bot.pulsa(Botones.START)))) {
                    estado = Estado.MENU;
                    t = 0;
                }
            }
            case MENU -> {
                int n = Juego.values().length;
                if (bot.repite(Botones.ABAJO, 14, 5)) {
                    sel = (sel + 1) % n;
                    JuegoGB.sonido(ModSounds.GB_MOVER);
                }
                if (bot.repite(Botones.ARRIBA, 14, 5)) {
                    sel = (sel + n - 1) % n;
                    JuegoGB.sonido(ModSounds.GB_MOVER);
                }
                ultimoSel = sel;
                if (bot.pulsa(Botones.A) || bot.pulsa(Botones.START)) {
                    Juego j = Juego.values()[sel];
                    if (desbloqueado(j)) empezar(j);
                    else {
                        aviso = 90;
                        JuegoGB.sonido(ModSounds.GB_FIN, 1.6F);
                    }
                }
            }
            case JUEGO -> pasoJuego();
        }
    }

    private void empezar(Juego j) {
        actual = j;
        juego = Juegos.crear(j, bot);
        juego.reiniciar();
        estado = Estado.JUEGO;
        pausa = false;
        finVisto = false;
        nuevoRecord = false;
        JuegoGB.sonido(ModSounds.GB_PUNTO, 1.2F);
        if (musica) empezarMusica();
    }

    private void pasoJuego() {
        if (juego.fin) {
            if (!finVisto) {
                finVisto = true;
                tFin = 0;
                pararMusica();
                JuegoGB.sonido(ModSounds.GB_FIN);
                if (juego.puntos > record(actual)) {
                    nuevoRecord = record(actual) > 0 || juego.puntos > 0;
                    RecordsPacket.cliente.putInt(actual.id, juego.puntos);
                    ModNetwork.CHANNEL.sendToServer(new RecordPacket(actual.ordinal(), juego.puntos));
                }
            }
            tFin++;
            if (tFin > 30 && (bot.pulsa(Botones.A) || bot.pulsa(Botones.START))) empezar(actual);
            else if (tFin > 30 && bot.pulsa(Botones.B)) {
                estado = Estado.MENU;
                t = 0;
            }
            return;
        }
        if (bot.pulsa(Botones.START)) {
            pausa = !pausa;
            JuegoGB.sonido(ModSounds.GB_PUNTO, pausa ? 0.7F : 1.3F);
            if (pausa) pararMusica();
            else if (musica) empezarMusica();
        }
        if (!pausa) juego.paso();
    }

    private void empezarMusica() {
        if (mus != null && Minecraft.getInstance().getSoundManager().isActive(mus)) return;
        mus = new MusicaGB();
        Minecraft.getInstance().getSoundManager().play(mus);
    }

    private void pararMusica() {
        if (mus != null) {
            Minecraft.getInstance().getSoundManager().stop(mus);
            mus = null;
        }
    }

    // ------------------------------------------------------------------ dibujo
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long ahora = Util.getMillis();
        if (ultimo == 0) ultimo = ahora;
        acum = Math.min(acum + (ahora - ultimo), 150);
        ultimo = ahora;
        while (acum >= 1000.0 / 60.0) {
            acum -= 1000.0 / 60.0;
            paso();
            bot.paso();
        }

        renderBackground(g);
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        float k = Math.min(width * 0.96F / CW, (height - 16) * 0.96F / CH);
        // que cada píxel de la LCD ocupe un número entero de píxeles reales: se ve nítido
        float kr = (float) (Math.floor(k * gs) / gs);
        if (kr >= 0.5F) k = kr;
        float ox = (width - CW * k) / 2F, oy = (height - 16 - CH * k) / 2F;
        g.pose().pushPose();
        g.pose().translate(ox, oy, 0);
        g.pose().scale(k, k, 1);
        carcasa(g);
        g.pose().pushPose();
        g.pose().translate(LX, LY, 0);
        lcd.g = g;
        lcd.font = font;
        pantalla();
        lcd.volcarTextos();
        g.pose().popPose();
        g.pose().popPose();

        String ayuda = I18n.get("consola.miscelanea.controles");
        float s = Math.min(1F, (width - 8F) / font.width(ayuda));
        g.pose().pushPose();
        g.pose().translate(width / 2F, height - 12, 0);
        g.pose().scale(s, s, 1);
        g.drawString(font, ayuda, -font.width(ayuda) / 2, 0, 0xFFB0B0B0, false);
        g.pose().popPose();
    }

    private static void redondo(GuiGraphics g, int cx, int cy, int r, int col) {
        for (int dy = -r; dy < r; dy++) {
            float yy = dy + 0.5F;
            int w = (int) Math.round(Math.sqrt(r * r - yy * yy));
            g.fill(cx - w, cy + dy, cx + w, cy + dy + 1, col);
        }
    }

    private static void caja(GuiGraphics g, int x0, int y0, int x1, int y1, int r, int col) {
        g.fill(x0 + r, y0, x1 - r, y1, col);
        g.fill(x0, y0 + r, x1, y1 - r, col);
        for (int i = 0; i < r; i++) {
            int w = r - (int) Math.round(Math.sqrt(r * r - (r - i - 0.5) * (r - i - 0.5)));
            g.fill(x0 + w, y0 + i, x0 + r, y0 + i + 1, col);
            g.fill(x1 - r, y0 + i, x1 - w, y0 + i + 1, col);
            g.fill(x0 + w, y1 - i - 1, x0 + r, y1 - i, col);
            g.fill(x1 - r, y1 - i - 1, x1 - w, y1 - i, col);
        }
    }

    private void carcasa(GuiGraphics g) {
        boolean[] ab = bot.abajo;
        caja(g, 5, 7, CW + 5, CH + 7, 22, 0x70000000);                         // sombra
        caja(g, 0, 0, CW, CH, 22, 0xFFB7B5AE);                                  // canto
        caja(g, 0, 0, CW, CH - 4, 22, 0xFFD3D1CA);                              // frente
        caja(g, 3, 2, CW - 3, 10, 6, 0xFFE2E0DA);                               // brillo superior
        g.fill(10, 20, 13, CH - 24, 0x18000000);
        g.fill(CW - 13, 20, CW - 10, CH - 24, 0x18000000);
        // marco de la pantalla (esquina inferior derecha más redondeada)
        caja(g, LX - 17, 7, LX + 177, LY + 157, 10, 0xFF44464F);
        caja(g, LX - 18, 6, LX + 178, LY + 156, 10, 0xFF555765);
        g.fill(LX - 1, LY - 1, LX + 161, LY + 145, 0xFF2E3038);
        g.pose().pushPose();
        g.pose().translate(LX + 80, 12, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        String cab = "MISCELÁNEA  ·  POCKET  ·  4 TONOS";
        g.fill(-110, 3, -font.width(cab) / 2 - 4, 4, 0xFF8E3050);
        g.fill(font.width(cab) / 2 + 4, 3, 110, 4, 0xFF34408E);
        g.drawString(font, cab, -font.width(cab) / 2, 0, 0xFFB9BBCB, false);
        g.pose().popPose();
        // LED de encendido
        redondo(g, LX - 10, LY + 50, 3, 0xFF3A1010);
        redondo(g, LX - 10, LY + 50, 2, estado == Estado.ARRANQUE && t < 8 ? 0xFF701818 : 0xFFFF3A2A);
        g.pose().pushPose();
        g.pose().translate(LX - 10, LY + 56, 0);
        g.pose().scale(0.45F, 0.45F, 1);
        g.drawString(font, "POWER", -font.width("POWER") / 2, 0, 0xFFB9BBCB, false);
        g.pose().popPose();
        // logotipo
        Component logo = Component.literal("Miscelánea").withStyle(ChatFormatting.BOLD, ChatFormatting.ITALIC);
        g.pose().pushPose();
        g.pose().translate(LX + 80, LY + 144 + 18, 0);
        g.pose().scale(1.1F, 1.1F, 1);
        g.drawString(font, logo, -font.width(logo) / 2, 0, 0xFF2B2F7A, false);
        g.pose().popPose();
        g.pose().pushPose();
        g.pose().translate(LX + 80 + font.width(logo) * 0.55F + 3, LY + 144 + 20, 0);
        g.pose().scale(0.6F, 0.6F, 1);
        g.drawString(font, "POCKET", 0, 0, 0xFF2B2F7A, false);
        g.pose().popPose();

        // cruceta
        int cx = 50, cy = 100;
        redondo(g, cx, cy + 1, 31, 0xFFC4C2BB);
        redondo(g, cx, cy, 30, 0xFFCBC9C2);
        int dk = 0xFF26262A, lit = 0xFF4E4E58;
        g.fill(cx - 25, cy - 8, cx + 25, cy + 10, 0xFF141416);
        g.fill(cx - 8, cy - 25, cx + 10, cy + 27, 0xFF141416);
        g.fill(cx - 25, cy - 9, cx + 25, cy + 8, dk);
        g.fill(cx - 9, cy - 25, cx + 8, cy + 25, dk);
        if (ab[Botones.ARRIBA]) g.fill(cx - 9, cy - 25, cx + 8, cy - 9, lit);
        if (ab[Botones.ABAJO]) g.fill(cx - 9, cy + 8, cx + 8, cy + 25, lit);
        if (ab[Botones.IZQ]) g.fill(cx - 25, cy - 9, cx - 9, cy + 8, lit);
        if (ab[Botones.DER]) g.fill(cx + 8, cy - 9, cx + 25, cy + 8, lit);
        redondo(g, cx, cy, 5, 0xFF1C1C20);
        flecha(g, cx, cy - 19, 0, -1);
        flecha(g, cx, cy + 18, 0, 1);
        flecha(g, cx - 19, cy, -1, 0);
        flecha(g, cx + 18, cy, 1, 0);

        // A y B (en diagonal, como en las portátiles clásicas)
        boton(g, 336, 90, ab[Botones.A], "A");
        boton(g, 302, 106, ab[Botones.B], "B");

        // SELECT y START, inclinados
        pastilla(g, 290, 150, ab[Botones.SELECT], "SELECT");
        pastilla(g, 324, 150, ab[Botones.START], "START");

        // altavoz
        g.pose().pushPose();
        g.pose().translate(318, 186, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-30));
        for (int i = 0; i < 6; i++) {
            g.fill(-28 + i * 9, -14, -25 + i * 9, 14, 0xFF8F8D86);
            g.fill(-28 + i * 9, -14, -27 + i * 9, 14, 0xFF6D6B65);
        }
        g.pose().popPose();
        // ranura del cable/volumen en el canto
        g.fill(CW - 3, 60, CW, 90, 0xFF9A9892);
    }

    private static void flecha(GuiGraphics g, int x, int y, int dx, int dy) {
        int c = 0xFF3C3C44;
        for (int i = 0; i < 3; i++) {
            if (dy != 0) g.fill(x - i, y - dy * i, x + i + 1, y - dy * i + 1, c);
            else g.fill(x - dx * i, y - i, x - dx * i + 1, y + i + 1, c);
        }
    }

    private void boton(GuiGraphics g, int x, int y, boolean on, String txt) {
        redondo(g, x, y + 2, 14, 0xFFB0AEA8);
        redondo(g, x, y + (on ? 1 : 0), 13, on ? 0xFF6E1232 : 0xFF861A40);
        redondo(g, x, y + (on ? 1 : 0), 11, on ? 0xFFB02858 : 0xFF9C1E4A);
        if (!on) redondo(g, x - 4, y - 4, 3, 0xFFB84A72);
        g.drawString(font, txt, x + 8, y + 15, 0xFF2B2F7A, false);
    }

    private void pastilla(GuiGraphics g, int x, int y, boolean on, String txt) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(-25));
        caja(g, -13, -4, 13, 4, 4, 0xFFA9A7A0);
        caja(g, -11, -3, 11, 3, 3, on ? 0xFF606068 : 0xFF84848C);
        g.pose().scale(0.5F, 0.5F, 1);
        g.drawString(font, txt, -font.width(txt) / 2, 14, 0xFF2B2F7A, false);
        g.pose().popPose();
    }

    private void pantalla() {
        Lcd l = lcd;
        l.rect(0, 0, Lcd.W, Lcd.H, 0);
        switch (estado) {
            case ARRANQUE -> {
                if (t < 8) {
                    l.rect(0, 0, Lcd.W, Lcd.H, 1);
                    return;
                }
                int y = Math.min(60, -12 + (t - 8));
                String logo = "Miscelánea";
                l.textoC(logo, 80, y, 3);
                l.textoC("·", 80 + l.ancho(logo) / 2 + 4, y - 4, 3);
                if (t > 75) l.textoC("POCKET", 80, 74, 2);
            }
            case MENU -> menu(l);
            case JUEGO -> {
                juego.dibujar(l);
                if (juego.fin) {
                    int h = nuevoRecord ? 66 : 56;
                    int y0 = 72 - h / 2;
                    l.caja(14, y0, 132, h);
                    String msg = juego.mensajeFin != null ? juego.mensajeFin : I18n.get("consola.miscelanea.fin");
                    l.textoC(msg, 80, y0 + 8, 3);
                    l.textoC(I18n.get("consola.miscelanea.puntos") + " " + juego.puntos, 80, y0 + 22, 3);
                    int yy = y0 + 34;
                    if (nuevoRecord) {
                        if ((tFin / 15) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.nuevo_record"), 80, yy, 2);
                        yy += 11;
                    }
                    if (tFin > 30) l.textoC(I18n.get("consola.miscelanea.reintentar"), 80, yy + 2, 2);
                } else if (pausa) {
                    l.caja(40, 56, 80, 32);
                    l.textoC(I18n.get("consola.miscelanea.pausa"), 80, 68, 3);
                }
            }
        }
    }

    private static final String[] PUNTERO = {"3...", "33..", "333.", "3333", "333.", "33..", "3..."};

    private void menu(Lcd l) {
        l.rect(0, 0, Lcd.W, 15, 3);
        l.textoC(I18n.get("consola.miscelanea.elige"), 80, 4, 0);
        Juego[] js = Juego.values();
        for (int i = 0; i < js.length; i++) {
            Juego j = js[i];
            int y = 19 + i * 17;
            boolean d = desbloqueado(j), s = i == sel;
            if (s) {
                l.rect(2, y - 1, 156, 15, 2);
                if ((t / 20) % 2 == 0) l.sprite(PUNTERO, 5, y + 3);
            }
            int ct = s ? 0 : 3;
            if (d) {
                l.texto(I18n.get(j.clave()), 13, y + 3, ct);
                int r = record(j);
                if (r > 0) l.textoD(Integer.toString(r), 155, y + 3, s ? 1 : 2);
            } else {
                l.texto("? ? ? ? ?", 13, y + 3, s ? 1 : 1);
                if (s) l.textoD(I18n.get("consola.miscelanea.sin_cartucho"), 155, y + 3, 1);
            }
        }
        l.rect(0, 122, Lcd.W, 1, 2);
        if (aviso > 0) {
            if ((aviso / 8) % 2 == 0) l.textoC(I18n.get("consola.miscelanea.falta"), 80, 129, 3);
        } else {
            l.texto(I18n.get("consola.miscelanea.musica", I18n.get(musica ? "consola.miscelanea.si" : "consola.miscelanea.no")), 5, 129, 2);
            if ((t / 25) % 2 == 0) l.textoD("A ▶", 155, 129, 3);
        }
    }
}
