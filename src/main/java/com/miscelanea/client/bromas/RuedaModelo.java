package com.miscelanea.client.bromas;

import com.miscelanea.tortura.aparatos.RuedaBlockEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Modelo de la rueda de Catalina (en píxeles, con Y hacia arriba, origen en el centro del suelo del bloque y el frente
 * hacia -Z, como los modelos de los aparatos): el soporte (poste, pies cruzados, tres riostras y las abrazaderas de
 * hierro del eje) y la rueda que gira (llanta de 24 piezas con su aro de hierro, 8 radios con sus chapas, cubo, eje y
 * las 4 correas de cuero con hebilla, en muñecas y tobillos de la víctima en aspa).
 * La textura (128x64) tiene zonas por material; ver scratchpad/gen_bromas.py (mismas medidas).
 */
public final class RuedaModelo {
    private RuedaModelo() {}

    /** Eje de la rueda (px): altura y profundidad del plano de la rueda (detrás del centro). */
    public static final float EJE_Y = (float) (RuedaBlockEntity.EJE_Y * 16.0), EJE_Z = (float) (RuedaBlockEntity.RUEDA_ATRAS * 16.0);
    /** Radio medio de la llanta (px) y ángulos (grados, desde +X hacia arriba) de las correas: muñeca der., izq., tobillo der., izq. */
    public static final float R_LLANTA = 16.8F;
    public static final float[] ANG_CORREAS = {43.8F, 136.2F, -69.4F, -110.6F};

    // zonas de la textura
    private static final int MADERA_H_U = 0, MADERA_H_V = 0, MADERA_V_U = 64, MADERA_V_V = 0, TESTA_U = 96, TESTA_V = 0,
            HIERRO_U = 0, HIERRO_V = 32, CUERO_U = 64, CUERO_V = 40;

