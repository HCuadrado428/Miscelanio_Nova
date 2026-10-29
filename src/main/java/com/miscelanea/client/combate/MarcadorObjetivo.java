package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Retícula giratoria sobre el enemigo fijado. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MarcadorObjetivo {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/misc/fijado.png");

    private MarcadorObjetivo() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var obj = CamaraCombate.fijado();
        if (obj == null || !CamaraCombate.activa()) return;
        Minecraft mc = Minecraft.getInstance();
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Vec3 c = obj.getPosition(pt).add(0, obj.getBbHeight() * 0.55, 0);
        c = c.add(cam.subtract(c).normalize().scale(obj.getBbWidth() * 0.7 + 0.3));   // por delante del mob
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(c.x - cam.x, c.y - cam.y, c.z - cam.z);
        ps.mulPose(e.getCamera().rotation());
        ps.mulPose(Axis.ZP.rotationDegrees((mc.player.tickCount + pt) * 4));
        float s = Math.max(0.45F, obj.getBbWidth() * 0.9F) * (1 + 0.06F * (float) Math.sin((mc.player.tickCount + pt) * 0.3));
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        RenderType rt = RenderType.entityTranslucentEmissive(TEX);
        VertexConsumer vc = buf.getBuffer(rt);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        int l = LightTexture.FULL_BRIGHT;
        vc.vertex(m, -s, -s, 0).color(255, 255, 255, 230).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, -s, 0).color(255, 255, 255, 230).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, s, s, 0).color(255, 255, 255, 230).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, -s, s, 0).color(255, 255, 255, 230).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(l).normal(n, 0, 1, 0).endVertex();
        buf.endBatch(rt);
        ps.popPose();
    }
}
