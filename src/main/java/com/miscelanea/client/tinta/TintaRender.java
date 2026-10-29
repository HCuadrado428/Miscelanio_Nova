package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.Tinta;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.Map;

/** Dibuja la tinta: cada cara pintada se une con sus vecinas del mismo color (charco continuo) y lleva una capa de reflejos. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TintaRender {
    private static final ResourceLocation MANCHAS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tinta.png");
    private static final ResourceLocation BRILLOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tinta_brillo.png");
    private static final float SEP = 0.004F;

    private TintaRender() {}

    @SubscribeEvent
    public static void descargar(ChunkEvent.Unload e) {
        if (e.getLevel().isClientSide()) Tinta.CLIENTE.remove(e.getChunk().getPos().toLong());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || Tinta.CLIENTE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = e.getCamera().getPosition();
        int dist = Math.min(8, mc.options.getEffectiveRenderDistance());
        ChunkPos cc = new ChunkPos(BlockPos.containing(cam));
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (int pasada = 0; pasada < 2; pasada++) {
        RenderType tipo = RenderType.entityTranslucent(pasada == 0 ? MANCHAS : BRILLOS);
        VertexConsumer vc = buf.getBuffer(tipo);
        BlockPos.MutableBlockPos fuera = new BlockPos.MutableBlockPos();
        for (Map.Entry<Long, Map<Long, Tinta.Mancha>> ech : Tinta.CLIENTE.entrySet()) {
            ChunkPos cp = new ChunkPos(ech.getKey());
            if (Math.abs(cp.x - cc.x) > dist || Math.abs(cp.z - cc.z) > dist) continue;
            for (Map.Entry<Long, Tinta.Mancha> eb : ech.getValue().entrySet()) {
                BlockPos p = BlockPos.of(eb.getKey());
                Tinta.Mancha mancha = eb.getValue();
                for (int i = 0; i < 6; i++) {
                    int col = mancha.color[i];
                    if (col == 0) continue;
                    Direction d = Direction.from3DDataValue(i);
                    fuera.setWithOffset(p, d);
                    int luz = LevelRenderer.getLightColor(mc.level, fuera);
                    cara(vc, m, n, mc, p, d, col, luz, (int) (eb.getKey() * 31 + i * 7), pasada == 1);
                }
            }
        }
        buf.endBatch(tipo);
        }
        ps.popPose();
    }

    /**
     * Un quad pegado a la cara 'd' del bloque 'p'. La forma se elige según qué caras vecinas (en el mismo plano)
     * tienen tinta del mismo color, para que la tinta forme un charco continuo con borde ondulado solo por fuera.
     */
    private static void cara(VertexConsumer vc, Matrix4f m, Matrix3f n, Minecraft mc, BlockPos p, Direction d, int col, int luz,
                             int hash, boolean brillo) {
        float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        if (brillo) r = g = b = 1.0F;
        float x = p.getX(), y = p.getY(), z = p.getZ();
        float[][] q;   // 4 esquinas en sentido antihorario visto desde fuera; uv: (0,0) (0,1) (1,1) (1,0)
        float s = brillo ? SEP + 0.001F : SEP;
        switch (d) {
            case UP -> q = new float[][]{{x, y + 1 + s, z}, {x, y + 1 + s, z + 1}, {x + 1, y + 1 + s, z + 1}, {x + 1, y + 1 + s, z}};
            case DOWN -> q = new float[][]{{x, y - s, z + 1}, {x, y - s, z}, {x + 1, y - s, z}, {x + 1, y - s, z + 1}};
            case NORTH -> q = new float[][]{{x + 1, y + 1, z - s}, {x + 1, y, z - s}, {x, y, z - s}, {x, y + 1, z - s}};
            case SOUTH -> q = new float[][]{{x, y + 1, z + 1 + s}, {x, y, z + 1 + s}, {x + 1, y, z + 1 + s}, {x + 1, y + 1, z + 1 + s}};
            case WEST -> q = new float[][]{{x - s, y + 1, z}, {x - s, y, z}, {x - s, y, z + 1}, {x - s, y + 1, z + 1}};
            default -> q = new float[][]{{x + 1 + s, y + 1, z + 1}, {x + 1 + s, y, z + 1}, {x + 1 + s, y, z}, {x + 1 + s, y + 1, z}};
        }
        // bits: 1 = borde v=0 (esquinas 0-3), 2 = borde u=1 (2-3), 4 = borde v=1 (1-2), 8 = borde u=0 (0-1)
        int[][] bordes = {{0, 3}, {2, 3}, {1, 2}, {0, 1}};
        float cx = (q[0][0] + q[2][0]) / 2, cy = (q[0][1] + q[2][1]) / 2, cz = (q[0][2] + q[2][2]) / 2;
        int mascara = 0;
        for (int k = 0; k < 4; k++) {
            float[] a = q[bordes[k][0]], c = q[bordes[k][1]];
            int dx = Math.round(((a[0] + c[0]) / 2 - cx) * 2), dy = Math.round(((a[1] + c[1]) / 2 - cy) * 2), dz = Math.round(((a[2] + c[2]) / 2 - cz) * 2);
            if (Tinta.color(mc.level, p.offset(dx, dy, dz), d) == col) mascara |= 1 << k;
        }
        int idx = mascara + 16 * Math.floorMod(hash >> 2, 2);
        float eu = 0.5F / 512F, ev = 0.5F / 256F;   // medio texel hacia dentro para no sangrar con la loseta vecina
        float u0 = (idx % 8) / 8F + eu, u1 = (idx % 8 + 1) / 8F - eu;
        float v0 = (idx / 8) / 4F + ev, v1 = (idx / 8 + 1) / 4F - ev;
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int k = 0; k < 4; k++) {
            float[] c = q[k];
            vc.vertex(m, c[0], c[1], c[2]).color(r, g, b, 1.0F).uv(uv[k][0], uv[k][1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(luz).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
        }
    }
}
