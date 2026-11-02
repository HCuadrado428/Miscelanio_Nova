package com.miscelanea.client.tortura;

import com.miscelanea.tortura.Lazo;
import com.miscelanea.tortura.LazoItem;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * Posturas del lazo (capa de playerAnimator, por encima de la de los grilletes):
 * <ul>
 *   <li>volteando: el brazo del lazo en alto describiendo círculos;</li>
 *   <li>tirando (tira y afloja): los dos brazos delante, echado hacia atrás y a tirones;</li>
 *   <li>enlazado: brazos pegados al cuerpo (el lazo los aprieta), forcejeando;</li>
 *   <li>derribado: boca abajo en el suelo, brazos por delante;</li>
 *   <li>maniatado: boca abajo, muñecas a la espalda y tobillos doblados hacia ellas (hogtie). Igual a hombros o en el
 *       caballo: LazoCliente solo lo coloca;</li>
 *   <li>cargando a alguien: el brazo sujetándolo sobre el hombro.</li>
 * </ul>
 * playerAnimator: el cuerpo gira alrededor de un punto a 0,7 bloques del suelo; x &lt; 0 lo inclina hacia delante.
 */
public class AnimLazo implements IAnimation {
    static final int NADA = 0, GIRANDO = 1, TIRANDO = 2, ENLAZADO = 3, DERRIBADO = 4, MANIATADO = 5, CARGANDO = 6;
    /** Bajada del cuerpo tumbado (para que quede en el suelo tras girarlo 90º sobre el punto de 0,7). */
    static final float BAJADA = -0.55F;
    /** Giro del cuerpo tumbado boca abajo (90º). */
    static final float TUMBADO = (float) (Math.PI / 2);

    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int modo, ultimo;

    public AnimLazo(AbstractClientPlayer p) {
        this.p = p;
    }

    private int calcular() {
        int est = TorturaCliente.lazo(p.getId());
        if (est == Lazo.MANIATADO) return MANIATADO;
        if (est == Lazo.DERRIBADO) return DERRIBADO;
        if (est == Lazo.ENLAZADO) return ENLAZADO;
        if (TorturaCliente.esCaptorTirando(p.getId())) return TIRANDO;
        if (LazoItem.usando(p)) return GIRANDO;
        if (LazoCliente.cargando(p)) return CARGANDO;
        return NADA;
    }

    @Override
    public void tick() {
        pesoO = peso;
        modo = calcular();
        if (modo != NADA) ultimo = modo;
        float vel = modo == DERRIBADO || ultimo == DERRIBADO ? 0.34F : 0.25F;
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
        boolean tumbado = m == DERRIBADO || m == MANIATADO;
        if (tipo == TransformType.POSITION) {
            if (tumbado && parte.equals("body")) return new Vec3f(v.getX(), v.getY() + BAJADA * k, v.getZ());
            return v;
        }
        if (tipo != TransformType.ROTATION) return v;
        float t = p.tickCount + td;
        switch (m) {
            case GIRANDO: {
                boolean der = LazoCliente.brazoDelLazo(p) == HumanoidArm.RIGHT;
                float a = LazoCliente.anguloGiro(p, td);
                String brazo = der ? "rightArm" : "leftArm", otro = der ? "leftArm" : "rightArm";
                float s = der ? 1 : -1;
                // en alto, la mano trazando el círculo (z > 0 abre el brazo derecho hacia fuera)
                if (parte.equals(brazo)) return mezclar(k, v, -2.75F + Mth.cos(a) * 0.16F, s * Mth.sin(a) * 0.12F, s * (0.22F + Mth.sin(a) * 0.14F));
                if (parte.equals(otro)) return mezclar(k, v, -0.45F, s * 0.25F, 0F);
                return v;
            }
            case TIRANDO: {
                boolean fuerte = LazoItem.usando(p);
                float tiron = fuerte ? Math.max(0F, Mth.sin(t * 0.55F)) : 0F;
                float vib = Mth.sin(t * 2.3F) * 0.03F;
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -1.35F + tiron * 0.4F + vib, -0.25F, 0F);
                    case "leftArm" -> mezclar(k, v, -1.35F + tiron * 0.4F - vib, 0.35F, 0F);
                    case "body" -> mezclar(k, v, 0.16F + tiron * 0.1F, v.getY(), 0F);
                    case "rightLeg" -> mezclar(k, v, -0.35F, v.getY(), 0.05F);
                    case "leftLeg" -> mezclar(k, v, 0.28F, v.getY(), -0.05F);
                    default -> v;
                };
            }
            case ENLAZADO: {
                float f = Mth.sin(t * 0.9F) * 0.18F, g = Mth.sin(t * 1.3F + 1F) * 0.1F;
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, f, 0F, 0.07F);
                    case "leftArm" -> mezclar(k, v, -f, 0F, -0.07F);
                    case "body" -> mezclar(k, v, 0.12F + g * 0.5F, g, 0F);
                    default -> v;
                };
            }
            case DERRIBADO:
                return switch (parte) {
                    case "body" -> mezclar(k, v, -TUMBADO, 0F, 0F);
                    case "head" -> mezclar(k, v, -0.95F, v.getY() * 0.3F, 0F);
                    case "rightArm" -> mezclar(k, v, -2.85F, 0F, 0.3F);
                    case "leftArm" -> mezclar(k, v, -2.85F, 0F, -0.3F);
                    case "rightLeg" -> mezclar(k, v, 0.05F, 0F, 0.12F);
                    case "leftLeg" -> mezclar(k, v, 0.05F, 0F, -0.12F);
                    default -> v;
                };
            case MANIATADO: {
                // forcejeo leve de vez en cuando
                float f = Mth.sin(t * 0.7F) * Math.max(0F, Mth.sin(t * 0.11F)) * 0.08F;
                return switch (parte) {
                    case "body" -> mezclar(k, v, -TUMBADO, 0F, f * 0.5F);
                    case "head" -> mezclar(k, v, -0.9F + f, v.getY() * 0.3F, 0F);
                    // muñecas cruzadas a la espalda (x > 0: hacia atrás; z < 0 cierra el brazo derecho)
                    case "rightArm" -> mezclar(k, v, 0.62F + f, 0F, -0.32F);
                    case "leftArm" -> mezclar(k, v, 0.62F - f, 0F, 0.32F);
                    // tobillos doblados hacia atrás, hacia las muñecas
                    case "rightLeg" -> mezclar(k, v, 1.35F - f, 0F, -0.08F);
                    case "leftLeg" -> mezclar(k, v, 1.35F + f, 0F, 0.08F);
                    default -> v;
                };
            }
            case CARGANDO:
                return switch (parte) {
                    case "rightArm" -> mezclar(k, v, -2.7F, 0F, -0.12F);
                    case "leftArm" -> mezclar(k, v, Mth.lerp(0.5F, v.getX(), -0.3F), v.getY(), v.getZ());
                    default -> v;
                };
            default:
                return v;
        }
    }
}
