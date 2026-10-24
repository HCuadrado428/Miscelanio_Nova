package com.miscelanea.client.porra;

import com.miscelanea.porra.PorraItem;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.core.util.Vec3f;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.jetbrains.annotations.NotNull;

/**
 * Ataque normal con la porra: estocada de esgrima con zancada (reloj propio de 12 ticks).
 * 0–30 %  Preparación: porra recogida en la cadera apuntando al frente, hombro atrás, peso en la pierna de atrás,
 *         la otra mano en guardia delante del pecho.
 * 30–42 % Embestida explosiva: brazo extendido hacia donde miras con la porra alineada, torso girado,
 *         zancada larga, cuerpo inclinado y adelantado, la otra mano atrás de contrapeso.
 * 42–55 % Impacto: pequeño rebote.
 * 55–100% Recuperación suave hasta la pose normal.
 */
public class EstocadaAnimacion implements IAnimation {
    private static final float DURACION = 12.0F;

    /** Valores de una pose clave. Rotaciones en radianes; desplazamientos en bloques. */
    private record Pose(float ax, float ay, float az, float item, float ox, float oy, float oz,
                        float torso, float inclina, float piernaMano, float piernaOtra, float baja, float avanza) {
        Pose mezcla(float k, Pose b) {
            return new Pose(Mth.lerp(k, ax, b.ax), Mth.lerp(k, ay, b.ay), Mth.lerp(k, az, b.az), Mth.lerp(k, item, b.item),
                    Mth.lerp(k, ox, b.ox), Mth.lerp(k, oy, b.oy), Mth.lerp(k, oz, b.oz), Mth.lerp(k, torso, b.torso),
                    Mth.lerp(k, inclina, b.inclina), Mth.lerp(k, piernaMano, b.piernaMano), Mth.lerp(k, piernaOtra, b.piernaOtra),
                    Mth.lerp(k, baja, b.baja), Mth.lerp(k, avanza, b.avanza));
        }
    }

    //                                   brazo              porra    otra mano         torso inclina piernas      baja   avanza
    private static final Pose NEUTRA = new Pose(-0.1F, 0F, 0F,         0.0F,    0F, 0F, 0F,          0F,   0F,    0F, 0F,        0F,    0F);
    private static final Pose PREPARA = new Pose(0.35F, 0.15F, 0.1F,   -0.1F,   -1.1F, 0.5F, 0F,    0.45F, -0.1F, 0.2F, -0.25F,  -0.05F, 0.05F);
    private static final Pose GOLPE = new Pose(-1.6F, -0.15F, 0F,      -2.217F, 0.6F, 0F, -0.15F,   -0.4F, 0.25F, 0.5F, -0.6F,   -0.15F, -0.25F);
    private static final Pose REBOTE = new Pose(-1.5F, -0.12F, 0F,     -2.217F, 0.45F, 0F, -0.1F,   -0.3F, 0.18F, 0.45F, -0.55F, -0.13F, -0.2F);

    private final AbstractClientPlayer player;
    private float inicio = -100.0F;
    private float ataqueAnterior;
    private boolean zurdo;
    private float peso;
    private Pose pose = NEUTRA;
    private float apunta;

    public EstocadaAnimacion(AbstractClientPlayer player) {
        this.player = player;
    }

    @Override
    public boolean isActive() {
        float ahora = ClientAgarres.ahora(0.0F);
        boolean porra = player.getMainHandItem().getItem() instanceof PorraItem;
        // Empieza cuando arranca el golpe del jugador (flanco de subida de attackAnim)
        float ataque = player.getAttackAnim(1.0F);
        if (porra && ataque > 0.0F && ataqueAnterior == 0.0F) inicio = ahora;
        ataqueAnterior = ataque;
        return porra && ahora - inicio < DURACION && ClientAgarres.de(player.getId()) == null;
    }

    private static float suave(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    private static float sale(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        float y = 1.0F - x;
        return 1.0F - y * y * y; // muy rápido al principio: golpe explosivo
    }

    @Override
    public void setupAnim(float tickDelta) {
        float u = (ClientAgarres.ahora(tickDelta) - inicio) / DURACION;
        zurdo = player.getMainArm() == HumanoidArm.LEFT;
        apunta = Mth.lerp(tickDelta, player.xRotO, player.getXRot()) * Mth.DEG_TO_RAD;

        if (u < 0.3F) pose = NEUTRA.mezcla(suave(u / 0.3F), PREPARA);
        else if (u < 0.42F) pose = PREPARA.mezcla(sale((u - 0.3F) / 0.12F), GOLPE);
        else if (u < 0.55F) pose = GOLPE.mezcla(suave((u - 0.42F) / 0.13F), REBOTE);
        else pose = REBOTE.mezcla(suave((u - 0.55F) / 0.45F), NEUTRA);

        // Entra y sale mezclándose con la pose normal
        peso = u < 0.08F ? suave(u / 0.08F) : (u > 0.85F ? 1.0F - suave((u - 0.85F) / 0.15F) : 1.0F);
    }

    private Vec3f mezcla(Vec3f base, float x, float y, float z) {
        return new Vec3f(Mth.lerp(peso, base.getX(), x), Mth.lerp(peso, base.getY(), y), Mth.lerp(peso, base.getZ(), z));
    }

    @Override
    public @NotNull Vec3f get3DTransform(@NotNull String modelName, @NotNull TransformType type, float tickDelta, @NotNull Vec3f value0) {
        float s = zurdo ? -1.0F : 1.0F;
        String l = zurdo ? "left" : "right";
        String o = zurdo ? "right" : "left";
        Pose p = pose;
        if (type == TransformType.POSITION) {
            // Todo el cuerpo baja y avanza con la zancada
            if (modelName.equals("body")) return new Vec3f(0.0F, p.baja() * peso, p.avanza() * peso);
            return value0;
        }
        if (type != TransformType.ROTATION) return value0;
        // El brazo de la porra sigue hacia donde miras durante la embestida
        float ax = p.ax() + apunta * Mth.clamp((p.item() / -2.217F), 0.0F, 1.0F);
        if (modelName.equals(l + "Arm")) return mezcla(value0, ax, p.ay() * s, p.az() * s);
        if (modelName.equals(o + "Arm")) return mezcla(value0, p.ox(), p.oy() * s, p.oz() * s);
        if (modelName.equals(l + "Item")) return new Vec3f(p.item() * peso, 0.0F, 0.0F);
        if (modelName.equals(l + "Leg")) return mezcla(value0, p.piernaMano(), 0.0F, 0.0F);
        if (modelName.equals(o + "Leg")) return mezcla(value0, p.piernaOtra(), 0.0F, 0.0F);
        // Rotación de todo el cuerpo (el signo es el del sistema de playerAnimator: x<0 inclina hacia delante)
        if (modelName.equals("body")) return new Vec3f(-p.inclina() * peso, -p.torso() * s * peso, 0.0F);
        return value0;
    }

    @Override
    public @NotNull FirstPersonMode getFirstPersonMode(float tickDelta) {
        return FirstPersonMode.THIRD_PERSON_MODEL;
    }

    @Override
    public @NotNull FirstPersonConfiguration getFirstPersonConfiguration(float tickDelta) {
        return new FirstPersonConfiguration(!zurdo, zurdo, !zurdo, zurdo);
    }
}
