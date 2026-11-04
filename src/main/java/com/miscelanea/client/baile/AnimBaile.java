package com.miscelanea.client.baile;

import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * El baile de la pista de baile en el jugador (capa de playerAnimator): la coreografía de la canción en el momento en
 * que va ({@link Coreografia}), la misma para el que baila y para los que miran. Si falla una flecha da un traspié
 * (un tambaleo que se suma y se apaga en medio segundo) pero el baile sigue. Entra y sale en 4 tics.
 */
public class AnimBaile implements IAnimation {
    private final AbstractClientPlayer p;
    private float peso, pesoO;
    private final float[] pose = new float[Coreografia.N];
    private float tropiezo = 99, tiempo;

    public AnimBaile(AbstractClientPlayer p) {
        this.p = p;
    }

    @Override
    public void tick() {
        pesoO = peso;
        float objetivo = BailePoses.baila(p.getId()) ? 1F : 0F;
        peso = peso < objetivo ? Math.min(objetivo, peso + 0.25F) : Math.max(objetivo, peso - 0.25F);
    }

    @Override
    public boolean isActive() {
        return peso > 0 || pesoO > 0 || BailePoses.baila(p.getId());
    }

    @Override
    public void setupAnim(float td) {
        BailePoses.Estado e = BailePoses.ESTADOS.get(p.getId());
        if (e == null) return;
        long ahora = System.currentTimeMillis();
        tiempo = BailePoses.segundo(e, ahora);
        float[] v = Coreografia.pose(e.cancion, tiempo);
        System.arraycopy(v, 0, pose, 0, Coreografia.N);
        tropiezo = BailePoses.desdeTropiezo(e, ahora);
    }

    /** Traspié tras un fallo: sacudida que se suma a la pieza j (0..6) y se apaga en 0,45 s. */
    private float tambaleo(int j, int eje) {
        if (tropiezo > 0.45F) return 0;
        float k = 1F - tropiezo / 0.45F;
        k *= k;
        float w = Mth.sin(tropiezo * 38F + j * 1.3F + eje * 2.1F);
        return switch (j) {
            case 0 -> eje == 2 ? (0.5F + 0.35F * w) * k : eje == 0 ? -0.4F * k : 0;      // brazos al aire para no caerse
            case 1 -> eje == 2 ? (-0.5F - 0.35F * w) * k : eje == 0 ? -0.3F * k : 0;
            case 2, 3 -> eje == 0 ? 0.25F * w * k : 0;
            case 4 -> eje == 0 ? 0.3F * k : eje == 2 ? 0.2F * w * k : 0;
            case 5 -> eje == 2 ? 0.13F * w * k : eje == 0 ? -0.06F * k : 0;               // el cuerpo se tambalea
            case 6 -> eje == 1 ? -0.03F * k : eje == 0 ? 0.03F * w * k : 0;
            default -> 0;
        };
    }

    private float valor(int j, int eje) {
        return pose[j * 3 + eje] + tambaleo(j, eje);
    }

    private Vec3f mezclar(float k, Vec3f v, int j) {
        return new Vec3f(Mth.lerp(k, v.getX(), valor(j, 0)), Mth.lerp(k, v.getY(), valor(j, 1)), Mth.lerp(k, v.getZ(), valor(j, 2)));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String parte, @NotNull TransformType tipo, float td, @NotNull Vec3f v) {
        float k = Mth.lerp(td, pesoO, peso);
        if (k <= 0) return v;
        if (tipo == TransformType.POSITION) {
            if (!parte.equals("body")) return v;
            return new Vec3f(v.getX() + valor(6, 0) * k, v.getY() + valor(6, 1) * k, v.getZ() + valor(6, 2) * k);
        }
        if (tipo != TransformType.ROTATION) return v;
        return switch (parte) {
            case "body" -> new Vec3f(v.getX() + valor(5, 0) * k, v.getY() + valor(5, 1) * k, v.getZ() + valor(5, 2) * k);
            case "torso" -> new Vec3f(Mth.lerp(k, v.getX(), 0), Mth.lerp(k, v.getY(), 0), Mth.lerp(k, v.getZ(), 0));
            case "head" -> mezclar(k, v, 4);
            case "rightArm" -> mezclar(k, v, 0);
            case "leftArm" -> mezclar(k, v, 1);
            case "rightLeg" -> mezclar(k, v, 2);
            case "leftLeg" -> mezclar(k, v, 3);
            default -> v;
        };
    }
}
