package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.DoncellaBlockEntity;
import com.miscelanea.tortura.aparatos.PotroBlockEntity;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Posturas de la víctima en los aparatos (capa de playerAnimator, por encima del lazo y los grilletes):
 * <ul>
 *   <li>doncella: de pie, encogida, con los brazos pegados al cuerpo; tiembla más cuanto más cerradas las puertas;</li>
 *   <li>potro: brazos estirados por encima de la cabeza y piernas juntas; con la tensión, los hombros y las caderas se
 *       separan (el cuerpo, además, se alarga al dibujarlo tumbado en AparatosCliente);</li>
 *   <li>silla: sentado (lo hace el juego) con los antebrazos en los reposabrazos; durante la descarga, convulsiones.</li>
 * </ul>
 */
public class AnimAparato implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int tipo, ultimo;

    public AnimAparato(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        tipo = AparatosVictimas.tipo(p.getId());
        if (tipo != 0) ultimo = tipo;
        peso = Mth.clamp(peso + (tipo != 0 ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || AparatosVictimas.tipo(p.getId()) != 0;
    }

    @Override
    public void setupAnim(float td) {}

    private static Vec3f mezclar(float k, Vec3f v, float x, float y, float z) {
        return new Vec3f(Mth.lerp(k, v.getX(), x), Mth.lerp(k, v.getY(), y), Mth.lerp(k, v.getZ(), z));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tt, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        int m = tipo != 0 ? tipo : ultimo;
        if (k <= 0 || m == 0) return v;
        float t = p.tickCount + td;
        AparatosVictimas.Entrada en = AparatosVictimas.de(p.getId());
        switch (m) {
            case AparatosVictimas.DONCELLA: {
                if (tt != TransformType.ROTATION) return v;
                float cierre = en != null && p.level().getBlockEntity(en.pos()) instanceof DoncellaBlockEntity d ? Mth.lerp(td, d.visO, d.vis) : 0F;
                float tiembla = (0.02F + cierre * 0.09F) * Mth.sin(t * 2.3F) + (p.hurtTime > 0 ? Mth.sin(t * 5.1F) * 0.12F : 0F);
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -0.25F + tiembla, 0.15F, 0.05F);
                    case "leftArm" -> mezclar(k, v, -0.25F - tiembla, -0.15F, -0.05F);
                    case "body" -> mezclar(k, v, 0.06F + tiembla * 0.3F, 0F, tiembla * 0.2F);
                    case "head" -> mezclar(k, v, v.getX() * 0.5F + 0.15F, v.getY() * 0.6F, tiembla);
                    case "rightLeg" -> mezclar(k, v, 0F, 0F, -0.02F);
                    case "leftLeg" -> mezclar(k, v, 0F, 0F, 0.02F);
                    default -> v;
                };
            }
            case AparatosVictimas.POTRO: {
                float ten = en != null && p.level().getBlockEntity(en.pos()) instanceof PotroBlockEntity pb ? Mth.lerp(td, pb.tensionVisO, pb.tensionVis) : 0F;
                float retuerce = ten > 0.4F ? Mth.sin(t * 0.9F) * Math.max(0F, Mth.sin(t * 0.13F)) * 0.08F * ten : 0F;
                if (tt == TransformType.POSITION) {
                    // hombros hacia la cabecera y caderas hacia los pies (en píxeles del modelo)
                    return switch (parte) {
                        case "rightArm", "leftArm" -> new Vec3f(v.getX(), v.getY() - 2.6F * ten * k, v.getZ());
                        case "rightLeg", "leftLeg" -> new Vec3f(v.getX(), v.getY() + 1.8F * ten * k, v.getZ());
                        case "head" -> new Vec3f(v.getX(), v.getY() - 1.2F * ten * k, v.getZ());
                        default -> v;
                    };
                }
                if (tt != TransformType.ROTATION) return v;
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -2.98F + retuerce, 0F, 0.16F - ten * 0.08F);
                    case "leftArm" -> mezclar(k, v, -2.98F - retuerce, 0F, -0.16F + ten * 0.08F);
                    case "rightLeg" -> mezclar(k, v, retuerce * 0.5F, 0F, 0.04F);
                    case "leftLeg" -> mezclar(k, v, -retuerce * 0.5F, 0F, -0.04F);
                    case "head" -> mezclar(k, v, Mth.clamp(v.getX(), -0.6F, 0.4F) * 0.5F, v.getY() * 0.5F, retuerce);
                    case "body" -> mezclar(k, v, 0F, 0F, retuerce * 0.3F);
                    default -> v;
                };
            }
            case AparatosVictimas.SILLA: {
                if (tt != TransformType.ROTATION) return v;
                float d = AparatosCliente.descarga(p.getId(), td);
                float a = d * (0.18F + AparatosCliente.voltajeDescarga(p.getId()) * 0.03F);
                float j1 = Mth.sin(t * 7.3F) * a, j2 = Mth.sin(t * 9.1F + 1.3F) * a, j3 = Mth.sin(t * 6.1F + 2.1F) * a;
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -0.62F + j1, 0.1F, 0.08F + j2 * 0.5F);
                    case "leftArm" -> mezclar(k, v, -0.62F + j2, -0.1F, -0.08F + j3 * 0.5F);
                    case "head" -> mezclar(k, v, d > 0 ? -0.45F * Math.min(1F, d * 3) + j3 : v.getX(), d > 0 ? j1 * 0.5F : v.getY(), j2 * 0.6F);
                    case "body" -> mezclar(k, v, j1 * 0.25F, 0F, j3 * 0.25F);
                    case "rightLeg" -> d > 0 ? new Vec3f(v.getX() + j2 * 0.4F, v.getY(), v.getZ() + j1 * 0.2F) : v;
                    case "leftLeg" -> d > 0 ? new Vec3f(v.getX() + j3 * 0.4F, v.getY(), v.getZ() + j2 * 0.2F) : v;
                    default -> v;
                };
            }
            default:
                return v;
        }
    }
}
