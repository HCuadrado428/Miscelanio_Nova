package com.miscelanea.client.tortura;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Ejecución de la trampa de oso (capa de playerAnimator por encima de todas las de la sala de juegos):
 * <ul>
 *   <li>forcejeo desesperado (0 .. {@link EjecucionCliente#ESTALLIDO}): las dos manos agarran la trampa por los lados
 *       y tiran hacia fuera a tirones, la cabeza y el torso se sacuden de lado a lado, las rodillas ceden hasta quedar
 *       de rodillas y, a ratos, una mano araña las mandíbulas;</li>
 *   <li>al reventar: un latigazo hacia atrás, los brazos salen despedidos y caen sin fuerza, el cuerpo se dobla;</li>
 *   <li>al morir se apaga en medio segundo (manda la animación de muerte normal).</li>
 * </ul>
 * playerAnimator: "body" mueve/gira el cuerpo entero (alrededor de un punto a 0,7 bloques; x &lt; 0 lo inclina hacia
 * delante), "torso" es la parte del modelo. En los brazos levantados, z &lt; 0 abre el brazo derecho hacia fuera.
 */
public class AnimEjecucion implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int muerto;

    public AnimEjecucion(AbstractClientPlayer p) {
        this.p = p;
    }

    private boolean activa() {
        return EjecucionCliente.activa(p.getId());
    }

    @Override
    public void tick() {
        pesoO = peso;
        boolean act = activa();
        if (act && p.isDeadOrDying()) muerto++;
        else muerto = 0;
        float objetivo = !act ? 0F : Math.max(0F, 1F - muerto / 10F);
        peso = peso < objetivo ? Math.min(objetivo, peso + 0.34F) : Math.max(objetivo, peso - 0.2F);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || activa();
    }

    @Override
    public void setupAnim(float td) {}

    private static Vec3f mezclar(float k, Vec3f v, float x, float y, float z) {
        return new Vec3f(Mth.lerp(k, v.getX(), x), Mth.lerp(k, v.getY(), y), Mth.lerp(k, v.getZ(), z));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        EjecucionCliente.Ejec ej = EjecucionCliente.de(p.getId());
        if (ej == null) return v;
        float t = EjecucionCliente.tiempo(ej, td);
        float rod = EjecucionCliente.rodillas(t);
        boolean revento = t >= EjecucionCliente.ESTALLIDO;
        // sacudidas cada vez más fuertes
        float a = 0.6F + 0.4F * Math.min(1F, t / EjecucionCliente.ESTALLIDO);
        float f1 = Mth.sin(t * 1.9F), f2 = Mth.sin(t * 2.7F + 1F), f3 = Mth.sin(t * 3.7F + 2F), f4 = Mth.sin(t * 5.3F + 0.5F);
        float s = t - EjecucionCliente.ESTALLIDO;
        float sacudida = revento ? (float) Math.exp(-s * 0.4F) : 0F;

        if (tipo == TransformType.POSITION) {
            if (!parte.equals("body")) return v;
            float bote = revento ? 0F : 0.025F * f3 * a;
            float lado = revento ? 0F : 0.03F * f1 * a;
            return new Vec3f(v.getX() + lado * k, v.getY() + (-EjecucionCliente.BAJADA_RODILLAS * rod + bote) * k, v.getZ());
        }
        if (tipo != TransformType.ROTATION) return v;

        if (!revento) {
            // tirones hacia fuera de las dos manos y, a ratos, la izquierda araña las mandíbulas
            float tiron = Mth.square(Math.max(0F, Mth.sin(t * 1.3F)));
            float arana = t < 16F ? 0F : Mth.clamp((Mth.sin(t * 0.3F) + 0.2F) * 2F, 0F, 1F);
            switch (parte) {
                case "body":
                    return mezclar(k, v, v.getX() - 0.12F * rod + 0.05F * f2 * a, v.getY() + 0.1F * f1 * a, v.getZ() + 0.13F * f1 * a);
                case "torso":
                    return mezclar(k, v, 0.18F * rod + 0.1F * f2 * a, 0.22F * f1 * a, 0.06F * f3 * a);
                case "head":
                    return mezclar(k, v, 0.2F + 0.15F * f2 * a, 0.5F * f1 * a, 0.22F * f3 * a);
                case "rightArm":
                    return mezclar(k, v, -2.8F + 0.12F * f2 * a, 0.05F * f4, -0.08F - 0.25F * tiron * a);
                case "leftArm": {
                    float gx = -2.8F - 0.12F * f2 * a, gy = -0.05F * f4, gz = 0.08F + 0.25F * tiron * a;
                    // arañando: la mano por delante de la cara, rascando arriba y abajo
                    float cx = -2.25F + 0.3F * f4, cy = 0.45F + 0.08F * f3, cz = 0.1F;
                    return mezclar(k, v, Mth.lerp(arana, gx, cx), Mth.lerp(arana, gy, cy), Mth.lerp(arana, gz, cz));
                }
                case "rightLeg":
                    return mezclar(k, v, 1.08F * rod + 0.04F * f3, 0F, 0.08F * rod);
                case "leftLeg":
                    return mezclar(k, v, 1.08F * rod - 0.04F * f3, 0F, -0.08F * rod);
                default:
                    return v;
            }
        }
        // reventada: latigazo hacia atrás, brazos despedidos que caen, cuerpo doblado hacia delante
        float caer = 1F - sacudida * sacudida;
        float tic = Mth.sin(s * 7F) * 0.1F * sacudida;
        switch (parte) {
            case "body":
                return mezclar(k, v, v.getX() - 0.12F * rod + 0.3F * sacudida - 0.18F * (1F - sacudida), v.getY(), v.getZ() + tic * 0.5F);
            case "torso":
                return mezclar(k, v, 0.45F * (1F - sacudida) - 0.1F * sacudida, 0F, tic);
            case "head":
                // sin cabeza: el muñón sigue al torso (los dos giran sobre el mismo punto del cuello)
                return mezclar(k, v, 0.45F * (1F - sacudida) - 0.1F * sacudida, 0F, tic);
            case "rightArm":
                return mezclar(k, v, Mth.lerp(caer, -2.8F, -0.25F), 0F, 0.1F + 0.6F * sacudida + tic);
            case "leftArm":
                return mezclar(k, v, Mth.lerp(caer, -2.8F, -0.3F), 0F, -0.1F - 0.6F * sacudida - tic);
            case "rightLeg":
                return mezclar(k, v, 1.08F * rod, 0F, 0.08F * rod);
            case "leftLeg":
                return mezclar(k, v, 1.08F * rod, 0F, -0.08F * rod);
            default:
                return v;
        }
    }
}
