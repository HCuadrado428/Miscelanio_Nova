package com.miscelanea.client.armasfuego.rifle;

import com.miscelanea.armasfuego.rifle.BalaRifle;
import com.miscelanea.armasfuego.rifle.Rifle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * La bala en vuelo: una raya fina encarada a la cámara a lo largo de su trayectoria (de cobre la normal, marrón la
 * de cuerda y larga y brillante la trazadora). Solo unos pocos vértices.
 */
public class BalaRifleRenderer extends EntityRenderer<BalaRifle> {
    public BalaRifleRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public void render(BalaRifle e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vec3 mov = new Vec3(e.getX() - e.xo, e.getY() - e.yo, e.getZ() - e.zo);
        if (mov.lengthSqr() < 1e-4) mov = Vec3.directionFromRotation(e.getXRot(), e.getYRot()).scale(Rifle.VELOCIDAD);
        int tipo = e.tipo();
        double largo = tipo == Rifle.TRAZADORA ? 3.5 : 1.4;
        Vec3 atras = mov.normalize().scale(-largo);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 pos = new Vec3(e.xo + (e.getX() - e.xo) * pt, e.yo + (e.getY() - e.yo) * pt, e.zo + (e.getZ() - e.zo) * pt);
        Vec3 haciaCam = cam.subtract(pos);
        Vec3 lado = atras.cross(haciaCam);
        if (lado.lengthSqr() < 1e-8) return;
        float ancho = tipo == Rifle.TRAZADORA ? 0.06F : 0.025F;
        lado = lado.normalize().scale(ancho);
        int r, g, b;
        float a0, a1;
        if (tipo == Rifle.TRAZADORA) { r = 255; g = 170; b = 60; a0 = 1F; a1 = 0F; }
        else if (tipo == Rifle.CUERDA) { r = 150; g = 115; b = 70; a0 = 0.9F; a1 = 0.2F; }
        else { r = 240; g = 200; b = 150; a0 = 0.75F; a1 = 0F; }
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        quad(vc, m, Vec3.ZERO, atras, lado, r, g, b, a0, a1);
        if (tipo == Rifle.TRAZADORA) quad(vc, m, Vec3.ZERO, atras.scale(0.5), lado.scale(0.4), 255, 255, 220, 1F, 0F);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 lado, int r, int g, int bl, float a0, float a1) {
        v(vc, m, a.subtract(lado), r, g, bl, a0);
        v(vc, m, a.add(lado), r, g, bl, a0);
        v(vc, m, b.add(lado), r, g, bl, a1);
        v(vc, m, b.subtract(lado), r, g, bl, a1);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, (int) (a * 255)).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(BalaRifle e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
