package com.miscelanea.client;

import com.miscelanea.client.combate.LlaveCliente;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Teclas configurables de los minijuegos y pantallas del mod (Opciones → Controles → Miscelánea). Los nombres llevan
 * un prefijo ("Minijuegos: ", "Ritmo: ", "Consola: ", "Pip-Boy: ") para que salgan agrupadas, porque la lista de
 * controles ordena cada categoría por nombre.
 * <p>
 * Cada grupo tiene su propio contexto de conflicto ({@link Contexto}): solo choca consigo mismo, así que Espacio para
 * "Minijuegos: acción" no sale en rojo contra Saltar, ni las flechas de la consola contra las del baile. Estas teclas
 * se leen con {@link KeyMapping#matches} en los eventos de teclado y pantallas, o con {@link #pulsada} para "mantener".
 * Los números de las ruedas y de cortar cables usan las teclas de la barra rápida de vanilla (también configurables).
 */
public final class TeclasMod {
    private TeclasMod() {}

    /** Contextos propios: cada uno solo entra en conflicto con las teclas de su mismo grupo. */
    public enum Contexto implements IKeyConflictContext {
        /** QTE de tortura, aparatos (pulso, doncella, inmersión, interrogatorio) y la pesca. */
        MINIJUEGO {
            @Override
            public boolean isActive() {
                Screen s = Minecraft.getInstance().screen;
                return s == null ? com.miscelanea.client.tortura.qte.QteCliente.activo() : s instanceof com.miscelanea.client.bichos.PescaScreen;
            }
        },
        /** Pista de baile (flechas de los carriles). */
        RITMO {
            @Override
            public boolean isActive() {
                return Minecraft.getInstance().screen == null && com.miscelanea.client.baile.BaileCliente.activo();
            }
        },
        /** Consola portátil y recreativas. */
        CONSOLA {
            @Override
            public boolean isActive() {
                Screen s = Minecraft.getInstance().screen;
                return s instanceof com.miscelanea.client.consola.ConsolaScreen || s instanceof com.miscelanea.client.arcade.ArcadeScreen;
            }
        },
        /** Ruedas de selección (poderes de la Estática, herramientas del maletín). */
        RUEDAS {
            @Override
            public boolean isActive() {
                Screen s = Minecraft.getInstance().screen;
                return s instanceof com.miscelanea.client.estatica.RuedaPoderes || s instanceof com.miscelanea.client.tortura.aparatos.RuedaHerramientas;
            }
        },
        /** Pantalla del Pip-Boy. */
        PIPBOY {
            @Override
            public boolean isActive() {
                return Minecraft.getInstance().screen instanceof com.miscelanea.client.pipboy.PipBoyScreen;
            }
        };

        @Override
        public boolean conflicts(IKeyConflictContext otro) {
            return this == otro;
        }
    }

    private static KeyMapping k(String id, Contexto c, int tecla) {
        return new KeyMapping("key.miscelanea." + id, c, InputConstants.Type.KEYSYM, tecla, LlaveCliente.CATEGORIA);
    }

    // ---------------------------------------------------------------- minijuegos (QTE, aparatos, pesca)
    public static final KeyMapping MJ_ACCION = k("mj_accion", Contexto.MINIJUEGO, GLFW.GLFW_KEY_SPACE);
    public static final KeyMapping MJ_ARRIBA = k("mj_arriba", Contexto.MINIJUEGO, GLFW.GLFW_KEY_UP);
    public static final KeyMapping MJ_ABAJO = k("mj_abajo", Contexto.MINIJUEGO, GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping MJ_IZQ = k("mj_izquierda", Contexto.MINIJUEGO, GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping MJ_DER = k("mj_derecha", Contexto.MINIJUEGO, GLFW.GLFW_KEY_RIGHT);

    // ---------------------------------------------------------------- pista de baile (izquierda, abajo, arriba, derecha)
    public static final KeyMapping[] RITMO = {
            k("ritmo_izquierda", Contexto.RITMO, GLFW.GLFW_KEY_LEFT),
            k("ritmo_abajo", Contexto.RITMO, GLFW.GLFW_KEY_DOWN),
            k("ritmo_arriba", Contexto.RITMO, GLFW.GLFW_KEY_UP),
            k("ritmo_derecha", Contexto.RITMO, GLFW.GLFW_KEY_RIGHT)};

    // ---------------------------------------------------------------- consola y recreativas: [botón][0 principal, 1 alternativa]
    /** Mismo orden que las constantes de {@link com.miscelanea.client.consola.Botones} (ARRIBA..SELECT, C). */
    private static final String[] BOTON_ID = {"arriba", "abajo", "izquierda", "derecha", "a", "b", "start", "select", "c"};
    private static final int[][] BOTON_TECLA = {
            {GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_UP}, {GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_DOWN},
            {GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_LEFT}, {GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_RIGHT},
            {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_SPACE}, {GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_LEFT_SHIFT},
            {GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER}, {GLFW.GLFW_KEY_TAB, GLFW.GLFW_KEY_BACKSPACE},
            {GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_L}};
    public static final KeyMapping[][] CONSOLA = new KeyMapping[BOTON_ID.length][2];

    static {
        for (int i = 0; i < BOTON_ID.length; i++) {
            CONSOLA[i][0] = k("consola_" + BOTON_ID[i], Contexto.CONSOLA, BOTON_TECLA[i][0]);
            CONSOLA[i][1] = k("consola_" + BOTON_ID[i] + "_alt", Contexto.CONSOLA, BOTON_TECLA[i][1]);
        }
    }

    // ---------------------------------------------------------------- Pip-Boy
    public static final KeyMapping PIP_ANTERIOR = k("pipboy_anterior", Contexto.PIPBOY, GLFW.GLFW_KEY_Q);
    public static final KeyMapping PIP_SIGUIENTE = k("pipboy_siguiente", Contexto.PIPBOY, GLFW.GLFW_KEY_E);
    public static final KeyMapping PIP_QUITAR = k("pipboy_quitar", Contexto.PIPBOY, GLFW.GLFW_KEY_DELETE);

    // ---------------------------------------------------------------- ruedas (los números 1-9 son los de la barra rápida)
    public static final KeyMapping RUEDA_NINGUNO = k("rueda_ninguno", Contexto.RUEDAS, GLFW.GLFW_KEY_0);

    public static void registrar(RegisterKeyMappingsEvent e) {
        e.register(RUEDA_NINGUNO);
        e.register(MJ_ACCION);
        e.register(MJ_ARRIBA);
        e.register(MJ_ABAJO);
        e.register(MJ_IZQ);
        e.register(MJ_DER);
        for (KeyMapping m : RITMO) e.register(m);
        for (KeyMapping[] par : CONSOLA) for (KeyMapping m : par) e.register(m);
        e.register(PIP_ANTERIOR);
        e.register(PIP_SIGUIENTE);
        e.register(PIP_QUITAR);
    }

    // ---------------------------------------------------------------- utilidades
    /** Si la tecla está físicamente pulsada ahora (vale dentro de pantallas y sin depender del contexto). */
    public static boolean pulsada(KeyMapping k) {
        if (k.isUnbound()) return false;
        InputConstants.Key t = k.getKey();
        long w = Minecraft.getInstance().getWindow().getWindow();
        if (t.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(w, t.getValue()) == GLFW.GLFW_PRESS;
        return t.getType() == InputConstants.Type.KEYSYM && t.getValue() > 0 && InputConstants.isKeyDown(w, t.getValue());
    }

    /** Acción de minijuego: la tecla propia o Saltar de vanilla. */
    public static boolean esAccion(int key, int scan) {
        return MJ_ACCION.matches(key, scan) || Minecraft.getInstance().options.keyJump.matches(key, scan);
    }

    /** Acción de minijuego mantenida (propia o Saltar). */
    public static boolean accionPulsada() {
        return pulsada(MJ_ACCION) || Minecraft.getInstance().options.keyJump.isDown();
    }

    /** 0 arriba, 1 abajo, 2 izquierda, 3 derecha (teclas de minijuego o WASD de vanilla), -1 si no es una dirección. */
    public static int direccion(int key, int scan) {
        var o = Minecraft.getInstance().options;
        if (MJ_ARRIBA.matches(key, scan) || o.keyUp.matches(key, scan)) return 0;
        if (MJ_ABAJO.matches(key, scan) || o.keyDown.matches(key, scan)) return 1;
        if (MJ_IZQ.matches(key, scan) || o.keyLeft.matches(key, scan)) return 2;
        if (MJ_DER.matches(key, scan) || o.keyRight.matches(key, scan)) return 3;
        return -1;
    }

    /** Dirección de minijuego mantenida (0 arriba, 1 abajo, 2 izquierda, 3 derecha). */
    public static boolean direccionPulsada(int d) {
        var o = Minecraft.getInstance().options;
        return switch (d) {
            case 0 -> pulsada(MJ_ARRIBA) || o.keyUp.isDown();
            case 1 -> pulsada(MJ_ABAJO) || o.keyDown.isDown();
            case 2 -> pulsada(MJ_IZQ) || o.keyLeft.isDown();
            default -> pulsada(MJ_DER) || o.keyRight.isDown();
        };
    }

    /** Opción n (0..8) de una rueda o lista numerada: las teclas de la barra rápida de vanilla (1-9). -1 si no es ninguna. */
    public static int numero(int key, int scan, int max) {
        KeyMapping[] slots = Minecraft.getInstance().options.keyHotbarSlots;
        for (int i = 0; i < Math.min(max, slots.length); i++) if (slots[i].matches(key, scan)) return i;
        return -1;
    }

    /** Nombre de la tecla asignada, corto: las flechas como símbolo. */
    public static String corto(KeyMapping k) {
        if (k.getKey().getType() == InputConstants.Type.KEYSYM) {
            switch (k.getKey().getValue()) {
                case GLFW.GLFW_KEY_UP: return "↑";
                case GLFW.GLFW_KEY_DOWN: return "↓";
                case GLFW.GLFW_KEY_LEFT: return "←";
                case GLFW.GLFW_KEY_RIGHT: return "→";
                default:
            }
        }
        return k.getTranslatedKeyMessage().getString();
    }

    /** "principal / alternativa" (o solo una si coinciden o la otra no está asignada). */
    public static String dos(KeyMapping a, KeyMapping b) {
        if (b.isUnbound() || a.getKey().equals(b.getKey())) return corto(a);
        if (a.isUnbound()) return corto(b);
        return corto(a) + "/" + corto(b);
    }

    /** La cruceta de la consola: "WASD/↑←↓→" (arriba, izquierda, abajo, derecha; principales y alternativas). */
    public static String cruceta() {
        int[] orden = {0, 2, 1, 3};
        StringBuilder a = new StringBuilder(), b = new StringBuilder();
        for (int i : orden) {
            if (a.length() > 0 && corto(CONSOLA[i][0]).length() > 1) a.append(',');
            a.append(corto(CONSOLA[i][0]));
            if (!CONSOLA[i][1].isUnbound()) {
                if (b.length() > 0 && corto(CONSOLA[i][1]).length() > 1) b.append(',');
                b.append(corto(CONSOLA[i][1]));
            }
        }
        return b.length() == 0 ? a.toString() : a + "/" + b;
    }

    /** Ayuda de la consola o de la recreativa con las teclas asignadas ahora. */
    public static String ayudaConsola(String clave) {
        return net.minecraft.network.chat.Component.translatable(clave, cruceta(), consola(4), consola(5), consola(8), consola(6), consola(7)).getString();
    }

    /** Nombre de un botón de la consola con sus dos teclas. */
    public static String consola(int boton) {
        return dos(CONSOLA[boton][0], CONSOLA[boton][1]);
    }
}
