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

}
