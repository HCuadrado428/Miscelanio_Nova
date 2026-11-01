package com.miscelanea.client.baile;

import com.miscelanea.Miscelanea;
import com.miscelanea.baile.BailePacket;
import com.miscelanea.client.arcade.PasoDoble;
import com.miscelanea.client.arcade.RitmoCanciones;
import com.miscelanea.network.ModNetwork;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Pista de baile del lado del jugador: menú de canción, flechas en la pantalla (a la derecha), juicio de cada pulsación
 * al momento exacto de la tecla, cámara en tercera persona para verse bailar y la pose que ven los demás.
 * WASD o flechas del teclado: los cuatro pasos · saltar: empezar · agacharse: bajarse de la pista.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BaileCliente {
    static final int NADA = 0, MENU = 1, BAILE = 2, RESULTADO = 3;
    static final int LATENCIA = 4, VENTANA = 10;
    static final int[] COLOR = {0xFFB070F0, 0xFF5ADCFF, 0xFF69DC67, 0xFFFF5050}, LUZ = {0xFFE0C0FF, 0xFFC0F4FF, 0xFFC0FFC0, 0xFFFFC0C0};
    static boolean[][][] mascaras;

    static int modo, cancion, dif = 1, menuT;
    static BlockPos pista;
    static CameraType camaraAntes;
    static long inicio;
    static int[] notas;
    static boolean[] hecha;
    static int puntos, combo, maxCombo, vida, perf, geni, bien, fall, juicio = -1;
    static long juicioT, finT;
    static final long[] pulsado = new long[4];

    private BaileCliente() {}

    // ------------------------------------------------------------------ red
    public static void recibir(BailePacket m) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer yo = mc.player;
        if (yo == null) return;
        switch (m.tipo) {
            case BailePacket.ABRIR -> {
                pista = m.pos;
                modo = MENU;
                menuT = 0;
                if (camaraAntes == null) camaraAntes = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                BailePoses.empezar(yo.getId(), 100);
            }
            case BailePacket.EMPIEZA -> {
                BailePoses.empezar(m.entidad, RitmoCanciones.BPM[Math.max(0, Math.min(2, m.a))]);
                if (m.entidad == yo.getId() && modo != NADA) empezarCancion(m.a);
            }
            case BailePacket.PASO -> BailePoses.paso(m.entidad, m.a, m.b);
            case BailePacket.PARA -> {
                BailePoses.parar(m.entidad);
                if (m.entidad == yo.getId()) cerrar();
            }
            default -> {}
        }
    }

    static void empezarCancion(int c) {
        cancion = c;
        int[] base = RitmoCanciones.NOTAS[c][dif];
        java.util.List<Integer> l = new java.util.ArrayList<>();
        for (int n : base) {
            l.add(n & ~4);
            if ((n & 4) != 0) l.add(((n >> 3) << 3) | (((n & 3) + 2) % 4));
        }
        notas = l.stream().mapToInt(Integer::intValue).toArray();
        hecha = new boolean[notas.length];
        puntos = combo = maxCombo = perf = geni = bien = fall = 0;
        vida = 70;
        juicio = -1;
        inicio = System.currentTimeMillis();
        modo = BAILE;
    }

    static void cerrar() {
        Minecraft mc = Minecraft.getInstance();
        if (camaraAntes != null) mc.options.setCameraType(camaraAntes);
        camaraAntes = null;
        modo = NADA;
        pista = null;
        if (mc.player != null) BailePoses.parar(mc.player.getId());
    }

    static int tiempo() {
        return (int) ((System.currentTimeMillis() - inicio) * 60 / 1000) - LATENCIA;
    }

    // ------------------------------------------------------------------ teclas
    static int carrilDe(int key, Minecraft mc) {
        if (key == GLFW.GLFW_KEY_LEFT || mc.options.keyLeft.getKey().getValue() == key) return 0;
        if (key == GLFW.GLFW_KEY_DOWN || mc.options.keyDown.getKey().getValue() == key) return 1;
        if (key == GLFW.GLFW_KEY_UP || mc.options.keyUp.getKey().getValue() == key) return 2;
        if (key == GLFW.GLFW_KEY_RIGHT || mc.options.keyRight.getKey().getValue() == key) return 3;
        return -1;
    }

    @SubscribeEvent
    public static void tecla(InputEvent.Key e) {
        Minecraft mc = Minecraft.getInstance();
        if (modo == NADA || mc.screen != null || e.getAction() != GLFW.GLFW_PRESS || mc.player == null) return;
        int c = carrilDe(e.getKey(), mc);
        if (modo == MENU) {
            if (c == 2) cancion = (cancion + 2) % 3;
            if (c == 1) cancion = (cancion + 1) % 3;
            if (c == 0) dif = Math.max(0, dif - 1);
            if (c == 3) dif = Math.min(2, dif + 1);
            if (c >= 0) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.get(), 0.6F, 1.6F);
            if (mc.options.keyJump.getKey().getValue() == e.getKey())
                ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.EMPIEZA, pista, 0, cancion, dif));
            return;
        }
        if (modo != BAILE || c < 0) return;
        pulsado[c] = System.currentTimeMillis();
        int t = tiempo(), mejor = -1, md = 99;
        for (int i = 0; i < notas.length; i++) {
            if (hecha[i] || (notas[i] & 3) != c) continue;
            int d = Math.abs((notas[i] >> 3) - t);
            if (d < md) {
                md = d;
                mejor = i;
            }
            if ((notas[i] >> 3) - t > VENTANA) break;
        }
        int j = 3;
        if (mejor >= 0 && md <= VENTANA) {
            hecha[mejor] = true;
            j = md <= 3 ? 0 : md <= 6 ? 1 : 2;
            juzgar(j);
        }
        // la pose se ve aunque no haya flecha (bailar libre)
        int pose = j == 3 ? 1 : j;
        BailePoses.paso(mc.player.getId(), c, pose);
        ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.PASO, pista, 0, c, pose));
    }

    static void juzgar(int j) {
        juicio = j;
        juicioT = System.currentTimeMillis();
        switch (j) {
            case 0 -> {
                perf++;
                puntos += 300 + combo * 2;
                vida = Math.min(100, vida + 2);
            }
            case 1 -> {
                geni++;
                puntos += 200 + combo;
                vida = Math.min(100, vida + 1);
            }
            case 2 -> {
                bien++;
                puntos += 100;
            }
            default -> {
                fall++;
                vida -= 6;
            }
        }
        combo = j < 3 ? combo + 1 : 0;
        maxCombo = Math.max(maxCombo, combo);
    }

    static int nota() {
        int total = notas == null ? 0 : notas.length * 300;
        float p = total == 0 ? 0 : (perf * 300 + geni * 200 + bien * 100) / (float) total;
        if (vida <= 0) return 5;
        return p >= 0.93F ? 0 : p >= 0.8F ? 1 : p >= 0.65F ? 2 : p >= 0.5F ? 3 : 4;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || modo == NADA) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || pista == null) {
            cerrar();
            return;
        }
        menuT++;
        if (mc.options.keyShift.isDown() || p.position().distanceToSqr(Vec3.atBottomCenterOf(pista)) > 6) {
            ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.SALIR, pista, 0, 0, 0));
            cerrar();
            return;
        }
        if (modo == BAILE) {
            int t = tiempo();
            for (int i = 0; i < notas.length; i++) {
                if (hecha[i]) continue;
                int nt = notas[i] >> 3;
                if (nt > t) break;
                if (t - nt > VENTANA) {
                    hecha[i] = true;
                    juzgar(3);
                    BailePoses.paso(p.getId(), notas[i] & 3, 3);
                    ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.PASO, pista, 0, notas[i] & 3, 3));
                }
            }
            if (vida <= 0 || t > RitmoCanciones.DURACION[cancion] + 30) {
                vida = Math.max(0, vida);
                modo = RESULTADO;
                finT = System.currentTimeMillis();
                ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.FIN, pista, 0, puntos, nota()));
            }
        } else if (modo == RESULTADO && System.currentTimeMillis() - finT > 5000) {
            modo = MENU;
            BailePoses.empezar(p.getId(), 100);
        }
    }

    @SubscribeEvent
    public static void mover(MovementInputUpdateEvent e) {
        if (modo == NADA) return;
        var in = e.getInput();
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    // ------------------------------------------------------------------ pantalla
    static boolean[][] mascara(int c) {
        if (mascaras == null) {
            mascaras = new boolean[4][14][14];
            for (int y = 0; y < 14; y++)
                for (int x = 0; x < 14; x++) {
                    boolean v = PasoDoble.FLECHA[y].charAt(x) == '#';
                    mascaras[2][y][x] = v;
                    mascaras[1][13 - y][x] = v;
                    mascaras[0][x][y] = v;
                    mascaras[3][x][13 - y] = v;
                }
        }
        return mascaras[c];
    }

    static void flecha(GuiGraphics g, int x, int y, int c, int col, int luz, boolean receptor, int esc) {
        boolean[][] m = mascara(c);
        for (int yy = 0; yy < 14; yy++)
            for (int xx = 0; xx < 14; xx++) {
                if (!m[yy][xx]) continue;
                boolean borde = yy == 0 || xx == 0 || yy == 13 || xx == 13 || !m[yy - 1][xx] || !m[yy + 1][xx] || !m[yy][xx - 1] || !m[yy][xx + 1];
                int color = receptor ? (borde ? col : 0x30000000) : (borde ? 0xFF101018 : (yy + xx < 12 ? luz : col));
                g.fill(x + xx * esc, y + yy * esc, x + (xx + 1) * esc, y + (yy + 1) * esc, color);
            }
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (modo == NADA) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int W = g.guiWidth(), H = g.guiHeight();
        var font = mc.font;
        if (modo == MENU) {
            int x0 = W / 2 - 110, y0 = H / 2 - 70;
            g.fill(x0, y0, x0 + 220, y0 + 120, 0xC0180830);
            g.fill(x0, y0, x0 + 220, y0 + 2, 0xFFEC6CA8);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.titulo"), W / 2, y0 + 8, 0xFFFFE05A);
            for (int c = 0; c < 3; c++) {
                int y = y0 + 26 + c * 18;
                boolean s = c == cancion;
                if (s) g.fill(x0 + 8, y - 4, x0 + 212, y + 12, 0xFF612895);
                g.drawString(font, I18n.get("paso.miscelanea.cancion." + c), x0 + 16, y, s ? 0xFFFFFFFF : 0xFFA090C0);
                g.drawString(font, RitmoCanciones.BPM[c] + " BPM", x0 + 170, y, s ? 0xFFFFE05A : 0xFF706090);
            }
            String[] d = {"paso.miscelanea.facil", "paso.miscelanea.normal", "paso.miscelanea.dificil"};
            g.drawCenteredString(font, "◄ " + I18n.get(d[dif]) + " ►", W / 2, y0 + 84, 0xFF69DC67);
            g.drawCenteredString(font, I18n.get("baile.miscelanea.menu_ayuda"), W / 2, y0 + 102, (menuT / 10) % 2 == 0 ? 0xFFFFE05A : 0xFFC0A040);
            return;
        }
        // pista de flechas a la derecha de la pantalla
        int esc = 2, paso = 14 * esc + 6, x0 = W - 4 * paso - 12, objY = 16;
        g.fill(x0 - 6, 0, W, H, 0x50000000);
        long ahora = System.currentTimeMillis();
        int t = modo == BAILE ? tiempo() : 0;
        for (int c = 0; c < 4; c++) {
            boolean p = ahora - pulsado[c] < 120;
            flecha(g, x0 + c * paso, objY, c, p ? 0xFFFFFFFF : 0xFF8080A0, 0, true, esc);
        }
        if (notas != null && modo == BAILE) {
            float vel = new float[]{1.7F, 2.2F, 2.7F}[dif] * esc * 0.75F;
            for (int i = 0; i < notas.length; i++) {
                if (hecha[i]) continue;
                int c = notas[i] & 3, y = objY + Math.round(((notas[i] >> 3) - t) * vel);
                if (y > H) break;
                if (y < -30) continue;
                flecha(g, x0 + c * paso, y, c, COLOR[c], LUZ[c], false, esc);
            }
        }
        // vida y puntos
        int bx = x0, by = H - 24;
        g.fill(bx, by, bx + 4 * paso - 6, by + 6, 0xFF000000);
        g.fill(bx + 1, by + 1, bx + 1 + (int) ((4 * paso - 8) * Math.max(0, vida) / 100F), by + 5, vida < 25 ? 0xFFE04040 : 0xFF50D060);
        g.drawString(font, Integer.toString(puntos), bx, by - 12, 0xFFFFFFFF);
        if (juicio >= 0 && ahora - juicioT < 500) {
            String[] j = {"paso.miscelanea.perfecto", "paso.miscelanea.genial", "paso.miscelanea.bien", "paso.miscelanea.fallo"};
            int[] col = {0xFFFFE05A, 0xFF5ADCFF, 0xFF69DC67, 0xFFFF5050};
            g.drawCenteredString(font, I18n.get(j[juicio]), x0 + 2 * paso - 3, objY + 50, col[juicio]);
        }
        if (combo >= 5) g.drawCenteredString(font, combo + " COMBO", x0 + 2 * paso - 3, objY + 64, 0xFFFFE05A);
        if (modo == RESULTADO) {
            String[] n = {"S", "A", "B", "C", "D", "E"};
            int cx = W / 2;
            g.fill(cx - 80, H / 2 - 50, cx + 80, H / 2 + 40, 0xD0180830);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.resultado"), cx, H / 2 - 44, 0xFFFFE05A);
            g.pose().pushPose();
            g.pose().translate(cx, H / 2 - 30, 0);
            g.pose().scale(3, 3, 1);
            g.drawCenteredString(font, n[nota()], 0, 0, nota() == 0 ? 0xFFFFE05A : 0xFFFFFFFF);
            g.pose().popPose();
            g.drawCenteredString(font, I18n.get("paso.miscelanea.perfecto") + " " + perf + "  " + I18n.get("paso.miscelanea.genial") + " " + geni, cx, H / 2 + 2, 0xFFE0E0E0);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.bien") + " " + bien + "  " + I18n.get("paso.miscelanea.fallo") + " " + fall, cx, H / 2 + 14, 0xFFE0E0E0);
            g.drawCenteredString(font, "COMBO " + maxCombo + "  ·  " + puntos, cx, H / 2 + 26, 0xFFFFE05A);
        }
        g.drawString(font, I18n.get("baile.miscelanea.salir"), 6, H - 12, 0xA0FFFFFF);
    }
}
