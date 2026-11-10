package com.miscelanea.armasfuego.oeste;

import com.miscelanea.registry.ModItems;
import com.miscelanea.registry.ModRecipes;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * La bayoneta del mosquete en la mesa de trabajo: mosquete + cualquier espada = mosquete con esa espada calada (si ya
 * llevaba una, la vieja se queda en la mesa); el mosquete solo = se quita la bayoneta (la espada se queda en la mesa).
 */
public class MosqueteBayonetaRecipe extends CustomRecipe {
    public MosqueteBayonetaRecipe(ResourceLocation id, CraftingBookCategory c) {
        super(id, c);
    }

    /** [índice del mosquete, índice de la espada (-1 si no hay)] o null si no vale. */
    private static int[] buscar(CraftingContainer inv) {
        int m = -1, e = -1;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.MOSQUETE.get()) && m < 0) m = i;
            else if (MosqueteItem.esEspada(s) && e < 0) e = i;
            else return null;
        }
        if (m < 0) return null;
        if (e < 0 && !MosqueteItem.tieneBayoneta(inv.getItem(m))) return null;
        return new int[]{m, e};
    }

    @Override
    public boolean matches(CraftingContainer inv, Level l) {
        return buscar(inv) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess r) {
        int[] k = buscar(inv);
        if (k == null) return ItemStack.EMPTY;
        ItemStack out = inv.getItem(k[0]).copyWithCount(1);
        MosqueteItem.ponerBayoneta(out, k[1] >= 0 ? inv.getItem(k[1]) : ItemStack.EMPTY);
        return out;
    }

    /** La espada que se quita (o la vieja, al cambiarla) se queda en el hueco del mosquete. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingContainer inv) {
        NonNullList<ItemStack> r = super.getRemainingItems(inv);
        int[] k = buscar(inv);
        if (k != null) {
            ItemStack m = inv.getItem(k[0]);
            if (MosqueteItem.tieneBayoneta(m)) r.set(k[0], MosqueteItem.bayoneta(m));
        }
        return r;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 1;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MOSQUETE_BAYONETA.get();
    }
}
