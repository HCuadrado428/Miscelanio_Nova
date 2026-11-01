package com.miscelanea.client.feria;

import com.miscelanea.feria.VagonetaBruja;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Vagoneta del tren de la bruja, inclinada según la vía como las vagonetas normales. */
public class VagonetaBrujaRenderer extends EntityRenderer<VagonetaBruja> {
    public VagonetaBrujaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.7F;
    }

    @Override
    public void render(VagonetaBruja e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ps.pushPose();
        double x = Mth.lerp(pt, e.xOld, e.getX()), y = Mth.lerp(pt, e.yOld, e.getY()), z = Mth.lerp(pt, e.zOld, e.getZ());
        Vec3 pos = e.getPos(x, y, z);
        float pitch = Mth.lerp(pt, e.xRotO, e.getXRot());
        if (pos != null) {
            Vec3 a = e.getPosOffs(x, y, z, 0.3), b = e.getPosOffs(x, y, z, -0.3);
            if (a == null) a = pos;
            if (b == null) b = pos;
            ps.translate(pos.x - x, (a.y + b.y) / 2 - y, pos.z - z);
            Vec3 d = b.add(-a.x, -a.y, -a.z);
            if (d.length() != 0) {
                d = d.normalize();
                yaw = (float) (Math.atan2(d.z, d.x) * 180 / Math.PI);
                pitch = (float) (Math.atan(d.y) * 73);
            }
        }
        ps.translate(0, 0.375, 0);
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        ps.mulPose(Axis.ZP.rotationDegrees(-pitch));
        float golpe = e.getHurtTime() - pt, dano = Math.max(0, e.getDamage() - pt);
        if (golpe > 0) ps.mulPose(Axis.XP.rotationDegrees(Mth.sin(golpe) * golpe * dano / 10 * e.getHurtDir()));
        ps.translate(-0.5, -0.375, -0.5);
        FeriaPiezas.modelo(ps, buf.getBuffer(Sheets.cutoutBlockSheet()), FeriaPiezas.rl("vagoneta"), 1, 1, 1, luz);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(VagonetaBruja e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
