package com.miscelanea.client.dream;

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
 * Poses del mordisco (playerAnimator), en primera y tercera persona:
 * <ul>
 *   <li>Quien muerde: se abalanza (cuerpo inclinado, una pierna atrás), las dos manos agarran los hombros de la víctima y
 *       la cabeza se hunde de lado en su cuello; en cada mordisco da un tirón seco con la cabeza y aprieta las manos.</li>
 *   <li>La víctima: se echa hacia atrás con la cabeza girada (el cuello al descubierto), empuja con los brazos y patalea;
 *       en cada mordisco, un espasmo.</li>
 * </ul>
 * Al empezar y al soltar se mezcla suavemente con la pose normal (5 ticks).
 */
public class MordiscoAnimacion implements IAnimation {
    private static final float TRANSICION = 5.0F;

    // ---- quien muerde (radianes)
    private static final float[] MO_BRAZO_D = {-1.58F, -0.46F, 0.06F};
    private static final float[] MO_BRAZO_I = {-1.58F, 0.46F, -0.06F};

    // ---- víctima
    private static final float[] VI_BRAZO_D = {-1.25F, -0.3F, 0.12F};
    private static final float[] VI_BRAZO_I = {-1.25F, 0.3F, -0.12F};
    private static final float[] VI_ESPASMO_D = {-0.6F, 0.15F, 0.85F};
    private static final float[] VI_ESPASMO_I = {-0.6F, -0.15F, -0.85F};

    private final AbstractClientPlayer player;
    private ClientMordiscos.Estado estado;
    private float peso;
    private final Map<String, Vec3f> rot = new HashMap<>();
    private Vec3f cabeza, cuerpo;

    public MordiscoAnimacion(AbstractClientPlayer player) {
        this.player = player;
    }

    @Override
    public boolean isActive() {
        estado = ClientMordiscos.de(player.getId());
        return estado != null;
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float ruido(float t, float s) {
        return Mth.sin(t * 37.0F + s) * 0.6F + Mth.sin(t * 23.0F + s * 2.1F) * 0.4F;
    }

    private static float[] mezcla(float k, float[] a, float[] b) {
        return new float[]{Mth.lerp(k, a[0], b[0]), Mth.lerp(k, a[1], b[1]), Mth.lerp(k, a[2], b[2])};
    }

    @Override
    public void setupAnim(float pt) {
        rot.clear();
        cabeza = cuerpo = null;
        if (estado == null) return;
        float t = ClientMordiscos.ahora(pt);
        peso = suave((t - estado.inicio) / TRANSICION);
        if (estado.fin >= 0) peso = Math.min(peso, 1.0F - suave((t - estado.fin) / TRANSICION));
        float dm = t - estado.ultimoMordisco;
        // tirón del mordisco: sube rápido y se relaja en ~10 ticks
        float tiron = dm < 2.0F ? dm / 2.0F : dm < 12.0F ? 1.0F - (dm - 2.0F) / 10.0F : 0.0F;
        tiron = suave(tiron);

        if (estado.rol == ClientMordiscos.Rol.MORDEDOR) {
            float resp = Mth.sin(t * 0.18F) * 0.03F;
            rot.put("rightArm", new Vec3f(MO_BRAZO_D[0] + resp - 0.12F * tiron, MO_BRAZO_D[1] - 0.09F * tiron, MO_BRAZO_D[2]));
            rot.put("leftArm", new Vec3f(MO_BRAZO_I[0] - resp - 0.12F * tiron, MO_BRAZO_I[1] + 0.09F * tiron, MO_BRAZO_I[2]));
            // una pierna atrás para abalanzarse
            rot.put("rightLeg", new Vec3f(0.32F, 0.0F, 0.05F));
            rot.put("leftLeg", new Vec3f(-0.22F, 0.0F, -0.04F));
            // cabeza hundida de lado en el cuello; en cada mordisco, un tirón seco hacia abajo y un meneo
            cabeza = new Vec3f(0.55F + 0.22F * tiron + Mth.sin(t * 0.3F) * 0.03F, 0.42F + ruido(t, 1) * 0.05F * tiron,
                    0.28F + 0.08F * tiron * Mth.sin(t * 2.2F));
            cuerpo = new Vec3f(-0.24F - 0.06F * tiron, 0.08F, 0.0F); // x<0: inclinado hacia la víctima
        } else {
            float lucha = Mth.sin(t * 1.4F);
            float j = 0.25F * tiron;
            float[] rd = {VI_BRAZO_D[0] + lucha * 0.3F, VI_BRAZO_D[1] + lucha * 0.12F, VI_BRAZO_D[2]};
            float[] ri = {VI_BRAZO_I[0] - lucha * 0.3F, VI_BRAZO_I[1] - lucha * 0.12F, VI_BRAZO_I[2]};
            rd = mezcla(tiron, rd, VI_ESPASMO_D);
            ri = mezcla(tiron, ri, VI_ESPASMO_I);
            rot.put("rightArm", new Vec3f(rd[0] + ruido(t, 2) * j, rd[1] + ruido(t, 3) * j, rd[2]));
            rot.put("leftArm", new Vec3f(ri[0] + ruido(t, 4) * j, ri[1] + ruido(t, 5) * j, ri[2]));
            float patada = Mth.sin(t * 1.7F) * 0.45F;
            rot.put("rightLeg", new Vec3f(0.12F + patada + ruido(t, 6) * j, 0.0F, 0.06F));
            rot.put("leftLeg", new Vec3f(0.02F - patada + ruido(t, 7) * j, 0.0F, -0.06F));
            // la cabeza se aparta (cuello al descubierto) y mira al cielo con el mordisco
            cabeza = new Vec3f(-0.3F - 0.3F * tiron + ruido(t, 8) * j, -0.5F + Mth.sin(t * 1.1F) * 0.12F, -0.3F - 0.15F * tiron);
            cuerpo = new Vec3f(0.14F + 0.08F * tiron, 0.0F, ruido(t, 9) * j * 0.3F); // x>0: echada hacia atrás
        }
    }

    private static Vec3f lerp(float k, Vec3f a, Vec3f b) {
        return new Vec3f(Mth.lerp(k, a.getX(), b.getX()), Mth.lerp(k, a.getY(), b.getY()), Mth.lerp(k, a.getZ(), b.getZ()));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float pt, @NotNull Vec3f value0) {
        if (type != TransformType.ROTATION) return value0;
        if (modelName.equals("head") && cabeza != null) return value0.add(cabeza.scale(peso));
        if (modelName.equals("body") && cuerpo != null) return cuerpo.scale(peso);
        Vec3f r = rot.get(modelName);
        return r != null ? lerp(peso, value0, r) : value0;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float pt) {
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float pt) {
        return new FirstPersonConfiguration(true, true, true, false);
    }
}
