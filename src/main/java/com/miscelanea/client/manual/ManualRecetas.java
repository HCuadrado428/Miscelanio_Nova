package com.miscelanea.client.manual;

import com.miscelanea.brewing.PotionMixRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraftforge.common.brewing.BrewingRecipe;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.common.crafting.IShapedRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Recetas de un objeto, leídas en el momento del RecipeManager del mundo (el servidor las manda al entrar, así que
 * salen también las de los paquetes de datos) y de las recetas de alquimia de Forge. Se dibujan en {@link ManualScreen}.
 */
final class ManualRecetas {
    private ManualRecetas() {}

    enum Tipo {MESA, HORNO, ALQUIMIA, OTRA}

    /**
     * Una receta lista para dibujar. {@code huecos}: para la mesa, 9 casillas (fila a fila; vacías = lista vacía); para el
     * resto, las entradas en orden. Cada casilla es la lista de objetos que valen (se van alternando).
     */
    record Vista(Tipo tipo, List<List<ItemStack>> huecos, ItemStack resultado, ItemStack estacion) {}

    static List<Vista> buscar(List<ItemStack> objetos, String receta) {
        List<Vista> l = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || "no".equals(receta)) return l;
        RegistryAccess ra = mc.level.registryAccess();
        var rm = mc.level.getRecipeManager();
        if (receta != null && !"auto".equals(receta)) {
            ResourceLocation rl = ResourceLocation.tryParse(receta);
            if (rl != null) rm.byKey(rl).ifPresent(r -> {
                Vista v = vista(r, resultado(r, ra));
                if (v != null) l.add(v);
            });
            return l;
        }
        Set<ResourceLocation> vistos = new HashSet<>();
        for (ItemStack ob : objetos) {
            for (Recipe<?> r : rm.getRecipes()) {
                if (l.size() >= 4) return l;
                ItemStack res = resultado(r, ra);
                if (res.isEmpty() || !igual(res, ob) || !vistos.add(r.getId())) continue;
                Vista v = vista(r, res);
                if (v != null) l.add(v);
            }
            for (IBrewingRecipe br : BrewingRecipeRegistry.getRecipes()) {
                if (l.size() >= 4) return l;
                Vista v = alquimia(br, ob);
                if (v != null && l.stream().noneMatch(x -> x.tipo == Tipo.ALQUIMIA && ItemStack.matches(x.resultado, v.resultado))) l.add(v);
            }
        }
        return l;
    }

    private static ItemStack resultado(Recipe<?> r, RegistryAccess ra) {
        try {
            ItemStack s = r.getResultItem(ra);
            return s == null ? ItemStack.EMPTY : s;
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    private static boolean igual(ItemStack res, ItemStack ob) {
        if (!res.is(ob.getItem())) return false;
        Potion p = PotionUtils.getPotion(ob);
        return p == Potions.EMPTY || PotionUtils.getPotion(res) == p;
    }

    private static List<ItemStack> opciones(Ingredient in) {
        return in == null || in.isEmpty() ? List.of() : Arrays.asList(in.getItems());
    }

    private static Vista vista(Recipe<?> r, ItemStack res) {
        if (res.isEmpty()) return null;
        var ings = r.getIngredients();
        if (r instanceof CraftingRecipe) {
            if (ings.isEmpty()) return null;
            List<List<ItemStack>> h = new ArrayList<>();
            for (int i = 0; i < 9; i++) h.add(List.of());
            if (r instanceof IShapedRecipe<?> sh) {
                int w = sh.getRecipeWidth(), alto = sh.getRecipeHeight();
                for (int y = 0; y < alto && y < 3; y++)
                    for (int x = 0; x < w && x < 3; x++) {
                        int i = y * w + x;
                        if (i < ings.size()) h.set(y * 3 + x, opciones(ings.get(i)));
                    }
            } else {
                for (int i = 0; i < ings.size() && i < 9; i++) h.set(i, opciones(ings.get(i)));
            }
            return new Vista(Tipo.MESA, h, res, new ItemStack(Items.CRAFTING_TABLE));
        }
        if (r instanceof AbstractCookingRecipe && !ings.isEmpty()) {
            ItemStack est = new ItemStack(r instanceof SmokingRecipe ? Items.SMOKER : r instanceof BlastingRecipe ? Items.BLAST_FURNACE
                    : r instanceof CampfireCookingRecipe ? Items.CAMPFIRE : Items.FURNACE);
            return new Vista(Tipo.HORNO, List.of(opciones(ings.get(0))), res, est);
        }
        if (r instanceof StonecutterRecipe && !ings.isEmpty())
            return new Vista(Tipo.HORNO, List.of(opciones(ings.get(0))), res, new ItemStack(Items.STONECUTTER));
        if (ings.isEmpty()) return null;
        List<List<ItemStack>> h = new ArrayList<>();
        for (int i = 0; i < ings.size() && i < 3; i++) h.add(opciones(ings.get(i)));
        return new Vista(Tipo.OTRA, h, res, ItemStack.EMPTY);
    }

    private static Vista alquimia(IBrewingRecipe br, ItemStack ob) {
        ItemStack soporte = new ItemStack(Items.BREWING_STAND);
        if (br instanceof PotionMixRecipe pm) {
            if (PotionUtils.getPotion(ob) != pm.to()) return null;
            var forma = ob.is(Items.TIPPED_ARROW) ? Items.POTION : ob.getItem();
            ItemStack base = PotionUtils.setPotion(new ItemStack(forma), pm.from());
            ItemStack res = PotionUtils.setPotion(new ItemStack(forma), pm.to());
            return new Vista(Tipo.ALQUIMIA, List.of(List.of(base), List.of(new ItemStack(pm.ingredient()))), res, soporte);
        }
        if (br instanceof BrewingRecipe b) {
            ItemStack out = b.getOutput();
            if (out.isEmpty() || !igual(out, ob)) return null;
            return new Vista(Tipo.ALQUIMIA, List.of(opciones(b.getInput()), opciones(b.getIngredient())), out, soporte);
        }
        return null;
    }
}
