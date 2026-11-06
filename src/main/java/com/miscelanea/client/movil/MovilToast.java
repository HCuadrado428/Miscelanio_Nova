package com.miscelanea.client.movil;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;

/** Notificación del móvil (mensaje, llamada, alarma) con el móvil cerrado, con forma de aviso de móvil moderno. */
public class MovilToast implements Toast {
    private final int app;
    private final String titulo, texto;

    public MovilToast(int app, String titulo, String texto) {
        this.app = app;
        this.titulo = titulo;
        this.texto = texto;
    }

    @Override
    public Visibility render(GuiGraphics g, ToastComponent tc, long t) {
        Ui.redondo(g, 0, 0, width(), height(), 7, 0xF0161C22);
        Ui.redondo(g, 1, 1, width() - 2, 1, 1, 0x30FFFFFF);
        Ui.icono(g, app, 4, 4);
        Ui.texto(g, Ui.cortar(titulo, width() - 36), 32, 6, 0xFFFFFFFF);
        Ui.texto(g, Ui.cortar(texto, width() - 36), 32, 18, Ui.TEXTO2);
        return t >= 5000L ? Visibility.HIDE : Visibility.SHOW;
    }
}
