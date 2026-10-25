package com.miscelanea.tinta;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Lo común de las armas de tinta: el color (se tiñe con tintes) y saber si el jugador lleva una. */
public final class ArmaTinta {
    public static final String COLOR = "ColorTinta";
    /** Naranja de Splatoon. */
    public static final int COLOR_DEFECTO = 0xF0641E;

    private ArmaTinta() {}

    public static boolean es(ItemStack s) {
        Item i = s.getItem();
        return i instanceof LanzatintasItem || i instanceof RodilloItem;
    }

    public static int color(ItemStack s) {
        return s.hasTag() && s.getTag().contains(COLOR) ? s.getTag().getInt(COLOR) : COLOR_DEFECTO;
    }

    /** El arma de tinta que lleva el jugador en alguna mano (o vacío). */
    public static ItemStack enMano(Player p) {
        if (es(p.getMainHandItem())) return p.getMainHandItem();
        if (es(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** Color del jugador (el de su arma), 0 si no lleva. */
    public static int colorJugador(Player p) {
        ItemStack s = enMano(p);
        return s.isEmpty() ? 0 : color(s);
    }

    /** Colores vivos y saturados como los de Splatoon. */
    public static int colorDeTinte(DyeColor d) {
        return switch (d) {
            case WHITE -> 0xF2F2F2;
            case ORANGE -> 0xF0641E;
            case MAGENTA -> 0xE21ED2;
            case LIGHT_BLUE -> 0x2FC8F5;
            case YELLOW -> 0xF2E21E;
            case LIME -> 0xA6F01E;
            case PINK -> 0xF25AA8;
            case GRAY -> 0x6A6A78;
            case LIGHT_GRAY -> 0xB4B4BE;
            case CYAN -> 0x1ED2C4;
            case PURPLE -> 0x8A28F0;
            case BLUE -> 0x2846F0;
            case BROWN -> 0x9A5A2A;
            case GREEN -> 0x28C83C;
            case RED -> 0xF02828;
            case BLACK -> 0x24242C;
        };
    }
}
