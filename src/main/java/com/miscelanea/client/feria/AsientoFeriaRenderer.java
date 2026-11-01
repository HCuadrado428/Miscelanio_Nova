package com.miscelanea.client.feria;

import com.miscelanea.feria.AsientoFeria;
import com.miscelanea.feria.AtraccionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Cabina de la noria o caballito del tiovivo, en el sitio exacto que da la atracción para este fotograma. */
public class AsientoFeriaRenderer extends EntityRenderer<AsientoFeria> {
    public AsientoFeriaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0;
    }

    @Override
    public void render(AsientoFeria e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        AtraccionBlockEntity b = e.be();
        if (b == null) return;
        double[] p = b.puesto(e.indice(), e.level().getGameTime() + pt);
        double ex = Mth.lerp(pt, e.xo, e.getX()), ey = Mth.lerp(pt, e.yo, e.getY()), ez = Mth.lerp(pt, e.zo, e.getZ());
        ps.pushPose();
        ps.translate(p[0] - ex, p[1] - ey, p[2] - ez);
        ps.mulPose(Axis.YP.rotationDegrees((float) -p[3]));
        var vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        int c = e.indice() % 4;
        if (e.esNoria()) {
            // un leve balanceo al girar
            float t = e.level().getGameTime() + pt;
            ps.translate(0, 1.5, 0);
            ps.mulPose(Axis.XP.rotationDegrees((b.gira ? 3F : 0.8F) * Mth.sin(t * 0.09F + e.indice())));
            ps.translate(-0.5, -1.5, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.CABINA[c], 1, 1, 1, luz);
        } else {
            ps.translate(-0.5, 0, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.CABALLO[c], 1, 1, 1, luz);
        }
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(AsientoFeria e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
