package com.miscelanea.bichos;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.function.Predicate;

/**
 * Bichos y peces de la Bichopedia. Cada uno sale en ciertos biomas, a cierta hora y con cierto tiempo.
 * Los bichos se ven desde arriba (su icono sirve para dibujarlos en el mundo); los peces se pescan con la caña.
 */
public enum Especie {
    // ------------------------------------------------------------------ bichos
    MARIPOSA_COL(Tipo.VUELA, Hora.DIA, Lluvia.SECO, 10, 1, 0.5F, b(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.FLOWER_FOREST).or(t(BiomeTags.IS_FOREST))),
    MARIPOSA_MONARCA(Tipo.VUELA, Hora.DIA, Lluvia.SECO, 5, 2, 0.55F, b(Biomes.FLOWER_FOREST, Biomes.MEADOW, Biomes.SUNFLOWER_PLAINS, Biomes.CHERRY_GROVE)),
    MARIPOSA_MORFO(Tipo.VUELA, Hora.DIA, Lluvia.CUALQUIERA, 3, 3, 0.6F, t(BiomeTags.IS_JUNGLE)),
    POLILLA(Tipo.VUELA, Hora.NOCHE, Lluvia.CUALQUIERA, 8, 1, 0.45F, t(BiomeTags.IS_OVERWORLD).and(t(BiomeTags.IS_OCEAN).negate())),
    LUCIERNAGA(Tipo.BRILLA, Hora.NOCHE, Lluvia.SECO, 9, 2, 0.3F, b(Biomes.SWAMP, Biomes.MANGROVE_SWAMP, Biomes.PLAINS, Biomes.MEADOW).or(t(BiomeTags.IS_FOREST)).or(t(BiomeTags.IS_RIVER))),
    LIBELULA(Tipo.ZUMBA, Hora.DIA, Lluvia.SECO, 6, 2, 0.6F, b(Biomes.SWAMP, Biomes.MANGROVE_SWAMP).or(t(BiomeTags.IS_RIVER))),
    ABEJORRO(Tipo.ZUMBA, Hora.DIA, Lluvia.SECO, 6, 2, 0.35F, b(Biomes.FLOWER_FOREST, Biomes.MEADOW, Biomes.SUNFLOWER_PLAINS, Biomes.PLAINS, Biomes.CHERRY_GROVE)),
    MARIQUITA(Tipo.SUELO, Hora.DIA, Lluvia.SECO, 7, 1, 0.25F, b(Biomes.PLAINS, Biomes.FLOWER_FOREST, Biomes.MEADOW).or(t(BiomeTags.IS_FOREST))),
    ESCARABAJO_RINOCERONTE(Tipo.SUELO, Hora.NOCHE, Lluvia.CUALQUIERA, 2, 4, 0.42F, b(Biomes.DARK_FOREST).or(t(BiomeTags.IS_JUNGLE))),
    SALTAMONTES(Tipo.SALTA, Hora.DIA, Lluvia.SECO, 8, 1, 0.38F, b(Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW).or(t(BiomeTags.IS_SAVANNA))),
    ESCORPION(Tipo.SUELO, Hora.NOCHE, Lluvia.CUALQUIERA, 3, 3, 0.45F, b(Biomes.DESERT).or(t(BiomeTags.IS_BADLANDS))),
    CIGARRA(Tipo.ZUMBA, Hora.DIA, Lluvia.SECO, 4, 3, 0.42F, t(BiomeTags.IS_SAVANNA).or(t(BiomeTags.IS_FOREST))),
    // ------------------------------------------------------------------ peces (bioma del anzuelo)
    TRUCHA(Tipo.PEZ, Hora.DIA, Lluvia.CUALQUIERA, 8, 1, 25, 60, 2, t(BiomeTags.IS_RIVER).or(b(Biomes.MEADOW, Biomes.TAIGA, Biomes.FOREST))),
    SALMON(Tipo.PEZ, Hora.SIEMPRE, Lluvia.CUALQUIERA, 6, 2, 50, 100, 3, t(BiomeTags.IS_RIVER).or(b(Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.FROZEN_RIVER))),
    CARPA(Tipo.PEZ, Hora.SIEMPRE, Lluvia.CUALQUIERA, 10, 1, 30, 80, 1, t(BiomeTags.IS_OCEAN).negate()),
    LUCIO(Tipo.PEZ, Hora.SIEMPRE, Lluvia.CUALQUIERA, 3, 3, 50, 130, 5, t(BiomeTags.IS_RIVER).or(t(BiomeTags.IS_FOREST))),
    SILURO(Tipo.PEZ, Hora.NOCHE, Lluvia.CUALQUIERA, 4, 3, 80, 200, 5, b(Biomes.SWAMP, Biomes.MANGROVE_SWAMP).or(t(BiomeTags.IS_RIVER))),
    ANGUILA(Tipo.PEZ, Hora.SIEMPRE, Lluvia.LLUVIA, 4, 3, 40, 120, 6, b(Biomes.SWAMP, Biomes.MANGROVE_SWAMP).or(t(BiomeTags.IS_RIVER))),
    PEZ_PAYASO(Tipo.PEZ, Hora.DIA, Lluvia.CUALQUIERA, 7, 2, 8, 12, 2, b(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN)),
    PEZ_GLOBO(Tipo.PEZ, Hora.SIEMPRE, Lluvia.CUALQUIERA, 6, 2, 15, 40, 3, b(Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN, Biomes.OCEAN)),
    ATUN(Tipo.PEZ, Hora.DIA, Lluvia.CUALQUIERA, 4, 3, 100, 250, 6, t(BiomeTags.IS_DEEP_OCEAN).or(b(Biomes.OCEAN))),
    PEZ_ESPADA(Tipo.PEZ, Hora.DIA, Lluvia.CUALQUIERA, 2, 4, 150, 400, 7, t(BiomeTags.IS_OCEAN)),
    PEZ_LINTERNA(Tipo.PEZ, Hora.NOCHE, Lluvia.CUALQUIERA, 4, 3, 5, 15, 4, t(BiomeTags.IS_DEEP_OCEAN)),
    CELACANTO(Tipo.PEZ, Hora.SIEMPRE, Lluvia.LLUVIA, 1, 5, 120, 200, 8, t(BiomeTags.IS_OCEAN));

