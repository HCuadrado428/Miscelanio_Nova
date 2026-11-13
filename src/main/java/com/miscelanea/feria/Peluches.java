package com.miscelanea.feria;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.RegistryObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * La lista de peluches de personas.
 *
 * <p>Para añadir uno nuevo basta con poner su nombre en {@link #NOMBRES} y dejar su textura en
 * {@code textures/block/peluche_<nombre>.png}. El bloque, el objeto, el modelo y la pestaña salen solos; solo hay que
 * escribir su nombre en los dos idiomas ({@code block.miscelanea.peluche_<nombre>}).
 */
public final class Peluches {
    private Peluches() {}

    /** Los peluches que existen. El orden es el que sale en la pestaña del creativo. */
    public static final String[] NOMBRES = {
            "doug", "nikki", "aaron", "nova",
    };

    /** Bloques ya registrados, por nombre. Lo rellena ModBlocks al arrancar. */
    public static final Map<String, RegistryObject<Block>> BLOQUES = new LinkedHashMap<>();
    /** Objetos ya registrados, por nombre. Lo rellena ModItems al arrancar. */
    public static final Map<String, RegistryObject<Item>> OBJETOS = new LinkedHashMap<>();

    /** El id de registro de un peluche. */
    public static String id(String nombre) {
        return "peluche_" + nombre;
    }

    /** Uno al azar, para cuando la máquina de garra suelta un peluche de persona. */
    public static RegistryObject<Item> alAzar(net.minecraft.util.RandomSource r) {
        String n = NOMBRES[r.nextInt(NOMBRES.length)];
        return OBJETOS.get(n);
    }
}
