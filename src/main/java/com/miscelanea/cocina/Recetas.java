package com.miscelanea.cocina;

import com.miscelanea.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Recetas del fogón: ingredientes (sin orden) → plato. */
public final class Recetas {
    public record Receta(String id, List<Supplier<Item>> ingredientes, Supplier<ItemStack> plato, int color) {}

    public static final List<Receta> TODAS = new ArrayList<>();

    static {
        TODAS.add(new Receta("paella", List.of(ModItems.ARROZ::get, () -> Items.CHICKEN, ModItems.TOMATE::get, () -> Items.COD),
                () -> new ItemStack(ModItems.PAELLA.get()), 0xF2B437));
        TODAS.add(new Receta("paella", List.of(ModItems.ARROZ::get, () -> Items.CHICKEN, ModItems.TOMATE::get),
                () -> new ItemStack(ModItems.PAELLA.get()), 0xF2B437));
        TODAS.add(new Receta("tortilla", List.of(() -> Items.POTATO, () -> Items.EGG, () -> Items.EGG),
                () -> new ItemStack(ModItems.TORTILLA.get(), 2), 0xF0C860));
        TODAS.add(new Receta("gazpacho", List.of(ModItems.TOMATE::get, ModItems.TOMATE::get, () -> Items.BREAD, () -> Items.BOWL),
                () -> new ItemStack(ModItems.GAZPACHO.get()), 0xD8402C));
        TODAS.add(new Receta("chocolate", List.of(ModItems.CHURROS::get, () -> Items.MILK_BUCKET, () -> Items.COCOA_BEANS),
                () -> new ItemStack(ModItems.CHURROS_CHOCOLATE.get(), 2), 0x5A321E));
        TODAS.add(new Receta("churros", List.of(() -> Items.WHEAT, () -> Items.SUGAR),
                () -> new ItemStack(ModItems.CHURROS.get(), 4), 0xD89A50));
    }

    private Recetas() {}

    /** Receta con exactamente estos ingredientes (en cualquier orden); si hubiera varias, la de más ingredientes. */
    public static Receta buscar(List<ItemStack> dentro) {
        Receta mejor = null;
        for (Receta r : TODAS) {
            if (r.ingredientes().size() != dentro.size()) continue;
            List<Item> f = faltan(r, dentro);
            if (f == null || !f.isEmpty()) continue;
            if (mejor == null || r.ingredientes().size() > mejor.ingredientes().size()) mejor = r;
        }
        return mejor;
    }

    /**
     * Ingredientes de la receta que aún no están en la sartén, o null si en la sartén hay algo que la receta no lleva
     * (el orden da igual).
     */
    private static List<Item> faltan(Receta r, List<ItemStack> dentro) {
        List<Item> faltan = new ArrayList<>();
        for (var s : r.ingredientes()) faltan.add(s.get());
        for (ItemStack st : dentro) if (!faltan.remove(st.getItem())) return null;
        return faltan;
    }

    /**
     * ¿Hay una receta más grande que contiene todo lo que hay en la sartén y para la que aún cabe lo que falta? (p. ej. la
     * paella con bacalao cuando ya está lo de la paella normal): entonces el fogón espera un poco antes de empezar.
     */
    public static boolean ampliable(List<ItemStack> dentro, int max) {
        for (Receta r : TODAS) {
            if (r.ingredientes().size() <= dentro.size() || r.ingredientes().size() > max) continue;
            List<Item> f = faltan(r, dentro);
            if (f != null && !f.isEmpty()) return true;
        }
        return false;
    }

    /** ¿Este objeto aparece en alguna receta? */
    public static boolean sirve(ItemStack st) {
        for (Receta r : TODAS) for (var s : r.ingredientes()) if (st.is(s.get())) return true;
        return false;
    }

    /** Color de un ingrediente (para dibujarlo en la sartén). */
    public static int color(Item i) {
        if (i == ModItems.TOMATE.get()) return 0xE0302A;
        if (i == ModItems.ARROZ.get()) return 0xF4EEDC;
        if (i == Items.CHICKEN) return 0xF2B0A0;
        if (i == Items.COD) return 0xC8D0D8;
        if (i == Items.POTATO) return 0xE0C080;
        if (i == Items.EGG) return 0xFFF2C0;
        if (i == Items.BREAD) return 0xC08040;
        if (i == Items.WHEAT) return 0xE0C870;
        if (i == Items.SUGAR) return 0xFFFFFF;
        if (i == Items.MILK_BUCKET) return 0xF8F8F8;
        if (i == Items.COCOA_BEANS) return 0x6A3A1E;
        if (i == ModItems.CHURROS.get()) return 0xD89A50;
        return 0xB0B0B0;
    }
}
