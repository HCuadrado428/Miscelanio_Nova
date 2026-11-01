package com.miscelanea.client.arcade;

import com.miscelanea.arcade.RecreativaBlock;
import com.miscelanea.arcade.RecreativaBlockEntity;
import com.miscelanea.client.consola.Lcd;
import com.miscelanea.client.consola.LcdTextura;
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
        Lcd lcd;
        LcdTextura tex;
        if (be.pantallaCliente instanceof ArcadeCliente.Remota r && r.tex != null && System.currentTimeMillis() - r.ultimo < 2500) {
            lcd = r.lcd;
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
        // plano de la pantalla (mitad de arriba, inclinada hacia atrás): esquina sup-izq vista desde delante = (13.5, 28.5, 8.7)
        float s = 1 / 16F;
        float ux = -11F / Lcd.W, vy = -9.9F / Lcd.H, vz = 2.1F / Lcd.H;
        float nz = -0.05F;
        Matrix4f m = new Matrix4f(ux * s, 0, 0, 0, 0, vy * s, vz * s, 0, 0, 0, nz * s, 0, 13.5F * s, 28.5F * s, 8.7F * s, 1);
        ps.last().pose().mul(m);
        Matrix4f pm = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.text(tex.rl));
        vc.vertex(pm, 0, 0, 0).color(255, 255, 255, 255).uv(0, 0).uv2(LUZ).endVertex();
        vc.vertex(pm, 0, Lcd.H, 0).color(255, 255, 255, 255).uv(0, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Lcd.W, Lcd.H, 0).color(255, 255, 255, 255).uv(1, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Lcd.W, 0, 0).color(255, 255, 255, 255).uv(1, 0).uv2(LUZ).endVertex();
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