    public static ModelPart crear() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition raiz = mesh.getRoot();
        soporte(raiz.addOrReplaceChild("soporte", CubeListBuilder.create(), PartPose.ZERO));
        rueda(raiz.addOrReplaceChild("rueda", CubeListBuilder.create(), PartPose.offset(0, EJE_Y, EJE_Z)));
        return LayerDefinition.create(mesh, 128, 64).bakeRoot();
    }

    private static CubeListBuilder caja(int u, int v, float x, float y, float z, float w, float h, float d) {
        return CubeListBuilder.create().texOffs(u, v).addBox(x, y, z, w, h, d);
    }

    /** Viga a lo largo de X que va de (x0, y0) a (x1, y1) en el plano z = zc (gira sobre Z). */
    private static void vigaXY(PartDefinition p, String n, float x0, float y0, float x1, float y1, float zc, float grosor) {
        float dx = x1 - x0, dy = y1 - y0, l = Mth.sqrt(dx * dx + dy * dy);
        p.addOrReplaceChild(n, caja(MADERA_H_U, MADERA_H_V, -l / 2, -grosor / 2, -grosor / 2, l, grosor, grosor),
                PartPose.offsetAndRotation((x0 + x1) / 2, (y0 + y1) / 2, zc, 0, 0, (float) Math.atan2(dy, dx)));
    }

    /** Viga a lo largo de Z que va de (y0, z0) a (y1, z1) en el plano x = xc (gira sobre X). */
    private static void vigaYZ(PartDefinition p, String n, float y0, float z0, float y1, float z1, float xc, float grosor) {
        float dy = y1 - y0, dz = z1 - z0, l = Mth.sqrt(dy * dy + dz * dz);
        p.addOrReplaceChild(n, caja(MADERA_H_U, MADERA_H_V, -grosor / 2, -grosor / 2, -l / 2, grosor, grosor, l),
                PartPose.offsetAndRotation(xc, (y0 + y1) / 2, (z0 + z1) / 2, (float) Math.atan2(-dy, dz), 0, 0));
    }

    private static void soporte(PartDefinition s) {
        // pies cruzados en el suelo
        s.addOrReplaceChild("pie_x", caja(MADERA_H_U, MADERA_H_V, -13F, 0F, 5.5F, 26F, 3F, 6F), PartPose.ZERO);
        s.addOrReplaceChild("pie_z", caja(MADERA_H_U, MADERA_H_V, -2F, 0F, -9F, 4F, 3F, 24F), PartPose.ZERO);
        // poste
        s.addOrReplaceChild("poste", caja(MADERA_V_U, MADERA_V_V, -2.5F, 3F, 6F, 5F, EJE_Y + 3.9F - 3F, 5F), PartPose.ZERO);
        // riostras: dos a los lados y una detrás
        vigaXY(s, "riostra_izq", 11F, 3F, 2.5F, 14F, 8.5F, 2.4F);
        vigaXY(s, "riostra_der", -11F, 3F, -2.5F, 14F, 8.5F, 2.4F);
        vigaYZ(s, "riostra_atras", 3F, 15F, 14F, 11F, 0F, 2.4F);
        // abrazaderas de hierro alrededor del poste, por encima y por debajo del eje
        s.addOrReplaceChild("abrazadera_1", caja(HIERRO_U, HIERRO_V + 16, -2.8F, EJE_Y - 3.6F, 5.7F, 5.6F, 2.0F, 5.6F), PartPose.ZERO);
        s.addOrReplaceChild("abrazadera_2", caja(HIERRO_U, HIERRO_V + 16, -2.8F, EJE_Y + 1.6F, 5.7F, 5.6F, 2.0F, 5.6F), PartPose.ZERO);
    }

    private static void rueda(PartDefinition r) {
        // llanta (madera) y aro (hierro), 24 piezas
        int n = 24;
        float largoLlanta = 4.8F, rAro = 18.65F, largoAro = 5.0F;
        for (int i = 0; i < n; i++) {
            float a = Mth.TWO_PI * i / n;
            r.addOrReplaceChild("llanta_" + i, caja(MADERA_H_U, MADERA_H_V + 16, -largoLlanta / 2, -1.5F, -1.2F, largoLlanta, 3F, 2.4F),
                    PartPose.offsetAndRotation(R_LLANTA * Mth.cos(a), R_LLANTA * Mth.sin(a), 0, 0, 0, a + Mth.HALF_PI));
            r.addOrReplaceChild("aro_" + i, caja(HIERRO_U, HIERRO_V, -largoAro / 2, -0.35F, -1.4F, largoAro, 0.7F, 2.8F),
                    PartPose.offsetAndRotation(rAro * Mth.cos(a), rAro * Mth.sin(a), 0, 0, 0, a + Mth.HALF_PI));
        }
        // 8 radios con su chapa de hierro donde se clavan en la llanta
        float r0 = 3F, r1 = 15.6F, rc = (r0 + r1) / 2, l = r1 - r0;
        for (int i = 0; i < 8; i++) {
            float a = Mth.TWO_PI * i / 8;
            r.addOrReplaceChild("radio_" + i, caja(MADERA_H_U, MADERA_H_V + 24, -l / 2, -0.8F, -0.8F, l, 1.6F, 1.6F),
                    PartPose.offsetAndRotation(rc * Mth.cos(a), rc * Mth.sin(a), 0, 0, 0, a));
            r.addOrReplaceChild("chapa_" + i, caja(HIERRO_U + 40, HIERRO_V, -0.6F, -1.3F, -1.4F, 1.2F, 2.6F, 2.8F),
                    PartPose.offsetAndRotation(14.6F * Mth.cos(a), 14.6F * Mth.sin(a), 0, 0, 0, a));
        }
        // cubo (dos bloques girados 45º para que parezca redondo), tapa de hierro y eje
        r.addOrReplaceChild("cubo", caja(TESTA_U, TESTA_V, -3.5F, -3.5F, -1.4F, 7F, 7F, 3.8F), PartPose.ZERO);
        r.addOrReplaceChild("cubo_45", caja(TESTA_U, TESTA_V, -3F, -3F, -1.2F, 6F, 6F, 3.4F), PartPose.rotation(0, 0, Mth.PI / 4));
        r.addOrReplaceChild("tapa", caja(HIERRO_U + 40, HIERRO_V + 8, -2F, -2F, -2F, 4F, 4F, 0.6F), PartPose.ZERO);
        r.addOrReplaceChild("eje", caja(TESTA_U, TESTA_V + 12, -1.1F, -1.1F, -1F, 2.2F, 2.2F, 9.5F), PartPose.ZERO);
        // correas de cuero (se ven solo las que siguen atadas) con su hebilla delante
        for (int i = 0; i < 4; i++) {
            float a = ANG_CORREAS[i] * Mth.DEG_TO_RAD;
            PartDefinition c = r.addOrReplaceChild("correa_" + i, caja(CUERO_U, CUERO_V, -1.2F, -1.95F, -1.65F, 2.4F, 3.9F, 3.3F),
                    PartPose.offsetAndRotation(R_LLANTA * Mth.cos(a), R_LLANTA * Mth.sin(a), 0, 0, 0, a + Mth.HALF_PI));
            c.addOrReplaceChild("hebilla", caja(HIERRO_U + 52, HIERRO_V + 8, -0.7F, -0.7F, -0.25F, 1.4F, 1.4F, 0.5F),
                    PartPose.offset(0, 0, -1.9F));
        }
    }
}
