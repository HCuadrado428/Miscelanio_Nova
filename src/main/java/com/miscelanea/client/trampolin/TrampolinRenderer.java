package com.miscelanea.client.trampolin;

import com.miscelanea.Miscelanea;
import com.miscelanea.trampolin.TrampolinBlock;
import com.miscelanea.trampolin.TrampolinBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/** Lona del trampolín como una malla que se hunde bajo quien esté encima (y entre trampolines unidos sin costuras). */
public class TrampolinRenderer implements BlockEntityRenderer<TrampolinBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/trampolin_lona.png");
    private static final int G = 8;

    public TrampolinRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(TrampolinBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        if (be.getLevel() == null) return;
        BlockState s = be.getBlockState();
        float bx = be.getBlockPos().getX(), by = be.getBlockPos().getY(), bz = be.getBlockPos().getZ();
        float x0 = s.getValue(TrampolinBlock.WEST) ? 0 : 3.6F / 16, x1 = s.getValue(TrampolinBlock.EAST) ? 1 : 12.4F / 16;
        float z0 = s.getValue(TrampolinBlock.NORTH) ? 0 : 3.6F / 16, z1 = s.getValue(TrampolinBlock.SOUTH) ? 1 : 12.4F / 16;
        List<Entity> encima = be.getLevel().getEntities((Entity) null, new AABB(be.getBlockPos()).inflate(1.2, 0.2, 1.2).move(0, 0.3, 0), e -> !e.isSpectator());
        float[][] h = new float[G + 1][G + 1];
        for (int i = 0; i <= G; i++)
            for (int j = 0; j <= G; j++) {
                float x = x0 + (x1 - x0) * i / G, z = z0 + (z1 - z0) * j / G;
                float hundido = 0;
                for (Entity e : encima) {
                    double ex = e.getX() + (e.getX() - e.xOld) * (pt - 1) - bx, ez = e.getZ() + (e.getZ() - e.zOld) * (pt - 1) - bz;
                    double ey = e.yOld + (e.getY() - e.yOld) * pt - by;
                    double prof = TrampolinBlock.LONA - ey;
                    if (prof <= -0.3) continue;
                    double fuerza = Math.max(0, Math.min(0.26, prof + 0.04));
                    double d2 = (x - ex) * (x - ex) + (z - ez) * (z - ez);
                    hundido = (float) Math.max(hundido, fuerza * Math.exp(-d2 / 0.22));
                }
                // los bordes de la lona están sujetos a los muelles: allí no se hunde
                float borde = 1;
                if (x0 > 0) borde = Math.min(borde, Math.min(1, (x - x0) / 0.12F));
                if (x1 < 1) borde = Math.min(borde, Math.min(1, (x1 - x) / 0.12F));
                if (z0 > 0) borde = Math.min(borde, Math.min(1, (z - z0) / 0.12F));
                if (z1 < 1) borde = Math.min(borde, Math.min(1, (z1 - z) / 0.12F));
                h[i][j] = (float) TrampolinBlock.LONA - hundido * Math.max(0, borde);
            }
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (int i = 0; i < G; i++)
            for (int j = 0; j < G; j++) {
                float xa = x0 + (x1 - x0) * i / G, xb = x0 + (x1 - x0) * (i + 1) / G;
                float za = z0 + (z1 - z0) * j / G, zb = z0 + (z1 - z0) * (j + 1) / G;
                v(vc, m, n, xa, h[i][j], za, luz);
                v(vc, m, n, xa, h[i][j + 1], zb, luz);
                v(vc, m, n, xb, h[i + 1][j + 1], zb, luz);
                v(vc, m, n, xb, h[i + 1][j], za, luz);
            }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, int luz) {
        vc.vertex(m, x, y, z).color(255, 255, 255, 255).uv(x, z).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
