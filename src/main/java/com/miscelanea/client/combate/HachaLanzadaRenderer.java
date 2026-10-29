package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.miscelanea.hacha.HachaLanzada;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Hacha lanzada: girando (ligero en horizontal, pesado en vertical), clavada con la cabeza hacia delante,
 * o volviendo girando. GeckoLib la gira 180º por su cuenta, por eso el filo se orienta hacia -z antes.
 */
public class HachaLanzadaRenderer extends GeoEntityRenderer<HachaLanzada> {
    public HachaLanzadaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, "hacha_lanzada")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void render(HachaLanzada e, float yaw, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        ps.translate(0, 0.15, 0);
        ps.mulPose(Axis.YP.rotationDegrees(e.rumboY()));
        ps.mulPose(Axis.XP.rotationDegrees(-e.rumboX()));
        float giro = (e.tickCount + partial) * 45F;
        if (e.estado() == HachaLanzada.CLAVADA) {
            // la cabeza clavada delante; el mango sale hacia atrás y arriba
            ps.mulPose(Axis.XP.rotationDegrees(-55));
            ps.translate(0, -0.62, 0);
        } else if (e.pesado() && e.estado() == HachaLanzada.VOLANDO) {
            ps.mulPose(Axis.XP.rotationDegrees(giro));          // gira en vertical
            ps.translate(0, -0.34, 0);
        } else {
            ps.mulPose(Axis.XP.rotationDegrees(90));            // tumbada
            ps.mulPose(Axis.ZP.rotationDegrees(giro));          // gira en horizontal
            ps.translate(0, -0.34, 0);
        }
        ps.mulPose(Axis.YP.rotationDegrees(-90));               // filo hacia delante (antes del giro de GeckoLib)
        ps.scale(0.85F, 0.85F, 0.85F);
        super.render(e, 0, partial, ps, buf, luz);
        ps.popPose();
    }
}
