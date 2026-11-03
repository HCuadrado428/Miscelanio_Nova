package com.miscelanea.client.juegos;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.ArrayList;
import java.util.List;

/**
 * Panel de HUD común a todos los juegos de mesa (sustituye a los carteles flotantes que tapaban el tablero).
 * <p>
 * Cada renderizador, mientras dibuja su tablero, ofrece un panel con {@link #ofrecer}; si hay varias mesas cerca gana
 * la que el jugador está apuntando y, si no, la más cercana (a menos de {@link #ALCANCE} bloques). El panel elegido se
 * pinta en la esquina superior izquierda con el mismo estilo para todos los juegos: título, estado, jugadores, detalle
 * de lo que se apunta, última jugada y una pista de controles.
 */
public final class PanelJuego {
    public static final double ALCANCE = 6;
    public static final int AMARILLO = 0xFFFFE070, CIAN = 0xFF80E0FF, BLANCO = 0xFFFFFFFF, GRIS = 0xFFB8B8B8, ROJO = 0xFFFF7060,
            VERDE = 0xFF90E890;
    private static final int ANCHO_MAX = 230, ANCHO_MIN = 120, MARGEN = 6, PAD = 6;

    /** Tecla de un control (se muestra con el nombre real de la tecla que tenga asignada el jugador). */
    public enum Tecla {USAR, AGACHADO_USAR, ATACAR, MOVER, ADELANTE_ATRAS, SALTAR, AGACHARSE}

    private static Panel siguiente, mostrado;
    private static float alfa;
    private static long ultimoNs;

    private PanelJuego() {}

