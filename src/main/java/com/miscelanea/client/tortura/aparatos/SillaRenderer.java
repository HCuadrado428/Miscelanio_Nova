package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.SillaElectricaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;

/** Palanca y aguja del dial del cuadro de mando de la silla, y la pantallita con los voltios. */
public class SillaRenderer implements BlockEntityRenderer<SillaElectricaBlockEntity> {
    public SillaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(SillaElectricaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float dial = Mth.lerp(pt, be.dialVisO, be.dialVis);
        float pal = Mth.lerp(pt, be.palancaVisO, be.palancaVis);
        boolean chispa = be.descargaCliente > 0;
        float t = be.getLevel() == null ? 0 : be.getLevel().getGameTime() + pt;
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        // palanca: casi vertical en reposo; al dar la descarga baja hacia quien la acciona
        ps.pushPose();
        ModelosAparatos.girarX(ps, ModelosAparatos.PALANCA_Y, ModelosAparatos.PALANCA_Z, Mth.lerp(pal, -15F, -100F));
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.SILLA_PALANCA, luz);
        ps.popPose();
        // aguja: de -120º (0 V) a +120º (máximo); tiembla durante la descarga
        float ang = -120F + 240F * dial + (chispa ? Mth.sin(t * 2.7F) * 6F : 0F);
        ps.pushPose();
        ModelosAparatos.girarZ(ps, ModelosAparatos.DIAL_X, ModelosAparatos.DIAL_Y, ang);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.SILLA_AGUJA, LightTexture.FULL_BRIGHT);
        ps.popPose();
        // pantallita: voltios (cara de delante del cuadro, que mira al norte)
        Font f = Minecraft.getInstance().font;
        int v = be.voltaje() * SillaElectricaBlockEntity.VOLTIOS;
        String txt = v + " V";
        int col = be.voltaje() >= SillaElectricaBlockEntity.MAX ? 0xFFFF3030 : be.voltaje() >= 6 ? 0xFFFFA030 : 0xFF60FF80;
        if (chispa && ((int) t & 2) == 0) col = 0xFFE8F4FF;
        ps.pushPose();
        ps.translate(ModelosAparatos.DIAL_X / 16F, (ModelosAparatos.DIAL_Y - 5.6F) / 16F, (ModelosAparatos.DIAL_Z - 0.5F) / 16F);
        float k = 1F / 16F / 7F;
        ps.scale(-k, -k, -k);
        f.drawInBatch(txt, -f.width(txt) / 2F, -4, col, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, LightTexture.FULL_BRIGHT);
        ps.popPose();
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(SillaElectricaBlockEntity be) {
        return true;
    }
}
