package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.Miscelanea;
import com.miscelanea.armasfuego.gatling.GatlingEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.model.data.ModelData;

/**
 * La Gatling plantada: cinco piezas (modelos JSON, los mismos materiales que en la mano) que se mueven en el
 * renderizador: el yugo gira con el tirador, el cuerpo cabecea, los seis cañones giran y la manivela da vueltas.
 * Con el calor, los cañones se ponen rojos y brillan.
 */
public class GatlingRenderer extends EntityRenderer<GatlingEntity> {
    public static final ResourceLocation BASE = rl("base"), YUGO = rl("yugo"), CUERPO = rl("cuerpo"), CANONES = rl("canones"), MANIVELA = rl("manivela");
    public static final ResourceLocation[] TODOS = {BASE, YUGO, CUERPO, CANONES, MANIVELA};
    /** Pivote del cabeceo y eje de los cañones (px del modelo) y eje de la manivela (y, z). */
    private static final float PIV_Y = 17.25F / 16F, PIV_Z = 8F / 16F, MAN_Y = 17.25F / 16F, MAN_Z = 12.5F / 16F;

    private static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "item/armas3d/gatling_" + n);
    }

    public GatlingRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.6F;
    }

    @Override
    public void render(GatlingEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        float base = e.getYRot();
        Player p = e.tirador();
        float gy = p != null ? Mth.rotLerp(pt, p.yRotO, p.getYRot()) : base;
        float gx = p != null ? Mth.lerp(pt, p.xRotO, p.getXRot()) : 0;
        float calor = e.calor() / 100F;
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180F - base));
        ps.translate(-0.5, 0, -0.5);
        dibujar(ps, buf, BASE, luz, 1, 1, 1);
        // yugo: gira con el tirador
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.wrapDegrees(gy - base)));
        ps.translate(-0.5, 0, -0.5);
        dibujar(ps, buf, YUGO, luz, 1, 1, 1);
        // cuerpo: cabecea sobre los muñones
        ps.translate(0.5, PIV_Y, PIV_Z);
        ps.mulPose(Axis.XP.rotationDegrees(-gx));
        ps.translate(-0.5, -PIV_Y, -PIV_Z);
        dibujar(ps, buf, CUERPO, luz, 1, 1, 1);
        // cañones: giran sobre su eje; con el calor, rojos y brillando
        ps.pushPose();
        ps.translate(0.5, PIV_Y, 0);
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.canonesO, e.canones)));
        ps.translate(-0.5, -PIV_Y, 0);
        float r = 1F, g = 1F - 0.55F * calor * calor, b = 1F - 0.7F * calor * calor;
        int luzC = calor > 0.6F ? 0xF000F0 : luz;
        dibujar(ps, buf, CANONES, luzC, Math.min(1F, r + calor * 0.3F), g, b);
        ps.popPose();
        // manivela
        ps.translate(0, MAN_Y, MAN_Z);
        ps.mulPose(Axis.XP.rotationDegrees(Mth.lerp(pt, e.manivelaO, e.manivela)));
        ps.translate(0, -MAN_Y, -MAN_Z);
        dibujar(ps, buf, MANIVELA, luz, 1, 1, 1);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    private static void dibujar(PoseStack ps, MultiBufferSource buf, ResourceLocation rl, int luz, float r, float g, float b) {
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(rl);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                r, g, b, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
    }

    @Override
    public ResourceLocation getTextureLocation(GatlingEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
