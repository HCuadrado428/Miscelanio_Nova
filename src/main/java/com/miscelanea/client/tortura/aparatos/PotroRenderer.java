package com.miscelanea.client.tortura.aparatos;

import com.miscelanea.tortura.aparatos.PotroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Rodillos (enrollan la cuerda según la tensión) y la rueda de la manivela del potro; y la barra de conversión del mob. */
public class PotroRenderer implements BlockEntityRenderer<PotroBlockEntity> {
    public PotroRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(PotroBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        float tension = Mth.lerp(pt, be.tensionVisO, be.tensionVis);
        float giro = Mth.lerp(pt, be.giroVisO, be.giroVis);
        // cinemática de muerte: la vuelta final de la manivela (y la tensión, a tope)
        float c = com.miscelanea.client.tortura.muertes.MuertesCliente.canal(be.victimaId(), "manivela", pt);
        if (!Float.isNaN(c)) {
            giro += c;
            tension = Math.max(tension, 1F);
        }
        float y = ModelosAparatos.RODILLO_Y;
        ps.pushPose();
        ModelosAparatos.orientar(ps, be.frente());
        // rodillo de los pies + manivela (mismo eje)
        ps.pushPose();
        ModelosAparatos.girarX(ps, y, ModelosAparatos.RODILLO_PIES_Z, -giro * 0.5F - tension * 300F);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.POTRO_RODILLO, luz);
        ps.popPose();
        ps.pushPose();
        ModelosAparatos.girarX(ps, y, ModelosAparatos.RODILLO_PIES_Z, -giro);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.POTRO_MANIVELA, luz);
        ps.popPose();
        // rodillo de la cabecera (el mismo modelo, desplazado), gira al revés
        ps.pushPose();
        ps.translate(0, 0, (ModelosAparatos.RODILLO_CABEZA_Z - ModelosAparatos.RODILLO_PIES_Z) / 16F);
        ModelosAparatos.girarX(ps, y, ModelosAparatos.RODILLO_PIES_Z, giro * 0.5F + tension * 300F);
        ModelosAparatos.dibujar(ps, buf, ModelosAparatos.POTRO_RODILLO, luz);
        ps.popPose();
        ps.popPose();
        if (be.ocupado() && !be.victimaJugador() && be.conversion() > 0F) barra(be, ps, buf);
    }

    private static void barra(PotroBlockEntity be, PoseStack ps, MultiBufferSource buf) {
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        Vec3 c = be.centroMesa().subtract(Vec3.atLowerCornerOf(be.getBlockPos())).add(0, 1.55, 0);
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.025F, -0.025F, 0.025F);
        Matrix4f m = ps.last().pose();
        int pct = Math.round(be.conversion() * 100);
        String t = Component.translatable("hud.miscelanea.potro.conversion", pct).getString();
        f.drawInBatch(t, -f.width(t) / 2F, -12, be.desmayado() ? 0xFF9A9AA8 : 0xFFE6C2FF, false, m, buf, Font.DisplayMode.NORMAL, 0x70000000,
                LightTexture.FULL_BRIGHT);
        String fondo = " ".repeat(25);
        float x0 = -f.width(fondo) / 2F;
        f.drawInBatch(fondo, x0, 0, 0, false, m, buf, Font.DisplayMode.NORMAL, 0xC0181018, LightTexture.FULL_BRIGHT);
        int n = Math.round(be.conversion() * 25);
        if (n > 0) {
            ps.translate(0, 0, -0.6F);
            f.drawInBatch(" ".repeat(n), x0, 0, 0, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0xF0B04CE8, LightTexture.FULL_BRIGHT);
        }
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(PotroBlockEntity be) {
        return true;
    }
}
