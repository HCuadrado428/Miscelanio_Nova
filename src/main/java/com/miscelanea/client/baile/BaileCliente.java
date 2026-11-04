package com.miscelanea.client.baile;

import com.miscelanea.Miscelanea;
import com.miscelanea.baile.BailePacket;
import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.baile.PistaBaileBlock;
import com.miscelanea.client.arcade.PasoDoble;
import com.miscelanea.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Pista de baile del lado del jugador: menú de canción, flechas en la pantalla (a la derecha), juicio de cada pulsación
 * al momento exacto de la tecla, flechas que se mantienen (con cola: se pisan al empezar y no se sueltan hasta que acaba
 * la cola), la cámara de cine ({@link CamaraBaile}) y la coreografía de la canción ({@link Coreografia}: el cuerpo baila
 * con la música, no con las teclas; los fallos solo le hacen dar un traspié).
 * WASD o flechas del teclado: los cuatro pasos · saltar: empezar · agacharse: bajarse de la pista.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class BaileCliente {
    static final int NADA = 0, MENU = 1, BAILE = 2, RESULTADO = 3;
    static final int LATENCIA = 4, VENTANA = 10;
    /** Estado de cada flecha: sin tocar, manteniéndola, hecha, fallada (o soltada antes de tiempo). */
    static final int PENDIENTE = 0, MANTENIENDO = 1, HECHA = 2, FALLADA = 3;
    static final int[] COLOR = {0xFFB070F0, 0xFF5ADCFF, 0xFF69DC67, 0xFFFF5050}, LUZ = {0xFFE0C0FF, 0xFFC0F4FF, 0xFFC0FFC0, 0xFFFFC0C0};
    static final String[] JUICIOS = {"paso.miscelanea.perfecto", "paso.miscelanea.genial", "paso.miscelanea.bien", "paso.miscelanea.fallo",
            "paso.miscelanea.mantenido", "paso.miscelanea.soltado"};
    static final int[] COLOR_JUICIO = {0xFFFFE05A, 0xFF5ADCFF, 0xFF69DC67, 0xFFFF5050, 0xFFFFE05A, 0xFFFF8040};
    static boolean[][][] mascaras;

    static int modo, sel, dif = 1, menuT;
    static BlockPos pista;
    static CameraType camaraAntes;
    /** Hacia dónde mira el que baila (yaw de Minecraft): hacia la flecha de arriba de la pista. */
    static float frente;
    static CancionesBaile.Cancion cancion;
    static long inicio;
    static CancionesBaile.Nota[] notas;
    static int[] estado, tRoto;
    static int puntos, combo, maxCombo, vida, perf, geni, bien, fall, mant, mantTotal, juicio = -1;
    static long juicioT, finT;
    static final long[] pulsado = new long[4];
    static final boolean[] pisando = new boolean[4];

    private BaileCliente() {}

    // ------------------------------------------------------------------ para la cámara, el ratón y la animación
    static boolean camaraActiva() {
        return modo != NADA && pista != null;
    }

    /** Desde MouseHandlerMixin: mientras se baila el ratón no gira al jugador (la cámara va sola). */
    public static boolean raton() {
        return modo != NADA;
    }

    static CancionesBaile.Cancion cancionEnMarcha() {
        return modo == BAILE || modo == RESULTADO ? cancion : null;
    }

    /** Segundo de la canción que se está oyendo (con el retraso del sonido descontado). */
    static float segundoCancion() {
        return (System.currentTimeMillis() - inicio) / 1000F - LATENCIA / 60F;
    }

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
                BlockState st = mc.level == null ? null : mc.level.getBlockState(pista);
                frente = st != null && st.hasProperty(PistaBaileBlock.FACING) ? st.getValue(PistaBaileBlock.FACING).toYRot() : yo.getYRot();
                if (camaraAntes == null) camaraAntes = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                CamaraBaile.olvidar();
                BailePoses.empezar(yo.getId(), null, System.currentTimeMillis());
            }
            case BailePacket.EMPIEZA -> {
                CancionesBaile.Cancion c = CancionesBaile.de(m.cancion);
                if (c == null) {
                    if (m.entidad == yo.getId())
                        yo.displayClientMessage(Component.translatable("baile.miscelanea.sin_cancion", m.cancion).withStyle(ChatFormatting.RED), true);
                    return;
                }
                ponerMusica(m.pos, c);
                if (m.entidad == yo.getId() && modo != NADA) {
                    dif = Math.max(0, Math.min(2, m.b));
                    empezarCancion(c);
                } else BailePoses.empezar(m.entidad, c, System.currentTimeMillis());
            }
            case BailePacket.PASO -> BailePoses.paso(m.entidad, m.a, m.b);
            case BailePacket.PARA -> {
                BailePoses.parar(m.entidad);
                quitarMusica(m.pos);
                if (m.entidad == yo.getId()) cerrar();
            }
            default -> {}
        }
    }

    /** Canción que suena en cada pista (solo se corta la de esa pista, no las de las demás). */
    static final java.util.Map<BlockPos, MusicaPista> MUSICA = new java.util.HashMap<>();

    static void ponerMusica(BlockPos pos, CancionesBaile.Cancion c) {
        quitarMusica(pos);
        ResourceLocation rl = ResourceLocation.tryParse(c.sonido.contains(":") ? c.sonido : Miscelanea.MOD_ID + ":" + c.sonido);
        if (rl == null) return;
        // no hace falta que el sonido esté registrado: basta con que esté en el sounds.json de su espacio
        MusicaPista mp = new MusicaPista(SoundEvent.createVariableRangeEvent(rl), pos, c.id);
        MUSICA.put(mp.pista, mp);
        Minecraft.getInstance().getSoundManager().play(mp);
    }

    static void quitarMusica(BlockPos pos) {
        MusicaPista antes = MUSICA.remove(pos);
        if (antes != null) Minecraft.getInstance().getSoundManager().stop(antes);
    }

    @SubscribeEvent
    public static void limpiarMusica(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        // los bailarines de otras pistas que ya no se ven (se fueron lejos sin que llegara el aviso) dejan de bailar
        BailePoses.olvidarViejos(mc.player == null ? -1 : mc.player.getId());
        if (MUSICA.isEmpty()) return;
        // las que ya se han parado solas (pista rota, canción terminada...) se olvidan
        MUSICA.values().removeIf(mp -> mp.isStopped() || !mc.getSoundManager().isActive(mp) && mp.edadTics() > 40);
        if (mc.level == null) MUSICA.clear();
    }

    static void empezarCancion(CancionesBaile.Cancion c) {
        cancion = c;
        notas = c.notas[dif];
        estado = new int[notas.length];
        tRoto = new int[notas.length];
        mantTotal = 0;
        for (CancionesBaile.Nota n : notas) if (n.mantener()) mantTotal++;
        puntos = combo = maxCombo = perf = geni = bien = fall = mant = 0;
        vida = 70;
        juicio = -1;
        inicio = System.currentTimeMillis();
        modo = BAILE;
        CamaraBaile.olvidar();
        LocalPlayer yo = Minecraft.getInstance().player;
        if (yo != null) BailePoses.empezar(yo.getId(), c, inicio + LATENCIA * 1000L / 60);
    }

    static void cerrar() {
        Minecraft mc = Minecraft.getInstance();
        if (camaraAntes != null) mc.options.setCameraType(camaraAntes);
        camaraAntes = null;
        modo = NADA;
        pista = null;
        cancion = null;
        CamaraBaile.olvidar();
        java.util.Arrays.fill(pisando, false);
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
        if (modo == NADA || mc.player == null) return;
        int c = carrilDe(e.getKey(), mc);
        if (e.getAction() == GLFW.GLFW_RELEASE) {
            if (c >= 0) {
                pisando[c] = false;
                if (modo == BAILE) soltar(c, tiempo());
            }
            return;
        }
        if (mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        if (modo == MENU) {
            int n = Math.max(1, CancionesBaile.lista().size());
            if (c == 2) sel = (sel + n - 1) % n;
            if (c == 1) sel = (sel + 1) % n;
            if (c == 0) dif = Math.max(0, dif - 1);
            if (c == 3) dif = Math.min(2, dif + 1);
            if (c >= 0) mc.player.playSound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.get(), 0.6F, 1.6F);
            List<CancionesBaile.Cancion> l = CancionesBaile.lista();
            if (mc.options.keyJump.getKey().getValue() == e.getKey() && !l.isEmpty())
                ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.EMPIEZA, pista, 0, 0, dif, l.get(Math.min(sel, l.size() - 1)).id));
            return;
        }
        if (modo != BAILE || c < 0) return;
        pisando[c] = true;
        pulsado[c] = System.currentTimeMillis();
        int t = tiempo(), mejor = -1, md = 99;
        for (int i = 0; i < notas.length; i++) {
            if (notas[i].t - t > VENTANA) break;
            if (estado[i] != PENDIENTE || notas[i].carril != c) continue;
            int d = Math.abs(notas[i].t - t);
            if (d < md) {
                md = d;
                mejor = i;
            }
        }
        if (mejor >= 0 && md <= VENTANA) {
            int j = md <= 3 ? 0 : md <= 6 ? 1 : 2;
            estado[mejor] = notas[mejor].mantener() ? MANTENIENDO : HECHA;
            juzgar(j);
            mostrar(j);
            enviarPaso(c, j);
        }
    }

    /** Se suelta la tecla de un carril: si se estaba manteniendo una flecha, se juzga cuándo se ha soltado. */
    static void soltar(int c, int t) {
        for (int i = 0; i < notas.length; i++) {
            if (estado[i] != MANTENIENDO || notas[i].carril != c) continue;
            int falta = notas[i].fin - t;
            if (falta <= VENTANA) {
                // soltada a tiempo: cuanto más cerca del final de la cola, mejor
                int j = falta <= 3 ? 0 : falta <= 6 ? 1 : 2;
                estado[i] = HECHA;
                mant++;
                juzgar(j);
                mostrar(j == 0 ? 4 : j);
            } else {
                // soltada antes de tiempo: la cola se rompe
                estado[i] = FALLADA;
                tRoto[i] = t;
                juzgar(3);
                mostrar(5);
                enviarPaso(c, 3);
            }
        }
    }

    static void enviarPaso(int c, int j) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || pista == null) return;
        BailePoses.paso(mc.player.getId(), c, j);
        ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.PASO, pista, 0, c, j));
    }

    static void mostrar(int j) {
        juicio = j;
        juicioT = System.currentTimeMillis();
    }

    static void juzgar(int j) {
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
        int total = notas == null ? 0 : (notas.length + mantTotal) * 300;
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
        // mirando siempre hacia delante de la pista (la coreografía y los planos de la cámara cuentan con ello)
        p.setYRot(frente);
        p.yRotO = frente;
        p.setYHeadRot(frente);
        p.yHeadRotO = frente;
        p.yBodyRot = frente;
        p.yBodyRotO = frente;
        p.setXRot(0);
        if (mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        if (modo == BAILE) {
            int t = tiempo();
            // si se ha abierto una pantalla (o la tecla se soltó sin aviso) cuenta como soltar
            for (int c = 0; c < 4; c++) if (pisando[c] && (mc.screen != null || !teclaAbajo(mc, c))) {
                pisando[c] = false;
                soltar(c, t);
            }
            for (int i = 0; i < notas.length; i++) {
                CancionesBaile.Nota n = notas[i];
                if (n.t > t) break;
                if (estado[i] == PENDIENTE && t - n.t > VENTANA) {
                    estado[i] = FALLADA;
                    tRoto[i] = n.t;
                    juzgar(3);
                    mostrar(3);
                    enviarPaso(n.carril, 3);
                } else if (estado[i] == MANTENIENDO && t >= n.fin) {
                    // mantenida hasta el final de la cola
                    estado[i] = HECHA;
                    mant++;
                    juzgar(0);
                    mostrar(4);
                }
            }
            if (vida <= 0 || t > cancion.duracionPasos() + 30) {
                vida = Math.max(0, vida);
                modo = RESULTADO;
                finT = System.currentTimeMillis();
                ModNetwork.CHANNEL.sendToServer(new BailePacket(BailePacket.FIN, pista, 0, puntos, nota()));
            }
        } else if (modo == RESULTADO && System.currentTimeMillis() - finT > 5000) {
            modo = MENU;
            cancion = null;
            CamaraBaile.olvidar();
            BailePoses.empezar(p.getId(), null, System.currentTimeMillis());
        }
    }

    static boolean teclaAbajo(Minecraft mc, int c) {
        long w = mc.getWindow().getWindow();
        int[] flecha = {GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_RIGHT};
        var km = new net.minecraft.client.KeyMapping[]{mc.options.keyLeft, mc.options.keyDown, mc.options.keyUp, mc.options.keyRight}[c];
        int k = km.getKey().getValue();
        // solo teclas del teclado (si el paso está en un botón del ratón, se mira si el botón sigue pulsado)
        boolean teclado = km.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && k >= 32;
        return GLFW.glfwGetKey(w, flecha[c]) == GLFW.GLFW_PRESS || (teclado ? GLFW.glfwGetKey(w, k) == GLFW.GLFW_PRESS : km.isDown());
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

    static String titulo(CancionesBaile.Cancion c) {
        return c.tituloClave != null && I18n.exists(c.tituloClave) ? I18n.get(c.tituloClave) : c.titulo;
    }

    static int mezclarColor(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255),
                bl = (int) Mth.lerp(k, a & 255, b & 255);
        return (a & 0xFF000000) | (r << 16) | (g << 8) | bl;
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (modo == NADA) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int W = g.guiWidth(), H = g.guiHeight();
        var font = mc.font;
        if (modo == MENU) {
            List<CancionesBaile.Cancion> l = CancionesBaile.lista();
            int filas = Math.min(6, Math.max(1, l.size())), alto = 64 + filas * 18;
            int x0 = W / 2 - 120, y0 = H / 2 - alto / 2 - 10;
            g.fill(x0, y0, x0 + 240, y0 + alto, 0xC0180830);
            g.fill(x0, y0, x0 + 240, y0 + 2, 0xFFEC6CA8);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.titulo"), W / 2, y0 + 8, 0xFFFFE05A);
            if (l.isEmpty()) g.drawCenteredString(font, I18n.get("baile.miscelanea.sin_canciones"), W / 2, y0 + 30, 0xFFFF8080);
            int primera = Math.max(0, Math.min(sel - filas / 2, l.size() - filas));
            for (int f = 0; f < filas && primera + f < l.size(); f++) {
                int c = primera + f, y = y0 + 26 + f * 18;
                boolean s = c == sel;
                CancionesBaile.Cancion can = l.get(c);
                if (s) g.fill(x0 + 8, y - 4, x0 + 232, y + 12, 0xFF612895);
                String t = titulo(can);
                if (font.width(t) > 160) t = font.plainSubstrByWidth(t, 154) + "…";
                g.drawString(font, t, x0 + 16, y, s ? 0xFFFFFFFF : 0xFFA090C0);
                g.drawString(font, Math.round(can.bpm) + " BPM", x0 + 186, y, s ? 0xFFFFE05A : 0xFF706090);
            }
            if (primera > 0) g.drawCenteredString(font, "▲", x0 + 228, y0 + 16, 0xFFA090C0);
            if (primera + filas < l.size()) g.drawCenteredString(font, "▼", x0 + 228, y0 + 26 + filas * 18 - 4, 0xFFA090C0);
            String[] d = {"paso.miscelanea.facil", "paso.miscelanea.normal", "paso.miscelanea.dificil"};
            int yd = y0 + 30 + filas * 18;
            g.drawCenteredString(font, "◄ " + I18n.get(d[dif]) + " ►", W / 2, yd, 0xFF69DC67);
            g.drawCenteredString(font, I18n.get("baile.miscelanea.menu_ayuda"), W / 2, yd + 16, (menuT / 10) % 2 == 0 ? 0xFFFFE05A : 0xFFC0A040);
            return;
        }
        // pista de flechas a la derecha de la pantalla (la cámara deja al que baila a la izquierda)
        int esc = 2, paso = 14 * esc + 6, x0 = W - 4 * paso - 12, objY = 16;
        g.fill(x0 - 6, 0, W, H, 0x68000000);
        g.fill(x0 - 7, 0, x0 - 6, H, 0x40FFFFFF);
        long ahora = System.currentTimeMillis();
        int t = modo == BAILE ? tiempo() : 0;
        for (int c = 0; c < 4; c++) {
            boolean p = ahora - pulsado[c] < 120 || pisando[c];
            flecha(g, x0 + c * paso, objY, c, p ? 0xFFFFFFFF : 0xFF8080A0, 0, true, esc);
        }
        if (notas != null && modo == BAILE) {
            float vel = new float[]{1.7F, 2.2F, 2.7F}[dif] * esc * 0.75F;
            // primero las colas de las flechas que se mantienen (por debajo de las cabezas)
            for (int i = 0; i < notas.length; i++) {
                CancionesBaile.Nota n = notas[i];
                int yh = objY + Math.round((n.t - t) * vel);
                if (yh > H) break;
                if (!n.mantener() || estado[i] == HECHA) continue;
                int ye = objY + Math.round((n.fin - t) * vel);
                if (ye < -30) continue;
                int c = n.carril, cx = x0 + c * paso + 7 * esc;
                boolean sujeta = estado[i] == MANTENIENDO;
                if (sujeta) yh = objY;
                int arriba = yh + 7 * esc, abajo = Math.min(H, ye + 7 * esc);
                if (abajo <= arriba) continue;
                if (estado[i] == FALLADA) {
                    // rota: gris, partida en trozos y cayéndose
                    int caida = Math.max(0, t - tRoto[i]);
                    for (int y = arriba; y < abajo; y += 10 * esc)
                        g.fill(cx - 3 * esc + (y / 7 % 2) * esc, y + caida / 3, cx + 3 * esc - (y / 5 % 2) * esc, Math.min(abajo, y + 7 * esc) + caida / 3, 0x90606070);
                    continue;
                }
                int col = COLOR[c];
                if (sujeta) {
                    // brilla mientras se mantiene
                    float k = 0.5F + 0.5F * Mth.sin(ahora / 70F);
                    col = mezclarColor(LUZ[c], 0xFFFFFFFF, k);
                    g.fill(cx - 5 * esc, arriba, cx + 5 * esc, abajo, (COLOR[c] & 0x00FFFFFF) | 0x50000000);
                }
                g.fill(cx - 3 * esc, arriba, cx + 3 * esc, abajo, sujeta ? col : (col & 0x00FFFFFF) | 0xB0000000);
                g.fill(cx - 3 * esc, arriba, cx - 2 * esc, abajo, 0x60FFFFFF);
                // remate de la cola
                g.fill(cx - 5 * esc, abajo - 2 * esc, cx + 5 * esc, abajo, 0xFF101018);
                g.fill(cx - 4 * esc, abajo - 2 * esc + 1, cx + 4 * esc, abajo - 1, sujeta ? 0xFFFFFFFF : LUZ[c]);
            }
            for (int i = 0; i < notas.length; i++) {
                CancionesBaile.Nota n = notas[i];
                int c = n.carril, y = objY + Math.round((n.t - t) * vel);
                if (y > H) break;
                if (estado[i] == MANTENIENDO) {
                    flecha(g, x0 + c * paso, objY, c, 0xFFFFFFFF, LUZ[c], false, esc);
                    continue;
                }
                if (estado[i] != PENDIENTE || y < -30) continue;
                flecha(g, x0 + c * paso, y, c, COLOR[c], LUZ[c], false, esc);
            }
        }
        // vida y puntos
        int bx = x0, by = H - 24;
        g.fill(bx, by, bx + 4 * paso - 6, by + 6, 0xFF000000);
        g.fill(bx + 1, by + 1, bx + 1 + (int) ((4 * paso - 8) * Math.max(0, vida) / 100F), by + 5, vida < 25 ? 0xFFE04040 : 0xFF50D060);
        g.drawString(font, Integer.toString(puntos), bx, by - 12, 0xFFFFFFFF);
        if (juicio >= 0 && ahora - juicioT < 500) g.drawCenteredString(font, I18n.get(JUICIOS[juicio]), x0 + 2 * paso - 3, objY + 50, COLOR_JUICIO[juicio]);
        if (combo >= 5) g.drawCenteredString(font, combo + " COMBO", x0 + 2 * paso - 3, objY + 64, 0xFFFFE05A);
        if (modo == RESULTADO) {
            String[] n = {"S", "A", "B", "C", "D", "E"};
            int cx = W / 2;
            g.fill(cx - 90, H / 2 - 50, cx + 90, H / 2 + 52, 0xD0180830);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.resultado"), cx, H / 2 - 44, 0xFFFFE05A);
            g.pose().pushPose();
            g.pose().translate(cx, H / 2 - 30, 0);
            g.pose().scale(3, 3, 1);
            g.drawCenteredString(font, n[nota()], 0, 0, nota() == 0 ? 0xFFFFE05A : 0xFFFFFFFF);
            g.pose().popPose();
            g.drawCenteredString(font, I18n.get("paso.miscelanea.perfecto") + " " + perf + "  " + I18n.get("paso.miscelanea.genial") + " " + geni, cx, H / 2 + 2, 0xFFE0E0E0);
            g.drawCenteredString(font, I18n.get("paso.miscelanea.bien") + " " + bien + "  " + I18n.get("paso.miscelanea.fallo") + " " + fall, cx, H / 2 + 14, 0xFFE0E0E0);
            if (mantTotal > 0) g.drawCenteredString(font, I18n.get("baile.miscelanea.mantenidas", mant, mantTotal), cx, H / 2 + 26, 0xFFE0E0E0);
            g.drawCenteredString(font, "COMBO " + maxCombo + "  ·  " + puntos, cx, H / 2 + 38, 0xFFFFE05A);
        }
        g.drawString(font, I18n.get("baile.miscelanea.salir"), 6, H - 12, 0xA0FFFFFF);
    }
}
