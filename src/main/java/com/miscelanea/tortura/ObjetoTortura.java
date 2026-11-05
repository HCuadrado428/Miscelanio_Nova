package com.miscelanea.tortura;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Base de los objetos de la sala de juegos: ayuda en el tooltip ("item.miscelanea.X.ayuda", ".ayuda2") y "solo creativo". */
public class ObjetoTortura extends Item {
    private final int lineas;

    public ObjetoTortura(Properties p, int lineasAyuda) {
        super(p);
        this.lineas = lineasAyuda;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> l, TooltipFlag f) {
        for (int i = 1; i <= lineas; i++)
            l.add(Component.translatable(getDescriptionId() + ".ayuda" + (i == 1 ? "" : String.valueOf(i)),
                    // teclas que pueden citar las líneas (ganzúa: girar; maletín: atajos 1-4)
                    Component.keybind("key.miscelanea.mj_izquierda"), Component.keybind("key.left"),
                    Component.keybind("key.hotbar.1"), Component.keybind("key.hotbar.4")).withStyle(ChatFormatting.GRAY));
        l.add(Component.translatable("item.miscelanea.solo_creativo").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
