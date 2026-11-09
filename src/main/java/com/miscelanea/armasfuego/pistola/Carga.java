package com.miscelanea.armasfuego.pistola;

import com.miscelanea.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/** Lo que puede haber en una recámara del revólver (el orden es el que se guarda en el NBT: no cambiarlo). */
public enum Carga {
    VACIA("vacia", null, 0x00000000),
    BALA("bala", ModItems.REVOLVER_BALA, 0xFFE0A548),
    BENGALA("bengala", ModItems.REVOLVER_BENGALA, 0xFFFF4A2A),
    DARDO("dardo", ModItems.REVOLVER_DARDO, 0xFF7BC248),
    FOGUEO("fogueo", ModItems.REVOLVER_FOGUEO, 0xFFF2EAD2),
    IMAN("iman", ModItems.REVOLVER_IMAN, 0xFF4F7FE0);

    public final String id;
    private final Supplier<Item> item;
    /** Color del puntito en el tambor del HUD. */
    public final int color;

    Carga(String id, Supplier<Item> item, int color) {
        this.id = id;
        this.item = item;
        this.color = color;
    }

    public Item item() {
        return item == null ? null : item.get();
    }

    public static Carga de(int i) {
        Carga[] v = values();
        return i < 0 || i >= v.length ? VACIA : v[i];
    }

    /** La munición que es este objeto, o null si no es munición del revólver. */
    public static Carga de(ItemStack s) {
        if (s.isEmpty()) return null;
        for (Carga c : values())
            if (c.item != null && s.is(c.item.get())) return c;
        return null;
    }

    public String clave() {
        return "pistola.miscelanea.carga." + id;
    }
}
