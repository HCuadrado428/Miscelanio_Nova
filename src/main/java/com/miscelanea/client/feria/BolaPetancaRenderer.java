package com.miscelanea.client.feria;

import com.miscelanea.petanca.BolaPetanca;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/** Bola de petanca (con el color de quien la tiró) o el boliche de madera. */
public class BolaPetancaRenderer extends EntityRenderer<BolaPetanca> {
    public BolaPetancaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.12F;
    }

    @Override
    public void render(BolaPetanca e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        float esc = e.boliche() ? 0.14F : 0.26F;
        ps.translate(0, esc / 2, 0);
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, -0.5, -0.5);
        int c = e.color();
        if (e.boliche()) FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("boliche"), 1, 1, 1, luz);
        else FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("bola_petanca"), (c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, luz);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(BolaPetanca e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
