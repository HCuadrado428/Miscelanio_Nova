package com.miscelanea.client.tortura.muertes;

/**
 * Fotogramas clave de las cinemáticas de muerte. GENERADO con scratchpad/muertes.py (la misma fuente que la vista
 * previa muertes_prev.py): no tocar a mano. Cada pista: filas {tick, x, y, z, curva} (vectores) o {tick, v, curva}
 * (escalares); curvas: 0 lineal, 1 suave, 2 frena, 3 acelera, 4 se pasa, 5 rebote, 6 salto.
 */
public final class MuertesDatos {
    private MuertesDatos() {}

    // ---------------------------------------------------------------- DONCELLA
    public static final int DONCELLA_MUERTE = 84, DONCELLA_FIN = 112;
    public static final int DONCELLA_CHIRRIDO = 2;
    public static final int DONCELLA_PORTAZO = 18;
    public static final int DONCELLA_PINCHOS = 21;
    public static final float DONCELLA_MARGEN = 0.9F;
    public static final int DONCELLA_SIN_CABEZA = -1;
    public static final String[] DONCELLA_N = {"puertas", "cam", "mira", "pos", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "temblor", "vineta", "negro"};
    public static final float[][][] DONCELLA_K = {
            {{0F, 0.7F, 1F}, {12F, 0.78F, 1F}, {18F, 1F, 3F}, {20F, 0.965F, 2F}, {23F, 1F, 1F}},
            {{0F, 12F, 4F, 1.55F, 1F}, {10F, 8F, 2F, 1.38F, 1F}, {17F, 5F, 2F, 1.3F, 1F}, {21F, 0F, 7F, 2.35F, 2F}, {42F, -18F, 10F, 2.6F, 1F}, {84F, -32F, 14F, 2.9F, 1F}, {112F, -36F, 16F, 3F, 1F}},
            {{0F, 0F, 1.5F, 0F, 1F}, {18F, 0F, 1.5F, 0F, 1F}, {26F, 0F, 1.15F, 0.1F, 1F}, {84F, 0F, 0.65F, 0.2F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {10F, 0F, -0.05F, 0.03F, 1F}, {18F, 0F, -0.06F, 0.03F, 1F}, {19F, 0F, 0.02F, 0F, 2F}, {30F, 0F, -0.12F, 0F, 1F}, {84F, 0F, -0.25F, 0F, 3F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.08F, 0F, 0F, 2F}, {10F, 0.15F, 0F, 0F, 1F}, {18F, 0.2F, 0F, 0F, 1F}, {19F, -0.25F, 0F, 0F, 2F}, {26F, -0.1F, 0F, 0F, 1F}, {60F, 0.3F, 0F, 0.05F, 1F}, {84F, 0.4F, 0F, 0.08F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.35F, 0.1F, 0F, 2F}, {8F, 0.25F, -0.2F, 0.05F, 1F}, {15F, 0.35F, -0.4F, 0.1F, 1F}, {18F, 0.35F, -0.4F, 0.1F, 1F}, {19F, -0.55F, 0.05F, -0.1F, 2F}, {26F, -0.3F, 0F, 0.1F, 1F}, {50F, 0.5F, 0.2F, 0.25F, 1F}, {84F, 0.7F, 0.25F, 0.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.6F, 0F, 0.1F, 2F}, {9F, -1.95F, -0.45F, 0.1F, 2F}, {15F, -2.05F, -0.5F, 0.05F, 1F}, {18F, -2.05F, -0.5F, 0.05F, 1F}, {19F, -1.4F, -0.2F, 0.5F, 2F}, {24F, -0.9F, 0F, 0.25F, 2F}, {40F, -0.3F, 0F, 0.1F, 1F}, {84F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.6F, 0F, -0.1F, 2F}, {9F, -1.95F, 0.45F, -0.1F, 2F}, {15F, -2.05F, 0.5F, -0.05F, 1F}, {18F, -2.05F, 0.5F, -0.05F, 1F}, {19F, -1.4F, 0.2F, -0.5F, 2F}, {24F, -0.9F, 0F, -0.25F, 2F}, {40F, -0.3F, 0F, -0.1F, 1F}, {84F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {18F, 0F, 0F, 0F, 1F}, {19F, 0.15F, 0F, 0.05F, 2F}, {84F, 0.35F, 0F, 0.05F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {18F, 0F, 0F, 0F, 1F}, {19F, 0.1F, 0F, -0.05F, 2F}, {84F, 0.3F, 0F, -0.05F, 1F}},
            {{0F, 0.02F, 1F}, {15F, 0.06F, 3F}, {18F, 0.07F, 1F}, {19F, 0.12F, 2F}, {30F, 0.05F, 1F}, {60F, 0.02F, 1F}, {84F, 0F, 1F}},
            {{0F, 0.3F, 1F}, {18F, 0.5F, 1F}, {19F, 1F, 2F}, {112F, 1F, 1F}},
            {{0F, 0F, 1F}, {18F, 0F, 1F}, {20F, 0.55F, 2F}, {40F, 0.7F, 1F}, {84F, 0.92F, 1F}, {112F, 0.95F, 1F}},
    };

    // ---------------------------------------------------------------- POTRO
    public static final int POTRO_MUERTE = 72, POTRO_FIN = 100;
    public static final int POTRO_CARRACA1 = 2;
    public static final int POTRO_CARRACA2 = 8;
    public static final int POTRO_CARRACA3 = 14;
    public static final int POTRO_ESFUERZO = 10;
    public static final int POTRO_FINAL = 18;
    public static final int POTRO_CRACK = 24;
    public static final float POTRO_MARGEN = 0.3F;
    public static final int POTRO_SIN_CABEZA = -1;
    public static final String[] POTRO_N = {"estira", "manivela", "cam", "mira", "rightArmPos", "leftArmPos", "rightLegPos", "leftLegPos", "headPos", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "pos", "temblor", "vineta", "negro"};
    public static final float[][][] POTRO_K = {
            {{0F, 1.07F, 1F}, {14F, 1.09F, 1F}, {22F, 1.1F, 1F}, {24F, 1.2F, 4F}, {30F, 1.17F, 1F}},
            {{0F, 0F, 1F}, {12F, 160F, 1F}, {16F, 170F, 1F}, {24F, 420F, 3F}, {28F, 400F, 2F}},
            {{0F, 75F, 32F, 1.9F, 1F}, {12F, 80F, 30F, 1.6F, 1F}, {22F, 85F, 28F, 1.3F, 1F}, {24F, 88F, 26F, 1.15F, 2F}, {40F, 60F, 42F, 1.6F, 1F}, {72F, 40F, 55F, 2.1F, 1F}, {100F, 35F, 60F, 2.3F, 1F}},
            {{0F, 0F, 0.3F, -0.1F, 1F}, {22F, 0F, 0.3F, -0.35F, 1F}, {24F, 0F, 0.3F, -0.45F, 2F}, {72F, 0F, 0.25F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, -0.6F, 0F, 1F}, {22F, 0F, -0.9F, 0F, 1F}, {24F, 0F, -2.6F, 0F, 4F}, {30F, 0F, -2.3F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, -0.6F, 0F, 1F}, {22F, 0F, -0.9F, 0F, 1F}, {24F, 0F, -2.6F, 0F, 4F}, {30F, 0F, -2.3F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, 0.4F, 0F, 1F}, {22F, 0F, 0.6F, 0F, 1F}, {24F, 0F, 2F, 0F, 4F}, {30F, 0F, 1.8F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, 0.4F, 0F, 1F}, {22F, 0F, 0.6F, 0F, 1F}, {24F, 0F, 2F, 0F, 4F}, {30F, 0F, 1.8F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {22F, 0F, -0.3F, 0F, 1F}, {24F, 0F, -1.2F, 0F, 4F}, {30F, 0F, -1F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.08F, 0F, 0F, 1F}, {14F, -0.15F, 0F, 0.05F, 1F}, {22F, -0.2F, 0F, 0F, 1F}, {24F, -0.45F, 0F, 0F, 2F}, {30F, -0.1F, 0F, 0.04F, 1F}, {45F, 0.05F, 0F, 0.08F, 1F}, {72F, 0.05F, 0F, 0.1F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, 0.35F, 0F, 0F, 2F}, {14F, 0.15F, 0.25F, 0.1F, 1F}, {20F, -0.3F, -0.2F, 0F, 1F}, {24F, -0.6F, 0F, 0.15F, 2F}, {30F, -0.3F, 0.3F, 0.2F, 1F}, {50F, 0F, 0.8F, 0.25F, 1F}, {72F, 0F, 0.9F, 0.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0.05F, 0F, -0.05F, 1F}, {22F, 0.06F, 0F, -0.06F, 1F}, {24F, 0.12F, 0F, -0.14F, 2F}, {50F, 0.2F, 0F, 0.1F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0.05F, 0F, 0.05F, 1F}, {22F, 0.06F, 0F, 0.06F, 1F}, {24F, 0.12F, 0F, 0.14F, 2F}, {50F, 0.2F, 0F, -0.1F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {24F, 0.06F, 0F, 0.05F, 2F}, {50F, 0F, 0.3F, 0.06F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {24F, 0.06F, 0F, -0.05F, 2F}, {50F, 0F, -0.3F, -0.06F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {22F, 0F, 0F, 0F, 1F}, {24F, 0F, 0.03F, 0F, 2F}, {30F, 0F, 0F, 0F, 1F}},
            {{0F, 0.02F, 1F}, {14F, 0.07F, 3F}, {22F, 0.09F, 1F}, {24F, 0.16F, 2F}, {32F, 0.08F, 1F}, {50F, 0.02F, 1F}, {72F, 0F, 1F}},
            {{0F, 0.3F, 1F}, {20F, 0.6F, 1F}, {24F, 0.9F, 2F}, {72F, 1F, 1F}},
            {{0F, 0F, 1F}, {30F, 0.1F, 1F}, {72F, 0.85F, 3F}, {100F, 0.95F, 1F}},
    };

    // ---------------------------------------------------------------- SILLA
    public static final int SILLA_MUERTE = 96, SILLA_FIN = 120;
    public static final int SILLA_ZUMBIDO = 2;
    public static final int SILLA_DESCARGA1 = 8;
    public static final int SILLA_DESCARGA2 = 26;
    public static final int SILLA_DESCARGA3 = 44;
    public static final int SILLA_FUNDIDO = 68;
    public static final int SILLA_CHISPORROTEO = 72;
    public static final int SILLA_ESPASMO = 86;
    public static final float SILLA_MARGEN = 0.3F;
    public static final int SILLA_SIN_CABEZA = -1;
    public static final String[] SILLA_N = {"convulsion", "cam", "mira", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "pos", "temblor", "parpadeo", "negro", "vineta"};
    public static final float[][][] SILLA_K = {
            {{0F, 0F, 1F}, {5F, 0F, 1F}, {8F, 0.9F, 3F}, {14F, 0.6F, 1F}, {18F, 1F, 1F}, {30F, 0.75F, 1F}, {38F, 1F, 1F}, {52F, 0.8F, 1F}, {60F, 0.9F, 1F}, {68F, 0.35F, 2F}, {74F, 0.1F, 1F}, {80F, 0F, 1F}, {86F, 0.25F, 2F}, {89F, 0F, 1F}},
            {{0F, -75F, 8F, 2.5F, 1F}, {8F, -62F, 10F, 2.3F, 1F}, {68F, 40F, 16F, 2F, 1F}, {96F, 68F, 24F, 2.3F, 1F}, {120F, 80F, 28F, 2.6F, 1F}},
            {{0F, 0F, 1.15F, 0F, 1F}, {74F, 0F, 1.1F, 0F, 1F}, {96F, 0F, 0.95F, 0.1F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {5F, 0.1F, 0F, 0F, 1F}, {8F, -0.35F, 0F, 0F, 2F}, {66F, -0.3F, 0F, 0F, 1F}, {74F, 0.35F, 0F, 0.05F, 3F}, {80F, 0.32F, 0F, 0.05F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {5F, 0.2F, 0F, 0F, 1F}, {8F, -0.65F, 0F, 0F, 2F}, {66F, -0.55F, 0.1F, 0F, 1F}, {74F, 0.75F, 0.15F, 0.25F, 3F}, {78F, 0.68F, 0.15F, 0.22F, 2F}, {96F, 0.8F, 0.2F, 0.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {5F, -0.08F, 0F, 0F, 1F}, {8F, -0.3F, 0F, 0.12F, 2F}, {66F, -0.25F, 0F, 0.1F, 1F}, {74F, 0.45F, 0F, 0.18F, 3F}, {80F, 0.4F, 0F, 0.15F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {5F, -0.08F, 0F, 0F, 1F}, {8F, -0.3F, 0F, -0.12F, 2F}, {66F, -0.25F, 0F, -0.1F, 1F}, {74F, 0.45F, 0F, -0.18F, 3F}, {80F, 0.4F, 0F, -0.15F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, -0.32F, 0F, 0.04F, 2F}, {66F, -0.28F, 0F, 0.04F, 1F}, {74F, 0.1F, 0F, 0.06F, 3F}, {80F, 0.08F, 0F, 0.06F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, -0.32F, 0F, -0.04F, 2F}, {66F, -0.28F, 0F, -0.04F, 1F}, {74F, 0.1F, 0F, -0.06F, 3F}, {80F, 0.08F, 0F, -0.06F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, 0F, 0.04F, 0F, 2F}, {66F, 0F, 0.04F, 0F, 1F}, {74F, 0F, -0.04F, 0F, 3F}, {78F, 0F, -0.03F, 0F, 2F}},
            {{0F, 0.02F, 1F}, {5F, 0.03F, 1F}, {66F, 0.03F, 1F}, {80F, 0F, 1F}},
            {{0F, 0F, 1F}, {6F, 0.6F, 1F}, {8F, 1F, 1F}, {60F, 1F, 1F}, {68F, 0.4F, 1F}, {72F, 0F, 1F}},
            {{0F, 0F, 1F}, {70F, 0.1F, 1F}, {96F, 0.85F, 3F}, {120F, 0.95F, 1F}},
            {{0F, 0.2F, 1F}, {8F, 0.5F, 1F}, {96F, 0.8F, 1F}},
    };

    // ---------------------------------------------------------------- PENDULO
    public static final int PENDULO_MUERTE = 104, PENDULO_FIN = 125;
    public static final int PENDULO_TICTAC = 0;
    public static final int PENDULO_SILBIDO = 50;
    public static final int PENDULO_CORTE = 60;
    public static final float PENDULO_MARGEN = 0.3F;
    public static final int PENDULO_SIN_CABEZA = -1;
    public static final String[] PENDULO_N = {"pendulo", "cam", "mira", "head", "torso", "rightArm", "leftArm", "rightLeg", "leftLeg", "pos", "temblor", "vineta", "negro"};
    public static final float[][][] PENDULO_K = {
            {{0F, 34F, 1F}, {60F, 0F, 3F}, {78F, -16F, 2F}, {96F, -9F, 1F}, {125F, 0F, 1F}},
            {{0F, 0F, 78F, 2.8F, 1F}, {55F, 8F, 74F, 1.9F, 1F}, {60F, 12F, 72F, 1.75F, 1F}, {64F, 20F, 68F, 2.2F, 2F}, {104F, 35F, 70F, 2.6F, 1F}, {125F, 40F, 72F, 2.8F, 1F}},
            {{0F, 0F, 0.25F, -0.15F, 1F}, {60F, 0F, 0.25F, -0.1F, 1F}, {104F, 0F, 0.2F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {10F, 0.25F, 0F, 0F, 1F}, {30F, 0.4F, 0.15F, 0F, 1F}, {50F, 0.35F, -0.1F, 0F, 1F}, {57F, 0.45F, 0F, 0F, 1F}, {60F, -0.5F, 0F, 0.1F, 2F}, {66F, -0.25F, 0.3F, 0.15F, 1F}, {90F, 0F, 0.75F, 0.25F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {57F, 0.08F, 0F, 0F, 1F}, {60F, -0.55F, 0F, 0F, 2F}, {64F, -0.2F, 0F, 0.05F, 1F}, {75F, 0.05F, 0F, 0.08F, 1F}, {104F, 0.05F, 0F, 0.1F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {12F, 0.15F, 0F, 0F, 1F}, {24F, -0.05F, 0F, 0F, 1F}, {36F, 0.18F, 0F, 0F, 1F}, {48F, 0F, 0F, 0F, 1F}, {56F, 0.12F, 0F, 0F, 1F}, {60F, -0.25F, 0F, 0.25F, 2F}, {70F, 0.05F, 0F, 0.05F, 1F}, {100F, 0F, 0F, 0.12F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.05F, 0F, 0F, 1F}, {18F, 0.16F, 0F, 0F, 1F}, {30F, -0.04F, 0F, 0F, 1F}, {42F, 0.17F, 0F, 0F, 1F}, {54F, 0.02F, 0F, 0F, 1F}, {60F, -0.22F, 0F, -0.25F, 2F}, {70F, 0.05F, 0F, -0.05F, 1F}, {100F, 0F, 0F, -0.12F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {20F, 0.08F, 0F, 0F, 1F}, {40F, -0.05F, 0F, 0F, 1F}, {58F, 0F, 0F, 0F, 1F}, {60F, -0.25F, 0F, 0.05F, 2F}, {70F, 0F, 0F, 0.04F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {28F, 0.08F, 0F, 0F, 1F}, {46F, -0.05F, 0F, 0F, 1F}, {58F, 0F, 0F, 0F, 1F}, {60F, -0.2F, 0F, -0.05F, 2F}, {70F, 0F, 0F, -0.04F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {58F, 0F, 0F, 0F, 1F}, {60F, 0F, 0.06F, 0F, 2F}, {66F, 0F, 0F, 0F, 1F}},
            {{0F, 0.03F, 1F}, {55F, 0.06F, 3F}, {60F, 0.14F, 2F}, {66F, 0.1F, 1F}, {80F, 0.03F, 1F}, {104F, 0F, 1F}},
            {{0F, 0.25F, 1F}, {58F, 0.4F, 1F}, {60F, 1F, 2F}, {125F, 1F, 1F}},
            {{0F, 0F, 1F}, {60F, 0F, 1F}, {62F, 0.5F, 2F}, {104F, 0.9F, 1F}, {125F, 0.95F, 1F}},
    };

    // ---------------------------------------------------------------- INMERSION
    public static final int INMERSION_MUERTE = 100, INMERSION_FIN = 120;
    public static final int INMERSION_BURBUJAS1 = 0;
    public static final int INMERSION_BURBUJAS2 = 14;
    public static final int INMERSION_BURBUJAS3 = 28;
    public static final int INMERSION_ULTIMO = 70;
    public static final float INMERSION_MARGEN = 0.2F;
    public static final int INMERSION_SIN_CABEZA = -1;
    public static final String[] INMERSION_N = {"cam", "mira", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "pos", "temblor", "vineta", "negro"};
    public static final float[][][] INMERSION_K = {
            {{0F, 25F, -12F, 1.35F, 1F}, {36F, 12F, -10F, 1.25F, 1F}, {70F, -5F, -8F, 1.3F, 1F}, {74F, -6F, -8F, 1.35F, 1F}, {100F, -18F, -4F, 1.9F, 1F}, {120F, -22F, -2F, 2.1F, 1F}},
            {{0F, 0F, -0.05F, 0F, 1F}, {100F, 0F, -0.2F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.25F, 0F, 0F, 2F}, {9F, 0.15F, 0F, 0F, 1F}, {14F, -0.3F, 0F, 0F, 2F}, {20F, 0.1F, 0F, 0F, 1F}, {26F, -0.25F, 0F, 0F, 2F}, {32F, 0.05F, 0F, 0F, 1F}, {40F, -0.15F, 0F, 0F, 1F}, {50F, 0.05F, 0F, 0F, 1F}, {62F, -0.08F, 0F, 0F, 1F}, {70F, -0.35F, 0F, 0F, 2F}, {74F, 0.2F, 0F, 0F, 1F}, {100F, 0.35F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.5F, 0.4F, 0F, 2F}, {9F, 0.1F, -0.5F, 0.1F, 1F}, {14F, -0.6F, 0.3F, -0.1F, 2F}, {20F, 0F, -0.45F, 0.1F, 1F}, {26F, -0.4F, 0.35F, 0F, 2F}, {34F, 0F, -0.2F, 0F, 1F}, {46F, -0.2F, 0.15F, 0F, 1F}, {60F, 0.05F, -0.1F, 0F, 1F}, {70F, -0.7F, 0F, 0F, 2F}, {76F, -0.3F, 0F, 0.2F, 1F}, {100F, -0.45F, 0.2F, 0.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.35F, 0F, 0.25F, 2F}, {9F, 0.05F, 0F, 0F, 1F}, {14F, -0.4F, 0F, 0.3F, 2F}, {20F, 0F, 0F, 0.05F, 1F}, {26F, -0.3F, 0F, 0.2F, 2F}, {34F, 0F, 0F, 0F, 1F}, {46F, -0.15F, 0F, 0.1F, 1F}, {60F, 0F, 0F, 0F, 1F}, {70F, -0.45F, 0F, 0.35F, 2F}, {80F, -0.3F, 0F, 0.2F, 1F}, {100F, -0.4F, 0F, 0.28F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.35F, 0F, -0.25F, 2F}, {11F, 0.05F, 0F, 0F, 1F}, {16F, -0.4F, 0F, -0.3F, 2F}, {22F, 0F, 0F, -0.05F, 1F}, {28F, -0.3F, 0F, -0.2F, 2F}, {36F, 0F, 0F, 0F, 1F}, {48F, -0.15F, 0F, -0.1F, 1F}, {62F, 0F, 0F, 0F, 1F}, {72F, -0.45F, 0F, -0.35F, 2F}, {82F, -0.3F, 0F, -0.2F, 1F}, {102F, -0.4F, 0F, -0.28F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.4F, 0F, 0F, 2F}, {9F, 0.1F, 0F, 0F, 1F}, {14F, -0.35F, 0F, 0F, 2F}, {20F, 0.05F, 0F, 0F, 1F}, {26F, -0.3F, 0F, 0F, 2F}, {34F, 0F, 0F, 0F, 1F}, {70F, -0.45F, 0F, 0F, 2F}, {80F, -0.2F, 0F, 0.05F, 1F}, {100F, -0.25F, 0F, 0.08F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {7F, -0.4F, 0F, 0F, 2F}, {12F, 0.1F, 0F, 0F, 1F}, {17F, -0.35F, 0F, 0F, 2F}, {23F, 0.05F, 0F, 0F, 1F}, {29F, -0.3F, 0F, 0F, 2F}, {37F, 0F, 0F, 0F, 1F}, {73F, -0.45F, 0F, 0F, 2F}, {83F, -0.2F, 0F, -0.05F, 1F}, {103F, -0.25F, 0F, -0.08F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {66F, 0F, 0F, 0F, 1F}, {70F, 0F, 0.04F, 0F, 2F}, {100F, 0F, 0.05F, 0F, 1F}},
            {{0F, 0.06F, 1F}, {34F, 0.08F, 1F}, {46F, 0.04F, 1F}, {66F, 0.02F, 1F}, {70F, 0.1F, 2F}, {76F, 0.03F, 1F}, {90F, 0F, 1F}},
            {{0F, 0.2F, 1F}, {100F, 0.6F, 1F}},
            {{0F, 0.15F, 1F}, {40F, 0.3F, 1F}, {70F, 0.5F, 1F}, {100F, 0.92F, 3F}, {120F, 0.96F, 1F}},
    };

    // ---------------------------------------------------------------- COLLAR
    public static final int COLLAR_MUERTE = 84, COLLAR_FIN = 115;
    public static final int COLLAR_TONO = 70;
    public static final int COLLAR_ESTALLIDO = 84;
    public static final int[] COLLAR_PITIDOS = {0, 12, 23, 33, 42, 50, 57, 62, 66, 69, 72};
    public static final float COLLAR_MARGEN = 0.2F;
    public static final int COLLAR_SIN_CABEZA = 84;
    public static final String[] COLLAR_N = {"cam", "mira", "pos", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "temblor", "vineta", "negro"};
    public static final float[][][] COLLAR_K = {
            {{0F, 20F, 2F, 2.3F, 1F}, {10F, 15F, 0F, 2F, 1F}, {60F, 8F, -4F, 1.6F, 1F}, {64F, 8F, -6F, 1.5F, 1F}, {82F, 5F, -8F, 1.35F, 1F}, {84F, 5F, -8F, 1.35F, 1F}, {90F, 12F, 22F, 3.9F, 2F}, {115F, 15F, 25F, 4.3F, 1F}},
            {{0F, 0F, 1.5F, 0F, 1F}, {60F, 0F, 1.45F, 0F, 1F}, {64F, 0F, 1.1F, 0F, 1F}, {84F, 0F, 1.1F, 0F, 1F}, {92F, 0F, 0.6F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, 0F, 0F, 0.06F, 2F}, {8F, 0F, 0F, 0F, 1F}, {16F, 0.04F, 0F, 0.08F, 2F}, {22F, -0.05F, 0F, 0.02F, 1F}, {30F, 0.04F, 0F, 0.1F, 2F}, {38F, -0.04F, 0F, 0.03F, 1F}, {46F, 0.05F, 0F, 0.09F, 2F}, {54F, -0.03F, 0F, 0.04F, 1F}, {60F, 0F, 0F, 0.06F, 1F}, {64F, 0F, -0.47F, 0.06F, 3F}, {67F, 0F, -0.4F, 0.06F, 2F}, {70F, 0F, -0.42F, 0.06F, 1F}, {84F, 0F, -0.42F, 0.06F, 1F}, {86F, 0F, -0.36F, 0.06F, 2F}, {92F, 0F, -0.42F, 0.06F, 3F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, 0.06F, 0F, 0F, 2F}, {8F, 0F, 0F, 0F, 1F}, {16F, 0.1F, 0.12F, 0F, 2F}, {22F, 0.03F, -0.1F, 0F, 1F}, {30F, 0.12F, 0.15F, 0.04F, 2F}, {38F, 0.02F, -0.15F, -0.04F, 1F}, {46F, 0.1F, 0.1F, 0.03F, 2F}, {54F, 0.02F, -0.08F, 0F, 1F}, {60F, 0F, 0F, 0F, 1F}, {64F, -0.12F, 0F, 0F, 3F}, {68F, -0.04F, 0F, 0F, 2F}, {84F, 0.02F, 0F, 0F, 1F}, {86F, 0.22F, 0F, 0F, 2F}, {92F, -0.3F, 0F, 0.05F, 3F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.1F, 0F, 0F, 2F}, {10F, 0.08F, 0F, 0F, 1F}, {16F, -0.15F, 0.15F, 0F, 2F}, {22F, 0.02F, -0.1F, 0F, 1F}, {30F, -0.2F, -0.2F, 0.05F, 2F}, {38F, 0.05F, 0.2F, -0.05F, 1F}, {46F, -0.15F, -0.12F, 0.04F, 2F}, {54F, 0.05F, 0.08F, 0F, 1F}, {64F, 0.28F, 0F, 0F, 3F}, {68F, 0.15F, 0F, 0F, 2F}, {80F, -0.12F, 0F, 0F, 1F}, {84F, -0.14F, 0F, 0F, 1F}, {86F, -0.4F, 0F, 0F, 2F}, {92F, 0.6F, 0F, 0.08F, 3F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.35F, 0F, 0F, 2F}, {9F, 0.15F, 0F, 0F, 1F}, {14F, 0.05F, 0.5F, 0.1F, 2F}, {19F, -0.05F, -0.55F, -0.1F, 1F}, {24F, 0.1F, 0.5F, 0.12F, 1F}, {29F, -0.1F, -0.6F, -0.15F, 1F}, {34F, 0.05F, 0.45F, 0.1F, 1F}, {39F, -0.1F, -0.5F, -0.1F, 1F}, {44F, 0.05F, 0.35F, 0.05F, 1F}, {52F, -0.2F, -0.3F, 0F, 1F}, {60F, 0.1F, 0F, 0F, 1F}, {64F, 0.3F, 0F, 0F, 3F}, {67F, -0.55F, 0.05F, 0F, 2F}, {84F, -0.65F, 0F, 0.05F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.45F, 0F, 0.15F, 2F}, {11F, -1.92F, -0.62F, 0F, 2F}, {16F, -1.7F, -0.45F, 0.18F, 2F}, {20F, -2.05F, -0.7F, -0.04F, 1F}, {25F, -1.7F, -0.45F, 0.18F, 2F}, {29F, -2.05F, -0.7F, -0.04F, 1F}, {34F, -1.7F, -0.45F, 0.18F, 2F}, {38F, -2.05F, -0.7F, -0.04F, 1F}, {42F, -1.7F, -0.45F, 0.18F, 2F}, {46F, -2.05F, -0.7F, -0.04F, 1F}, {50F, -1.7F, -0.45F, 0.18F, 2F}, {54F, -2.05F, -0.7F, -0.04F, 1F}, {60F, -1.92F, -0.62F, 0F, 1F}, {66F, -2.1F, -0.56F, 0.12F, 2F}, {84F, -2.15F, -0.58F, 0.1F, 1F}, {87F, -3.15F, 0F, -0.95F, 2F}, {93F, -0.22F, 0F, 0.18F, 3F}, {96F, -0.42F, 0F, 0.12F, 2F}, {100F, -0.22F, 0F, 0.15F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.45F, 0F, -0.15F, 2F}, {12F, -1.92F, 0.62F, 0F, 2F}, {18F, -1.7F, 0.45F, -0.18F, 2F}, {22F, -2.05F, 0.7F, 0.04F, 1F}, {27F, -1.7F, 0.45F, -0.18F, 2F}, {31F, -2.05F, 0.7F, 0.04F, 1F}, {36F, -1.7F, 0.45F, -0.18F, 2F}, {40F, -2.05F, 0.7F, 0.04F, 1F}, {44F, -1.7F, 0.45F, -0.18F, 2F}, {48F, -2.05F, 0.7F, 0.04F, 1F}, {52F, -1.7F, 0.45F, -0.18F, 2F}, {56F, -2.05F, 0.7F, 0.04F, 1F}, {60F, -1.92F, 0.62F, 0F, 1F}, {66F, -2.1F, 0.56F, -0.12F, 2F}, {84F, -2.15F, 0.58F, -0.1F, 1F}, {87F, -3.15F, 0F, 0.95F, 2F}, {93F, -0.22F, 0F, -0.18F, 3F}, {96F, -0.42F, 0F, -0.12F, 2F}, {100F, -0.22F, 0F, -0.15F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, 0F, 0F, 1F}, {16F, 0.4F, 0F, 0F, 2F}, {19F, 0F, 0F, 0F, 1F}, {28F, -0.1F, 0F, 0F, 1F}, {30F, 0.4F, 0F, 0.05F, 2F}, {34F, -0.25F, 0F, 0F, 1F}, {44F, 0.3F, 0F, 0F, 2F}, {50F, -0.15F, 0F, 0F, 1F}, {60F, 0F, 0F, 0.05F, 1F}, {64F, 1.15F, 0F, 0.12F, 3F}, {67F, 1.08F, 0F, 0.1F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {14F, 0F, 0F, 0F, 1F}, {16F, -0.3F, 0F, 0F, 2F}, {19F, 0F, 0F, 0F, 1F}, {28F, 0.1F, 0F, 0F, 1F}, {30F, -0.35F, 0F, 0F, 2F}, {34F, 0.3F, 0F, -0.05F, 1F}, {44F, -0.25F, 0F, 0F, 2F}, {50F, 0.2F, 0F, 0F, 1F}, {60F, 0F, 0F, -0.05F, 1F}, {64F, 1.15F, 0F, -0.12F, 3F}, {67F, 1.08F, 0F, -0.1F, 2F}},
            {{0F, 0.015F, 1F}, {60F, 0.04F, 3F}, {84F, 0.1F, 3F}, {87F, 0F, 1F}, {88F, 0.06F, 1F}, {104F, 0F, 1F}},
            {{0F, 0.3F, 1F}, {60F, 0.55F, 1F}, {84F, 0.8F, 1F}, {85F, 1F, 1F}},
            {{0F, 0F, 1F}, {84F, 0F, 1F}, {87F, 0.6F, 2F}, {115F, 0.7F, 1F}},
    };

    // ---------------------------------------------------------------- CAJA
    public static final int CAJA_MUERTE = 100, CAJA_FIN = 120;
    public static final int CAJA_ULTIMO = 78;
    public static final int[] CAJA_GOLPES = {6, 13, 19, 27, 33, 41, 52, 64};
    public static final float CAJA_MARGEN = 0.2F;
    public static final int CAJA_SIN_CABEZA = -1;
    public static final String[] CAJA_N = {"cam", "mira", "rightArm", "leftArm", "head", "torso", "pos", "body", "rightLeg", "leftLeg", "temblor", "vineta", "negro"};
    public static final float[][][] CAJA_K = {
            {{0F, 10F, 0F, 1.25F, 1F}, {40F, -12F, 2F, 1.15F, 1F}, {70F, 8F, 0F, 1.2F, 1F}, {80F, 0F, 0F, 1.25F, 1F}, {100F, 0F, 8F, 2.2F, 1F}, {120F, 0F, 10F, 2.5F, 1F}},
            {{0F, 0F, 1.55F, 0F, 1F}, {70F, 0F, 1.5F, 0F, 1F}, {80F, 0F, 1.35F, 0F, 1F}, {100F, 0F, 1.1F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -2.5F, 0F, 0.3F, 2F}, {4F, -2.45F, 0F, 0.47F, 3F}, {6F, -2.77F, 0F, 0.02F, 2F}, {9F, -2.6F, 0F, 0.2F, 2F}, {11F, -2.2785F, 0F, 0.4665F, 3F}, {13F, -2.5796F, 0F, 0.02F, 2F}, {16F, -2.418F, 0F, 0.2F, 2F}, {17F, -2.107F, 0F, 0.463F, 3F}, {19F, -2.3892F, 0F, 0.02F, 2F}, {22F, -2.236F, 0F, 0.2F, 2F}, {25F, -1.9355F, 0F, 0.4595F, 3F}, {27F, -2.1988F, 0F, 0.02F, 2F}, {30F, -2.054F, 0F, 0.2F, 2F}, {31F, -1.764F, 0F, 0.456F, 3F}, {33F, -2.0084F, 0F, 0.02F, 2F}, {36F, -1.872F, 0F, 0.2F, 2F}, {39F, -1.5925F, 0F, 0.4525F, 3F}, {41F, -1.818F, 0F, 0.02F, 2F}, {44F, -1.69F, 0F, 0.2F, 2F}, {50F, -1.421F, 0F, 0.449F, 3F}, {52F, -1.6276F, 0F, 0.02F, 2F}, {55F, -1.508F, 0F, 0.2F, 2F}, {62F, -1.2495F, 0F, 0.4455F, 3F}, {64F, -1.4372F, 0F, 0.02F, 2F}, {67F, -1.326F, 0F, 0.2F, 2F}, {80F, -2.55F, 0F, 0.12F, 1F}, {98F, -0.45F, 0F, 0.1F, 3F}, {100F, -0.5F, 0F, 0.12F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -2.5F, 0F, -0.3F, 2F}, {4F, -2.45F, 0F, -0.47F, 3F}, {6F, -2.77F, 0F, -0.02F, 2F}, {9F, -2.6F, 0F, -0.2F, 2F}, {11F, -2.2785F, 0F, -0.4665F, 3F}, {13F, -2.5796F, 0F, -0.02F, 2F}, {16F, -2.418F, 0F, -0.2F, 2F}, {17F, -2.107F, 0F, -0.463F, 3F}, {19F, -2.3892F, 0F, -0.02F, 2F}, {22F, -2.236F, 0F, -0.2F, 2F}, {25F, -1.9355F, 0F, -0.4595F, 3F}, {27F, -2.1988F, 0F, -0.02F, 2F}, {30F, -2.054F, 0F, -0.2F, 2F}, {31F, -1.764F, 0F, -0.456F, 3F}, {33F, -2.0084F, 0F, -0.02F, 2F}, {36F, -1.872F, 0F, -0.2F, 2F}, {39F, -1.5925F, 0F, -0.4525F, 3F}, {41F, -1.818F, 0F, -0.02F, 2F}, {44F, -1.69F, 0F, -0.2F, 2F}, {50F, -1.421F, 0F, -0.449F, 3F}, {52F, -1.6276F, 0F, -0.02F, 2F}, {55F, -1.508F, 0F, -0.2F, 2F}, {62F, -1.2495F, 0F, -0.4455F, 3F}, {64F, -1.4372F, 0F, -0.02F, 2F}, {67F, -1.326F, 0F, -0.2F, 2F}, {80F, -2.55F, 0F, -0.12F, 1F}, {98F, -0.45F, 0F, -0.1F, 3F}, {100F, -0.5F, 0F, -0.12F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.3F, 0.2F, 0F, 2F}, {9F, 0.1F, -0.45F, 0.1F, 1F}, {16F, -0.25F, 0.4F, -0.1F, 1F}, {23F, 0.05F, -0.5F, 0.1F, 1F}, {30F, -0.3F, 0.35F, 0F, 1F}, {37F, 0.1F, -0.4F, 0.05F, 1F}, {46F, -0.2F, 0.3F, 0F, 1F}, {58F, 0.05F, -0.2F, 0F, 1F}, {70F, -0.1F, 0.1F, 0F, 1F}, {78F, -0.45F, 0F, 0F, 2F}, {90F, 0.5F, 0.1F, 0.1F, 1F}, {100F, 0.55F, 0.12F, 0.12F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.1F, 0F, 0F, 2F}, {19F, -0.12F, 0.1F, 0F, 2F}, {33F, -0.1F, -0.1F, 0F, 2F}, {52F, -0.06F, 0F, 0F, 1F}, {74F, 0.15F, 0F, 0F, 1F}, {78F, -0.2F, 0F, 0F, 2F}, {100F, 0.28F, 0F, 0.04F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {13F, 0.03F, 0F, 0.05F, 2F}, {20F, -0.03F, 0F, 0F, 1F}, {33F, 0.02F, 0F, 0.06F, 2F}, {45F, 0F, 0F, 0F, 1F}, {70F, 0F, 0F, 0F, 1F}, {74F, 0F, -0.18F, 0.03F, 3F}, {77F, 0F, -0.15F, 0.03F, 2F}, {100F, 0F, -0.2F, 0.04F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {13F, 0.06F, 0.1F, 0F, 2F}, {20F, 0F, -0.1F, 0F, 1F}, {33F, 0.06F, 0.12F, 0F, 2F}, {45F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {11F, 0.3F, 0F, 0F, 2F}, {15F, 0F, 0F, 0F, 1F}, {31F, 0.25F, 0F, 0F, 2F}, {36F, 0F, 0F, 0F, 1F}, {70F, 0F, 0F, 0F, 1F}, {74F, 0.45F, 0F, 0.05F, 3F}},
            {{0F, 0F, 0F, 0F, 1F}, {17F, -0.25F, 0F, 0F, 2F}, {22F, 0F, 0F, 0F, 1F}, {70F, 0F, 0F, 0F, 1F}, {74F, 0.45F, 0F, -0.05F, 3F}},
            {{0F, 0.04F, 1F}, {50F, 0.06F, 1F}, {70F, 0.03F, 1F}, {78F, 0.09F, 2F}, {86F, 0.02F, 1F}, {100F, 0F, 1F}},
            {{0F, 0.3F, 1F}, {100F, 0.8F, 1F}},
            {{0F, 0.1F, 1F}, {60F, 0.3F, 1F}, {80F, 0.55F, 1F}, {100F, 0.9F, 3F}, {120F, 0.95F, 1F}},
    };

    // ---------------------------------------------------------------- PARO
    public static final int PARO_MUERTE = 96, PARO_FIN = 120;
    public static final int PARO_PITIDO1 = 0;
    public static final int PARO_PITIDO2 = 11;
    public static final int PARO_PITIDO3 = 24;
    public static final int PARO_PLANO = 34;
    public static final float PARO_MARGEN = 0.2F;
    public static final int PARO_SIN_CABEZA = -1;
    public static final String[] PARO_N = {"aux", "cam", "mira", "torso", "head", "rightArm", "leftArm", "pos", "temblor", "vineta", "negro"};
    public static final float[][][] PARO_K = {
            {{0F, 1F, 1F}, {30F, 1F, 1F}, {56F, 0F, 1F}},
            {{0F, 0F, 4F, 0.75F, 1F}, {30F, 6F, 4F, 0.85F, 1F}, {56F, 25F, 8F, 2.1F, 1F}, {96F, 35F, 14F, 2.3F, 1F}, {120F, 38F, 16F, 2.4F, 1F}},
            {{0F, 0F, 1.05F, 0F, 1F}, {96F, 0F, 0.95F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.18F, 0F, 0F, 2F}, {8F, 0.05F, 0F, 0F, 1F}, {14F, -0.12F, 0F, 0F, 2F}, {20F, 0.05F, 0F, 0F, 1F}, {27F, -0.07F, 0F, 0F, 2F}, {33F, 0F, 0F, 0F, 1F}, {36F, 0.15F, 0F, 0.03F, 3F}, {60F, 0.2F, 0F, 0.05F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.35F, 0F, 0F, 2F}, {9F, 0.1F, 0F, 0F, 1F}, {14F, -0.25F, 0.1F, 0F, 2F}, {20F, 0.1F, 0F, 0F, 1F}, {27F, -0.1F, 0F, 0F, 2F}, {34F, -0.15F, 0F, 0F, 1F}, {37F, 0.25F, 0F, 0.05F, 3F}, {60F, 0.35F, 0.25F, 0.25F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {3F, -0.12F, 0F, 0.05F, 2F}, {8F, 0F, 0F, 0F, 1F}, {14F, -0.08F, 0F, 0.03F, 2F}, {20F, 0F, 0F, 0F, 1F}, {34F, -0.15F, 0F, 0.1F, 2F}, {40F, 0.25F, 0F, 0.12F, 3F}, {60F, 0.3F, 0F, 0.15F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.12F, 0F, -0.05F, 2F}, {9F, 0F, 0F, 0F, 1F}, {15F, -0.08F, 0F, -0.03F, 2F}, {21F, 0F, 0F, 0F, 1F}, {35F, -0.15F, 0F, -0.1F, 2F}, {41F, 0.25F, 0F, -0.12F, 3F}, {61F, 0.3F, 0F, -0.15F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {34F, 0F, 0F, 0F, 1F}, {37F, 0F, -0.03F, 0F, 3F}},
            {{0F, 0.015F, 1F}, {30F, 0.02F, 1F}, {34F, 0.06F, 2F}, {40F, 0F, 1F}},
            {{0F, 0.4F, 1F}, {96F, 0.7F, 1F}},
            {{0F, 0.25F, 1F}, {34F, 0.35F, 1F}, {40F, 0.45F, 1F}, {96F, 0.9F, 3F}, {120F, 0.96F, 1F}},
    };

    // ---------------------------------------------------------------- COLGADO
    public static final int COLGADO_MUERTE = 90, COLGADO_FIN = 115;
    public static final int COLGADO_CUERDA1 = 4;
    public static final int COLGADO_CUERDA2 = 22;
    public static final int COLGADO_CUERDA3 = 44;
    public static final int COLGADO_CUERDA4 = 70;
    public static final int COLGADO_CUERDA5 = 88;
    public static final int COLGADO_JADEO1 = 2;
    public static final int COLGADO_JADEO2 = 24;
    public static final int COLGADO_JADEO3 = 48;
    public static final float COLGADO_MARGEN = 0.2F;
    public static final int COLGADO_SIN_CABEZA = -1;
    public static final String[] COLGADO_N = {"cam", "mira", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "temblor", "vineta", "negro"};
    public static final float[][][] COLGADO_K = {
            {{0F, 30F, -18F, 2.1F, 1F}, {50F, 5F, -15F, 1.9F, 1F}, {90F, -25F, -10F, 2.1F, 1F}, {115F, -32F, -8F, 2.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {90F, 0F, -0.1F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, 0F, 0.28F, 0F, 1F}, {20F, 0F, -0.25F, 0F, 1F}, {32F, 0F, 0.2F, 0F, 1F}, {46F, 0F, -0.12F, 0F, 1F}, {60F, 0F, 0.08F, 0F, 1F}, {78F, 0F, -0.04F, 0F, 1F}, {90F, 0F, 0.02F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.15F, 0F, 0F, 1F}, {12F, 0.1F, 0F, 0F, 1F}, {18F, -0.14F, 0F, 0F, 1F}, {25F, 0.08F, 0F, 0F, 1F}, {50F, -0.12F, 0F, 0F, 2F}, {58F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, -0.3F, 0.2F, 0F, 1F}, {14F, 0.15F, -0.25F, 0F, 1F}, {22F, -0.25F, 0.2F, 0F, 1F}, {32F, 0.1F, -0.15F, 0F, 1F}, {50F, -0.3F, 0F, 0F, 2F}, {60F, 0.3F, 0.1F, 0.2F, 1F}, {90F, 0.35F, 0.15F, 0.25F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, 0.35F, 0F, 0.15F, 1F}, {12F, -0.3F, 0F, 0.05F, 1F}, {18F, 0.3F, 0F, 0.18F, 1F}, {25F, -0.25F, 0F, 0.05F, 1F}, {34F, 0.2F, 0F, 0.1F, 1F}, {44F, -0.1F, 0F, 0.04F, 1F}, {50F, 0.25F, 0F, 0.12F, 2F}, {56F, 0F, 0F, 0.05F, 1F}, {90F, 0.05F, 0F, 0.02F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {9F, 0.35F, 0F, -0.15F, 1F}, {15F, -0.3F, 0F, -0.05F, 1F}, {21F, 0.3F, 0F, -0.18F, 1F}, {28F, -0.25F, 0F, -0.05F, 1F}, {37F, 0.2F, 0F, -0.1F, 1F}, {47F, -0.1F, 0F, -0.04F, 1F}, {53F, 0.25F, 0F, -0.12F, 2F}, {59F, 0F, 0F, -0.05F, 1F}, {93F, 0.05F, 0F, -0.02F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {6F, 0.35F, 0F, 0F, 2F}, {11F, 0F, 0F, 0F, 1F}, {18F, 0.3F, 0F, 0F, 2F}, {24F, 0F, 0F, 0F, 1F}, {50F, 0.2F, 0F, 0F, 2F}, {56F, 0F, 0F, 0F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, 0.35F, 0F, 0F, 2F}, {13F, 0F, 0F, 0F, 1F}, {20F, 0.3F, 0F, 0F, 2F}, {26F, 0F, 0F, 0F, 1F}, {52F, 0.2F, 0F, 0F, 2F}, {58F, 0F, 0F, 0F, 1F}},
            {{0F, 0.03F, 1F}, {30F, 0.02F, 1F}, {50F, 0.08F, 2F}, {56F, 0.01F, 1F}, {70F, 0F, 1F}},
            {{0F, 0.5F, 1F}, {60F, 0.9F, 1F}, {115F, 1F, 1F}},
            {{0F, 0.1F, 1F}, {50F, 0.3F, 1F}, {90F, 0.9F, 3F}, {115F, 0.95F, 1F}},
    };

    // ---------------------------------------------------------------- VENENO
    public static final int VENENO_MUERTE = 90, VENENO_FIN = 115;
    public static final int VENENO_BAJA = 4;
    public static final int VENENO_AGARRA = 28;
    public static final int VENENO_RODILLAS = 58;
    public static final int VENENO_ESPUMA = 30;
    public static final int VENENO_CAE = 76;
    public static final int VENENO_SUELO = 83;
    public static final int VENENO_PITIDO = 48;
    public static final int[] VENENO_LATIDOS = {2, 9, 16, 23, 31, 40, 50, 61, 74, 88};
    public static final int[] VENENO_TOSES = {11, 16, 21};
    public static final float VENENO_MARGEN = 0.2F;
    public static final int VENENO_SIN_CABEZA = -1;
    public static final boolean VENENO_YACE = true;
    public static final String[] VENENO_N = {"cam", "mira", "pos", "body", "torso", "head", "rightArm", "leftArm", "rightLeg", "leftLeg", "temblor", "convulsion", "veneno", "pulso", "borroso", "venas", "negro", "boca"};
    public static final float[][][] VENENO_K = {
            {{0F, 18F, 4F, 2.3F, 1F}, {12F, 10F, 2F, 1.75F, 1F}, {24F, 6F, 1F, 1.6F, 1F}, {34F, -10F, 0F, 1.65F, 1F}, {56F, -26F, 4F, 2.1F, 1F}, {64F, -32F, -3F, 2F, 1F}, {76F, -30F, -2F, 2.15F, 1F}, {85F, 38F, 5F, 2.5F, 1F}, {90F, 44F, 6F, 2.6F, 1F}, {115F, 50F, 8F, 2.8F, 1F}},
            {{0F, 0F, 1.5F, 0F, 1F}, {34F, 0F, 1.45F, 0F, 1F}, {56F, 0F, 1.3F, 0F, 1F}, {64F, 0F, 1F, 0F, 1F}, {76F, 0F, 0.95F, 0.05F, 1F}, {85F, 0F, 0.3F, 0.3F, 1F}, {115F, 0F, 0.25F, 0.3F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {34F, 0F, 0F, 0F, 1F}, {38F, 0.08F, 0F, -0.05F, 2F}, {43F, -0.1F, 0F, 0.03F, 1F}, {49F, 0.07F, 0F, -0.08F, 2F}, {54F, -0.04F, 0F, 0.02F, 1F}, {56F, 0F, -0.02F, 0F, 1F}, {58F, 0F, -0.48F, 0.04F, 3F}, {61F, 0F, -0.42F, 0.04F, 2F}, {64F, 0F, -0.44F, 0.04F, 1F}, {76F, 0F, -0.44F, 0.04F, 1F}, {83F, 0F, -0.55F, 0.12F, 3F}, {86F, 0F, -0.51F, 0.12F, 2F}, {115F, 0F, -0.515F, 0.12F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {34F, 0F, 0F, 0F, 1F}, {38F, 0.05F, 0.1F, -0.1F, 2F}, {43F, 0.02F, -0.12F, 0.12F, 1F}, {49F, 0.08F, 0.12F, -0.12F, 2F}, {54F, 0.03F, -0.05F, 0.06F, 1F}, {58F, -0.15F, 0F, 0F, 3F}, {62F, -0.05F, 0F, 0F, 2F}, {76F, -0.12F, 0F, 0F, 1F}, {83F, -1.52F, 0F, 0.04F, 3F}, {86F, -1.45F, 0F, 0.04F, 2F}, {115F, -1.46F, 0F, 0.04F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {4F, -0.06F, 0F, 0F, 1F}, {10F, 0.05F, 0F, 0F, 1F}, {11F, 0.32F, 0F, 0F, 2F}, {14F, 0.1F, 0F, 0F, 1F}, {16F, 0.36F, 0F, 0F, 2F}, {19F, 0.12F, 0F, 0F, 1F}, {21F, 0.4F, 0F, 0F, 2F}, {25F, 0.12F, 0F, 0F, 1F}, {34F, 0.1F, 0F, 0F, 1F}, {58F, 0.25F, 0F, 0F, 3F}, {62F, -0.2F, 0F, 0F, 2F}, {68F, 0.25F, 0F, 0.05F, 1F}, {76F, 0.15F, 0F, 0F, 1F}, {83F, 0F, 0F, 0F, 1F}},
            {{0F, -0.45F, 0F, 0F, 1F}, {5F, -0.3F, 0F, 0F, 1F}, {10F, 0.1F, 0F, 0F, 1F}, {11F, 0.45F, 0F, 0F, 2F}, {14F, 0.15F, 0.1F, 0F, 1F}, {16F, 0.5F, 0F, 0F, 2F}, {19F, 0.2F, -0.1F, 0F, 1F}, {21F, 0.55F, 0F, 0.05F, 2F}, {26F, 0F, 0F, 0F, 1F}, {30F, -0.35F, 0F, 0F, 2F}, {38F, -0.2F, 0.3F, 0.1F, 1F}, {44F, -0.3F, -0.3F, -0.1F, 1F}, {50F, -0.15F, 0.25F, 0.05F, 1F}, {58F, 0.2F, 0F, 0F, 1F}, {62F, -0.6F, 0F, 0F, 2F}, {68F, 0.3F, 0.2F, 0.1F, 1F}, {76F, 0.3F, 0F, 0F, 1F}, {83F, -0.15F, 1.2F, 0.1F, 3F}, {86F, -0.1F, 1.25F, 0.08F, 2F}},
            {{0F, -1.55F, -0.5F, 0F, 1F}, {4F, -1.45F, -0.45F, 0F, 1F}, {10F, -0.3F, 0F, 0.08F, 1F}, {14F, -0.45F, 0F, 0.1F, 1F}, {19F, -0.3F, 0F, 0.12F, 1F}, {26F, -1.2F, -0.35F, 0.25F, 1F}, {30F, -1.92F, -0.62F, 0F, 2F}, {36F, -1.75F, -0.5F, 0.2F, 2F}, {39F, -2.02F, -0.7F, -0.02F, 1F}, {43F, -1.75F, -0.5F, 0.2F, 2F}, {46F, -2.02F, -0.7F, -0.02F, 1F}, {50F, -1.75F, -0.5F, 0.2F, 2F}, {53F, -2.02F, -0.7F, -0.02F, 1F}, {58F, -1.92F, -0.62F, 0F, 1F}, {62F, -1F, -0.2F, 0.35F, 2F}, {68F, -1.6F, -0.5F, 0.15F, 1F}, {75F, -0.9F, -0.1F, 0.25F, 1F}, {83F, -2.85F, 0.2F, 0.55F, 3F}, {86F, -2.75F, 0.2F, 0.5F, 2F}, {115F, -2.76F, 0.2F, 0.5F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {8F, -0.2F, 0F, -0.06F, 1F}, {11F, -1.75F, 0.42F, 0.05F, 2F}, {16F, -1.6F, 0.38F, 0.02F, 1F}, {21F, -1.72F, 0.42F, 0.05F, 1F}, {27F, -1.2F, 0.35F, -0.25F, 1F}, {31F, -1.92F, 0.62F, 0F, 2F}, {37F, -1.75F, 0.5F, -0.2F, 2F}, {40F, -2.02F, 0.7F, 0.02F, 1F}, {44F, -1.75F, 0.5F, -0.2F, 2F}, {47F, -2.02F, 0.7F, 0.02F, 1F}, {51F, -1.75F, 0.5F, -0.2F, 2F}, {54F, -2.02F, 0.7F, 0.02F, 1F}, {58F, -1.92F, 0.62F, 0F, 1F}, {63F, -1F, 0.2F, -0.35F, 2F}, {68F, -1.6F, 0.5F, -0.15F, 1F}, {75F, -0.9F, 0.1F, -0.25F, 1F}, {83F, 0.1F, 0F, -0.18F, 3F}, {86F, 0.02F, 0F, -0.12F, 2F}, {115F, 0.02F, 0F, -0.12F, 1F}},
            {{0F, 0F, 0F, 0F, 1F}, {34F, 0F, 0F, 0F, 1F}, {38F, -0.35F, 0F, 0.05F, 2F}, {42F, 0F, 0F, 0F, 1F}, {49F, 0.3F, 0F, 0.08F, 2F}, {53F, 0F, 0F, 0F, 1F}, {56F, 0.1F, 0F, 0.06F, 1F}, {58F, 1.2F, 0F, 0.12F, 3F}, {61F, 1.12F, 0F, 0.1F, 2F}, {76F, 1.12F, 0F, 0.1F, 1F}, {83F, 0.08F, 0F, 0.08F, 3F}, {86F, 0.04F, 0F, 0.07F, 2F}},
            {{0F, 0F, 0F, 0F, 1F}, {34F, 0F, 0F, 0F, 1F}, {43F, 0.3F, 0F, -0.06F, 2F}, {47F, 0F, 0F, 0F, 1F}, {52F, -0.25F, 0F, -0.05F, 2F}, {55F, 0F, 0F, 0F, 1F}, {56F, 0.1F, 0F, -0.06F, 1F}, {58F, 1.2F, 0F, -0.12F, 3F}, {61F, 1.12F, 0F, -0.1F, 2F}, {76F, 1.12F, 0F, -0.1F, 1F}, {83F, 0.08F, 0F, -0.08F, 3F}, {86F, 0.04F, 0F, -0.07F, 2F}},
            {{0F, 0F, 1F}, {10F, 0.015F, 1F}, {34F, 0.04F, 1F}, {58F, 0.07F, 3F}, {62F, 0.03F, 1F}, {76F, 0.02F, 1F}, {83F, 0F, 1F}},
            {{0F, 0F, 1F}, {60F, 0F, 1F}, {63F, 0.55F, 3F}, {66F, 0.35F, 1F}, {70F, 0.75F, 1F}, {75F, 0.25F, 1F}, {83F, 0F, 1F}, {85F, 0.3F, 2F}, {87F, 0F, 1F}, {89F, 0.12F, 2F}, {91F, 0F, 1F}},
            {{0F, 0.12F, 1F}, {12F, 0.3F, 1F}, {34F, 0.5F, 1F}, {58F, 0.72F, 1F}, {76F, 0.88F, 1F}, {90F, 1F, 1F}, {115F, 1F, 1F}},
            {{0F, 0F, 1F}, {2F, 0F, 1F}, {3F, 1F, 2F}, {7F, 0F, 1F}, {9F, 0F, 1F}, {10F, 1F, 2F}, {14F, 0F, 1F}, {16F, 0F, 1F}, {17F, 1F, 2F}, {21F, 0F, 1F}, {23F, 0F, 1F}, {24F, 1F, 2F}, {28F, 0F, 1F}, {31F, 0F, 1F}, {32F, 1F, 2F}, {36F, 0F, 1F}, {40F, 0F, 1F}, {41F, 1F, 2F}, {45F, 0F, 1F}, {50F, 0F, 1F}, {51F, 1F, 2F}, {55F, 0F, 1F}, {61F, 0F, 1F}, {62F, 1F, 2F}, {66F, 0F, 1F}, {74F, 0F, 1F}, {75F, 1F, 2F}, {79F, 0F, 1F}, {88F, 0F, 1F}, {89F, 1F, 2F}, {93F, 0F, 1F}},
            {{0F, 0F, 1F}, {16F, 0.2F, 1F}, {40F, 0.5F, 1F}, {64F, 0.8F, 1F}, {90F, 1F, 1F}},
            {{0F, 0F, 1F}, {8F, 0.15F, 1F}, {30F, 0.5F, 1F}, {60F, 0.85F, 1F}, {90F, 1F, 1F}},
            {{0F, 0F, 1F}, {40F, 0.08F, 1F}, {76F, 0.35F, 1F}, {90F, 0.88F, 3F}, {115F, 0.95F, 1F}},
            {{0F, 0F, 1.625F, 0.181F, 0F}, {2F, 0F, 1.615F, 0.192F, 0F}, {4F, 0F, 1.599F, 0.208F, 0F}, {6F, -0.001F, 1.586F, 0.219F, 0F}, {8F, -0.001F, 1.534F, 0.253F, 0F}, {10F, -0.001F, 1.497F, 0.269F, 0F}, {12F, 0.007F, 1.421F, 0.283F, 0F}, {14F, 0.027F, 1.482F, 0.272F, 0F}, {16F, 0F, 1.384F, 0.282F, 0F}, {18F, -0.02F, 1.447F, 0.28F, 0F}, {20F, -0.002F, 1.384F, 0.282F, 0F}, {22F, 0.001F, 1.389F, 0.283F, 0F}, {24F, -0.001F, 1.472F, 0.276F, 0F}, {26F, -0.002F, 1.525F, 0.257F, 0F}, {28F, -0.004F, 1.599F, 0.209F, 0F}, {30F, -0.003F, 1.607F, 0.201F, 0F}, {32F, 0.005F, 1.601F, 0.207F, 0F}, {34F, 0.022F, 1.59F, 0.215F, 0F}, {36F, 0.173F, 1.585F, 0.227F, 0F}, {38F, 0.203F, 1.579F, 0.23F, 0F}, {40F, 0.06F, 1.585F, 0.212F, 0F}, {42F, -0.167F, 1.589F, 0.175F, 0F}, {44F, -0.081F, 1.608F, 0.179F, 0F}, {46F, 0.099F, 1.603F, 0.214F, 0F}, {48F, 0.183F, 1.581F, 0.24F, 0F}, {50F, 0.185F, 1.573F, 0.236F, 0F}, {52F, 0.046F, 1.566F, 0.211F, 0F}, {54F, -0.049F, 1.536F, 0.208F, 0F}, {56F, -0.021F, 1.476F, 0.263F, 0F}, {58F, -0.001F, 0.946F, 0.348F, 0F}, {60F, -0.004F, 1.2F, 0.187F, 0F}, {62F, -0.005F, 1.223F, 0.143F, 0F}, {64F, -0.013F, 1.173F, 0.191F, 0F}, {66F, 0.018F, 1.038F, 0.278F, 0F}, {68F, 0.041F, 0.958F, 0.295F, 0F}, {70F, 0.056F, 0.947F, 0.302F, 0F}, {72F, 0.043F, 0.942F, 0.314F, 0F}, {74F, 0.016F, 0.946F, 0.324F, 0F}, {76F, 0.005F, 0.951F, 0.328F, 0F}, {78F, 0.012F, 0.957F, 0.349F, 0F}, {80F, 0.057F, 0.857F, 0.482F, 0F}, {82F, 0.163F, 0.484F, 0.712F, 0F}, {84F, 0.21F, 0.188F, 0.76F, 0F}, {86F, 0.217F, 0.223F, 0.752F, 0F}, {88F, 0.22F, 0.225F, 0.749F, 0F}, {90F, 0.221F, 0.224F, 0.747F, 0F}, {92F, 0.219F, 0.225F, 0.751F, 0F}, {94F, 0.219F, 0.224F, 0.751F, 0F}, {96F, 0.219F, 0.223F, 0.751F, 0F}, {98F, 0.219F, 0.222F, 0.751F, 0F}, {100F, 0.219F, 0.22F, 0.751F, 0F}, {102F, 0.219F, 0.219F, 0.751F, 0F}, {104F, 0.219F, 0.217F, 0.751F, 0F}, {106F, 0.219F, 0.216F, 0.751F, 0F}, {108F, 0.219F, 0.215F, 0.751F, 0F}, {110F, 0.219F, 0.214F, 0.751F, 0F}, {112F, 0.219F, 0.213F, 0.751F, 0F}, {114F, 0.219F, 0.213F, 0.751F, 0F}},
    };

}
