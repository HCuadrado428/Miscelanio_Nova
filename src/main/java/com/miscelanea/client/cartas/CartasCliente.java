package com.miscelanea.client.cartas;

import com.miscelanea.cartas.CartasPacket;
import net.minecraft.client.Minecraft;

/** Último estado de la partida recibido y apertura de la pantalla. */
public final class CartasCliente {
    static CartasPacket ultimo;

    private CartasCliente() {}

    public static void recibir(CartasPacket p) {
        ultimo = p;
        Minecraft mc = Minecraft.getInstance();
        if (p.abrir && !(mc.screen instanceof CartasScreen)) mc.setScreen(new CartasScreen(p.pos));
        else if (mc.screen instanceof CartasScreen s) s.actualizar();
    }
}
