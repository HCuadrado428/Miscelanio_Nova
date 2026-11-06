package com.miscelanea.movil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Móvil: clic derecho con él en la mano (o su tecla, B por defecto, si lo llevas en el inventario) abre la pantalla.
 * La carcasa se tiñe con tintes en la mesa de crafteo ({@link MovilTinteReceta}).
 */
public class MovilItem extends Item {
    public static final String COLOR = "Color";
    /** Grafito: el color de fábrica. */
    public static final int COLOR_BASE = 0x3A3E46;

    public MovilItem(Properties p) {
        super(p);
    }

    public static int color(ItemStack s) {
        return s.hasTag() && s.getTag().contains(COLOR) ? s.getTag().getInt(COLOR) : COLOR_BASE;
    }

    /** Color de carcasa para cada tinte (un poco más suaves que los de vanilla, como los de los móviles). */
    public static int colorDeTinte(DyeColor d) {
        return switch (d) {
            case WHITE -> 0xF2F0EA;
            case LIGHT_GRAY -> 0xB9BCC2;
            case GRAY -> 0x6E7279;
            case BLACK -> 0x24262B;
            case RED -> 0xC8363A;
            case ORANGE -> 0xE98A3A;
            case YELLOW -> 0xF0D24E;
            case LIME -> 0x9BD553;
            case GREEN -> 0x4E8A4A;
            case CYAN -> 0x3FA3B0;
            case LIGHT_BLUE -> 0x8CC6F0;
            case BLUE -> 0x3B5BC4;
            case PURPLE -> 0x8A55C9;
            case MAGENTA -> 0xD067C4;
            case PINK -> 0xF2A7C3;
            case BROWN -> 0x8B6243;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack s = player.getItemInHand(hand);
        if (level.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.movil.MovilCliente.abrir(color(s)));
        return InteractionResultHolder.sidedSuccess(s, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level level, List<Component> lista, TooltipFlag flag) {
        lista.add(Component.translatable("item.miscelanea.movil.ayuda1", Component.keybind("key.miscelanea.movil")).withStyle(ChatFormatting.GRAY));
        lista.add(Component.translatable("item.miscelanea.movil.ayuda2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
