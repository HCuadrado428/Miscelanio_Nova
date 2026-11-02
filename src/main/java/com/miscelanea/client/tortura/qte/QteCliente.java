package com.miscelanea.client.tortura.qte;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.Input;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Minijuego en curso en el cliente. Sin pantalla (no pausa y el mundo sigue): se dibuja encima del HUD y se capturan
 * las teclas con InputEvent.Key; mientras dura, el jugador no anda, no salta, no pega ni usa objetos, y en GANZUA el
 * ratón mueve la ganzúa en vez de la cámara (MouseHandlerMixin). Abrir cualquier menú (Esc, chat...) lo cancela.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class QteCliente {
    private QteCliente() {}

    private static JuegoQte actual;

    public static void iniciar(QteInicioPacket p) {
        if (actual != null && !actual.terminado) actual.acabar(QteResultado.CANCELADO);
        actual = switch (p.tipo) {
            case CIRCULO -> new JuegoCirculo(p);
            case SECUENCIA -> new JuegoSecuencia(p);
            case MACHACAR, ALTERNAR -> new JuegoMachacar(p);
            case GANZUA -> new JuegoGanzua(p);
            case BARRA_ZONA -> new JuegoBarraZona(p);
            case RITMO -> new JuegoRitmo(p);
        };
    }

    /** El servidor lo ha cerrado (cancelado o fuera de plazo). */
    public static void fin(int id) {
        if (actual != null && actual.id == id && !actual.terminado) actual = null;
    }

    /** ¿Hay un minijuego jugándose (no el cartel final)? */
    public static boolean activo() {
        return actual != null && !actual.terminado;
    }

    /** Desde MouseHandlerMixin: true = el ratón es para el minijuego y la cámara no gira. */
    public static boolean raton(double h, double v) {
        if (!activo() || !actual.usaRaton()) return false;
        actual.raton(h, v);
        return true;
    }

    @SubscribeEvent
    public static void tecla(InputEvent.Key e) {
        if (activo() && Minecraft.getInstance().screen == null) actual.tecla(e.getKey(), e.getScanCode(), e.getAction());
    }

    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        if (!activo()) return;
        Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
    }

    @SubscribeEvent
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        if (!activo()) return;
        // tira y afloja del lazo: el clic derecho sigue sirviendo para tirar de la cuerda
        if (e.isUseItem() && com.miscelanea.client.tortura.LazoCliente.puedeTirar(e.getHand())) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || actual == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            actual = null;
            return;
        }
        if (!actual.terminado && mc.screen != null) actual.acabar(QteResultado.CANCELADO);
        actual.tick();
        if (actual.terminado && Util.getMillis() - actual.fin > 1100) actual = null;
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        actual = null;
    }

    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (actual == null || Minecraft.getInstance().options.hideGui) return;
        actual.dibujar(g, w, h, pt);
    }
}
