package com.miscelanea.tinta;

import com.miscelanea.registry.ModRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Arma de tinta + un tinte = tinta de ese color. */
public class TintaTinteRecipe extends CustomRecipe {
    public TintaTinteRecipe(ResourceLocation id, CraftingBookCategory c) {
        super(id, c);
    }

    private static ItemStack[] buscar(CraftingContainer inv) {
        ItemStack arma = ItemStack.EMPTY, tinte = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (ArmaTinta.es(s) && arma.isEmpty()) arma = s;
            else if (s.getItem() instanceof DyeItem && tinte.isEmpty()) tinte = s;
            else return null;
        }
        return arma.isEmpty() || tinte.isEmpty() ? null : new ItemStack[]{arma, tinte};
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return buscar(inv) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess a) {
        ItemStack[] r = buscar(inv);
        if (r == null) return ItemStack.EMPTY;
        ItemStack out = r[0].copyWithCount(1);
        out.getOrCreateTag().putInt(ArmaTinta.COLOR, ArmaTinta.colorDeTinte(((DyeItem) r[1].getItem()).getDyeColor()));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TINTA_TINTE.get();
    }
}
