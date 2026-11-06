package com.miscelanea.laser;

import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/** Puntero láser + tinte rojo, verde, azul o morado = láser de ese color. */
public class LaserTinteRecipe extends CustomRecipe {
    public LaserTinteRecipe(ResourceLocation id, CraftingBookCategory c) {
        super(id, c);
    }

    private static ItemStack[] buscar(CraftingContainer inv) {
        ItemStack puntero = ItemStack.EMPTY, tinte = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof PunteroLaserItem && puntero.isEmpty()) puntero = s;
            else if (s.getItem() instanceof DyeItem d && Laser.colorDeTinte(d.getDyeColor()) >= 0 && tinte.isEmpty()) tinte = s;
            else return null;
        }
        if (puntero.isEmpty() || tinte.isEmpty()) return null;
        if (Laser.color(puntero) == Laser.colorDeTinte(((DyeItem) tinte.getItem()).getDyeColor())) return null;
        return new ItemStack[]{puntero, tinte};
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
        out.getOrCreateTag().putInt(Laser.COLOR, Laser.colorDeTinte(((DyeItem) r[1].getItem()).getDyeColor()));
        return out;
    }

    @Override
    public boolean canCraftInDimensions(int w, int h) {
        return w * h >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return LaserRegistro.TINTE.get();
    }
}
