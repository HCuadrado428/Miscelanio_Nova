package com.miscelanea.client.tortura.qte;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteResultado;
import com.miscelanea.tortura.qte.QteResultadoPacket;
import com.miscelanea.tortura.qte.QteTipo;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;

/** Base de los minijuegos del cliente: tiempos, teclas, resultado, sonido y el cartel final. */
public abstract class JuegoQte {
    protected final Minecraft mc = Minecraft.getInstance();
    public final int id;
    public final QteTipo tipo;
    protected final float dif;
    protected final int rondas;
    protected final long semilla;
    @Nullable protected final Component titulo;
    protected final long inicio = Util.getMillis();
    protected int ticks;
    public boolean terminado;
    public QteResultado resultado;
    public long fin;
    /** Temblor del widget (se gasta solo). */
    protected float sacudida;

    protected JuegoQte(QteInicioPacket p) {
        this.id = p.id;
        this.tipo = p.tipo;
        this.dif = p.dificultad;
        this.rondas = p.rondas;
        this.semilla = p.semilla;
        this.titulo = p.titulo;
    }

    /** Milisegundos desde que empezó. */
    protected long ms() {
        return Util.getMillis() - inicio;
    }

    public final void tick() {
        ticks++;
        if (sacudida > 0) sacudida = Math.max(0, sacudida - 1);
        if (!terminado) logica();
    }

    /** 20 veces por segundo mientras no ha terminado. */
    protected abstract void logica();

    /** Tecla del teclado (accion: GLFW_PRESS, GLFW_RELEASE o GLFW_REPEAT). */
    public void tecla(int key, int scan, int accion) {}

    public boolean usaRaton() {
        return false;
    }

    /** Movimiento del ratón (en las mismas unidades que el giro de la cámara). */
    public void raton(double h, double v) {}

    public abstract void dibujar(GuiGraphics g, int w, int h, float pt);

    /** Datos para que el servidor valide (ver QteLogica.plausible). */
    protected abstract int[] datos();

    public void acabar(QteResultado r) {
        if (terminado) return;
        terminado = true;
        resultado = r;
        fin = Util.getMillis();
        ModNetwork.CHANNEL.sendToServer(new QteResultadoPacket(id, r, datos()));
        switch (r) {
            case PERFECTO -> sonido(TorturaRegistro.QTE_PERFECTO.get(), 1F, 1F);
            case EXITO -> sonido(TorturaRegistro.QTE_EXITO.get(), 1F, 1F);
            case ROTO -> sonido(TorturaRegistro.GANZUA_ROTA.get(), 1F, 1F);
            case FALLO -> {
                sonido(TorturaRegistro.QTE_FALLO.get(), 1F, 1F);
                sacudida = 8;
            }
            default -> {
            }
        }
    }

    protected void sonido(SoundEvent s, float tono, float vol) {
        mc.getSoundManager().play(SimpleSoundInstance.forUI(s, tono, vol));
    }

    // ------------------------------------------------------------------ teclas
    protected boolean esSalto(int key, int scan) {
        return key == GLFW.GLFW_KEY_SPACE || mc.options.keyJump.matches(key, scan);
    }

    /** 0 arriba, 1 abajo, 2 izquierda, 3 derecha (WASD configurados o cursores), -1 si no es una dirección. */
    protected int direccion(int key, int scan) {
        if (key == GLFW.GLFW_KEY_UP || mc.options.keyUp.matches(key, scan)) return 0;
        if (key == GLFW.GLFW_KEY_DOWN || mc.options.keyDown.matches(key, scan)) return 1;
        if (key == GLFW.GLFW_KEY_LEFT || mc.options.keyLeft.matches(key, scan)) return 2;
        if (key == GLFW.GLFW_KEY_RIGHT || mc.options.keyRight.matches(key, scan)) return 3;
        return -1;
    }

    /** Nombre corto de la tecla configurada (para dibujarla en la tecla). */
    protected static String nombreTecla(net.minecraft.client.KeyMapping k) {
        String s = k.getTranslatedKeyMessage().getString();
        return s.length() > 7 ? s.substring(0, 7) : s.toUpperCase();
    }

    // ------------------------------------------------------------------ marco común
    /** Desplazamiento del temblor. */
    protected float temblorX() {
        return sacudida > 0 ? (float) Math.sin(ticks * 2.7 + sacudida) * sacudida * 0.6F : 0;
    }

    /** Título encima, ayuda debajo y, al acabar, el cartel del resultado. */
    protected void marco(GuiGraphics g, float cx, float arriba, float abajo, Component ayuda) {
        if (titulo != null) Dibujo.texto(g, mc.font, titulo.copy().withStyle(ChatFormatting.BOLD), cx, arriba - 12, 1F, 0xFFF2E6C8, true);
        if (!terminado) Dibujo.texto(g, mc.font, ayuda, cx, abajo + 4, 0.75F, 0xFFBFC4CC, true);
        if (terminado && resultado != null) {
            float t = (Util.getMillis() - fin) / 1000F;
            float a = Math.max(0, 1 - Math.max(0, t - 0.5F) * 2);
            float esc = 1.6F + Math.max(0, 0.25F - t) * 2.4F;
            String k = switch (resultado) {
                case PERFECTO -> "qte.miscelanea.perfecto";
                case EXITO -> "qte.miscelanea.exito";
                case ROTO -> "qte.miscelanea.roto";
                case CANCELADO -> "qte.miscelanea.cancelado";
                default -> "qte.miscelanea.fallo";
            };
            int col = switch (resultado) {
                case PERFECTO -> 0xFFFFD84A;
                case EXITO -> 0xFF6CF07A;
                case ROTO -> 0xFFFF9A3A;
                case CANCELADO -> 0xFFB0B0B0;
                default -> 0xFFFF4A40;
            };
            if (a > 0.04F)
                Dibujo.texto(g, mc.font, Component.translatable(k).withStyle(ChatFormatting.BOLD), cx, (arriba + abajo) / 2F - 6 * esc, esc, Dibujo.alfa(col, a), true);
        }
    }
}
