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
        // se dibuja donde está la entidad (interpolada como sus pasajeros), no un tic por delante: así el jugador va dentro
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(pt, e.yRotO, e.getYRot())));
        var vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        int c = e.indice() % 4;
        if (e.esPulpo()) {
            // cesta para dos: más ancha que un bloque
            ps.scale(1.6F, 1.0F, 1.6F);
            ps.translate(-0.5, 0, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("cesta" + c), 1, 1, 1, luz);
        } else if (e.esNoria()) {
            // un leve balanceo al girar
            float t = (float) (e.level().getGameTime() % 100000L) + pt;
            ps.translate(0, 3.0, 0);
            ps.mulPose(Axis.XP.rotationDegrees((b.gira ? 2.5F : 0.6F) * Mth.sin(t * 0.07F + e.indice())));
            ps.scale(2, 2, 2);
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
