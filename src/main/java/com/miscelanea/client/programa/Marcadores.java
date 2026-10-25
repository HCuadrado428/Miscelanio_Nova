package com.miscelanea.client.programa;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.CadaverBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Marcas en el mundo como en el juego: sobre los cadáveres ya mirados aparece su símbolo, y mientras te persiguen
 * los cadáveres sin registrar se marcan con "?".
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Marcadores {
    private static final List<BlockPos> CADAVERES = new ArrayList<>();

    private Marcadores() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ProgramaHud.enPrograma() || mc.player.tickCount % 20 != 0) return;
        CADAVERES.clear();
        BlockPos c = mc.player.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(c.offset(-24, -6, -24), c.offset(24, 6, 24))) {
            var s = mc.level.getBlockState(p);
            if (s.getBlock() instanceof CadaverBlock && !s.getValue(CadaverBlock.REGISTRADO)) CADAVERES.add(p.immutable());
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !ProgramaHud.enPrograma() || !ProgramaHud.activo) return;
        Minecraft mc = Minecraft.getInstance();
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        for (BlockPos p : CADAVERES) {
            Integer sim = ProgramaHud.cadaveres.get(p);
            if (sim == null && !ProgramaHud.perseguido) continue;
            ps.pushPose();
            ps.translate(p.getX() + 0.5 - cp.x, p.getY() + 1.1 - cp.y, p.getZ() + 0.5 - cp.z);
            ps.mulPose(cam.rotation());
            float esc = 0.025F;
            ps.scale(-esc, -esc, esc);
            Matrix4f m = ps.last().pose();
            if (sim != null) {
                VertexConsumer vc = buf.getBuffer(RenderType.textSeeThrough(ProgramaHud.SIMBOLOS));
                float u0 = sim / 4.0F, u1 = (sim + 1) / 4.0F;
                int l = LightTexture.FULL_BRIGHT;
                vc.vertex(m, -8, -8, 0).color(255, 255, 255, 220).uv(u0, 0).uv2(l).endVertex();
                vc.vertex(m, -8, 8, 0).color(255, 255, 255, 220).uv(u0, 1).uv2(l).endVertex();
                vc.vertex(m, 8, 8, 0).color(255, 255, 255, 220).uv(u1, 1).uv2(l).endVertex();
                vc.vertex(m, 8, -8, 0).color(255, 255, 255, 220).uv(u1, 0).uv2(l).endVertex();
            } else {
                String t = "?";
                font.drawInBatch(t, -font.width(t) / 2F, -4, 0xFFFFFFFF, false, m, buf, Font.DisplayMode.SEE_THROUGH, 0x60000000, LightTexture.FULL_BRIGHT);
            }
            ps.popPose();
        }
        buf.endBatch();
    }
}
