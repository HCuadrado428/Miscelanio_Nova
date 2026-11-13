package com.miscelanea.espera.eventos;

import com.miscelanea.espera.Espera;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Brújula de salida: en el Espera apunta a la puerta de SALIDA más cercana (con su cartel verde en la esfera).
 * Fuera de allí no sabe adónde mirar y da vueltas. El sitio lo calcula el servidor una vez por segundo (y solo si has
 * cambiado de chunk) y lo guarda en el objeto; el cliente solo gira la aguja.
 */
public class BrujulaSalidaItem extends Item {
    public static final String OBJETIVO = "SalidaCercana";

    public BrujulaSalidaItem(Properties p) {
        super(p);
    }

    /** El sitio al que apunta (o null: da vueltas). */
    @Nullable
    public static BlockPos objetivo(ItemStack st) {
        CompoundTag t = st.getTag();
        return t != null && t.contains(OBJETIVO) ? BlockPos.of(t.getLong(OBJETIVO)) : null;
    }

    @Override
    public void inventoryTick(ItemStack st, Level l, Entity e, int slot, boolean enMano) {
        if (l.isClientSide || e.tickCount % 20 != 7) return;
        if (!Espera.en(l)) {
            if (st.hasTag() && st.getTag().contains(OBJETIVO)) {
                st.getTag().remove(OBJETIVO);
                st.getTag().remove("Chunk");
            }
            return;
        }
        long chunk = e.chunkPosition().toLong();
        CompoundTag t = st.getOrCreateTag();
        if (t.contains(OBJETIVO) && t.getLong("Chunk") == chunk && e.tickCount % 200 != 7) return;
        BlockPos p = Salidas.cercana((ServerLevel) l, e.blockPosition(), false);
        t.putLong("Chunk", chunk);
        if (p == null) t.remove(OBJETIVO);
        else if (!t.contains(OBJETIVO) || t.getLong(OBJETIVO) != p.asLong()) t.putLong(OBJETIVO, p.asLong());
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack a, ItemStack b, boolean cambiaHueco) {
        return cambiaHueco || a.getItem() != b.getItem();
    }

    @Override
    public void appendHoverText(ItemStack s, @Nullable Level l, List<Component> lista, TooltipFlag f) {
        lista.add(Component.translatable("item.miscelanea.brujula_salida.ayuda").withStyle(ChatFormatting.GRAY));
    }
}
