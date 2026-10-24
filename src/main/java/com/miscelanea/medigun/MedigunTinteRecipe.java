package com.miscelanea.medigun;

import com.miscelanea.registry.ModItems;
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

/** Medigun + un tinte (en cualquier sitio de la mesa) = medigun con el rayo de ese color. */
public class MedigunTinteRecipe extends CustomRecipe {
    public MedigunTinteRecipe(ResourceLocation id, CraftingBookCategory category) {
        super(id, category);
    }

    private static ItemStack[] buscar(CraftingContainer inv) {
        ItemStack gun = ItemStack.EMPTY, tinte = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.is(ModItems.MEDIGUN.get()) && gun.isEmpty()) gun = s;
            else if (s.getItem() instanceof DyeItem && tinte.isEmpty()) tinte = s;
            else return null;
        }
        return gun.isEmpty() || tinte.isEmpty() ? null : new ItemStack[]{gun, tinte};
    }

    @Override
    public boolean matches(CraftingContainer inv, Level level) {
        return buscar(inv) != null;
    }

    @Override
    public ItemStack assemble(CraftingContainer inv, RegistryAccess access) {
        ItemStack[] r = buscar(inv);
        if (r == null) return ItemStack.EMPTY;
        ItemStack out = r[0].copyWithCount(1);
        out.getOrCreateTag().putInt(Medigun.COLOR, Medigun.colorDeTinte(((DyeItem) r[1].getItem()).getDyeColor()));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.MEDIGUN_TINTE.get();
    }
}
