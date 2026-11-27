package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Estado y movimiento de las luces de la rave (bola de discoteca, foco móvil y estroboscopio). Todo va en el blockstate
 * (encendida, modo y color), así que no hace falta sincronizar nada: cada cliente calcula el movimiento con la hora del mundo
 * y la posición, y todos ven lo mismo.
 */
public final class LucesRave {
    private LucesRave() {}

    public static final BooleanProperty ENCENDIDA = BooleanProperty.create("encendida");
    /** Si tenía señal de redstone (para encender solo al subir y apagar al bajar). */
    public static final BooleanProperty CON_SENAL = net.minecraft.world.level.block.state.properties.BlockStateProperties.POWERED;
    /** Velocidad o patrón (0 a 3); agachado + clic lo cambia. */
    public static final IntegerProperty MODO = IntegerProperty.create("modo", 0, 3);
    public static final BooleanProperty COLGADO = BooleanProperty.create("colgado");

    /** Desfase propio de cada luz (para que dos focos no se muevan igual). */
    public static float semilla(BlockPos p) {
        return (float) ((p.asLong() * 2654435761L) & 0xFFFF) / 0xFFFF * Mth.TWO_PI;
    }

    /** Giro de la bola de discoteca (radianes) en el instante t (ticks con fracción). */
    public static float giroBola(float t, int modo) {
        return t * (0.015F + 0.02F * modo);
    }

    /** Giro horizontal del foco (radianes). Modos: 0 barrido lento, 1 barrido rápido, 2 círculos, 3 locura. */
    public static float panFoco(float t, int modo, float s) {
        return switch (modo) {
            case 0 -> Mth.sin(t * 0.03F + s) * 1.2F;
            case 1 -> Mth.sin(t * 0.09F + s) * 1.3F;
            case 2 -> t * 0.07F + s;
            default -> Mth.sin(t * 0.21F + s) * 1.6F + Mth.sin(t * 0.05F) * 0.6F;
        };
    }

    /** Inclinación del foco (radianes, 0 = hacia arriba). */
    public static float tiltFoco(float t, int modo, float s) {
        return switch (modo) {
            case 0 -> 0.55F + Mth.sin(t * 0.021F + s * 1.7F) * 0.25F;
            case 1 -> 0.6F + Mth.sin(t * 0.13F + s * 1.3F) * 0.35F;
            case 2 -> 0.7F;
            default -> 0.5F + Mth.sin(t * 0.33F + s) * 0.45F;
        };
    }

    /** Cada cuántos ticks destella el estroboscopio según el modo. */
    public static int periodoEstrobo(int modo) {
        return switch (modo) {
            case 0 -> 12;
            case 1 -> 7;
            case 2 -> 4;
            default -> 2;
        };
    }

    public static boolean destello(long tiempo, int modo, BlockPos p) {
        int per = periodoEstrobo(modo);
        return Math.floorMod(tiempo + (p.asLong() & 7), per) == 0;
    }
}
