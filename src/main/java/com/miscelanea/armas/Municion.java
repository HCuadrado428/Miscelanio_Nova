package com.miscelanea.armas;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Munición de las armas: se busca en todo el inventario. En creativo no se gasta. */
public final class Municion {
    private Municion() {}

    public static int cuenta(Player p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    public static boolean tiene(Player p, Item item) {
        return p.getAbilities().instabuild || cuenta(p, item) > 0;
    }

    public static boolean gastar(Player p, Item item) {
        if (p.getAbilities().instabuild) return true;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(item)) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }
}
