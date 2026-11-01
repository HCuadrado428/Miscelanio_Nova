package com.miscelanea.client.baile;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Poses de baile de los jugadores que bailan en una pista: un vaivén al ritmo de la canción y, encima, el paso de la
 * última flecha (izquierda, abajo, arriba, derecha) que se mezcla y se suelta poco a poco. Lo usa el mixin del modelo.
 */
public final class BailePoses {
    static final class Estado {
        boolean bailando;
        long inicio;
        int bpm = 120;
        int carril = -1, juicio;
        long tPaso;
    }

    static final Map<Integer, Estado> ESTADOS = new HashMap<>();

    private BailePoses() {}

    static Estado de(int id) {
        return ESTADOS.computeIfAbsent(id, k -> new Estado());
    }

    public static void empezar(int id, int bpm) {
        Estado e = de(id);
        e.bailando = true;
        e.inicio = System.currentTimeMillis();
        e.bpm = bpm;
        e.carril = -1;
    }

    public static void parar(int id) {
        ESTADOS.remove(id);
    }

    public static void paso(int id, int carril, int juicio) {
        Estado e = de(id);
        e.bailando = true;
        e.carril = carril;
        e.juicio = juicio;
        e.tPaso = System.currentTimeMillis();
    }

    /** Último paso de una entidad: {carril, milisegundos desde entonces} o null. */
    public static long[] ultimo(int id) {
        Estado e = ESTADOS.get(id);
        if (e == null || e.carril < 0) return null;
        return new long[]{e.carril, System.currentTimeMillis() - e.tPaso};
    }

    public static void posar(HumanoidModel<?> m, LivingEntity ent) {
        Estado e = ESTADOS.get(ent.getId());
        if (e == null || !e.bailando) return;
        long ahora = System.currentTimeMillis();
        double pulso = (ahora - e.inicio) / 1000.0 * e.bpm / 60.0;
        float s = (float) Math.sin(pulso * Math.PI), s2 = (float) Math.sin(pulso * Math.PI * 2);
        // vaivén al ritmo
        m.rightArm.xRot = -0.35F + s * 0.6F;
        m.leftArm.xRot = -0.35F - s * 0.6F;
        m.rightArm.zRot = 0.2F + Math.abs(s) * 0.15F;
        m.leftArm.zRot = -0.2F - Math.abs(s) * 0.15F;
        m.rightArm.yRot = m.leftArm.yRot = 0;
        m.rightLeg.xRot = s * 0.25F;
        m.leftLeg.xRot = -s * 0.25F;
        m.rightLeg.zRot = m.leftLeg.zRot = 0;
        m.head.xRot = 0.12F * s2;
        m.head.zRot = 0.08F * s;
        // paso de la última flecha
        float k = 1 - Mth.clamp((ahora - e.tPaso) / 420F, 0, 1);
        k = k * k * (3 - 2 * k);
        if (e.carril >= 0 && k > 0) {
            float[] o = objetivo(e.carril, e.juicio, pulso);
            mezclar(m.rightArm, o[0], o[1], o[2], k);
            mezclar(m.leftArm, o[3], o[4], o[5], k);
            mezclar(m.rightLeg, o[6], 0, o[7], k);
            mezclar(m.leftLeg, o[8], 0, o[9], k);
            m.head.xRot = Mth.lerp(k, m.head.xRot, o[10]);
            m.head.yRot = Mth.lerp(k, m.head.yRot, o[11]);
            m.head.zRot = Mth.lerp(k, m.head.zRot, o[12]);
        }
        m.hat.copyFrom(m.head);
    }

    private static void mezclar(ModelPart p, float x, float y, float z, float k) {
        p.xRot = Mth.lerp(k, p.xRot, x);
        p.yRot = Mth.lerp(k, p.yRot, y);
        p.zRot = Mth.lerp(k, p.zRot, z);
    }

    /** {brazoD x,y,z, brazoI x,y,z, piernaD x,z, piernaI x,z, cabeza x,y,z} para cada flecha. */
    private static float[] objetivo(int carril, int juicio, double pulso) {
        if (juicio == 3) {
            // fallo: un traspié
            float w = (float) Math.sin(pulso * 7) * 0.4F;
            return new float[]{-0.6F, 0, 0.8F + w, -0.6F, 0, -0.8F - w, 0.3F, 0.1F, -0.2F, -0.1F, 0.3F, 0, 0.35F};
        }
        return switch (carril) {
            case 0 -> new float[]{-1.2F, -0.4F, 0.4F, 0, 0, -1.9F, 0, 0.05F, 0, -0.4F, 0, 0.5F, -0.1F};        // izquierda
            case 3 -> new float[]{0, 0, 1.9F, -1.2F, 0.4F, -0.4F, 0, 0.4F, 0, -0.05F, 0, -0.5F, 0.1F};         // derecha
            case 2 -> new float[]{-3.0F, 0, 0.35F, -3.0F, 0, -0.35F, 0, 0.1F, 0, -0.1F, -0.35F, 0, 0};         // arriba
            default -> new float[]{-0.9F, -0.5F, -0.3F, -0.9F, 0.5F, 0.3F, -0.7F, 0, 0.5F, 0, 0.45F, 0, 0};   // abajo
        };
    }
}
