package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.AparatosVictimas;
import com.miscelanea.tortura.aparatos.GanchoBlockEntity;
import com.miscelanea.tortura.aparatos.Herramienta;
import com.miscelanea.tortura.aparatos.SillaInterrogatorioBlockEntity;
import com.miscelanea.client.Articulaciones;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Posturas de la silla de interrogatorio y del colgado (capa de playerAnimator por encima de {@link AnimAparato}, que
 * para estos tipos no hace nada):
 * <ul>
 *   <li>silla: sentado con los antebrazos atados a los reposabrazos; respira más deprisa cuanto más pulsaciones; con las
 *       pinzas, convulsiones; con cada herramienta, un respingo (la llave le dobla, los alicates le echan la cabeza atrás);
 *       con el corazón parado, desplomado (cabeza caída, brazos muertos);</li>
 *   <li>colgado (el cuerpo ya va boca abajo, lo voltea InterrogaCliente): los brazos "caen" hacia el suelo, por encima de
 *       la cabeza, balanceándose; piernas juntas (atadas); al agarrarse a la cuerda, se encoge con los brazos hacia los
 *       tobillos.</li>
 * </ul>
 */
public class AnimInterroga implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private int tipo, ultimo;

    public AnimInterroga(AbstractClientPlayer p) {
        this.p = p;
    }

    private static boolean nuestro(int t) {
        return t == AparatosVictimas.INTERROGATORIO || t == AparatosVictimas.COLGADO;
    }

    @Override
    public void tick() {
        pesoO = peso;
        int t = AparatosVictimas.tipo(p.getId());
        tipo = nuestro(t) ? t : 0;
        if (tipo != 0) ultimo = tipo;
        peso = Mth.clamp(peso + (tipo != 0 ? 0.25F : -0.25F), 0, 1);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || nuestro(AparatosVictimas.tipo(p.getId()));
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
        if (k <= 0 || m == 0 || (tt != TransformType.ROTATION && tt != TransformType.BEND)) return v;
        float t = p.tickCount + td;
        AparatosVictimas.Entrada en = AparatosVictimas.de(p.getId());
        if (tt == TransformType.BEND) {
            // codos y rodillas (bendy-lib): sentado con los antebrazos en los reposabrazos y las rodillas a ~85º;
            // colgado, los brazos casi rectos (encogidos al agarrarse a la cuerda)
            boolean brazo = Articulaciones.brazo(parte);
            if (!brazo && !Articulaciones.pierna(parte)) return v;
            if (m == AparatosVictimas.INTERROGATORIO) {
                SillaInterrogatorioBlockEntity be = en != null && p.level().getBlockEntity(en.pos()) instanceof SillaInterrogatorioBlockEntity s ? s : null;
                boolean paro = be != null && be.paroCli > 0;
                float d = AparatosCliente.descarga(p.getId(), td);
                float j = Mth.sin(t * 8.3F + (parte.startsWith("left") ? 1.7F : 0F)) * 0.25F * d;
                return Articulaciones.fijar(parte, v, brazo ? (paro ? 0.5F : 1.1F + 0.25F * d) + j : 1.45F - 0.2F * d + j, k);
            }
            GanchoBlockEntity g = en != null && p.level().getBlockEntity(en.pos()) instanceof GanchoBlockEntity gb ? gb : null;
            boolean agarra = g != null && g.agarrado();
            return Articulaciones.fijar(parte, v, brazo ? (agarra ? 0.7F : 0.15F + 0.05F * Mth.sin(t * 0.12F)) : (agarra ? 0.35F : 0.05F), k);
        }
        if (m == AparatosVictimas.INTERROGATORIO) {
            SillaInterrogatorioBlockEntity be = en != null && p.level().getBlockEntity(en.pos()) instanceof SillaInterrogatorioBlockEntity s ? s : null;
            boolean paro = be != null && be.paroCli > 0;
            float bpm = be == null ? 80F : Mth.lerp(td, be.bpmVisO, be.bpmVis);
            float resp = Mth.sin(t * (0.08F + Math.max(0F, bpm - 70F) * 0.0016F)) * (0.02F + Math.max(0F, bpm - 80F) * 0.0005F);
            float d = AparatosCliente.descarga(p.getId(), td);
            float a = d * 0.26F;
            float j1 = Mth.sin(t * 7.3F) * a, j2 = Mth.sin(t * 9.1F + 1.3F) * a, j3 = Mth.sin(t * 6.1F + 2.1F) * a;
            // respingo tras cada herramienta (≈ 10 ticks)
            float desde = be == null ? 99F : be.reloj + td - be.usoT;
            float resp2 = desde < 12F ? Mth.sin(desde / 12F * Mth.PI) : 0F;
            int herr = be == null ? -1 : be.usoHerr;
            float cabezaX = paro ? 0.75F : resp - (herr == Herramienta.ALICATES.ordinal() ? 0.55F : 0.2F) * resp2 + j3;
            float cuerpoX = paro ? 0.32F : resp * 0.5F + (herr == Herramienta.LLAVE.ordinal() ? 0.3F : 0.08F) * resp2 + j1 * 0.25F;
            return switch (parte) {
                case "rightArm" -> mezclar(k, v, (paro ? -0.45F : -0.62F + 0.3F * Articulaciones.K) + j1, 0.1F, 0.08F + j2 * 0.5F);
                case "leftArm" -> mezclar(k, v, (paro ? -0.45F : -0.62F + 0.3F * Articulaciones.K) + j2, -0.1F, -0.08F + j3 * 0.5F);
                case "head" -> mezclar(k, v, cabezaX, paro ? 0.25F : v.getY() * 0.8F + j1 * 0.5F, (paro ? 0.2F : 0F) + j2 * 0.6F);
                case "body" -> mezclar(k, v, cuerpoX, 0F, j3 * 0.25F);
                case "rightLeg" -> d > 0 ? new Vec3f(v.getX() + j2 * 0.4F, v.getY(), v.getZ() + j1 * 0.2F) : v;
                case "leftLeg" -> d > 0 ? new Vec3f(v.getX() + j3 * 0.4F, v.getY(), v.getZ() + j2 * 0.2F) : v;
                default -> v;
            };
        }
        // colgado: en el espacio del cuerpo (que va boca abajo) "hacia el suelo" es por encima de la cabeza
        GanchoBlockEntity g = en != null && p.level().getBlockEntity(en.pos()) instanceof GanchoBlockEntity gb ? gb : null;
        boolean agarra = g != null && g.agarrado();
        float vaiven = g == null ? 0F : (g.axVis(td) + g.azVis(td)) * 0.6F;
        float s1 = Mth.sin(t * 0.11F) * 0.1F + vaiven, s2 = Mth.sin(t * 0.13F + 1.2F) * 0.1F - vaiven;
        if (agarra) {
            float tir = Mth.sin(t * 0.9F) * 0.08F;
            return switch (parte) {
                case "rightArm" -> mezclar(k, v, -0.35F + tir, 0F, 0.25F);
                case "leftArm" -> mezclar(k, v, -0.35F - tir, 0F, -0.25F);
                case "body" -> mezclar(k, v, -0.45F, 0F, 0F);
                case "head" -> mezclar(k, v, -0.6F, v.getY() * 0.4F, 0F);
                case "rightLeg" -> mezclar(k, v, 0F, 0F, 0.02F);
                case "leftLeg" -> mezclar(k, v, 0F, 0F, -0.02F);
                default -> v;
            };
        }
        return switch (parte) {
            case "rightArm" -> mezclar(k, v, -3.0F + s1, 0F, 0.12F + s2 * 0.3F);
            case "leftArm" -> mezclar(k, v, -3.0F + s2, 0F, -0.12F + s1 * 0.3F);
            case "rightLeg" -> mezclar(k, v, 0F, 0F, 0.03F);
            case "leftLeg" -> mezclar(k, v, 0F, 0F, -0.03F);
            case "head" -> mezclar(k, v, Mth.clamp(v.getX(), -0.5F, 0.5F) * 0.6F, v.getY() * 0.7F, s1 * 0.3F);
            case "body" -> mezclar(k, v, s2 * 0.15F, 0F, s1 * 0.1F);
            default -> v;
        };
    }
}
