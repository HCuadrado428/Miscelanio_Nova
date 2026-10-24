package com.miscelanea.client.porra;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Animación procedural del agarre (playerAnimator), en primera y tercera persona.
 * Los ángulos están calculados con IK para la distancia real (víctima a 0,7 bloques y levantada 0,2):
 * la mano del agresor llega al cuello y la porra apunta a la víctima.
 * Al empezar y al soltar se mezcla suavemente con la pose normal (5 ticks).
 */
public class AgarreAnimacion implements IAnimation {
    // ---- Agresor (radianes). Brazo de la mano libre: IK al cuello de la víctima
    private static final float[] AG_CUELLO = {-1.984F, 0.467F, 0.0F};
    // Brazo de la porra + giro de la porra en la mano (X)
    private static final float[] BR_REPOSO = {-1.35F, -0.45F, 0.3F};
    private static final float IT_REPOSO = -0.105F;          // porra en alto, amenazando
    private static final float[] BR_DESCARGA = {-1.532F, -0.37F, 0.0F};
    private static final float IT_DESCARGA = -2.217F;        // porra alineada con el brazo, clavada
    private static final float[] BR_ALTO = {-2.85F, -0.1F, 0.25F};
    private static final float IT_ALTO = -2.339F;            // por encima de la cabeza
    private static final float[] BR_GOLPE = {-1.382F, -0.412F, 0.0F};
    private static final float IT_GOLPE = -2.217F;

    // ---- Víctima
    private static final float[] VI_BRAZO_D = {-1.75F, -0.5F, 0.1F};   // manos al brazo que la agarra
    private static final float[] VI_BRAZO_I = {-1.75F, 0.5F, -0.1F};
    private static final float[] VI_ESPASMO_D = {-1.2F, -0.2F, 0.9F};  // brazos abiertos por la descarga
    private static final float[] VI_ESPASMO_I = {-1.2F, 0.2F, -0.9F};

    private static final float TRANSICION = 5.0F;

    private final AbstractClientPlayer player;
    private ClientAgarres.Estado estado;
    private float peso;

    private final Map<String, Vec3f> rot = new HashMap<>();
    private Vec3f cabeza;   // se suma a la rotación normal de la cabeza
    private Vec3f cuerpo;   // rotación extra de todo el cuerpo

    public AgarreAnimacion(AbstractClientPlayer player) {
        this.player = player;
    }

