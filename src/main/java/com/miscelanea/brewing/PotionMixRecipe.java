package com.miscelanea.brewing;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.common.brewing.IBrewingRecipe;

/**
 * Receta de alquimia "poción A + ingrediente = poción B" que respeta el NBT de la poción
 * (Ingredient.of(ItemStack) lo ignora) y conserva el tipo: normal, arrojadiza o persistente.
 */
public class PotionMixRecipe implements IBrewingRecipe {
    private final Potion from;
    private final Item ingredient;
    private final Potion to;

    public PotionMixRecipe(Potion from, Item ingredient, Potion to) {
        this.from = from;
        this.ingredient = ingredient;
        this.to = to;
    }

    @Override
    public boolean isInput(ItemStack input) {
        Item item = input.getItem();
        boolean isPotionItem = item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION;
        return isPotionItem && PotionUtils.getPotion(input) == from;
    }

    @Override
    public boolean isIngredient(ItemStack stack) {
        return stack.is(ingredient);
    }

    @Override
    public ItemStack getOutput(ItemStack input, ItemStack stack) {
        if (!isInput(input) || !isIngredient(stack)) {
            return ItemStack.EMPTY;
        }
        return PotionUtils.setPotion(new ItemStack(input.getItem()), to);
    }
}
