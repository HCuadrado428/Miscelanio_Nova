package com.miscelanea.compat.jei;

import com.miscelanea.Miscelanea;
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

/** Máquinas de la feria: se les da (clic derecho) una cosa y sueltan otra. */
public class FeriaCategoria implements IRecipeCategory<FeriaCategoria.Receta> {
    /** clave: jei.miscelanea.feria.<clave> (nota de cómo se usa). */
    public record Receta(ItemStack maquina, ItemStack entrada, ItemStack salida, String clave) {}

    public static final RecipeType<Receta> TIPO = RecipeType.create(Miscelanea.MOD_ID, "feria", Receta.class);
    static final int ANCHO = 140, ALTO = 42;

    private final IDrawable fondo, icono, hueco;

    public FeriaCategoria(IGuiHelper g) {
        fondo = g.createBlankDrawable(ANCHO, ALTO);
        icono = g.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(ModItems.ALGODONERA.get()));
        hueco = g.getSlotDrawable();
    }

    @Override
    public RecipeType<Receta> getRecipeType() {
        return TIPO;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.miscelanea.feria");
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
    public void setRecipe(IRecipeLayoutBuilder b, Receta r, IFocusGroup focos) {
        b.addSlot(RecipeIngredientRole.INPUT, 11, 4).setBackground(hueco, -1, -1).addItemStack(r.entrada());
        b.addSlot(RecipeIngredientRole.CATALYST, 51, 4).addItemStack(r.maquina());
        b.addSlot(RecipeIngredientRole.OUTPUT, 91, 4).setBackground(hueco, -1, -1).addItemStack(r.salida());
    }

    @Override
    public void draw(Receta r, IRecipeSlotsView vista, GuiGraphics g, double ratonX, double ratonY) {
        var font = Minecraft.getInstance().font;
        g.drawString(font, "→", 34, 8, 0xFF808080, false);
        g.drawString(font, "→", 74, 8, 0xFF808080, false);
        g.drawWordWrap(font, Component.translatable("jei.miscelanea.feria." + r.clave()), 0, 24, ANCHO, 0xFF606060);
    }
}
