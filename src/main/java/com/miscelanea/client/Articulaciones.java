package com.miscelanea.client;

import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.ModList;

/**
 * Codos y rodillas que se doblan en las animaciones de playerAnimator.
 * <p>
 * playerAnimator ya trae la «flexión» de brazos y piernas (TransformType.BEND: {eje, ángulo}), pero solo la dibuja si
 * está instalado <b>bendy-lib</b> (modid "bendylib", de KosmX): dobla cada brazo/pierna por la mitad de la pieza (el
 * codo 4 px bajo el pivote del hombro, la rodilla 6 px bajo el de la cadera) y gira la mitad de abajo. Sin bendy-lib la flexión no hace nada y las piezas siguen rectas: por eso los retoques de la pose que solo
 * tienen sentido con la rodilla doblada (subir el cuerpo al arrodillarse, echar el muslo adelante en una sentadilla...)
 * se multiplican por {@link #K}.
 * <p>
 * Convención de todo el mod: la flexión va en radianes y es POSITIVA en el sentido natural (codo: el antebrazo hacia
 * delante; rodilla: la espinilla hacia atrás), con eje 0 (se dobla en el plano delante-atrás de la pieza). En
 * playerAnimator/bendy-lib el eje 0 gira la mitad de abajo alrededor de +X: positivo = hacia atrás (+Z del modelo), así
 * que el brazo lleva el signo cambiado. Rangos: codo 0..145º, rodilla 0..150º.
 */
public final class Articulaciones {
    private Articulaciones() {}

    /** ¿Está bendy-lib? (playerAnimator lo detecta por su cuenta; esto es solo para los retoques que dependen de él). */
    public static final boolean ACTIVO = ModList.get() != null && ModList.get().isLoaded("bendylib");
    /** 1 con bendy-lib, 0 sin él: peso de los retoques de pose que solo valen con la pieza doblada. */
    public static final float K = ACTIVO ? 1F : 0F;

    public static final float CODO_MAX = 145F * Mth.DEG_TO_RAD, RODILLA_MAX = 150F * Mth.DEG_TO_RAD;

    public static boolean brazo(String parte) {
        return parte.equals("rightArm") || parte.equals("leftArm");
    }

    public static boolean pierna(String parte) {
        return parte.equals("rightLeg") || parte.equals("leftLeg");
    }

    /** Flexión natural (rad) que lleva el valor de flexión de playerAnimator v de esa pieza. */
    public static float flexion(String parte, Vec3f v) {
        return brazo(parte) ? -v.getY() : v.getY();
    }

    private static Vec3f valor(String parte, float eje, float flex) {
        float max = brazo(parte) ? CODO_MAX : RODILLA_MAX;
        flex = Mth.clamp(flex, 0F, max);
        return new Vec3f(eje, brazo(parte) ? -flex : flex, 0F);
    }

    /** Suma una flexión (rad, positiva = natural) con peso k a la que ya traen las capas de abajo. */
    public static Vec3f sumar(String parte, Vec3f v, float flex, float k) {
        if (!brazo(parte) && !pierna(parte)) return v;
        if (flex == 0F || k == 0F) return v;
        return valor(parte, v.getX(), flexion(parte, v) + flex * k);
    }

    /** Lleva la flexión hacia el valor absoluto flex con peso k (las capas que mandan sobre la pose entera). */
    public static Vec3f fijar(String parte, Vec3f v, float flex, float k) {
        if (!brazo(parte) && !pierna(parte)) return v;
        if (k <= 0F) return v;
        return valor(parte, Mth.lerp(k, v.getX(), 0F), Mth.lerp(k, flexion(parte, v), flex));
    }
}
