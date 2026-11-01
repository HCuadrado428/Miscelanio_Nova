package com.miscelanea.client.arcade;

import com.miscelanea.arcade.RecreativaBlock;
import com.miscelanea.arcade.RecreativaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/** Dibuja la pantalla de la recreativa: lo que juega alguien (retransmitido) o la demostración con los récords. */
public class RecreativaRenderer implements BlockEntityRenderer<RecreativaBlockEntity> {
    private static final int LUZ = 0xF000F0;

    public RecreativaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(RecreativaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Direction f = be.getBlockState().getValue(RecreativaBlock.FACING);
        Pantalla lcd;
        PantallaTextura tex;
        if (be.pantallaCliente instanceof ArcadeCliente.Remota r && r.tex != null && System.currentTimeMillis() - r.ultimo < 2500) {
            lcd = r.pantalla;
            tex = r.tex;
        } else {
            ArcadeCliente.demoUsada = true;
            lcd = ArcadeCliente.DEMO;
            tex = ArcadeCliente.demoTex;
            if (tex == null) return;
        }
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180 - f.toYRot()));
        ps.translate(-0.5, 0, -0.5);
        // plano de la pantalla (mitad de arriba, inclinada hacia atrás); en la doble es más ancha
        boolean doble = be.getBlockState().getBlock() instanceof RecreativaBlock rb && rb.doble;
        float s = 1 / 16F;
        float ancho = doble ? 14F : 12F, alto = doble ? 10.5F : 9F, x0 = doble ? 15F : 14F, y0 = doble ? 28.8F : 28.4F;
        float ux = -ancho / Pantalla.W, vy = -alto / Pantalla.H, vz = (alto * 0.21F) / Pantalla.H;
        float nz = -0.05F;
        Matrix4f m = new Matrix4f(ux * s, 0, 0, 0, 0, vy * s, vz * s, 0, 0, 0, nz * s, 0, x0 * s, y0 * s, 8.7F * s, 1);
        ps.last().pose().mul(m);
        Matrix4f pm = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.text(tex.rl));
        vc.vertex(pm, 0, 0, 0).color(255, 255, 255, 255).uv(0, 0).uv2(LUZ).endVertex();
        vc.vertex(pm, 0, Pantalla.H, 0).color(255, 255, 255, 255).uv(0, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Pantalla.W, Pantalla.H, 0).color(255, 255, 255, 255).uv(1, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Pantalla.W, 0, 0).color(255, 255, 255, 255).uv(1, 0).uv2(LUZ).endVertex();
        Font font = Minecraft.getInstance().font;
        ps.translate(0, 0, 0.5F);
        Matrix4f tm = ps.last().pose();
        for (Object[] t : lcd.textos)
            font.drawInBatch((String) t[0], (int) t[1], (int) t[2], lcd.color((int) t[3]), false, tm, buf, Font.DisplayMode.POLYGON_OFFSET, 0, LUZ);
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
