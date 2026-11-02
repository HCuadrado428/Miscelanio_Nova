package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.castigo.BolaPresoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** La bola de hierro (modelo de bloque, centrado en 8,4,8) rodando; en brazos, delante del pecho del preso. */
public class BolaRenderer extends EntityRenderer<BolaPresoEntity> {
    public static final ResourceLocation MODELO = new ResourceLocation(Miscelanea.MOD_ID, "block/tortura/bola_preso");

    public BolaRenderer(EntityRendererProvider.Context c) {
        super(c);
        shadowRadius = 0.25F;
    }

    @Override
    public ResourceLocation getTextureLocation(BolaPresoEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(BolaPresoEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        Vec3 pos = e.getPosition(pt);
        Vec3 c = pos.add(0, BolaPresoEntity.RADIO, 0);
        LivingEntity preso = e.preso();
        boolean brazos = e.estado() == BolaPresoEntity.EN_BRAZOS && preso != null;
        if (brazos) c = BolaPresoEntity.enBrazos(preso, pt).add(0, BolaPresoEntity.RADIO, 0);
        ps.pushPose();
        ps.translate(c.x - pos.x, c.y - pos.y, c.z - pos.z);
        if (!brazos) ps.mulPose(new Quaternionf(e.giroO).slerp(e.giro, pt));
        // la argolla de la cadena (arriba de la bola, en el modelo a 8, 9, 8)
        CastigoCliente.capturarArgolla(e.getId(), ps.last().pose().transformPosition(new Vector3f(0, 4.6F / 16F, 0)));
        ps.translate(-0.5F, -0.25F, -0.5F);
        BakedModel bm = Minecraft.getInstance().getModelManager().getModel(MODELO);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buf.getBuffer(Sheets.cutoutBlockSheet()), null, bm,
                1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }
}
