package com.miscelanea.client.dream;

import com.miscelanea.Miscelanea;
import com.miscelanea.dream.CalderoBlock;
import com.miscelanea.dream.CalderoBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * El líquido del caldero de mezclas: un octógono (la boca del caldero) con la textura animada del remolino teñida del
 * color de la mezcla, que sube y baja con lo que queda y funde el color al echar otra poción; encima, burbujas que
 * crecen y revientan (más deprisa si lleva pólvora). Brilla un poco, como si la poción tuviera luz propia.
 */
public class CalderoRenderer implements BlockEntityRenderer<CalderoBlockEntity> {
    private static final ResourceLocation LIQUIDO = new ResourceLocation(Miscelanea.MOD_ID, "block/dream/caldero_liquido");
    private static final ResourceLocation BURBUJA = new ResourceLocation(Miscelanea.MOD_ID, "block/dream/caldero_burbuja");
    private static final float FUNDIDO = 20.0F;

    public CalderoRenderer(BlockEntityRendererProvider.Context c) {}

    private static TextureAtlasSprite sprite(ResourceLocation rl) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(rl);
    }

    private static float azar(int i, int k) {
        int h = i * 73856093 ^ k * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535F;
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(k, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(k, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    @Override
    public void render(CalderoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        float t = be.getLevel().getGameTime() + pt;
        if (be.colorAhora < 0) {
            // primera vez (el bloque acaba de cargarse): sin fundido
            be.colorAhora = be.colorAntes = be.color();
            be.nivelAhora = be.nivelAntes = be.nivel();
            be.cambio = -1000;
        }
        float k = Mth.clamp((t - be.cambio) / FUNDIDO, 0.0F, 1.0F);
        k = k * k * (3.0F - 2.0F * k);
        float nivel = Mth.lerp(k, be.nivelAntes, be.nivelAhora);
        if (nivel <= 0.001F) return;
        int col = mezcla(be.colorAntes, be.colorAhora, k);
        float r = (col >> 16 & 255) / 255F, g = (col >> 8 & 255) / 255F, b = (col & 255) / 255F;
        // nivel 0..1 → altura; si se está vaciando, el último trozo baja hasta el fondo
        float y = nivel >= 0.25F ? CalderoBlock.alturaLiquido(nivel) : Mth.lerp(nivel / 0.25F, 5.2F, CalderoBlock.alturaLiquido(0.25F));
        float a = (y <= 10.0F ? 5.75F : 5.05F) - 0.02F;
        // la poción tiene luz propia: nunca está a oscuras del todo
        int brillo = LightTexture.pack(Math.max(LightTexture.block(luz), 11), LightTexture.sky(luz));

        VertexConsumer vc = buf.getBuffer(Sheets.translucentCullBlockSheet());
        TextureAtlasSprite sp = sprite(LIQUIDO);
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        // octógono en 4 cuartos (centro + 3 vértices seguidos), con un leve oleaje
        double radio = a / Math.cos(Math.PI / 8);
        float yy = (y + Mth.sin(t * 0.08F) * 0.06F) / 16F;
        float[] vx = new float[9], vz = new float[9];
        for (int i = 0; i <= 8; i++) {
            double ang = Math.PI / 8 + i * Math.PI / 4;
            vx[i] = (float) (8 + Math.cos(ang) * radio);
            vz[i] = (float) (8 + Math.sin(ang) * radio);
        }
        float alfa = 0.88F;
        for (int q = 0; q < 4; q++) {
            int i0 = q * 2;
            vert(vc, m, n, sp, 8, yy, 8, r, g, b, alfa, brillo);
            vert(vc, m, n, sp, vx[i0 + 2], yy, vz[i0 + 2], r, g, b, alfa, brillo);
            vert(vc, m, n, sp, vx[i0 + 1], yy, vz[i0 + 1], r, g, b, alfa, brillo);
            vert(vc, m, n, sp, vx[i0], yy, vz[i0], r, g, b, alfa, brillo);
        }

        // burbujas: aparecen, crecen y revientan en sitios al azar de la superficie
        TextureAtlasSprite bs = sprite(BURBUJA);
        float br = Math.min(1.0F, r * 0.55F + 0.45F), bg = Math.min(1.0F, g * 0.55F + 0.45F), bb = Math.min(1.0F, b * 0.55F + 0.45F);
        int cuantas = be.arrojadizo ? 9 : 6;
        float velocidad = be.arrojadizo ? 0.07F : 0.04F;
        for (int i = 0; i < cuantas; i++) {
            float f = t * velocidad * (0.7F + azar(i, 9) * 0.6F) + azar(i, 1);
            int ciclo = (int) Math.floor(f);
            float fase = f - ciclo;
            float ang = azar(i * 31 + ciclo, 2) * Mth.TWO_PI;
            float dist = Mth.sqrt(azar(i * 31 + ciclo, 3)) * (a - 1.6F);
            float cx = 8 + Mth.cos(ang) * dist, cz = 8 + Mth.sin(ang) * dist;
            float tam = (0.5F + azar(i * 31 + ciclo, 4) * 0.9F) * (fase < 0.85F ? fase / 0.85F : 1.0F + (fase - 0.85F) * 3.0F);
            float al = fase < 0.85F ? 0.9F : 0.9F * (1.0F - (fase - 0.85F) / 0.15F);
            float by = yy + (0.04F + tam * 0.02F) / 16F;
            float h = tam * 0.5F;
            burbuja(vc, m, n, bs.getU0(), bs.getV0(), cx - h, by, cz - h, br, bg, bb, al, brillo);
            burbuja(vc, m, n, bs.getU0(), bs.getV1(), cx - h, by, cz + h, br, bg, bb, al, brillo);
            burbuja(vc, m, n, bs.getU1(), bs.getV1(), cx + h, by, cz + h, br, bg, bb, al, brillo);
            burbuja(vc, m, n, bs.getU1(), bs.getV0(), cx + h, by, cz - h, br, bg, bb, al, brillo);
        }
    }

    private static void burbuja(VertexConsumer vc, Matrix4f m, Matrix3f n, float u, float v, float x, float y, float z,
                                float r, float g, float b, float a, int luz) {
        vc.vertex(m, x / 16F, y, z / 16F).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }

    /** Vértice horizontal (normal hacia arriba); x/z en píxeles del bloque, y en bloques. */
    private static void vert(VertexConsumer vc, Matrix4f m, Matrix3f n, TextureAtlasSprite sp, float x, float y, float z,
                             float r, float g, float b, float a, int luz) {
        float u = sp.getU(Mth.clamp(x, 0, 16)), v = sp.getV(Mth.clamp(z, 0, 16));
        vc.vertex(m, x / 16F, y, z / 16F).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0, 1, 0).endVertex();
    }
}