    /**
     * Propone el panel de una mesa para este fotograma.
     *
     * @param centro   centro de la mesa en el mundo
     * @param apuntado si el jugador está apuntando a esa mesa (o jugando en ella): tiene preferencia
     * @param titulo   clave de idioma del nombre del juego
     * @param acento   color del juego (barra lateral y título)
     * @return el panel a rellenar, o {@code null} si no hace falta (lejos u otra mesa tiene preferencia)
     */
    public static Panel ofrecer(Vec3 centro, boolean apuntado, String titulo, int acento) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        double d = mc.player.getEyePosition().distanceToSqr(centro);
        if (!apuntado && d > ALCANCE * ALCANCE) return null;
        if (d > 16 * 16) return null;
        double prio = apuntado ? d - 10000 : d;
        BlockPos pos = BlockPos.containing(centro);
        if (siguiente != null && !siguiente.pos.equals(pos) && siguiente.prioridad <= prio) return null;
        siguiente = new Panel(pos, prio, I18n.get(titulo), acento);
        return siguiente;
    }

    public static Panel ofrecer(BlockPos bloque, boolean apuntado, String titulo, int acento) {
        return ofrecer(Vec3.atCenterOf(bloque), apuntado, titulo, acento);
    }

    // ------------------------------------------------------------------ contenido

    public static final class Panel {
        final BlockPos pos;
        final double prioridad;
        final String titulo;
        final int acento;
        String marcador;
        int colMarcador = BLANCO;
        final List<Fila> filas = new ArrayList<>(), detalle = new ArrayList<>();
        final List<String[]> controles = new ArrayList<>();
        String nota;

        Panel(BlockPos pos, double prioridad, String titulo, int acento) {
            this.pos = pos;
            this.prioridad = prioridad;
            this.titulo = titulo;
            this.acento = acento;
        }

        /** Texto grande a la derecha del título (p. ej. «3 - 2»). */
        public Panel marcador(String s, int col) {
            marcador = s;
            colMarcador = col;
            return this;
        }

        /** Línea de estado (de quién es el turno, qué pasa ahora...). */
        public Panel linea(String s, int col) {
            if (s != null && !s.isEmpty()) filas.add(new Fila(s, col, -1, false));
            return this;
        }

        /** Fila de un jugador: muestra de color, nombre y datos; resaltada si le toca. */
        public Panel jugador(String nombre, String extra, int col, boolean turno) {
            filas.add(new Fila(nombre + (extra == null || extra.isEmpty() ? "" : "  §7" + extra), turno ? BLANCO : 0xFFD8D8D8, 0xFF000000 | col, turno));
            return this;
        }

        /** Información de lo que se está apuntando (casilla, ficha...), en un bloque aparte. */
        public Panel detalle(String s, int col) {
            if (s != null && !s.isEmpty()) detalle.add(new Fila(s, col, -1, false));
            return this;
        }

        /** Última jugada (en gris, al pie). */
        public Panel nota(String s) {
            nota = s;
            return this;
        }

        /** Pista de control: «[tecla] acción». */
        public Panel control(Tecla t, String claveAccion, Object... args) {
            controles.add(new String[]{tecla(t), I18n.get(claveAccion, args)});
            return this;
        }
    }

    record Fila(String txt, int col, int muestra, boolean turno) {}

    static String tecla(Tecla t) {
        var o = Minecraft.getInstance().options;
        return switch (t) {
            case USAR -> nombre(o.keyUse);
            case AGACHADO_USAR -> nombre(o.keyShift) + " + " + nombre(o.keyUse);
            case ATACAR -> nombre(o.keyAttack);
            case SALTAR -> nombre(o.keyJump);
            case AGACHARSE -> nombre(o.keyShift);
            case ADELANTE_ATRAS -> nombre(o.keyUp) + "/" + nombre(o.keyDown);
            case MOVER -> {
                String a = nombre(o.keyUp), b = nombre(o.keyLeft), c = nombre(o.keyDown), d = nombre(o.keyRight);
                yield a.length() == 1 && b.length() == 1 && c.length() == 1 && d.length() == 1 ? a + b + c + d : a + "/" + b + "/" + c + "/" + d;
            }
        };
    }

    private static String nombre(KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString();
        // los nombres de los botones del ratón son largos: se abrevian
        if (k.getKey().getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            int b = k.getKey().getValue();
            if (b == 0) return I18n.get("panel.miscelanea.raton.izq");
            if (b == 1) return I18n.get("panel.miscelanea.raton.der");
            if (b == 2) return I18n.get("panel.miscelanea.raton.medio");
        }
        return s.length() == 1 ? s.toUpperCase() : s;
    }

    // ------------------------------------------------------------------ dibujo

    public static void render(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Panel p = siguiente;
        siguiente = null;
        long ahora = System.nanoTime();
        float dt = ultimoNs == 0 ? 0 : Math.min(0.1F, (ahora - ultimoNs) / 1e9F);
        ultimoNs = ahora;
        if (p != null) mostrado = p;
        alfa = Mth.clamp(alfa + (p != null ? dt : -dt) * 7F, 0F, 1F);
        if (mostrado == null || alfa <= 0.01F || mc.options.renderDebug || mc.player == null) {
            if (alfa <= 0.01F) mostrado = null;
            return;
        }
        dibujar(g, mc.font, mostrado, alfa, w);
    }

    private static void dibujar(GuiGraphics g, Font font, Panel p, float a, int anchoPantalla) {
        int max = Math.min(ANCHO_MAX, anchoPantalla / 2 - MARGEN * 2);
        // ---- medir
        int ancho = font.width(Component.literal(p.titulo).withStyle(s -> s.withBold(true))) + (p.marcador != null ? font.width(p.marcador) * 2 + 12 : 0);
        List<List<FormattedCharSequence>> filas = new ArrayList<>(), det = new ArrayList<>();
        for (Fila f : p.filas) filas.add(partir(font, f.txt, max - (f.muestra != -1 ? 10 : 0)));
        for (Fila f : p.detalle) det.add(partir(font, f.txt, max));
        List<FormattedCharSequence> nota = p.nota == null ? List.of() : partir(font, p.nota, max);
        for (int i = 0; i < filas.size(); i++)
            for (FormattedCharSequence s : filas.get(i)) ancho = Math.max(ancho, font.width(s) + (p.filas.get(i).muestra != -1 ? 10 : 0));
        for (List<FormattedCharSequence> l : det) for (FormattedCharSequence s : l) ancho = Math.max(ancho, font.width(s));
        for (FormattedCharSequence s : nota) ancho = Math.max(ancho, font.width(s));
        for (String[] c : p.controles) ancho = Math.max(ancho, Math.min(max, font.width(c[0]) + 8 + font.width(c[1])));
        ancho = Mth.clamp(ancho, ANCHO_MIN, max);

        int alto = PAD + 12;                       // título
        for (List<FormattedCharSequence> l : filas) alto += l.size() * 10;
        if (!det.isEmpty()) {
            alto += 5;
            for (List<FormattedCharSequence> l : det) alto += l.size() * 10;
        }
        if (!nota.isEmpty()) alto += 4 + nota.size() * 10;
        if (!p.controles.isEmpty()) alto += 5 + p.controles.size() * 12;
        alto += PAD - 2;

        int x0 = MARGEN, y0 = MARGEN, x1 = x0 + ancho + PAD * 2, y1 = y0 + alto;
        int fondo = col(0xE0101216, a), borde = col(0x60FFFFFF, a);
        // fondo con esquinas recortadas y un filo claro
        g.fill(x0 + 1, y0, x1 - 1, y1, fondo);
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, fondo);
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, fondo);
        g.fill(x0 + 1, y0, x1 - 1, y0 + 1, borde);
        g.fill(x0 + 3, y0, x0 + 3 + 2, y1, col(p.acento, a));                 // barra de color del juego
        int x = x0 + PAD + 3, y = y0 + PAD;

        // ---- título y marcador
        g.drawString(font, Component.literal(p.titulo).withStyle(s -> s.withBold(true)), x, y, col(p.acento | 0xFF000000, a), true);
        if (p.marcador != null) {
            g.pose().pushPose();
            g.pose().translate(x1 - PAD - font.width(p.marcador) * 1.5F, y - 3, 0);
            g.pose().scale(1.5F, 1.5F, 1);
            g.drawString(font, p.marcador, 0, 0, col(p.colMarcador, a), true);
            g.pose().popPose();
        }
        y += 12;
        g.fill(x, y - 3, x1 - PAD, y - 2, col(0x40FFFFFF, a));

        // ---- filas
        for (int i = 0; i < filas.size(); i++) {
            Fila f = p.filas.get(i);
            int dx = 0;
            if (f.muestra != -1) {
                if (f.turno) g.fill(x - 2, y - 1, x1 - PAD + 1, y + 9, col(0x30FFFFFF, a));
                g.fill(x, y + 1, x + 6, y + 7, col(0xFF000000, a));
                g.fill(x + 1, y + 2, x + 5, y + 6, col(f.muestra, a));
                dx = 10;
            }
            for (FormattedCharSequence s : filas.get(i)) {
                g.drawString(font, s, x + dx, y, col(f.col, a), true);
                y += 10;
            }
        }
        // ---- detalle de lo apuntado
        if (!det.isEmpty()) {
            y += 2;
            g.fill(x - 2, y - 1, x1 - PAD + 1, y + det.stream().mapToInt(List::size).sum() * 10 + 1, col(0x28FFFFFF, a));
            for (int i = 0; i < det.size(); i++)
                for (FormattedCharSequence s : det.get(i)) {
                    g.drawString(font, s, x, y + 1, col(p.detalle.get(i).col, a), true);
                    y += 10;
                }
            y += 3;
        }
        // ---- última jugada
        if (!nota.isEmpty()) {
            y += 4;
            for (FormattedCharSequence s : nota) {
                g.drawString(font, s, x, y, col(0xFF9A9A9A, a), true);
                y += 10;
            }
        }
        // ---- controles
        if (!p.controles.isEmpty()) {
            y += 3;
            g.fill(x, y - 1, x1 - PAD, y, col(0x40FFFFFF, a));
            y += 3;
            for (String[] c : p.controles) {
                int tw = font.width(c[0]);
                g.fill(x, y - 1, x + tw + 6, y + 10, col(0xFF3A3F48, a));
                g.fill(x, y + 9, x + tw + 6, y + 10, col(0xFF22252B, a));
                g.drawString(font, c[0], x + 3, y + 1, col(0xFFFFFFFF, a), false);
                String acc = font.plainSubstrByWidth(c[1], x1 - PAD - (x + tw + 10));
                g.drawString(font, acc, x + tw + 10, y + 1, col(0xFFC8D8E8, a), true);
                y += 12;
            }
        }
    }

    private static List<FormattedCharSequence> partir(Font font, String s, int max) {
        List<FormattedCharSequence> l = font.split(Component.literal(s).withStyle(Style.EMPTY), Math.max(40, max));
        return l.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : l;
    }

    private static int col(int argb, float a) {
        int al = (int) (((argb >>> 24) & 255) * a);
        return (Math.max(al, 5) << 24) | (argb & 0xFFFFFF);
    }
}
