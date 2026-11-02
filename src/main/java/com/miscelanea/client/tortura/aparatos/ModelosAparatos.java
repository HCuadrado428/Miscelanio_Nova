package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Piezas móviles de los aparatos (modelos de bloque sueltos, en las mismas coordenadas que el modelo del bloque, que
 * mira al norte). Las coordenadas de los ejes tienen que coincidir con scratchpad/gen_tortura3b.py.
 */
public final class ModelosAparatos {
    private ModelosAparatos() {}

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/" + n);
    }

    public static final ResourceLocation DONCELLA_PUERTA_IZQ = rl("doncella_puerta_izq");
    public static final ResourceLocation DONCELLA_PUERTA_DER = rl("doncella_puerta_der");
    public static final ResourceLocation POTRO_RODILLO = rl("potro_rodillo");
    public static final ResourceLocation POTRO_MANIVELA = rl("potro_manivela");
    public static final ResourceLocation SILLA_PALANCA = rl("silla_palanca");
    public static final ResourceLocation SILLA_AGUJA = rl("silla_aguja");
    public static final ResourceLocation[] TODOS = {DONCELLA_PUERTA_IZQ, DONCELLA_PUERTA_DER, POTRO_RODILLO, POTRO_MANIVELA,
            SILLA_PALANCA, SILLA_AGUJA};

    // ---- doncella: bisagras (x, z) de las puertas
    public static final float BISAGRA_IZQ_X = 1.6F, BISAGRA_DER_X = 14.4F, BISAGRA_Z = 2.2F;
    public static final float PUERTA_ABIERTA = 112F;
    // ---- potro: rodillos (y, z) sobre el eje x (el modelo del rodillo está en el de los pies; el de la cabecera es el
    //      mismo desplazado). La manivela gira con el rodillo de los pies.
    public static final float RODILLO_Y = 13F, RODILLO_PIES_Z = -6F, RODILLO_CABEZA_Z = 30F;
    // ---- silla: eje de la palanca (x, y, z) sobre el eje x; centro del dial (x, y, z) sobre el eje z
    public static final float PALANCA_X = -4.5F, PALANCA_Y = 6F, PALANCA_Z = 3.4F;
    public static final float DIAL_X = -8F, DIAL_Y = 14.5F, DIAL_Z = 3.6F;

    public static void dibujar(PoseStack ps, MultiBufferSource buf, ResourceLocation rl, int luz) {
        dibujar(ps, buf, rl, luz, 1F, 1F, 1F);
    }

    public static void dibujar(PoseStack ps, MultiBufferSource buf, ResourceLocation rl, int luz, float r, float g, float b) {
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(rl);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                r, g, b, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    /** Del espacio del bloque (BER) al del modelo, que se diseña mirando al norte. */
    public static void orientar(PoseStack ps, Direction frente) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180F - frente.toYRot()));
        ps.translate(-0.5, 0, -0.5);
    }

    /** Gira alrededor de un eje vertical que pasa por (x, z) en píxeles. */
    public static void girarY(PoseStack ps, float x, float z, float grados) {
        ps.translate(x / 16F, 0, z / 16F);
        ps.mulPose(Axis.YP.rotationDegrees(grados));
        ps.translate(-x / 16F, 0, -z / 16F);
    }

    /** Gira alrededor de un eje paralelo a X que pasa por (y, z) en píxeles. */
    public static void girarX(PoseStack ps, float y, float z, float grados) {
        ps.translate(0, y / 16F, z / 16F);
        ps.mulPose(Axis.XP.rotationDegrees(grados));
        ps.translate(0, -y / 16F, -z / 16F);
    }

    /** Gira alrededor de un eje paralelo a Z que pasa por (x, y) en píxeles. */
    public static void girarZ(PoseStack ps, float x, float y, float grados) {
        ps.translate(x / 16F, y / 16F, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(grados));
        ps.translate(-x / 16F, -y / 16F, 0);
    }
}
