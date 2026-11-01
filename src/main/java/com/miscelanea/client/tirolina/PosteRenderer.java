package com.miscelanea.client.tirolina;

import com.miscelanea.Miscelanea;
import com.miscelanea.tirolina.Cable;
import com.miscelanea.tirolina.PosteTirolinaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** El cable de acero entre dos postes, colgando, con la luz de cada tramo. */
public class PosteRenderer implements BlockEntityRenderer<PosteTirolinaBlockEntity> {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/tirolina_cable.png");
    public static final ResourceLocation CABEZA = new ResourceLocation(Miscelanea.MOD_ID, "block/poste_tirolina_cabeza");

    public PosteRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(PosteTirolinaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        if (be.getLevel() == null) return;
        // cabeza del poste (tercer bloque) con la polea
        var mc = net.minecraft.client.Minecraft.getInstance();
        ps.pushPose();
        ps.translate(0, 2, 0);
        mc.getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet()), null,
                mc.getModelManager().getModel(CABEZA), 1F, 1F, 1F, LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(2)), OverlayTexture.NO_OVERLAY,
                net.minecraftforge.client.model.data.ModelData.EMPTY, null);
        ps.popPose();
        if (be.destino == null) return;
        Vec3 a = Cable.anclaje(be.getBlockPos()), b = Cable.anclaje(be.destino);
        Vec3 o = Vec3.atLowerCornerOf(be.getBlockPos());
        int n = Math.max(8, (int) (a.distanceTo(b) * 1.5));
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        double r = 0.035;
        double acum = 0;
        Vec3 prev = Cable.punto(a, b, 0);
        for (int i = 0; i < n; i++) {
            Vec3 p0 = prev, p1 = Cable.punto(a, b, (i + 1) / (double) n);
            Vec3 d = p1.subtract(p0);
            Vec3 lado = d.cross(new Vec3(0, 1, 0)).normalize().scale(r);
            Vec3 arriba = d.cross(lado).normalize().scale(r);
            int l = LevelRenderer.getLightColor(be.getLevel(), BlockPos.containing(p0));
            float v0 = (float) (acum * 3), v1 = (float) ((acum + d.length()) * 3);
            cinta(vc, m, nm, p0.subtract(o), p1.subtract(o), lado, v0, v1, l);
            cinta(vc, m, nm, p0.subtract(o), p1.subtract(o), arriba, v0, v1, l);
            acum += d.length();
            prev = p1;
        }
    }

    private static void cinta(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p0, Vec3 p1, Vec3 off, float v0, float v1, int luz) {
        punto(vc, m, n, p0.subtract(off), 0, v0, luz);
        punto(vc, m, n, p0.add(off), 1, v0, luz);
        punto(vc, m, n, p1.add(off), 1, v1, luz);
        punto(vc, m, n, p1.subtract(off), 0, v1, luz);
    }

    private static void punto(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int luz) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(PosteTirolinaBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
