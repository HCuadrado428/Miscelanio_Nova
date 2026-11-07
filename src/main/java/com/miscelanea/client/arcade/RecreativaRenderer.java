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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.function.BiFunction;

/**
 * Dibuja la pantalla de la recreativa: lo que juega alguien en tiempo real (retransmitido 10 veces por segundo, o lo del
 * propio jugador) o, si nadie juega, la demostración con los récords. Las dos llevan el toque de tubo de imagen.
 */
public class RecreativaRenderer implements BlockEntityRenderer<RecreativaBlockEntity> {
    private static final int LUZ = 0xF000F0;

    /**
     * Gancho del cliente para otros sistemas (p. ej. el hombre estático metido en una máquina): si no es null y para esta
     * recreativa (posición de su mitad de abajo, nivel del cliente) devuelve una textura, se dibuja esa a pantalla completa
     * en vez del juego o la demostración (sin textos encima). Devolver null deja la pantalla normal.
     */
    @Nullable
    public static BiFunction<BlockPos, Level, ResourceLocation> PANTALLA_ESPECIAL;

    public RecreativaRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(RecreativaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Direction f = be.getBlockState().getValue(RecreativaBlock.FACING);
        Pantalla lcd;
        ResourceLocation rl = null;
        if (PANTALLA_ESPECIAL != null && be.getLevel() != null) {
            try {
                rl = PANTALLA_ESPECIAL.apply(be.getBlockPos(), be.getLevel());
            } catch (RuntimeException ignored) {
            }
        }
        PantallaTextura tex;
        if (rl != null) {
            lcd = null;
            tex = null;
        } else if (be.pantallaCliente instanceof ArcadeCliente.Remota r && r.tex != null && System.currentTimeMillis() - r.ultimo < 2500) {
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
        float s = 1 / 16F;
        Matrix4f m;
        if (be.getBlockState().getBlock() instanceof com.miscelanea.arcade.MesaCoctelBlock) {
            // mesa cóctel: pantalla tumbada en el tablero, derecha para el jugador 1 (lado de delante)
            float ancho = 12F, alto = 9F;
            m = new Matrix4f(-ancho / Pantalla.W * s, 0, 0, 0, 0, 0, -alto / Pantalla.H * s, 0, 0, 0.05F * s, 0, 0, 14F * s, 12.56F * s, 12.5F * s, 1);
        } else {
            // plano de la pantalla (mitad de arriba, inclinada hacia atrás); en la doble es más ancha
            boolean doble = be.getBlockState().getBlock() instanceof RecreativaBlock rb && rb.doble;
            float ancho = doble ? 14F : 12F, alto = doble ? 10.5F : 9F, x0 = doble ? 15F : 14F, y0 = doble ? 28.8F : 28.4F;
            // inclinada hacia atrás: arriba pegada al fondo del hueco (z = 9) y abajo un poco más hacia el jugador. Antes
            // se inclinaba al revés y la parte de abajo quedaba metida dentro del mueble (solo se veía una franja arriba)
            float ux = -ancho / Pantalla.W, vy = -alto / Pantalla.H, vz = -(alto * 0.21F) / Pantalla.H;
            float nz = -0.05F;
            m = new Matrix4f(ux * s, 0, 0, 0, 0, vy * s, vz * s, 0, 0, 0, nz * s, 0, x0 * s, y0 * s, 8.85F * s, 1);
        }
        ps.last().pose().mul(m);
        Matrix4f pm = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.text(rl != null ? rl : tex.rl));
        vc.vertex(pm, 0, 0, 0).color(255, 255, 255, 255).uv(0, 0).uv2(LUZ).endVertex();
        vc.vertex(pm, 0, Pantalla.H, 0).color(255, 255, 255, 255).uv(0, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Pantalla.W, Pantalla.H, 0).color(255, 255, 255, 255).uv(1, 1).uv2(LUZ).endVertex();
        vc.vertex(pm, Pantalla.W, 0, 0).color(255, 255, 255, 255).uv(1, 0).uv2(LUZ).endVertex();
        if (lcd == null) {
            ps.popPose();
            return;
        }
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