    @Override
    public boolean isActive() {
        estado = ClientAgarres.de(player.getId());
        return estado != null;
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float ruido(float t, float semilla) {
        return Mth.sin(t * 37.0F + semilla) * 0.6F + Mth.sin(t * 23.0F + semilla * 2.1F) * 0.4F;
    }

    private static float[] mezcla(float k, float[] a, float[] b) {
        return new float[]{Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
    }

    @Override
    public void setupAnim(float tickDelta) {
        rot.clear();
        cabeza = null;
        cuerpo = null;
        if (estado == null) return;

        float t = ClientAgarres.ahora(tickDelta);
        peso = suave((t - estado.inicio) / TRANSICION);
        if (estado.fin >= 0) peso = Math.min(peso, 1.0F - suave((t - estado.fin) / TRANSICION));

        float ds = t - estado.ultimaDescarga;
        float choque = ds < 10.0F ? 1.0F - ds / 10.0F : 0.0F;
        boolean zurdo = player.getMainArm() == HumanoidArm.LEFT;

        if (estado.rol == ClientAgarres.Rol.AGRESOR) {
            // Mano libre al cuello (respira un poco y aprieta en cada descarga)
            poner(zurdo ? "rightArm" : "leftArm",
                    new float[]{AG_CUELLO[0] + Mth.sin(t * 0.2F) * 0.03F - 0.06F * choque, AG_CUELLO[1], AG_CUELLO[2]}, zurdo);

            float[] br = {BR_REPOSO[0] + Mth.sin(t * 0.15F) * 0.05F, BR_REPOSO[1], BR_REPOSO[2]};
            float it = IT_REPOSO;
            if (estado.ejecucion >= 0) {
                float e = t - estado.ejecucion;
                if (e < 30.0F) {
                    float k = suave(e / 30.0F);
                    br = mezcla(k, BR_REPOSO, BR_ALTO);
                    br[2] += Mth.sin(e * 3.0F) * 0.04F * k;     // tiembla cargando
                    it = Mth.lerp(k, IT_REPOSO, IT_ALTO);
                } else {
                    float k = suave((e - 30.0F) / 4.0F);
                    br = mezcla(k, BR_ALTO, BR_GOLPE);
                    br[0] += Mth.sin(e * 5.0F) * 0.06F;
                    it = Mth.lerp(k, IT_ALTO, IT_GOLPE);
                }
            } else if (ds < 8.0F) {
                // Descarga: clava la porra y vuelve
                float p = Mth.sin((float) Math.PI * ds / 8.0F);
                br = mezcla(p, br, BR_DESCARGA);
                br[0] += ruido(t, 1) * 0.04F * p;
                it = Mth.lerp(p, IT_REPOSO, IT_DESCARGA);
            }
            poner(zurdo ? "leftArm" : "rightArm", br, zurdo);
            rot.put(zurdo ? "leftItem" : "rightItem", new Vec3f(it, 0.0F, 0.0F));
            cuerpo = new Vec3f(-0.08F - choque * 0.03F * ruido(t, 1), 0.0F, 0.0F); // x<0: inclinado hacia la víctima
        } else {
            boolean forcejea = t - estado.ultimoForcejeo < 6.0F;
            if (estado.ejecucion >= 0 && t - estado.ejecucion >= 30.0F) choque = 1.0F;
            float a = forcejea ? 1.0F : 0.15F;
            float j = 0.3F * choque;
            float tira = Mth.sin(t * 1.6F);

            float[] rd = {VI_BRAZO_D[0] + tira * 0.35F * a, VI_BRAZO_D[1] + tira * 0.15F * a, VI_BRAZO_D[2]};
            float[] ri = {VI_BRAZO_I[0] - tira * 0.35F * a, VI_BRAZO_I[1] - tira * 0.15F * a, VI_BRAZO_I[2]};
            rd = mezcla(choque, rd, VI_ESPASMO_D);
            ri = mezcla(choque, ri, VI_ESPASMO_I);
            rot.put("rightArm", new Vec3f(rd[0] + ruido(t, 1) * j, rd[1] + ruido(t, 2) * j, rd[2]));
            rot.put("leftArm", new Vec3f(ri[0] + ruido(t, 3) * j, ri[1] + ruido(t, 4) * j, ri[2]));

            // Piernas colgando; patalea al forcejear; se quedan rígidas con la descarga
            float vaiven = Mth.sin(t * 0.25F) * 0.08F;
            float patada = Mth.sin(t * 1.9F) * 0.6F * a;
            float pd = Mth.lerp(choque, 0.15F + vaiven + patada, 0.35F);
            float pi = Mth.lerp(choque, 0.05F - vaiven - patada, 0.35F);
            rot.put("rightLeg", new Vec3f(pd + ruido(t, 5) * j, 0.0F, Mth.lerp(choque, 0.05F, 0.12F)));
            rot.put("leftLeg", new Vec3f(pi + ruido(t, 6) * j, 0.0F, Mth.lerp(choque, -0.05F, -0.12F)));

            cabeza = new Vec3f(-0.35F - 0.35F * choque + ruido(t, 7) * j,
                    Mth.sin(t * 1.3F) * 0.25F * a + ruido(t, 8) * j, ruido(t, 9) * j * 0.5F);
            cuerpo = new Vec3f(0.1F + 0.1F * choque + ruido(t, 10) * j * 0.3F, 0.0F, ruido(t, 11) * j * 0.2F); // x>0: echada hacia atrás
        }
    }

    /** Rotación de un brazo, invirtiendo Y/Z si el jugador es zurdo. */
    private void poner(String parte, float[] r, boolean espejo) {
        rot.put(parte, espejo ? new Vec3f(r[0], -r[1], -r[2]) : new Vec3f(r[0], r[1], r[2]));
    }

    private static Vec3f lerp(float k, Vec3f a, Vec3f b) {
        return new Vec3f(Mth.lerp(k, a.getX(), b.getX()), Mth.lerp(k, a.getY(), b.getY()), Mth.lerp(k, a.getZ(), b.getZ()));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        if (type != TransformType.ROTATION) return value0;
        if (modelName.equals("head") && cabeza != null) return value0.add(cabeza.scale(peso));
        if (modelName.equals("body") && cuerpo != null) return cuerpo.scale(peso);
        Vec3f r = rot.get(modelName);
        return r != null ? lerp(peso, value0, r) : value0;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        // En primera persona se ven los brazos del modelo: tu mano agarrando, o tus manos forcejeando
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return new FirstPersonConfiguration(true, true, true, false);
    }
}
