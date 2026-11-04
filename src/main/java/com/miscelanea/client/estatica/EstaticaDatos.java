package com.miscelanea.client.estatica;

/**
 * Fotogramas clave de la transformación en estática. GENERADO con scratchpad/gen_estatica.py (fuente:
 * estatica_comun.py, la misma que la vista previa estatica_prev.py): no tocar a mano. Cada pista: filas
 * {tick, x, y, z, curva} (vectores) o {tick, v, curva} (escalares); curvas como en Pista.
 */
public final class EstaticaDatos {
    private EstaticaDatos() {}

    public static final int FIN = 112;
    public static final int INICIO = 0;
    public static final int CARA = 12;
    public static final int MANOS = 26;
    public static final int CABEZA = 40;
    public static final int RODILLA = 58;
    public static final int GRITO = 66;
    public static final int ESTALLIDO = 78;
    public static final int LEVANTA = 86;
    public static final String[] N = {"cam", "mira", "pos", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "temblor", "convulsion", "cobertura", "ojos", "ruido", "vhs", "barras"};
    public static final float[][][] K = {
            {{0F, 25F, 6F, 3.6F, 1F}, {10F, 8F, 3F, 2.5F, 1F}, {24F, 4F, -2F, 2F, 1F}, {38F, -6F, -6F, 1.8F, 1F}, {52F, -14F, 6F, 2.9F, 1F}, {64F, 0F, 2F, 2.4F, 1F}, {78F, 0F, -10F, 1.9F, 1F}, {84F, 0F, -8F, 2.3F, 2F}, {104F, 18F, 9F, 3.4F, 1F}, {112F, 22F, 10F, 3.6F, 1F}},
            {{0F, 0F, 1.35F, 0F, 1F}, {24F, 0F, 1.4F, 0F, 1F}, {38F, 0F, 1.2F, 0.2F, 1F}, {52F, 0F, 1.4F, 0.3F, 1F}, {64F, 0F, 0.95F, 0.15F, 1F}, {80F, 0F, 1F, 0.1F, 1F}, {100F, 0F, 1.35F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {40F, 0F, 0F, 0F, 1F}, {45F, 0.04F, 0F, 0.18F, 2F}, {50F, -0.03F, 0F, 0.36F, 2F}, {54F, 0F, 0F, 0.4F, 1F}, {60F, 0F, -0.42F, 0.42F, 5F}, {84F, 0F, -0.42F, 0.42F, 1F}, {100F, 0F, 0F, 0.42F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {44F, 0F, 0F, 0.06F, 1F}, {49F, 0F, 0F, -0.07F, 1F}, {54F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {12F, 0.12F, 0F, 0F, 1F}, {28F, 0.22F, 0F, 0F, 1F}, {40F, 0.05F, 0F, 0F, 1F}, {54F, -0.05F, 0F, 0F, 1F}, {60F, 0.38F, 0F, 0F, 5F}, {66F, 0.3F, 0F, 0F, 1F}, {72F, -0.28F, 0F, 0F, 2F}, {80F, -0.3F, 0F, 0F, 1F}, {92F, 0.1F, 0F, 0F, 1F}, {112F, 0F, 0F, 0F, 1F}},
            {{0F, 0.1F, 0F, 0F, 1F}, {12F, 0.25F, 0.35F, 0.1F, 2F}, {22F, 0.3F, 0.45F, 0.12F, 1F}, {28F, 0.65F, 0F, 0F, 1F}, {38F, 0.7F, -0.08F, 0F, 1F}, {42F, 0.1F, 0.4F, 0.05F, 1F}, {45F, 0.15F, -0.4F, -0.05F, 1F}, {48F, 0.1F, 0.35F, 0.05F, 1F}, {51F, 0.15F, -0.3F, 0F, 1F}, {54F, 0.2F, 0F, 0F, 1F}, {60F, 0.55F, 0F, 0F, 5F}, {66F, 0.5F, 0F, 0F, 1F}, {72F, -0.75F, 0F, 0.05F, 2F}, {80F, -0.8F, 0F, 0.08F, 1F}, {92F, -0.05F, 0.1F, 0.28F, 1F}, {104F, -0.08F, 0.12F, 0.3F, 1F}, {112F, 0F, 0F, 0.15F, 1F}},
            {{0F, -0.35F, 0F, 0.05F, 1F}, {8F, -0.1F, 0F, 0.08F, 1F}, {12F, -1.95F, -0.55F, 0.3F, 2F}, {22F, -2.05F, -0.6F, 0.32F, 1F}, {28F, -1.25F, -0.25F, 0.05F, 1F}, {38F, -1.35F, -0.3F, 0.1F, 1F}, {44F, -2.65F, -0.15F, -0.3F, 2F}, {54F, -2.7F, -0.2F, -0.35F, 1F}, {60F, -0.55F, 0F, 0.12F, 5F}, {66F, -0.4F, 0F, 0.1F, 1F}, {72F, -0.25F, 0F, 1.25F, 2F}, {80F, -0.2F, 0F, 1.35F, 1F}, {92F, -0.05F, 0F, 0.12F, 1F}, {112F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {12F, -0.2F, 0F, -0.06F, 1F}, {22F, -0.25F, 0F, -0.08F, 1F}, {28F, -1.25F, 0.25F, -0.05F, 1F}, {38F, -1.3F, 0.3F, -0.1F, 1F}, {44F, -2.65F, 0.15F, 0.3F, 2F}, {54F, -2.7F, 0.2F, 0.35F, 1F}, {60F, -0.5F, 0F, -0.12F, 5F}, {66F, -0.35F, 0F, -0.1F, 1F}, {72F, -0.25F, 0F, -1.25F, 2F}, {80F, -0.2F, 0F, -1.35F, 1F}, {92F, -0.05F, 0F, -0.12F, 1F}, {112F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {54F, 0F, 0F, 0F, 1F}, {60F, -1.45F, 0.15F, 0.05F, 5F}, {84F, -1.45F, 0.15F, 0.05F, 1F}, {100F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {54F, 0F, 0F, 0F, 1F}, {60F, 1.1F, 0F, -0.05F, 5F}, {84F, 1.1F, 0F, -0.05F, 1F}, {100F, 0F, 0F, 0F, 1F}},
            {{0F, 0.02F, 1F}, {12F, 0.05F, 1F}, {26F, 0.06F, 1F}, {40F, 0.1F, 1F}, {54F, 0.08F, 1F}, {66F, 0.05F, 1F}, {80F, 0.12F, 1F}, {92F, 0.03F, 1F}, {112F, 0F, 1F}},
            {{0F, 0F, 1F}, {28F, 0F, 1F}, {32F, 0.35F, 2F}, {38F, 0.3F, 1F}, {42F, 0F, 1F}, {66F, 0F, 1F}, {70F, 0.7F, 2F}, {80F, 0.65F, 1F}, {86F, 0.15F, 1F}, {92F, 0.25F, 1F}, {96F, 0F, 1F}},
            {{0F, 0F, 1F}, {8F, 0F, 1F}, {14F, 0.12F, 1F}, {24F, 0.28F, 1F}, {40F, 0.55F, 1F}, {54F, 0.75F, 1F}, {66F, 0.88F, 1F}, {78F, 1F, 1F}},
            {{0F, 0F, 1F}, {40F, 0F, 1F}, {48F, 0.35F, 1F}, {56F, 0.5F, 1F}, {70F, 0.75F, 1F}, {74F, 1F, 2F}},
            {{0F, 0F, 1F}, {10F, 0.08F, 1F}, {26F, 0.14F, 1F}, {40F, 0.22F, 1F}, {58F, 0.3F, 1F}, {76F, 0.5F, 3F}, {78F, 0.9F, 2F}, {84F, 0.25F, 1F}, {100F, 0.06F, 1F}, {112F, 0F, 1F}},
            {{0F, 0.1F, 1F}, {26F, 0.25F, 1F}, {54F, 0.45F, 1F}, {76F, 0.8F, 3F}, {84F, 0.4F, 1F}, {104F, 0.1F, 1F}, {112F, 0F, 1F}},
            {{0F, 0F, 1F}, {6F, 1F, 2F}, {104F, 1F, 1F}, {112F, 0F, 1F}},
    };
}
