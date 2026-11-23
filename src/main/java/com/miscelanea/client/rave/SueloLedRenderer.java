package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.rave.Neon;
import com.miscelanea.rave.SueloLedBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

/**
 * La luz del suelo LED: un cuadrado de color encima de cada baldosa. Los colores van en diagonal y avanzan solos; si suena
 * una mesa de DJ cerca, avanzan un paso por pulso y la baldosa late con el ritmo.
 */
public class SueloLedRenderer implements BlockEntityRenderer<SueloLedBlockEntity> {
    private static final ResourceLocation LUZ = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/baldosa_led.png");
    private static final DyeColor[] COLORES = {DyeColor.MAGENTA, DyeColor.CYAN, DyeColor.LIME, DyeColor.YELLOW, DyeColor.ORANGE, DyeColor.PURPLE, DyeColor.BLUE, DyeColor.PINK};

    public SueloLedRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(SueloLedBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        BlockPos p = be.getBlockPos();
        float pulsos = MusicaDj.pulsos(p, pt);
        int paso;
        float brillo;
        if (pulsos >= 0) {
            paso = (int) pulsos;
            float k = 1 - (pulsos % 1F);
            brillo = 0.55F + 0.45F * k * k;
        } else {
            paso = (int) ((be.getLevel().getGameTime() + pt) / 12);
            brillo = 0.75F;
        }
        // ondas en diagonal: cada fila de la diagonal va un color por detrás
        int i = Math.floorMod(p.getX() + p.getZ() + paso, COLORES.length);
        if (Math.floorMod(p.getX() - p.getZ() + paso / 2, 5) == 0) i = Math.floorMod(i + 3, COLORES.length);
        int rgb = Neon.rgb(COLORES[i]);
        float r = ((rgb >> 16) & 255) / 255F * brillo, g = ((rgb >> 8) & 255) / 255F * brillo, b = (rgb & 255) / 255F * brillo;
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(LUZ));
        var m = ps.last().pose();
        var n = ps.last().normal();
        float y = 1.002F, a = 0.06F, z = 0.94F;
        vc.vertex(m, a, y, a).color(r, g, b, 1F).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, a, y, z).color(r, g, b, 1F).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, z, y, z).color(r, g, b, 1F).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, z, y, a).color(r, g, b, 1F).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }
}
