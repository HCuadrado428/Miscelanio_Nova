package com.miscelanea.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Pose a dos manos en tercera persona: la mano principal agarra la empuñadura trasera (gatillo)
 * y la otra mano llega por IK a la empuñadura delantera.
 *
 * IMPORTANTE: DISPLAY_* tiene que coincidir con "thirdperson_righthand" de models/item/aspirafantasmas.json,
 * porque aquí se reproduce la misma cadena de transformaciones que usa Minecraft para colocar el ítem.
 */
public final class PoseAspiradora {
    // Brazo principal (radianes). Se le suma el cabeceo de la cabeza para apuntar adonde miras.
    private static final float BRAZO_X = -0.8F;
    private static final float BRAZO_Y = -1.1F;

    // = models/item/aspirafantasmas.json → display.thirdperson_righthand
    public static final float DISPLAY_RX = 44.163F, DISPLAY_RY = -63.025F, DISPLAY_RZ = 0.0F;
    public static final float DISPLAY_TX = 3.119F, DISPLAY_TY = -2.23F, DISPLAY_TZ = -2.436F;
    public static final float DISPLAY_S = 0.7F;

    // Puntos del modelo de la boquilla (coordenadas de Blockbench, px)
    public static final Vector3f EMPUNADURA_DELANTERA = new Vector3f(0, 3.5F, -1.5F);
    public static final Vector3f SALIDA_MANGUERA = new Vector3f(0, 6.75F, 9.0F);
    public static final Vector3f BOCA = new Vector3f(0, 7.0F, -10.75F);

    public static final HumanoidModel.ArmPose POSE =
            HumanoidModel.ArmPose.create("MISCELANEA_ASPIRAFANTASMAS", true, PoseAspiradora::aplicar);

    private PoseAspiradora() {}

    /** Fuerza la creación del ArmPose durante el arranque del cliente. */
    public static void init() {}

    private static float rad(float deg) {
        return deg * Mth.DEG_TO_RAD;
    }

    /**
     * Matriz que lleva un punto del modelo geo de la boquilla (px, coordenadas de Blockbench) al espacio
     * del modelo del jugador (bloques), igual que ItemInHandLayer + ItemTransform + GeoItemRenderer.
     */
    public static Matrix4f geoAModelo(ModelPart brazo, boolean izquierda) {
        int i = izquierda ? -1 : 1;
        float ry = izquierda ? -DISPLAY_RY : DISPLAY_RY;
        float rz = izquierda ? -DISPLAY_RZ : DISPLAY_RZ;
        return new Matrix4f()
                .translate(brazo.x / 16.0F, brazo.y / 16.0F, brazo.z / 16.0F)
                .rotateZYX(brazo.zRot, brazo.yRot, brazo.xRot)
                .rotateX(rad(-90.0F))
                .rotateY(rad(180.0F))
                .translate(i / 16.0F, 0.125F, -0.625F)
                .translate(i * DISPLAY_TX / 16.0F, DISPLAY_TY / 16.0F, DISPLAY_TZ / 16.0F)
                .rotateXYZ(rad(DISPLAY_RX), rad(ry), rad(rz))
                .scale(DISPLAY_S)
                .translate(0.0F, 0.01F, 0.0F); // -0.5 de ItemRenderer y +0.5/+0.51 de GeckoLib
    }

    /** Punto geo (px de Blockbench; GeckoLib invierte X) transformado. */
    public static Vector3f punto(Matrix4f m, Vector3f geo) {
        return m.transformPosition(new Vector3f(-geo.x / 16.0F, geo.y / 16.0F, geo.z / 16.0F));
    }

    public static void aplicar(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean derecha = arm == HumanoidArm.RIGHT;
        ModelPart principal = derecha ? model.rightArm : model.leftArm;
        ModelPart otro = derecha ? model.leftArm : model.rightArm;
        float signo = derecha ? 1.0F : -1.0F;

        principal.xRot = BRAZO_X + model.head.xRot;
        principal.yRot = signo * BRAZO_Y + model.head.yRot;
        principal.zRot = 0.0F;

        // IK de la otra mano hacia la empuñadura delantera
        Vector3f objetivo = punto(geoAModelo(principal, !derecha), EMPUNADURA_DELANTERA);
        float dx = objetivo.x - otro.x / 16.0F;
        float dy = objetivo.y - otro.y / 16.0F;
        float dz = objetivo.z - otro.z / 16.0F;
        float len = Mth.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-4F) return;
        otro.xRot = -(float) Math.acos(Mth.clamp(dy / len, -1.0F, 1.0F));
        otro.yRot = (float) Mth.atan2(-dx, -dz);
        otro.zRot = 0.0F;
    }
}
