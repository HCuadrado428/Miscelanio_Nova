package com.miscelanea.client.armasfuego.comun;

import com.miscelanea.armasfuego.oeste.BolaCanonEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * El bolaño en vuelo: lo que se ha disparado (la roca, el bloque que cae, el yunque...) con su forma, dando vueltas
 * sobre sí mismo mientras vuela, en vez de un dibujo plano que mira siempre a la cámara.
 */
public class BolaCanonRenderer extends EntityRenderer<BolaCanonEntity> {
    private final ItemRenderer objetos;

    public BolaCanonRenderer(EntityRendererProvider.Context c) {
        super(c);
        objetos = c.getItemRenderer();
        shadowRadius = 0.25F;
    }

    @Override
    public void render(BolaCanonEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int luz) {
        ItemStack s = e.getItem();
        float t = e.tickCount + pt;
        ps.pushPose();
        ps.translate(0, 0.15F, 0);
        ps.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, e.yRotO, e.getYRot())));
        ps.mulPose(Axis.XP.rotationDegrees(t * 26F));
        ps.mulPose(Axis.ZP.rotationDegrees(t * 9F));
        float escala = s.getItem() instanceof BlockItem ? 2.4F : 1.5F;
        ps.scale(escala, escala, escala);
        objetos.renderStatic(s, ItemDisplayContext.GROUND, luz, OverlayTexture.NO_OVERLAY, ps, buf, e.level(), e.getId());
        ps.popPose();
        super.render(e, yaw, pt, ps, buf, luz);
    }

    @Override
    public ResourceLocation getTextureLocation(BolaCanonEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
