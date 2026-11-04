package com.miscelanea.client.camara;

import com.miscelanea.Miscelanea;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sacudida de cámara "de cine", reutilizable:
 * <ul>
 *   <li>TRAUMA (0..1): cada golpe suma; baja solo, en línea recta, con el tiempo real. La sacudida es trauma² (los
 *       golpes pequeños apenas se notan, los grandes sí) por un ruido suave ({@link Ruido}) en guiñada, cabeceo y
 *       alabeo, cada uno con su semilla: nada de saltos aleatorios por fotograma.</li>
 *   <li>PATADA: un empujón con dirección (la cabeza que se va hacia atrás con el susto, el retroceso de una pisada):
 *       sube en ~50 ms y vuelve con un rebote amortiguado.</li>
 * </ul>
 * Todo va con {@link Reloj} (segundos reales, parado en pausa), así que es igual a 30 que a 240 FPS. Quien la usa la
 * aplica en su {@code ComputeCameraAngles} con la escala que quiera (normalmente {@link #escalaPantalla()}, la opción
 * de accesibilidad "Efectos de distorsión"). {@link #GLOBAL} la aplica esta clase sola, para golpes sueltos (estallidos).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Sacudida {
    private static int semillas = 0x5ACD;

    /** Sacudida compartida para golpes sueltos (estallido del collar...); la aplica esta clase. */
    public static final Sacudida GLOBAL = new Sacudida(2.5F, 3F, 4F, 11F, 1.1F);

    private final float maxYaw, maxPitch, maxRoll, frec, caida;
    private final int semilla;
    private float trauma;
    private double tTrauma;
    // patada
    private float pYaw, pPitch, pRoll;
    private double tPatada = -100;

    /**
     * @param maxYaw   grados de guiñada con trauma 1
     * @param maxPitch grados de cabeceo con trauma 1
     * @param maxRoll  grados de alabeo con trauma 1
     * @param frec     ondulaciones por segundo del ruido (8-14 es un golpe; 1-3 un balanceo)
     * @param caida    trauma que se pierde por segundo
     */
    public Sacudida(float maxYaw, float maxPitch, float maxRoll, float frec, float caida) {
        this.maxYaw = maxYaw;
        this.maxPitch = maxPitch;
        this.maxRoll = maxRoll;
        this.frec = frec;
        this.caida = caida;
        this.semilla = semillas++ * 7919;
    }

    private void actualizar() {
        double ahora = Reloj.t();
        if (ahora > tTrauma) trauma = Math.max(0F, trauma - caida * (float) (ahora - tTrauma));
        tTrauma = ahora;
    }

    /** Suma trauma (0..1). */
    public void golpe(float cantidad) {
        actualizar();
        trauma = Mth.clamp(trauma + cantidad, 0F, 1F);
    }

    /** Sube el trauma hasta al menos este valor (para temblores sostenidos sin acumular). */
    public void minimo(float valor) {
        actualizar();
        trauma = Math.max(trauma, Mth.clamp(valor, 0F, 1F));
    }

    /** Empujón con dirección, en grados (pitch negativo = la cabeza hacia arriba/atrás). */
    public void patada(float yaw, float pitch, float roll) {
        double ahora = Reloj.t();
        float resto = forma(ahora - tPatada);
        pYaw = pYaw * resto + yaw;
        pPitch = pPitch * resto + pitch;
        pRoll = pRoll * resto + roll;
        tPatada = ahora;
    }

    public float trauma() {
        actualizar();
        return trauma;
    }

    public boolean quieta() {
        return trauma() <= 0.001F && Reloj.t() - tPatada > 1.5;
    }

    public void parar() {
        trauma = 0F;
        tPatada = -100;
        pYaw = pPitch = pRoll = 0F;
    }

    /** Forma de la patada: 0 → 1 en ~50 ms, y vuelta con un rebote pequeño. */
    private static float forma(double dt) {
        if (dt < 0 || dt > 1.5) return 0F;
        return (float) ((1.0 - Math.exp(-dt * 38.0)) * Math.exp(-dt * 5.5) * Math.cos(dt * 6.0));
    }

    /** Aplica la sacudida (escala 0..1, normalmente {@link #escalaPantalla()}). */
    public void aplicar(ViewportEvent.ComputeCameraAngles e, float escala) {
        if (escala <= 0F) return;
        actualizar();
        double t = Reloj.t();
        float s = trauma * trauma * escala;
        if (s > 0.0001F) {
            double x = t * frec;
            e.setYaw(e.getYaw() + maxYaw * s * Ruido.fbm(semilla, x, 2));
            e.setPitch(e.getPitch() + maxPitch * s * Ruido.fbm(semilla + 101, x, 2));
            e.setRoll(e.getRoll() + maxRoll * s * Ruido.fbm(semilla + 202, x, 2));
        }
        float f = forma(t - tPatada) * escala;
        if (f != 0F) {
            e.setYaw(e.getYaw() + pYaw * f);
            e.setPitch(e.getPitch() + pPitch * f);
            e.setRoll(e.getRoll() + pRoll * f);
        }
    }

    /**
     * Temblor sostenido con ruido suave (para efectos que ya calculan su propia fuerza): grados por eje a fuerza 1,
     * {@code frec} ondulaciones por segundo.
     */
    public static void temblor(ViewportEvent.ComputeCameraAngles e, int semilla, float frec, float fuerza, float yaw, float pitch, float roll) {
        if (fuerza <= 0F) return;
        double x = Reloj.t() * frec;
        e.setYaw(e.getYaw() + yaw * fuerza * Ruido.fbm(semilla, x, 2));
        e.setPitch(e.getPitch() + pitch * fuerza * Ruido.fbm(semilla + 31, x, 2));
        e.setRoll(e.getRoll() + roll * fuerza * Ruido.fbm(semilla + 67, x, 2));
    }

    /** Opción de accesibilidad "Efectos de distorsión" (0..1). */
    public static float escalaPantalla() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1F : mc.options.screenEffectScale().get().floatValue();
    }

    /** Opción de accesibilidad "Efectos de campo de visión" (0..1). */
    public static float escalaFov() {
        Minecraft mc = Minecraft.getInstance();
        return mc.options == null ? 1F : mc.options.fovEffectScale().get().floatValue();
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        GLOBAL.aplicar(e, escalaPantalla());
    }
}
