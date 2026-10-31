package com.miscelanea.consola;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/** Cartucho de un juego: con llevarlo en el inventario, el juego aparece desbloqueado en la consola. */
public class CartuchoItem extends Item {
    public final Juego juego;

    public CartuchoItem(Juego juego, Properties p) {
        super(p);
        this.juego = juego;
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.cartucho.ayuda").withStyle(ChatFormatting.GRAY));
        if (level != null && level.isClientSide) {
            int r = RecordsPacket.cliente.getInt(juego.id);
            if (r > 0) lista.add(Component.translatable("item.miscelanea.cartucho.record", r).withStyle(ChatFormatting.GOLD));
        }
    }
}
