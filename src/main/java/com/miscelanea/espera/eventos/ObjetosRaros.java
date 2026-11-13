package com.miscelanea.espera.eventos;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Restos de errores: objetos tirados por el suelo del Espera con nombres de archivo, de copia de seguridad o de
 * clave sin traducir. Son cosas de poco valor (palos, tierra, una espada de madera a medio romper...): la gracia es el
 * nombre. Uno de cada diez en el Mundo sin Texturas es un bloque MissingNo. de verdad.
 */
public final class ObjetosRaros {
    private ObjetosRaros() {}

    public static final String MARCA = "MiscelaneaPaciente";

    /** Objeto, clave del nombre (o literal si empieza por '='), daño (0-1) y si lleva una línea de descripción. */
    private record Raro(Item item, String nombre, float danio, boolean nota) {}

    private static final Raro[] RAROS = {
            new Raro(Items.WOODEN_SWORD, "espada", 0.6F, true),
            // la clave sin traducir: se ve tal cual, que es justo la gracia
            new Raro(Items.STICK, "=item.minecraft.missingno", 0, false),
            new Raro(Items.PAPER, "documento", 0, true),
            new Raro(Items.APPLE, "manzana", 0, false),
            new Raro(Items.DIRT, "=tile.dirt.name", 0, false),
            new Raro(Items.COMPASS, "brujula", 0, true),
            new Raro(Items.BOOK, "todo", 0, false),
            new Raro(Items.POTATO, "patata", 0, true),
            new Raro(Items.STONE_PICKAXE, "pico", 0.8F, false),
            new Raro(Items.BONE, "steve", 0, true),
            new Raro(Items.STRING, "=null", 0, false),
            new Raro(Items.CLOCK, "reloj", 0, false),
            new Raro(Items.NAME_TAG, "etiqueta", 0, false),
            new Raro(Items.LEATHER_BOOTS, "botas", 0.4F, false),
            new Raro(Items.BREAD, "pan", 0, false),
            new Raro(Items.ROTTEN_FLESH, "bocadillo", 0, true),
            new Raro(Items.FEATHER, "=entity.minecraft.chicken.name", 0, false),
            new Raro(Items.COBBLESTONE, "piedra", 0, false),
    };

    public static int cuantos() {
        return RAROS.length;
    }

    /** Uno al azar (k < 0) o el k-ésimo. */
    public static ItemStack uno(RandomSource r, int k, boolean sinTextura) {
        if (k < 0 && sinTextura && r.nextInt(10) == 0) return marcar(new ItemStack(ExtrasRegistro.MISSINGNO_ITEM.get()));
        Raro x = RAROS[k < 0 ? r.nextInt(RAROS.length) : Math.floorMod(k, RAROS.length)];
        ItemStack st = new ItemStack(x.item());
        if (x.danio() > 0 && st.isDamageableItem()) st.setDamageValue((int) (st.getMaxDamage() * x.danio()));
        Component n = x.nombre().startsWith("=") ? Component.literal(x.nombre().substring(1))
                : Component.translatable("espera.miscelanea.raro." + x.nombre());
        st.setHoverName(n);
        if (x.nota()) {
            CompoundTag d = st.getOrCreateTagElement("display");
            ListTag lore = new ListTag();
            lore.add(StringTag.valueOf(Component.Serializer.toJson(Component.translatable("espera.miscelanea.raro." + x.nombre() + ".nota")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
            d.put("Lore", lore);
        }
        return marcar(st);
    }

    private static ItemStack marcar(ItemStack st) {
        st.getOrCreateTag().putBoolean(MARCA, true);
        return st;
    }
}
