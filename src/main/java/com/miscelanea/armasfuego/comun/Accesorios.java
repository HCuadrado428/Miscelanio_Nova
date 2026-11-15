package com.miscelanea.armasfuego.comun;

import com.miscelanea.registry.ModEnchantments;
import net.minecraft.world.item.ItemStack;

/**
 * Las piezas de cobre de las armas son sus encantamientos: el que lleva Cañón largo, Estrangulador, Mira de cobre o Ánima
 * rayada tiene esa pieza puesta (se ve en el modelo) y su efecto; igual el Silenciador, el Compensador y el Depósito largo,
 * que ya existían. Aquí, los efectos nuevos: dispersión y silencio.
 */
public final class Accesorios {
    private Accesorios() {}

    /** Multiplicador de la dispersión por las piezas puestas (cañón largo 0,6, estrangulador 0,6, mira 0,5, ánima 0,35). */
    public static float dispersion(ItemStack arma) {
        if (arma == null || arma.isEmpty()) return 1F;
        float m = 1F;
        if (ArmaFuegoItem.nivel(ModEnchantments.CANON_LARGO, arma) > 0) m *= 0.6F;
        if (ArmaFuegoItem.nivel(ModEnchantments.ESCOPETA_ESTRANGULADOR, arma) > 0) m *= 0.6F;
        if (ArmaFuegoItem.nivel(ModEnchantments.MOSQUETE_MIRA, arma) > 0) m *= 0.5F;
        if (ArmaFuegoItem.nivel(ModEnchantments.CANON_ANIMA, arma) > 0) m *= 0.35F;
        return m;
    }

    /** ¿Lleva silenciador? (el disparo casi no suena ni lo oye el sculk) */
    public static boolean silencioso(ItemStack arma) {
        return arma != null && !arma.isEmpty() && ArmaFuegoItem.nivel(ModEnchantments.SILENCIADOR, arma) > 0;
    }
}
