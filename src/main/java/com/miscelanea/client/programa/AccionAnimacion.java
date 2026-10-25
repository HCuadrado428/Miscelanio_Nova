package com.miscelanea.client.programa;

import com.miscelanea.programa.Acciones;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Poses procedurales (playerAnimator) de las interacciones de la prueba. Entran y salen mezclándose en 4 ticks.
 * Ángulos en radianes: x negativo levanta el brazo hacia delante; en "body", x negativo inclina hacia delante.
 */
public class AccionAnimacion implements IAnimation {
    private final AbstractClientPlayer player;
    private ClientAcciones.Estado e;
    private float peso;
    private final Map<String, Vec3f> rot = new HashMap<>();
    private Vec3f cuerpo, cabeza;

    public AccionAnimacion(AbstractClientPlayer p) {
        this.player = p;
    }

    @Override
    public boolean isActive() {
        e = ClientAcciones.de(player.getId());
        return e != null;
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    @Override
    public void setupAnim(float d) {
        rot.clear();
        cuerpo = null;
        cabeza = null;
        if (e == null) return;
        float t = ClientAcciones.ahora(d);
        float dt = t - e.inicio;
        peso = suave(dt / 4.0F);
        if (e.terminado >= 0) peso = Math.min(peso, 1 - suave((t - e.terminado) / 5.0F));
        float s = Mth.sin(t * 0.9F), c = Mth.cos(t * 0.9F);
        switch (e.accion) {
            case REGISTRAR -> {   // agachado sobre el cuerpo, rebuscando con las dos manos
                cuerpo = v(-0.55F, 0, 0);
                cabeza = v(0.45F, 0, 0);
                rot.put("rightArm", v(-1.15F + s * 0.25F, -0.2F, 0.1F));
                rot.put("leftArm", v(-1.15F - s * 0.25F, 0.2F, -0.1F));
                rot.put("rightLeg", v(-0.5F, 0, 0.1F));
                rot.put("leftLeg", v(0.35F, 0, -0.1F));
            }
            case EMPUJAR -> {     // manos en las asas, inclinado empujando
                float paso = Mth.sin(t * 0.6F) * 0.05F;
                cuerpo = v(-0.3F, 0, 0);
                rot.put("rightArm", v(-1.35F + paso, -0.25F, 0));
                rot.put("leftArm", v(-1.35F - paso, 0.25F, 0));
            }
            case MANIVELA -> {    // vueltas a la manivela con la derecha, la izquierda apoyada
                cuerpo = v(-0.2F + c * 0.04F, 0, 0);
                rot.put("rightArm", v(-1.1F + s * 0.45F, -0.1F, 0.15F + c * 0.3F));
                rot.put("leftArm", v(-0.9F, 0.35F, -0.1F));
            }
            case PALANCA -> {     // tirando de la palanca con las dos manos; tiembla por la corriente
                float j = Mth.sin(t * 7.0F) * 0.04F;
                cuerpo = v(0.18F + j, 0, j);
                rot.put("rightArm", v(-1.25F + j, -0.3F, 0));
                rot.put("leftArm", v(-1.25F - j, 0.3F, 0));
                rot.put("rightLeg", v(-0.35F, 0, 0));
                rot.put("leftLeg", v(0.25F, 0, 0));
            }
            case GANZUA -> {      // manos juntas en la cerradura, girando la ganzúa
                cabeza = v(0.35F, 0, 0);
                cuerpo = v(-0.25F, 0, 0);
                rot.put("rightArm", v(-1.2F, -0.45F, Mth.sin(t * 2.3F) * 0.08F));
                rot.put("leftArm", v(-1.15F, 0.45F, -0.05F));
            }
            case EMBESTIR -> {    // golpe con el hombro
                float g = Mth.sin(Math.min(1, dt / 6.0F) * (float) Math.PI);
                cuerpo = v(-0.25F * g, 0.7F, 0.1F * g);
                rot.put("rightArm", v(-0.4F, 0.2F, 0.5F));
                rot.put("leftArm", v(-1.3F, 0.6F, -0.2F));
            }
            case REANIMAR -> {    // de rodillas, presionando el pecho del compañero
                float p = Math.max(0, Mth.sin(t * 1.4F));
                cuerpo = v(-0.75F - p * 0.12F, 0, 0);
                cabeza = v(0.4F, 0, 0);
                rot.put("rightArm", v(-0.95F - p * 0.2F, -0.15F, 0));
                rot.put("leftArm", v(-0.95F - p * 0.2F, 0.15F, 0));
                rot.put("rightLeg", v(-1.3F, 0, 0.1F));
                rot.put("leftLeg", v(-0.2F, 0, -0.1F));
            }
            case CAIDO -> {       // arrastrándose (con la pose de nadar): brazos alternando hacia delante
                rot.put("rightArm", v(-2.6F + s * 0.5F, -0.2F, 0.3F));
                rot.put("leftArm", v(-2.6F - s * 0.5F, 0.2F, -0.3F));
                rot.put("rightLeg", v(0.1F + s * 0.1F, 0, 0.1F));
                rot.put("leftLeg", v(0.1F - s * 0.1F, 0, -0.1F));
            }
            case INYECTAR -> {    // se pincha en el antebrazo izquierdo
                float k = suave(dt / 10.0F);
                cabeza = v(0.5F, 0.2F, 0);
                rot.put("leftArm", v(-1.0F, 0.6F, 0));
                rot.put("rightArm", v(-1.0F - 0.25F * k, -0.7F + 0.25F * k, 0));
            }
            case LANZAR -> {      // brazo atrás y lanzamiento
                float k = Math.min(1, dt / 8.0F);
                float x = k < 0.4F ? Mth.lerp(k / 0.4F, -1.0F, -2.9F) : Mth.lerp((k - 0.4F) / 0.6F, -2.9F, -0.6F);
                cuerpo = v(k < 0.4F ? 0.1F : -0.15F, 0, 0);
                rot.put("rightArm", v(x, -0.1F, 0.1F));
                rot.put("leftArm", v(-0.6F, 0.3F, 0));
            }
            default -> {}
        }
    }

    private static Vec3f v(float x, float y, float z) {
        return new Vec3f(x, y, z);
    }

    private static Vec3f lerp(float k, Vec3f a, Vec3f b) {
        return new Vec3f(Mth.lerp(k, a.getX(), b.getX()), Mth.lerp(k, a.getY(), b.getY()), Mth.lerp(k, a.getZ(), b.getZ()));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String part, @NotNull TransformType type, float d, @NotNull Vec3f v0) {
        if (type != TransformType.ROTATION) return v0;
        if (part.equals("head") && cabeza != null) return v0.add(cabeza.scale(peso));
        if (part.equals("body") && cuerpo != null) return cuerpo.scale(peso);
        Vec3f r = rot.get(part);
        return r != null ? lerp(peso, v0, r) : v0;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float d) {
        return e != null && e.accion != Acciones.Accion.CAIDO ? FirstPersonMode.THIRD_PERSON_MODEL : FirstPersonMode.NONE;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float d) {
        return new FirstPersonConfiguration(true, true, true, false);
    }
}
