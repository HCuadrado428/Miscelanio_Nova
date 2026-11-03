package com.miscelanea.compat.jei;

import com.miscelanea.Miscelanea;
import com.miscelanea.cocina.FogonBlockEntity;
import com.miscelanea.cocina.Recetas;
import com.miscelanea.registry.ModItems;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Recetas del fogón: hasta 4 ingredientes en la sartén (sin orden) → plato. */
public class FogonCategoria implements IRecipeCategory<Recetas.Receta> {
    public static final RecipeType<Recetas.Receta> TIPO = RecipeType.create(Miscelanea.MOD_ID, "fogon", Recetas.Receta.class);
    static final int ANCHO = 120, ALTO = 46;

    private final IDrawable fondo, icono, hueco;

    public FogonCategoria(IGuiHelper g) {
        fondo = g.createBlankDrawable(ANCHO, ALTO);
        icono = g.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.FOGON.get()));
        hueco = g.getSlotDrawable();
    }

    @Override
    public RecipeType<Recetas.Receta> getRecipeType() {
        return TIPO;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.miscelanea.fogon");
    }

    @Override
    @SuppressWarnings("removal")
    public IDrawable getBackground() {
        return fondo;
    }

    // (en las versiones de JEI que ya no usan getBackground)
    public int getWidth() {
        return ANCHO;
    }

    public int getHeight() {
        return ALTO;
    }

    @Override
    public IDrawable getIcon() {
        return icono;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder b, Recetas.Receta receta, IFocusGroup focos) {
        var ing = receta.ingredientes();
        for (int i = 0; i < ing.size(); i++) {
            int x = 1 + (i % 2) * 18, y = 2 + (i / 2) * 18;
            b.addSlot(RecipeIngredientRole.INPUT, x, y).setBackground(hueco, -1, -1).addItemStack(new ItemStack(ing.get(i).get()));
        }
        b.addSlot(RecipeIngredientRole.CATALYST, 51, 24).addItemStack(new ItemStack(ModItems.FOGON.get()));
        b.addSlot(RecipeIngredientRole.OUTPUT, 96, 11).setBackground(hueco, -1, -1).addItemStack(receta.plato().get());
    }

    @Override
    public void draw(Recetas.Receta receta, IRecipeSlotsView vista, GuiGraphics g, double ratonX, double ratonY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "→", 72, 13, 0xFF808080, false);
        g.drawString(font, Component.translatable("jei.miscelanea.segundos", FogonBlockEntity.COCCION / 20), 44, 2, 0xFF808080, false);
    }
}
