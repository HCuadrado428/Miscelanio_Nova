package com.miscelanea.client.tortura.castigo;

import com.miscelanea.tortura.castigo.CepoEntity;
import com.miscelanea.tortura.castigo.JaulaEntity;
import com.miscelanea.client.Articulaciones;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Posturas de los castigos (capa de playerAnimator por encima de grilletes y lazo, por debajo de la ejecución):
 * <ul>
 *   <li>CEPO: doblado 70º hacia delante con la cabeza y las manos por los agujeros de la tabla. La entidad (y la cámara)
 *       va en la cabeza: el cuerpo se lleva {@link CepoEntity#ADELANTE} hacia atrás y {@link CepoEntity#BAJADA} hacia
 *       arriba para que los pies queden en el suelo detrás. Al esquivar, la cabeza se ladea.</li>
 *   <li>JAULA (cerrada): agarrado a los barrotes con los brazos en alto, meciéndose.</li>
 *   <li>BOLA: la bola de preso en brazos, delante del pecho; al lanzarla, los brazos salen hacia delante.</li>
 * </ul>
 * playerAnimator: el cuerpo ("body") gira alrededor de un punto a 0,7 bloques del suelo; x &lt; 0 lo inclina hacia
 * delante; la posición se suma antes de girar (en bloques, z &gt; 0 = hacia atrás).
 */
public class AnimCastigo implements IAnimation {
    static final int NADA = 0, CEPO = 1, JAULA = 2, BOLA = 3, LANZA = 4;
    static final float DOBLADO = 1.22F;

    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int modo, ultimo;

    public AnimCastigo(AbstractClientPlayer p) {
        this.p = p;
    }

    private int calcular() {
        if (CastigoCliente.enCepo(p)) return CEPO;
        if (p.getVehicle() instanceof JaulaEntity j && j.puerta() != JaulaEntity.ABIERTA && j.estado() == JaulaEntity.COLGANDO) return JAULA;
        if (p == Minecraft.getInstance().player && p.tickCount - CastigoCliente.lanzadoT < 8 && p.tickCount >= CastigoCliente.lanzadoT) return LANZA;
        if (CastigoCliente.llevaBolaEnBrazos(p.getId())) return BOLA;
        return NADA;
    }

    @Override
    public void tick() {
        pesoO = peso;
        modo = calcular();
        if (modo != NADA) ultimo = modo;
        // el cepo entra de golpe (si no, se vería el cuerpo flotando en la cabeza un momento)
        float vel = modo == CEPO || ultimo == CEPO ? 1F : 0.25F;
        peso = Mth.clamp(peso + (modo != NADA ? vel : -vel), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || calcular() != NADA;
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
        int m = modo != NADA ? modo : ultimo;
        if (m == NADA) return v;
        float t = p.tickCount + td;
        if (m == CEPO) {
            // en el cepo la postura es todo o nada: a medias el cuerpo quedaría fuera de sitio
            k = CastigoCliente.enCepo(p) ? 1F : k;
            float inc = CastigoCliente.inclinacion(p.getId(), td);
            if (tipo == TransformType.POSITION) {
                if (parte.equals("body")) return new Vec3f(v.getX(), v.getY() + (float) CepoEntity.BAJADA * k, v.getZ() + (float) CepoEntity.ADELANTE * k);
                if (parte.equals("head")) return new Vec3f(v.getX() - inc * 1.6F * k, v.getY(), v.getZ());
                return v;
            }
            // brazos rectos por los agujeros de la tabla; las rodillas, un pelín flojas
            if (tipo == TransformType.BEND) return Articulaciones.fijar(parte, v, Articulaciones.pierna(parte) ? 0.12F : 0.05F, k);
            if (tipo != TransformType.ROTATION) return v;
            float resp = Mth.sin(t * 0.12F) * 0.02F;   // respira, muy poco
            return switch (parte) {
                case "body" -> mezclar(k, v, -DOBLADO + resp, 0F, 0F);
                case "head" -> mezclar(k, v, -DOBLADO - resp, v.getY() * 0.35F, -inc * 0.5F);
                case "rightArm" -> mezclar(k, v, -2.79F - resp, 0F, -0.75F);
                case "leftArm" -> mezclar(k, v, -2.79F - resp, 0F, 0.75F);
                case "rightLeg" -> mezclar(k, v, -DOBLADO - resp, 0F, 0.04F);
                case "leftLeg" -> mezclar(k, v, -DOBLADO - resp, 0F, -0.04F);
                default -> v;
            };
        }
        if (tipo == TransformType.BEND) {
            if (!Articulaciones.brazo(parte)) return v;
            float f = switch (m) {
                case JAULA -> 0.5F + Mth.sin(t * 0.35F) * 0.25F;        // colgado de los barrotes: se aúpa y se deja caer
                case BOLA -> 0.45F;                                     // la bola abrazada contra el pecho
                case LANZA -> Mth.lerp(Mth.clamp((p.tickCount - CastigoCliente.lanzadoT + td) / 4F, 0F, 1F), 0.9F, 0.05F);
                default -> 0F;
            };
            return Articulaciones.fijar(parte, v, f, k);
        }
        if (tipo != TransformType.ROTATION) return v;
        switch (m) {
            case JAULA: {
                float f = Mth.sin(t * 0.35F) * 0.06F;
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -2.85F + f, 0F, -0.45F);
                    case "leftArm" -> mezclar(k, v, -2.85F - f, 0F, 0.45F);
                    default -> v;
                };
            }
            case BOLA:
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -1.0F + 0.25F * Articulaciones.K, -0.42F, 0F);
                    case "leftArm" -> mezclar(k, v, -1.0F + 0.25F * Articulaciones.K, 0.42F, 0F);
                    case "body" -> mezclar(k, v, 0.08F, v.getY(), v.getZ());
                    default -> v;
                };
            case LANZA: {
                float s = Mth.clamp((p.tickCount - CastigoCliente.lanzadoT + td) / 4F, 0F, 1F);
                float x = Mth.lerp(s, -2.4F, -1.3F);
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, x, -0.25F, 0F);
                    case "leftArm" -> mezclar(k, v, x, 0.25F, 0F);
                    case "body" -> mezclar(k, v, Mth.lerp(s, 0.12F, -0.15F), v.getY(), v.getZ());
                    default -> v;
                };
            }
            default:
                return v;
        }
    }
}
