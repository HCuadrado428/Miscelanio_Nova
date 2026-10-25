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

/** Dibuja la tinta: una mancha de forma irregular (4 variantes, giradas al azar) pegada a cada cara pintada. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TintaRender {
    private static final ResourceLocation MANCHAS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/tinta.png");
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
        VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(MANCHAS));
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
                    cara(vc, m, n, p, d, col, luz, (int) (eb.getKey() * 31 + i * 7));
                }
            }
        }
        buf.endBatch(RenderType.entityTranslucent(MANCHAS));
        ps.popPose();
    }

    /** Un quad pegado a la cara 'd' del bloque 'p'. */
    private static void cara(VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos p, Direction d, int col, int luz, int hash) {
        float r = ((col >> 16) & 255) / 255F, g = ((col >> 8) & 255) / 255F, b = (col & 255) / 255F;
        int variante = Math.floorMod(hash, 4);
        int giro = Math.floorMod(hash >> 3, 4);
        float u0 = variante / 4F, u1 = (variante + 1) / 4F;
        float x = p.getX(), y = p.getY(), z = p.getZ();
        float[][] q;   // 4 esquinas en sentido antihorario visto desde fuera
        float s = SEP;
        switch (d) {
            case UP -> q = new float[][]{{x, y + 1 + s, z}, {x, y + 1 + s, z + 1}, {x + 1, y + 1 + s, z + 1}, {x + 1, y + 1 + s, z}};
            case DOWN -> q = new float[][]{{x, y - s, z + 1}, {x, y - s, z}, {x + 1, y - s, z}, {x + 1, y - s, z + 1}};
            case NORTH -> q = new float[][]{{x + 1, y + 1, z - s}, {x + 1, y, z - s}, {x, y, z - s}, {x, y + 1, z - s}};
            case SOUTH -> q = new float[][]{{x, y + 1, z + 1 + s}, {x, y, z + 1 + s}, {x + 1, y, z + 1 + s}, {x + 1, y + 1, z + 1 + s}};
            case WEST -> q = new float[][]{{x - s, y + 1, z}, {x - s, y, z}, {x - s, y, z + 1}, {x - s, y + 1, z + 1}};
            default -> q = new float[][]{{x + 1 + s, y + 1, z + 1}, {x + 1 + s, y, z + 1}, {x + 1 + s, y, z}, {x + 1 + s, y + 1, z}};
        }
        float[][] uv = {{u0, 0}, {u0, 1}, {u1, 1}, {u1, 0}};
        for (int k = 0; k < 4; k++) {
            float[] c = q[k];
            float[] t = uv[(k + giro) % 4];
            vc.vertex(m, c[0], c[1], c[2]).color(r, g, b, 1.0F).uv(t[0], t[1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(luz).normal(n, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
        }
    }
}
