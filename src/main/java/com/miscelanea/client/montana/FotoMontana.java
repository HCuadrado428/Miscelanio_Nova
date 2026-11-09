package com.miscelanea.client.montana;

import com.miscelanea.client.movil.MovilCaptura;
import com.miscelanea.client.movil.MovilCliente;
import com.miscelanea.movil.MovilRed;
import com.miscelanea.movil.MovilRegistro;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * La foto de la bajada: como en las atracciones de verdad, una cámara de frente saca a los del vagón en plena caída y
 * la foto va directa a la galería del móvil (si llevas uno encima). Usa el capturador de la cámara del móvil, sin
 * filtro ni zoom.
 */
final class FotoMontana {
    private FotoMontana() {}

    static int hechas;
    private static int filtroAntes;
    private static float zoomAntes = 1;
    private static boolean pedida;

    /** ¿Lleva móvil y le cabe la foto? */
    static boolean puede(LocalPlayer p) {
        return p.getInventory().hasAnyMatching(MovilRegistro::esMovil) && MovilCliente.FOTOS.size() < MovilCliente.maxFotos;
    }

    /** Que el capturador del móvil copie la imagen de este fotograma (en vertical, sin filtro ni zoom). */
    static void pedir() {
        if (!pedida) {
            filtroAntes = MovilCaptura.filtro;
            zoomAntes = MovilCaptura.zoom;
            pedida = true;
        }
        MovilCaptura.filtro = 0;
        MovilCaptura.zoom = 1;
        MovilCaptura.pedir(false);
    }

    /** Saca la foto de lo capturado y la manda a la galería. */
    static void sacar() {
        byte[] jpg = MovilCaptura.foto(MovilCliente.maxBytesFoto);
        if (pedida) {
            MovilCaptura.filtro = filtroAntes;
            MovilCaptura.zoom = zoomAntes;
            pedida = false;
        }
        if (jpg == null) return;
        MovilRed.alServidor(MovilRed.C_FOTO_SUBIR, b -> b.writeByteArray(jpg));
        hechas++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.gui.setOverlayMessage(Component.translatable("montana.miscelanea.foto"), false);
    }
}