    public enum Tipo { VUELA, ZUMBA, BRILLA, SUELO, SALTA, PEZ }
    public enum Hora { DIA, NOCHE, SIEMPRE }
    public enum Lluvia { SECO, LLUVIA, CUALQUIERA }

    public final Tipo tipo;
    public final Hora hora;
    public final Lluvia lluvia;
    public final int peso, estrellas, cmMin, cmMax, dificultad;
    /** Tamaño al dibujarlo en el mundo (bloques). */
    public final float tam;
    private final Predicate<Holder<Biome>> bioma;

    Especie(Tipo tipo, Hora hora, Lluvia lluvia, int peso, int estrellas, float tam, Predicate<Holder<Biome>> bioma) {
        this(tipo, hora, lluvia, peso, estrellas, tam, 0, 0, 0, bioma);
    }

    Especie(Tipo tipo, Hora hora, Lluvia lluvia, int peso, int estrellas, int cmMin, int cmMax, int dificultad, Predicate<Holder<Biome>> bioma) {
        this(tipo, hora, lluvia, peso, estrellas, 0.5F, cmMin, cmMax, dificultad, bioma);
    }

    Especie(Tipo tipo, Hora hora, Lluvia lluvia, int peso, int estrellas, float tam, int cmMin, int cmMax, int dificultad, Predicate<Holder<Biome>> bioma) {
        this.tipo = tipo;
        this.hora = hora;
        this.lluvia = lluvia;
        this.peso = peso;
        this.estrellas = estrellas;
        this.tam = tam;
        this.cmMin = cmMin;
        this.cmMax = cmMax;
        this.dificultad = dificultad;
        this.bioma = bioma;
    }

    @SafeVarargs
    private static Predicate<Holder<Biome>> b(ResourceKey<Biome>... ks) {
        return h -> {
            for (ResourceKey<Biome> k : ks) if (h.is(k)) return true;
            return false;
        };
    }

    private static Predicate<Holder<Biome>> t(TagKey<Biome> tag) {
        return h -> h.is(tag);
    }

    public boolean esPez() {
        return tipo == Tipo.PEZ;
    }

    public boolean vuela() {
        return tipo == Tipo.VUELA || tipo == Tipo.ZUMBA || tipo == Tipo.BRILLA;
    }

    public String id() {
        return name().toLowerCase();
    }

    /** Nombre del ítem: bicho_xxx o pez_xxx. */
    public String item() {
        return (esPez() ? "pez_" : "bicho_") + id();
    }

    public static boolean esDeDia(Level l) {
        long t = l.getDayTime() % 24000;
        return t < 12500 || t > 23300;
    }

    public boolean puede(Level l, BlockPos pos) {
        boolean dia = esDeDia(l);
        if (hora == Hora.DIA && !dia) return false;
        if (hora == Hora.NOCHE && dia) return false;
        boolean llueve = l.isRainingAt(pos.above(2)) || (l.isRaining() && esPez());
        if (lluvia == Lluvia.SECO && llueve) return false;
        if (lluvia == Lluvia.LLUVIA && !llueve) return false;
        return bioma.test(l.getBiome(pos));
    }

    public static Especie de(String id) {
        for (Especie e : values()) if (e.id().equals(id)) return e;
        return null;
    }
}
