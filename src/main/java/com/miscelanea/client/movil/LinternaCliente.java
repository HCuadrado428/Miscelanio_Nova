package com.miscelanea.client.movil;

import com.miscelanea.Miscelanea;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Haz visible de la linterna del móvil: un cono de luz suave (mezcla aditiva, se desvanece hacia el final y se corta donde
 * choca con un bloque) para los demás jugadores con la linterna encendida y para ti en tercera persona. La luz de verdad
 * la pone el servidor ({@code LinternaLuz}); en primera persona ya está el halo del HUD.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class LinternaCliente {
    private LinternaCliente() {}

    private static final int LADOS = 14;
    private static final float LARGO = 7F, RADIO = 1.5F;

    private static boolean encendida(Player p, Minecraft mc) {
        if (p == mc.player) return MovilCliente.linterna && !mc.options.getCameraType().isFirstPerson();
        return MovilCliente.LINTERNAS.contains(p.getId());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!MovilCliente.linterna && MovilCliente.LINTERNAS.isEmpty()) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f m = e.getPoseStack().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        boolean alguno = false;
        for (Player p : mc.level.players()) {
            if (!encendida(p, mc) || p.isInvisible() || p.distanceToSqr(cam) > 96 * 96) continue;
            if (!alguno) {
                bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
                alguno = true;
            }
            cono(bb, m, p, pt, cam, mc);
        }
        if (!alguno) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(bb.end());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void cono(BufferBuilder bb, Matrix4f m, Player p, float pt, Vec3 cam, Minecraft mc) {
        Vec3 dir = p.getViewVector(pt).normalize();
        Vec3 ojo = p.getEyePosition(pt);
        // sale de la mano (un poco delante y abajo de los ojos)
        Vec3 o = ojo.add(dir.scale(0.45)).add(0, -0.3, 0);
        float largo = LARGO;
        HitResult hit = mc.level.clip(new ClipContext(o, o.add(dir.scale(LARGO)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) largo = (float) Math.max(0.8, hit.getLocation().distanceTo(o));
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(ref).normalize(), v = dir.cross(u).normalize();
        float radio = RADIO * largo / LARGO;
        Vec3 fin = o.add(dir.scale(largo));
        float fade = largo / LARGO;
        int a0 = 60, a1 = (int) (14 * (1 - fade));
        for (int i = 0; i < LADOS; i++) {
            double t0 = i * Math.PI * 2 / LADOS, t1 = (i + 1) * Math.PI * 2 / LADOS;
            Vec3 r0 = fin.add(u.scale(Math.cos(t0) * radio)).add(v.scale(Math.sin(t0) * radio));
            Vec3 r1 = fin.add(u.scale(Math.cos(t1) * radio)).add(v.scale(Math.sin(t1) * radio));
            vertice(bb, m, o, cam, a0);
            vertice(bb, m, r0, cam, a1);
            vertice(bb, m, r1, cam, a1);
        }
    }

    private static void vertice(BufferBuilder bb, Matrix4f m, Vec3 p, Vec3 cam, int alfa) {
        bb.vertex(m, (float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z)).color(255, 244, 214, alfa).endVertex();
    }
}
