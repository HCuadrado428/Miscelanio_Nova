package com.miscelanea.programa;

import com.miscelanea.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Qué objetos salen de cada sitio (como en The Outlast Trials: cambian en cada partida). */
public final class Botin {
    private Botin() {}

    private static ItemStack de(RandomSource r, Object... pares) {
        float total = 0;
        for (int i = 1; i < pares.length; i += 2) total += (Float) pares[i];
        float x = r.nextFloat() * total;
        for (int i = 0; i < pares.length; i += 2) {
            x -= (Float) pares[i + 1];
            if (x <= 0) return pares[i] == null ? ItemStack.EMPTY : new ItemStack(((java.util.function.Supplier<Item>) pares[i]).get());
        }
        return ItemStack.EMPTY;
    }

    /** Caja de herramientas cerrada. */
    public static ItemStack caja(RandomSource r) {
        return de(r, ModItems.PILA_GRANDE, 3f, ModItems.GANZUA, 2f, ModItems.LADRILLO, 2f, ModItems.ADRENALINA, 1.5f, ModItems.RECARGADOR, 1.5f);
    }

    /** Botiquín cerrado. */
    public static ItemStack botiquin(RandomSource r) {
        return de(r, ModItems.MEDICINA_GRANDE, 4.5f, ModItems.ANTIDOTO, 3f, ModItems.MEDICINA, 2.5f);
    }

    /** Archivador o cajón (sin cerradura): a veces no hay nada. */
    public static ItemStack cajon(RandomSource r) {
        return de(r, null, 4.5f, ModItems.BOTELLA, 1.2f, ModItems.PILA, 1.2f, ModItems.MEDICINA, 1f, ModItems.ANTIDOTO, 0.8f,
                ModItems.GANZUA, 1f, ModItems.LADRILLO, 0.8f);
    }

    /** Objeto suelto por el mapa. */
    public static ItemStack suelo(RandomSource r) {
        return de(r, ModItems.MEDICINA, 2f, ModItems.PILA, 2f, ModItems.BOTELLA, 2.5f, ModItems.LADRILLO, 2f, ModItems.ANTIDOTO, 1.5f,
                ModItems.GANZUA, 2f, ModItems.ADRENALINA, 0.5f);
    }
}
