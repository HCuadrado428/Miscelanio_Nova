package com.miscelanea.client.baile;

import com.miscelanea.baile.PistaBaileBlock;
import com.miscelanea.baile.CancionesBaile;
import com.miscelanea.baile.PistaBaileBlockEntity;
import com.miscelanea.client.arcade.PasoDoble;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;

/** Las flechas de la pista se encienden con cada paso del que baila (y laten suaves al ritmo mientras suena la canción). */
public class PistaBaileRenderer implements BlockEntityRenderer<PistaBaileBlockEntity> {
    static final int[] COLOR = {0xB070F0, 0x5ADCFF, 0x69DC67, 0xFF5050};
    /** Centro de cada flecha en la pista (px, mirando al norte): izquierda, abajo, arriba, derecha. */
    static final float[][] SITIO = {{3.6F, 8}, {8, 12.4F}, {8, 3.6F}, {12.4F, 8}};

    public PistaBaileRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public void render(PistaBaileBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.bailarin == null || be.getLevel() == null) return;
        Player p = be.getLevel().getPlayerByUUID(be.bailarin);
        if (p == null) return;
        // los que miran: el baile va con la canción de la pista, aunque se llegue a mitad (el propio lo lleva BaileCliente)
        if (p != Minecraft.getInstance().player) {
            CancionesBaile.Cancion c = CancionesBaile.de(be.cancion);
            long ahora = System.currentTimeMillis();
            BailePoses.sincronizar(p.getId(), c, c == null ? ahora : ahora - Math.max(0, be.getLevel().getGameTime() - be.inicio) * 50);
        }
        long[] u = BailePoses.ultimo(p.getId());
        Direction f = be.getBlockState().getValue(PistaBaileBlock.FACING);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180 - f.toYRot()));
        ps.translate(-0.5, 2.02 / 16, -0.5);
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.textBackground());
        for (int c = 0; c < 4; c++) {
            float k = u != null && u[0] == c ? Math.max(0, 1 - u[1] / 300F) : 0;
            float base = be.cancion != null ? 0.18F + 0.1F * (float) Math.sin(System.currentTimeMillis() / 150.0 + c) : 0.1F;
            float a = Math.max(base, k);
            if (a <= 0.02F) continue;
            int alfa = (int) (a * 230);
            flecha(vc, m, c, SITIO[c][0], SITIO[c][1], (alfa << 24) | COLOR[c]);
        }
        ps.popPose();
    }

    /** Dibuja la flecha (máscara de 14x14) a escala de 0.4 px por celda, tumbada sobre la pista. */
    static void flecha(VertexConsumer vc, Matrix4f m, int c, float cx, float cz, int argb) {
        String[] f = PasoDoble.FLECHA;
        float e = 0.4F / 16;
        int a = argb >>> 24, r = (argb >> 16) & 255, g = (argb >> 8) & 255, b = argb & 255;
        for (int y = 0; y < 14; y++)
            for (int x = 0; x < 14; x++) {
                // girar la máscara de «arriba» según la flecha (arriba = hacia el norte, -z)
                int mx, my;
                switch (c) {
                    case 1 -> { mx = x; my = 13 - y; }
                    case 0 -> { mx = y; my = x; }
                    case 3 -> { mx = 13 - y; my = x; }
                    default -> { mx = x; my = y; }
                }
                if (f[my].charAt(mx) != '#') continue;
                float x0 = cx / 16 + (x - 7) * e, z0 = cz / 16 + (y - 7) * e;
                vc.vertex(m, x0, 0, z0).color(r, g, b, a).uv2(0xF000F0).endVertex();
                vc.vertex(m, x0, 0, z0 + e).color(r, g, b, a).uv2(0xF000F0).endVertex();
                vc.vertex(m, x0 + e, 0, z0 + e).color(r, g, b, a).uv2(0xF000F0).endVertex();
                vc.vertex(m, x0 + e, 0, z0).color(r, g, b, a).uv2(0xF000F0).endVertex();
            }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
