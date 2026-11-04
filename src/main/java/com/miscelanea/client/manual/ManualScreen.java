package com.miscelanea.client.manual;

import com.miscelanea.Miscelanea;
import com.miscelanea.manual.ManualRegistro;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * El manual del mod: un libro abierto (dos páginas por pliego) con portada y buscador, índice de capítulos, una portada
 * por capítulo con su contenido y una o más páginas por entrada (icono, nombre, sello de "solo creativo", la receta
 * leída del RecipeManager, ilustración y texto). Navegación: flechas (o rueda, ←/→, RePág/AvPág), índice, atrás y
 * adelante (como un navegador, para los saltos por enlace) y hasta {@link ManualEstado#MAX_MARCADORES} cintas de
 * marcador. Al cerrar se recuerda la página.
 * <p>
 * Todo va en pixels de GUI: la textura del libro está a 2 texels por pixel, así que con la escala de GUI 2 queda nítida
 * y con 3 y 4 sigue siendo legible (el texto es la fuente normal del juego).
 */
public class ManualScreen extends Screen {
    static final ResourceLocation LIBRO = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/manual/libro.png");
    static final ResourceLocation SPR = new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/manual/sprites.png");
    static final int W = 304, H = 200;
    /** Zona de texto de cada página (relativa al libro). */
    static final int[] PX = {20, 164};
    static final int PW = 120, PY = 16, PH = 152, PIE = 177;
    static final int TINTA = 0xFF3B2A1E, TITULO = 0xFF6B1E0E, ENLACE = 0xFF27477A, ENLACE_H = 0xFF8A2A14, GRIS = 0xFF7D6A52,
            VERDE = 0xFF2F5A23, SELLO = 0xFFA8362A;
    static final int[] COLOR_CINTA = {0xFFC0392B, 0xFF2E6DB4, 0xFF3C8D3C, 0xFFD9A520, 0xFF8E44AD, 0xFFD35400};

    private ManualLibro libro;
    private final List<Pagina> paginas = new ArrayList<>();
    private final Map<String, Integer> anclas = new HashMap<>();
    private int pliego, x0, y0, tick;
    private String consulta = "";
    private boolean buscando;
    private final Deque<String> atras = new ArrayDeque<>(), adelante = new ArrayDeque<>();
    private ItemStack hoverStack = ItemStack.EMPTY;
    private Component hoverTexto;

    public ManualScreen() {
        super(Component.translatable("item.miscelanea.manual_miscelanea"));
    }

    public static void abrir() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ManualScreen());
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        y0 = Math.max(16, (height - H) / 2 + 6);
        if (libro == null) {
            ManualEstado.leer();
            libro = ManualLibro.cargar();
            construir();
            if (ManualEstado.ultima != null) {
                Integer i = buscarAncla(ManualEstado.ultima);
                if (i != null) pliego = i / 2;
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        tick++;
    }

    @Override
    public void removed() {
        ManualEstado.ultima = anclaActual();
        ManualEstado.guardar();
    }

    // =================================================================== maquetación

    /** Una página: piezas colocadas de arriba abajo. */
    final class Pagina {
        final String ancla, titulo;
        final ItemStack icono;
        final List<Pieza> piezas = new ArrayList<>();
        boolean portada;

        Pagina(String ancla, String titulo, ItemStack icono) {
            this.ancla = ancla;
            this.titulo = titulo;
            this.icono = icono;
        }
    }

    /** Coloca piezas y abre páginas nuevas cuando no caben. */
    final class Maq {
        Pagina pag;
        int y, sub;
        String base;

        void nueva(String ancla, String titulo, ItemStack icono) {
            base = ancla;
            sub = 0;
            abrirPagina(ancla, titulo, icono);
        }

        private void abrirPagina(String ancla, String titulo, ItemStack icono) {
            pag = new Pagina(ancla, titulo, icono);
            paginas.add(pag);
            if (ancla != null) anclas.putIfAbsent(ancla, paginas.size() - 1);
            y = 0;
        }

        /** Pone la pieza; si además hay que dejar sitio para lo que viene detrás (un título y su primera línea), {@code reserva}. */
        void poner(Pieza p, int reserva) {
            if (y > 0 && y + p.alto + reserva > PH) {
                Pagina vieja = pag;
                sub++;
                abrirPagina(base == null ? null : base + "#" + sub, vieja.titulo, vieja.icono);
            }
            if (p instanceof Espacio && y == 0) return;
            p.y = y;
            pag.piezas.add(p);
            y += p.alto;
        }

        void poner(Pieza p) {
            poner(p, 0);
        }

        /** Que lo siguiente empiece en una página izquierda (pliego nuevo). */
        void pliegoNuevo() {
            if (paginas.size() % 2 == 1) {
                pag = new Pagina(null, "", ItemStack.EMPTY);
                paginas.add(pag);
            }
        }
    }

    private void construir() {
        paginas.clear();
        anclas.clear();
        Maq m = new Maq();
        // pliego 0: portada (izquierda) e índice o resultados de la búsqueda (derecha)
        m.nueva("portada", title.getString(), new ItemStack(ManualRegistro.MANUAL.get()));
        m.pag.portada = true;
        String idx = Component.translatable("manual.miscelanea.indice").getString();
        m.nueva("indice", idx, new ItemStack(ManualRegistro.MANUAL.get()));
        if (consulta.isBlank()) {
            m.poner(new Linea(estilo("§l" + idx), 0, TITULO, true));
            m.poner(new Separador());
            for (ManualLibro.Capitulo c : libro.capitulos)
                m.poner(new Enlace(c.iconoStack, c.titulo(), null, "c:" + c.id, 15, 1F));
        } else {
            List<Object[]> res = resultados();
            m.poner(new Linea(estilo("§l" + Component.translatable("manual.miscelanea.resultados", res.size()).getString()), 0, TITULO, true));
            m.poner(new Separador());
            if (res.isEmpty()) parrafos(m, Component.translatable("manual.miscelanea.sin_resultados").getString(), GRIS);
            for (Object[] r : res) m.poner(new Enlace((ItemStack) r[0], (String) r[1], (String) r[2], (String) r[3], 18, 1F));
        }
        for (ManualLibro.Capitulo c : libro.capitulos) {
            m.pliegoNuevo();
            capitulo(m, c);
        }
        m.pliegoNuevo();
    }

    /** {icono, título, subtítulo, ancla} de lo que coincide con la búsqueda. */
    private List<Object[]> resultados() {
        String q = ManualLibro.normal(consulta.trim());
        List<Object[]> l = new ArrayList<>();
        for (ManualLibro.Capitulo c : libro.capitulos) {
            if (ManualLibro.normal(c.titulo()).contains(q)) l.add(new Object[]{c.iconoStack, c.titulo(), null, "c:" + c.id});
            for (ManualLibro.Entrada e : c.entradas) {
                boolean si = ManualLibro.normal(e.titulo()).contains(q);
                for (ItemStack st : e.objetos) si |= ManualLibro.normal(st.getHoverName().getString()).contains(q);
                if (e.comando != null) si |= e.comando.contains(q);
                if (si) l.add(new Object[]{e.icono(), e.titulo(), c.titulo(), e.ancla()});
            }
        }
        return l;
    }

    private void capitulo(Maq m, ManualLibro.Capitulo c) {
        m.nueva("c:" + c.id, c.titulo(), c.iconoStack);
        if (!c.iconoStack.isEmpty()) m.poner(new Icono(List.of(c.iconoStack), 2, true));
        for (FormattedCharSequence s : font.split(estilo("§l" + c.titulo()), PW)) m.poner(new Linea(s, 0, TITULO, true));
        m.poner(new Separador());
        if (c.intro != null) parrafos(m, c.intro.get(), TINTA);
        switch (c.tipo) {
            case "controles" -> controles(m, c);
            case "comandos" -> comandos(m, c);
            default -> {
                if (c.entradas.isEmpty()) return;
                m.poner(new Espacio(4));
                m.poner(new Linea(estilo("§l" + Component.translatable("manual.miscelanea.contenido").getString()), 0, GRIS, false), 13);
                for (ManualLibro.Entrada e : c.entradas) m.poner(new Enlace(e.icono(), e.titulo(), null, e.ancla(), 13, 0.75F));
                for (ManualLibro.Entrada e : c.entradas) entrada(m, e);
            }
        }
    }

    private void entrada(Maq m, ManualLibro.Entrada e) {
        m.nueva(e.ancla(), e.titulo(), e.icono());
        boolean creativo = Boolean.TRUE.equals(e.creativo);
        m.poner(new Cabecera(e, creativo));
        List<ManualRecetas.Vista> rec = e.objetos.isEmpty() ? List.of() : ManualRecetas.buscar(e.objetos, e.receta);
        if (!rec.isEmpty()) m.poner(new Receta(rec));
        else if (!creativo && !e.objetos.isEmpty() && !"no".equals(e.receta))
            m.poner(new Linea(estilo("§o" + Component.translatable("manual.miscelanea.sin_receta").getString()), 0, GRIS, false));
        if (e.imagen != null) m.poner(new Imagen(e.imagen, Math.min(PW, e.imagenAncho), e.imagenAlto));
        m.poner(new Separador());
        parrafos(m, e.texto(), TINTA);
    }

    private void controles(Maq m, ManualLibro.Capitulo c) {
        Set<String> vistas = new HashSet<>();
        for (ManualLibro.Entrada e : c.entradas) {
            m.poner(new Espacio(5));
            if (e.tecla != null) {
                KeyMapping k = tecla(e.tecla);
                if (k == null) continue;
                vistas.add(e.tecla);
                anclar(m, e);
                String nombre = e.titulo != null ? e.titulo.get() : Component.translatable(k.getName()).getString();
                m.poner(new Tecla(k, nombre), 10);
            } else if (e.titulo != null) {
                anclar(m, e);
                m.poner(new Linea(estilo("§l" + e.titulo.get()), 0, TITULO, false), 10);
            }
            if (!e.texto().isBlank()) parrafos(m, e.texto(), TINTA);
        }
        // las teclas del mod que no explica ningún JSON (p. ej. de una rama nueva) también salen
        boolean cabecera = false;
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) {
            if (vistas.contains(k.getName())) continue;
            if (!k.getCategory().equals("key.categories." + Miscelanea.MOD_ID) && !k.getName().startsWith("key." + Miscelanea.MOD_ID + ".")) continue;
            if (!cabecera) {
                m.poner(new Espacio(6));
                m.poner(new Linea(estilo("§l" + Component.translatable("manual.miscelanea.otras_teclas").getString()), 0, TITULO, false), 14);
                cabecera = true;
            }
            m.poner(new Tecla(k, Component.translatable(k.getName()).getString()));
        }
    }

    private void comandos(Maq m, ManualLibro.Capitulo c) {
        Map<String, List<String>> usos = usosComandos();
        Set<String> vistos = new HashSet<>();
        for (ManualLibro.Entrada e : c.entradas) {
            m.poner(new Espacio(6));
            anclar(m, e);
            if (e.comando != null) {
                vistos.add(e.comando);
                m.poner(new Linea(estilo("§l/miscelanea " + e.comando), 0, VERDE, false), 20);
                List<String> u = usos.get(e.comando);
                if (u != null) for (String s : u) for (FormattedCharSequence l : font.split(Component.literal(s), PW - 6)) m.poner(new Linea(l, 6, GRIS, false));
                else if (e.uso != null) parrafos(m, e.uso.get(), GRIS);
            } else if (e.titulo != null) {
                m.poner(new Linea(estilo("§l" + e.titulo.get()), 0, TITULO, false), 10);
            }
            if (!e.texto().isBlank()) parrafos(m, e.texto(), TINTA);
        }
        for (var en : usos.entrySet()) {
            if (vistos.contains(en.getKey())) continue;
            m.poner(new Espacio(6));
            m.poner(new Linea(estilo("§l/miscelanea " + en.getKey()), 0, VERDE, false), 10);
            for (String s : en.getValue()) for (FormattedCharSequence l : font.split(Component.literal(s), PW - 6)) m.poner(new Linea(l, 6, GRIS, false));
        }
    }

    /** Para que los enlaces y la búsqueda lleguen a una entrada que no empieza página (controles y comandos). */
    private void anclar(Maq m, ManualLibro.Entrada e) {
        anclas.putIfAbsent(e.ancla(), paginas.size() - 1);
    }

    /** Los subcomandos de /miscelanea que conoce el cliente (solo los que el jugador puede usar) y cómo se escriben. */
    private static Map<String, List<String>> usosComandos() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        ClientPacketListener con = Minecraft.getInstance().getConnection();
        if (con == null) return m;
        try {
            CommandDispatcher<SharedSuggestionProvider> d = con.getCommands();
            CommandNode<SharedSuggestionProvider> raiz = d.getRoot().getChild(Miscelanea.MOD_ID);
            if (raiz == null) return m;
            for (CommandNode<SharedSuggestionProvider> h : raiz.getChildren()) {
                List<String> l = new ArrayList<>();
                Map<CommandNode<SharedSuggestionProvider>, String> u = d.getSmartUsage(h, con.getSuggestionsProvider());
                for (String s : u.values()) l.add("/" + Miscelanea.MOD_ID + " " + h.getName() + " " + s);
                if (l.isEmpty()) l.add("/" + Miscelanea.MOD_ID + " " + h.getName());
                m.put(h.getName(), l);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    private static KeyMapping tecla(String nombre) {
        for (KeyMapping k : Minecraft.getInstance().options.keyMappings) if (k.getName().equals(nombre)) return k;
        return null;
    }

    private static String nombreTecla(KeyMapping k) {
        return k.isUnbound() ? "—" : k.getTranslatedKeyMessage().getString();
    }

    /** Párrafos: "# " título, "- " viñeta, línea vacía = espacio. {key:nombre} = la tecla actual del jugador. */
    private void parrafos(Maq m, String texto, int color) {
        if (texto == null) return;
        boolean primero = true;
        for (String p : teclas(texto).split("\n", -1)) {
            if (p.isBlank()) {
                m.poner(new Espacio(4));
                continue;
            }
            if (p.startsWith("# ")) {
                if (!primero) m.poner(new Espacio(3));
                List<FormattedCharSequence> ls = font.split(estilo("§l" + p.substring(2)), PW);
                for (int i = 0; i < ls.size(); i++) m.poner(new Linea(ls.get(i), 0, TITULO, false), i == ls.size() - 1 ? 10 : 0);
            } else if (p.startsWith("- ")) {
                List<FormattedCharSequence> ls = font.split(estilo(p.substring(2)), PW - 8);
                for (int i = 0; i < ls.size(); i++) {
                    Linea l = new Linea(ls.get(i), 8, color, false);
                    l.vineta = i == 0;
                    m.poner(l);
                }
            } else {
                for (FormattedCharSequence s : font.split(estilo(p), PW)) m.poner(new Linea(s, 0, color, false));
            }
            primero = false;
        }
    }

    /** Cambia {key:key.miscelanea.pipboy} por la tecla que tiene puesta el jugador. */
    private static String teclas(String s) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int a = s.indexOf('{', i);
            if (a < 0) break;
            int b = s.indexOf('}', a);
            if (b < 0) break;
            String dentro = s.substring(a + 1, b);
            sb.append(s, i, a);
            if (dentro.startsWith("key:") || dentro.startsWith("tecla:")) {
                KeyMapping k = tecla(dentro.substring(dentro.indexOf(':') + 1));
                sb.append("§l").append(k == null ? "?" : nombreTecla(k)).append("§r");
            } else {
                sb.append('{').append(dentro).append('}');
            }
            i = b + 1;
        }
        sb.append(s.substring(i));
        return sb.toString();
    }

    /** Texto con códigos § (§l negrita, §o cursiva, colores, §r) → componente con estilos (para partirlo en líneas). */
    static MutableComponent estilo(String s) {
        MutableComponent c = Component.empty();
        Style st = Style.EMPTY;
        StringBuilder trozo = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                ChatFormatting f = ChatFormatting.getByCode(s.charAt(i + 1));
                if (trozo.length() > 0) {
                    c.append(Component.literal(trozo.toString()).withStyle(st));
                    trozo.setLength(0);
                }
                if (f == ChatFormatting.RESET || f == null) st = Style.EMPTY;
                else if (f.isColor()) st = Style.EMPTY.withColor(f);
                else st = st.applyFormat(f);
                i++;
                continue;
            }
            trozo.append(ch);
        }
        if (trozo.length() > 0) c.append(Component.literal(trozo.toString()).withStyle(st));
        return c;
    }

    // =================================================================== piezas

    abstract class Pieza {
        int y;
        final int alto;

        Pieza(int alto) {
            this.alto = alto;
        }

        abstract void dibujar(GuiGraphics g, int x, int y, int mx, int my);

        boolean clic(int x, int y, double mx, double my) {
            return false;
        }
    }

    final class Espacio extends Pieza {
        Espacio(int alto) {
            super(alto);
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {}
    }

    final class Linea extends Pieza {
        final FormattedCharSequence s;
        final int dx, color;
        final boolean centrada;
        boolean vineta;

        Linea(FormattedCharSequence s, int dx, int color, boolean centrada) {
            super(10);
            this.s = s;
            this.dx = dx;
            this.color = color;
            this.centrada = centrada;
        }

        Linea(Component c, int dx, int color, boolean centrada) {
            this(c.getVisualOrderText(), dx, color, centrada);
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            int xx = centrada ? x + (PW - font.width(s)) / 2 : x + dx;
            if (vineta) g.drawString(font, "•", x + 1, y, color, false);
            g.drawString(font, s, xx, y, color, false);
        }
    }

    final class Separador extends Pieza {
        Separador() {
            super(10);
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            spr(g, x + (PW - 64) / 2, y + 1, 0, 50, 64, 6);
        }
    }

    final class Icono extends Pieza {
        final List<ItemStack> st;
        final int esc;
        final boolean centrado;

        Icono(List<ItemStack> st, int esc, boolean centrado) {
            super(16 * esc + 4);
            this.st = st;
            this.esc = esc;
            this.centrado = centrado;
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            int xx = centrado ? x + (PW - 16 * esc) / 2 : x;
            icono(g, ciclo(st), xx, y + 1, esc, mx, my);
        }
    }

    /** Fila con icono y texto que lleva a un ancla (índice, contenido de un capítulo, resultados). */
    final class Enlace extends Pieza {
        final ItemStack icono;
        final String texto, sub, ancla;
        final float esc;

        Enlace(ItemStack icono, String texto, String sub, String ancla, int alto, float esc) {
            super(alto);
            this.icono = icono;
            this.texto = texto;
            this.sub = sub;
            this.ancla = ancla;
            this.esc = esc;
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            boolean h = dentro(mx, my, x - 2, y, PW + 4, alto - 1);
            if (h) g.fill(x - 2, y, x + PW + 2, y + alto - 1, 0x30A0703C);
            int tam = Math.round(16 * esc);
            int ix = x, iy = y + (sub == null ? (alto - 1 - tam) / 2 : 1);
            if (!icono.isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(ix, iy, 0);
                g.pose().scale(esc, esc, 1);
                g.renderItem(icono, 0, 0);
                g.pose().popPose();
            }
            int tx = x + tam + 4, ancho = PW - tam - 4;
            String t = recortar(texto, ancho);
            int ty = sub == null ? y + (alto - 9) / 2 : y;
            g.drawString(font, t, tx, ty, h ? ENLACE_H : ENLACE, false);
            if (h && font.width(t) < font.width(texto)) hoverTexto = Component.literal(texto);
            if (sub != null) {
                g.pose().pushPose();
                g.pose().translate(tx, y + 9.5F, 0);
                g.pose().scale(0.75F, 0.75F, 1);
                g.drawString(font, recortar(sub, (int) (ancho / 0.75F)), 0, 0, GRIS, false);
                g.pose().popPose();
            }
        }

        boolean clic(int x, int y, double mx, double my) {
            if (!dentro(mx, my, x - 2, y, PW + 4, alto - 1)) return false;
            ir(ancla, true);
            return true;
        }
    }

    /** Arriba de cada entrada: icono grande (alterna entre sus objetos), nombre y el sello de "solo creativo". */
    final class Cabecera extends Pieza {
        final ManualLibro.Entrada e;
        final List<FormattedCharSequence> lineas;
        final boolean creativo;

        Cabecera(ManualLibro.Entrada e, boolean creativo) {
            this(e, creativo, font.split(estilo("§l" + e.titulo()), PW - 38));
        }

        private Cabecera(ManualLibro.Entrada e, boolean creativo, List<FormattedCharSequence> l) {
            super(Math.max(38, 2 + Math.min(4, l.size()) * 10 + (creativo ? 13 : 0)) + 2);
            this.e = e;
            this.creativo = creativo;
            this.lineas = l.size() > 4 ? l.subList(0, 4) : l;
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            spr(g, x, y, 18, 24, 26, 26);
            spr(g, x + 10, y + 10, 18, 24, 26, 26);
            if (!e.objetos.isEmpty()) icono(g, ciclo(e.objetos), x + 2, y + 2, 2, mx, my);
            int ty = y + 2;
            for (FormattedCharSequence s : lineas) {
                g.drawString(font, s, x + 38, ty, TITULO, false);
                ty += 10;
            }
            if (creativo) {
                String t = Component.translatable("manual.miscelanea.solo_creativo").getString();
                int w = font.width(t) + 6;
                g.fill(x + 38, ty + 1, x + 38 + w, ty + 12, SELLO);
                g.fill(x + 39, ty + 2, x + 37 + w, ty + 11, 0xFFC4473A);
                g.drawString(font, t, x + 41, ty + 3, 0xFFFFF2E0, false);
            }
        }
    }

    final class Receta extends Pieza {
        final List<ManualRecetas.Vista> v;

        Receta(List<ManualRecetas.Vista> v) {
            super(64);
            this.v = v;
        }

        private ManualRecetas.Vista actual() {
            return v.get((tick / 60) % v.size());
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            ManualRecetas.Vista r = actual();
            String t = Component.translatable(switch (r.tipo()) {
                case MESA -> "manual.miscelanea.receta";
                case HORNO -> "manual.miscelanea.receta_horno";
                case ALQUIMIA -> "manual.miscelanea.receta_alquimia";
                default -> "manual.miscelanea.receta_otra";
            }).getString();
            g.drawString(font, estilo("§l" + t), x, y, GRIS, false);
            if (v.size() > 1) {
                String n = ((tick / 60) % v.size() + 1) + "/" + v.size();
                g.drawString(font, n, x + PW - font.width(n), y, GRIS, false);
            }
            int gy = y + 10;
            int rx = x + 88, ry = gy + 14;
            switch (r.tipo()) {
                case MESA -> {
                    for (int i = 0; i < 9; i++) hueco(g, r.huecos().get(i), x + 2 + (i % 3) * 18, gy + (i / 3) * 18, mx, my);
                    spr(g, x + 62, gy + 21, 44, 24, 16, 11);
                }
                case ALQUIMIA -> {
                    hueco(g, r.huecos().get(0), x + 6, gy + 18, mx, my);
                    g.drawString(font, "+", x + 29, gy + 23, GRIS, false);
                    hueco(g, r.huecos().get(1), x + 38, gy + 18, mx, my);
                    spr(g, x + 62, gy + 21, 44, 24, 16, 11);
                }
                default -> {
                    int n = r.huecos().size();
                    int hx = x + 58 - n * 18;
                    for (int i = 0; i < n; i++) hueco(g, r.huecos().get(i), hx + i * 18, gy + 18, mx, my);
                    spr(g, x + 62, gy + 21, 44, 24, 16, 11);
                }
            }
            if (!r.estacion().isEmpty()) {
                g.pose().pushPose();
                g.pose().translate(x + 64, gy + 35, 0);
                g.pose().scale(0.75F, 0.75F, 1);
                g.renderItem(r.estacion(), 0, 0);
                g.pose().popPose();
                if (dentro(mx, my, x + 64, gy + 35, 12, 12)) hoverStack = r.estacion();
            }
            spr(g, rx, ry - 5, 18, 24, 26, 26);
            icono(g, r.resultado(), rx + 5, ry, 1, mx, my);
            g.renderItemDecorations(font, r.resultado(), rx + 5, ry);
        }

        private void hueco(GuiGraphics g, List<ItemStack> ops, int hx, int hy, int mx, int my) {
            spr(g, hx, hy, 0, 24, 18, 18);
            if (!ops.isEmpty()) icono(g, ciclo(ops), hx + 1, hy + 1, 1, mx, my);
        }

        boolean clic(int x, int y, double mx, double my) {
            // clic en un ingrediente (o en el resultado): a su entrada del manual, si la tiene
            if (!hoverStack.isEmpty()) {
                ManualLibro.Entrada e = libro.porObjeto.get(ManualLibro.clave(hoverStack));
                if (e != null && dentro(mx, my, x, y, PW, alto)) {
                    ir(e.ancla(), true);
                    return true;
                }
            }
            return false;
        }
    }

    final class Imagen extends Pieza {
        final ResourceLocation tex;
        final int w, h;

        Imagen(ResourceLocation tex, int w, int h) {
            super(h + 6);
            this.tex = tex;
            this.w = w;
            this.h = h;
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            int ix = x + (PW - w) / 2;
            g.fill(ix - 1, y + 2, ix + w + 1, y + h + 4, 0xFFB49A68);
            g.blit(tex, ix, y + 3, w, h, 0, 0, w * 2, h * 2, w * 2, h * 2);
        }
    }

    /** Una tecla del juego: tecla dibujada (con lo que tenga puesto el jugador) y su nombre al lado. */
    final class Tecla extends Pieza {
        final KeyMapping k;
        final List<FormattedCharSequence> nombre;
        final int ancho;

        Tecla(KeyMapping k, String nombre) {
            this(k, nombre, Math.min(64, Math.max(16, font.width(nombreTecla(k)) + 8)));
        }

        private Tecla(KeyMapping k, String nombre, int ancho) {
            this(k, ancho, font.split(estilo("§l" + nombre), PW - ancho - 4));
        }

        private Tecla(KeyMapping k, int ancho, List<FormattedCharSequence> l) {
            super(Math.max(15, l.size() * 10 + 3));
            this.k = k;
            this.ancho = ancho;
            this.nombre = l;
        }

        void dibujar(GuiGraphics g, int x, int y, int mx, int my) {
            String t = nombreTecla(k);
            g.fill(x, y, x + ancho, y + 13, 0xFF5A4630);
            g.fill(x + 1, y + 1, x + ancho - 1, y + 12, 0xFFB8A27A);
            g.fill(x + 1, y + 1, x + ancho - 1, y + 10, 0xFFF1E4C4);
            String tt = recortar(t, ancho - 4);
            g.drawString(font, tt, x + (ancho - font.width(tt)) / 2, y + 2, k.isUnbound() ? SELLO : TINTA, false);
            if (dentro(mx, my, x, y, ancho, 13) && !tt.equals(t)) hoverTexto = Component.literal(t);
            int ty = y + 2;
            for (FormattedCharSequence s : nombre) {
                g.drawString(font, s, x + ancho + 4, ty, TITULO, false);
                ty += 10;
            }
        }
    }

    // =================================================================== dibujo

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        hoverStack = ItemStack.EMPTY;
        hoverTexto = null;
        cintas(g, mx, my);
        g.blit(LIBRO, x0, y0, W, H, 0, 0, W * 2, H * 2, W * 2, H * 2);
        for (int lado = 0; lado < 2; lado++) {
            int i = pliego * 2 + lado;
            int x = x0 + PX[lado], y = y0 + PY;
            if (i >= paginas.size()) continue;
            Pagina p = paginas.get(i);
            if (p.portada) portada(g, x, y, mx, my);
            else if (p.piezas.isEmpty()) spr(g, x + (PW - 40) / 2, y + PH / 2 - 12, 64, 50, 40, 24);
            for (Pieza pz : p.piezas) pz.dibujar(g, x, y + pz.y, mx, my);
            if (!p.portada) {
                String n = String.valueOf(i);
                g.drawString(font, n, x + (PW - font.width(n)) / 2, y0 + PIE + 1, GRIS, false);
            }
        }
        pie(g, mx, my);
        super.render(g, mx, my, pt);
        if (!hoverStack.isEmpty()) g.renderTooltip(font, hoverStack, mx, my);
        else if (hoverTexto != null) g.renderTooltip(font, hoverTexto, mx, my);
    }

    private void portada(GuiGraphics g, int x, int y, int mx, int my) {
        String sub = Component.translatable("manual.miscelanea.portada.arriba").getString();
        g.drawString(font, sub, x + (PW - font.width(sub)) / 2, y + 2, GRIS, false);
        Component tit = estilo("§l" + Component.translatable("manual.miscelanea.portada.titulo").getString());
        int tw = font.width(tit);
        g.pose().pushPose();
        g.pose().translate(x + (PW - tw * 2) / 2F, y + 13, 0);
        g.pose().scale(2, 2, 1);
        g.drawString(font, tit, 0, 0, TITULO, false);
        g.pose().popPose();
        spr(g, x + (PW - 64) / 2, y + 33, 0, 50, 64, 6);
        icono(g, new ItemStack(ManualRegistro.MANUAL.get()), x + (PW - 32) / 2, y + 42, 2, mx, my);
        int ty = y + 80;
        for (FormattedCharSequence s : font.split(Component.translatable("manual.miscelanea.portada.intro"), PW)) {
            g.drawString(font, s, x + (PW - font.width(s)) / 2, ty, TINTA, false);
            ty += 10;
            if (ty > y + 114) break;
        }
        // buscador
        int bx = x, by = y + 120;
        g.fill(bx, by, bx + PW, by + 14, buscando ? 0xFF8A5A2A : 0xFFB49A68);
        g.fill(bx + 1, by + 1, bx + PW - 1, by + 13, 0xFFFBF3DE);
        spr(g, bx + 3, by + 3, 72, 24, 9, 9);
        String t = consulta;
        int maxw = PW - 18;
        while (font.width(t) > maxw && t.length() > 0) t = t.substring(1);
        if (t.isEmpty() && !buscando)
            g.drawString(font, estilo("§o" + Component.translatable("manual.miscelanea.buscar").getString()), bx + 14, by + 3, GRIS, false);
        else g.drawString(font, t, bx + 14, by + 3, TINTA, false);
        if (buscando && (tick / 6) % 2 == 0) g.fill(bx + 14 + font.width(t), by + 2, bx + 15 + font.width(t), by + 12, TINTA);
        // seguir donde lo dejaste
        String ult = ManualEstado.ultima;
        if (ult != null && !ult.equals("portada") && !ult.equals("indice")) {
            Integer i = buscarAncla(ult);
            if (i != null) {
                Pagina p = paginas.get(i);
                String s = "» " + Component.translatable("manual.miscelanea.seguir", p.titulo).getString();
                boolean h = dentro(mx, my, x, y + 138, PW, 10);
                g.drawString(font, recortar(s, PW), x, y + 139, h ? ENLACE_H : ENLACE, false);
            }
        }
    }

    private void pie(GuiGraphics g, int mx, int my) {
        int y = y0 + PIE;
        boton(g, mx, my, x0 + 16, y, 18, 10, 18, 0, pliego > 0, "manual.miscelanea.anterior");
        boton(g, mx, my, x0 + 40, y - 1, 12, 12, 36, 0, pliego > 0, "manual.miscelanea.ir_indice");
        boton(g, mx, my, x0 + 56, y - 1, 12, 12, 48, 0, !atras.isEmpty(), "manual.miscelanea.atras");
        boton(g, mx, my, x0 + 236, y - 1, 12, 12, 60, 0, !adelante.isEmpty(), "manual.miscelanea.adelante");
        boolean marcado = ManualEstado.MARCADORES.contains(anclaActual());
        boton(g, mx, my, x0 + 252, y - 1, 10, 12, marcado ? 82 : 72, 0, pliego > 0,
                marcado ? "manual.miscelanea.desmarcar" : "manual.miscelanea.marcar");
        boton(g, mx, my, x0 + 270, y, 18, 10, 0, 0, pliego < ultimoPliego(), "manual.miscelanea.siguiente");
    }

    private void boton(GuiGraphics g, int mx, int my, int x, int y, int w, int h, int u, int v, boolean activo, String ayuda) {
        boolean hov = activo && dentro(mx, my, x, y, w, h);
        if (!activo) g.setColor(1, 1, 1, 0.3F);
        spr(g, x, y, u, v + (hov ? h : 0), w, h);
        if (!activo) g.setColor(1, 1, 1, 1);
        if (hov) hoverTexto = Component.translatable(ayuda);
    }

    /** Cintas de los marcadores: asoman por arriba del libro (se dibujan antes que él, que tapa su parte de abajo). */
    private void cintas(GuiGraphics g, int mx, int my) {
        String actual = anclaActual();
        for (int i = 0; i < ManualEstado.MARCADORES.size(); i++) {
            String a = ManualEstado.MARCADORES.get(i);
            int x = x0 + 178 + i * 16, y = y0 - 13;
            boolean aqui = a.equals(actual), hov = dentro(mx, my, x, y, 10, 13);
            if (aqui || hov) y -= 3;
            int c = COLOR_CINTA[i % COLOR_CINTA.length];
            g.setColor(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1);
            spr(g, x, y, 60, 24, 10, 26);
            g.setColor(1, 1, 1, 1);
            if (hov) {
                Integer p = buscarAncla(a);
                hoverTexto = Component.literal(p == null ? a : paginas.get(p).titulo);
            }
        }
    }

    private void icono(GuiGraphics g, ItemStack st, int x, int y, int esc, int mx, int my) {
        if (st.isEmpty()) return;
        if (esc == 1) g.renderItem(st, x, y);
        else {
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(esc, esc, 1);
            g.renderItem(st, 0, 0);
            g.pose().popPose();
        }
        if (dentro(mx, my, x, y, 16 * esc, 16 * esc)) hoverStack = st;
    }

    private ItemStack ciclo(List<ItemStack> l) {
        return l.isEmpty() ? ItemStack.EMPTY : l.get((tick / 20) % l.size());
    }

    private static void spr(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        g.blit(SPR, x, y, w, h, u * 2, v * 2, w * 2, h * 2, 256, 256);
    }

    private String recortar(String s, int ancho) {
        if (font.width(s) <= ancho) return s;
        return font.plainSubstrByWidth(s, ancho - font.width("…")) + "…";
    }

    private static boolean dentro(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // =================================================================== navegación

    private int ultimoPliego() {
        return Math.max(0, (paginas.size() - 1) / 2);
    }

    private String anclaActual() {
        for (int lado = 0; lado < 2; lado++) {
            int i = pliego * 2 + lado;
            if (i < paginas.size() && paginas.get(i).ancla != null) return paginas.get(i).ancla;
        }
        return "portada";
    }

    private Integer buscarAncla(String a) {
        Integer i = anclas.get(a);
        if (i == null && a.contains("#")) i = anclas.get(a.substring(0, a.indexOf('#')));
        return i;
    }

    private void irPliego(int n) {
        n = Math.max(0, Math.min(ultimoPliego(), n));
        if (n == pliego) return;
        pliego = n;
        buscando = false;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    void ir(String ancla, boolean historial) {
        Integer i = buscarAncla(ancla);
        if (i == null) return;
        if (historial) {
            atras.push(anclaActual());
            adelante.clear();
        }
        irPliego(i / 2);
    }

    private void volver() {
        if (atras.isEmpty()) return;
        adelante.push(anclaActual());
        ir(atras.pop(), false);
    }

    private void avanzar() {
        if (adelante.isEmpty()) return;
        atras.push(anclaActual());
        ir(adelante.pop(), false);
    }

    private void marcar() {
        String a = anclaActual();
        if (!ManualEstado.MARCADORES.remove(a)) {
            if (ManualEstado.MARCADORES.size() >= ManualEstado.MAX_MARCADORES) ManualEstado.MARCADORES.remove(0);
            ManualEstado.MARCADORES.add(a);
        }
        ManualEstado.guardar();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PUT, 1.0F));
    }

    private void cambiarConsulta(String q) {
        if (q.equals(consulta)) return;
        consulta = q;
        construir();
        pliego = 0;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int boton) {
        if (boton != 0) return super.mouseClicked(mx, my, boton);
        int y = y0 + PIE;
        if (pliego > 0 && dentro(mx, my, x0 + 16, y, 18, 10)) { irPliego(pliego - 1); return true; }
        if (pliego > 0 && dentro(mx, my, x0 + 40, y - 1, 12, 12)) { ir("portada", true); return true; }
        if (dentro(mx, my, x0 + 56, y - 1, 12, 12)) { volver(); return true; }
        if (dentro(mx, my, x0 + 236, y - 1, 12, 12)) { avanzar(); return true; }
        if (pliego > 0 && dentro(mx, my, x0 + 252, y - 1, 10, 12)) { marcar(); return true; }
        if (pliego < ultimoPliego() && dentro(mx, my, x0 + 270, y, 18, 10)) { irPliego(pliego + 1); return true; }
        for (int i = 0; i < ManualEstado.MARCADORES.size(); i++) {
            int x = x0 + 178 + i * 16;
            if (dentro(mx, my, x, y0 - 16, 10, 16)) { ir(ManualEstado.MARCADORES.get(i), true); return true; }
        }
        if (pliego == 0) {
            int px = x0 + PX[0], py = y0 + PY;
            buscando = dentro(mx, my, px, py + 120, PW, 14);
            if (buscando) return true;
            if (dentro(mx, my, px, py + 138, PW, 10) && ManualEstado.ultima != null && buscarAncla(ManualEstado.ultima) != null) {
                ir(ManualEstado.ultima, true);
                return true;
            }
        }
        for (int lado = 0; lado < 2; lado++) {
            int i = pliego * 2 + lado;
            if (i >= paginas.size()) continue;
            int x = x0 + PX[lado], py = y0 + PY;
            for (Pieza pz : paginas.get(i).piezas)
                // (hoverStack es el objeto bajo el ratón en el último fotograma: lo usan los clics en las recetas)
                if (my >= py + pz.y && my < py + pz.y + pz.alto && pz.clic(x, py + pz.y, mx, my)) return true;
        }
        return super.mouseClicked(mx, my, boton);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0) irPliego(pliego - 1);
        else if (delta < 0) irPliego(pliego + 1);
        return true;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (pliego == 0 && c >= ' ' && c != '§' && c != 127) {
            buscando = true;
            if (consulta.length() < 40) cambiarConsulta(consulta + c);
            return true;
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(key, scan, mods);
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_F) {
            ir("portada", pliego != 0);
            buscando = true;
            return true;
        }
        if (buscando && pliego == 0) {
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!consulta.isEmpty()) cambiarConsulta(ctrl ? "" : consulta.substring(0, consulta.length() - 1));
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                // Intro: a la primera coincidencia
                buscando = false;
                if (!consulta.isBlank() && paginas.size() > 1)
                    for (Pieza pz : paginas.get(1).piezas)
                        if (pz instanceof Enlace en) {
                            ir(en.ancla, true);
                            break;
                        }
                return true;
            }
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP -> { irPliego(pliego - 1); return true; }
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN -> { irPliego(pliego + 1); return true; }
            case GLFW.GLFW_KEY_HOME -> { ir("portada", true); return true; }
            case GLFW.GLFW_KEY_END -> { irPliego(ultimoPliego()); return true; }
            case GLFW.GLFW_KEY_BACKSPACE -> { volver(); return true; }
            default -> {}
        }
        return super.keyPressed(key, scan, mods);
    }
}
