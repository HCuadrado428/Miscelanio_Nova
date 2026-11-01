package com.miscelanea.client.cuatro;

import com.miscelanea.Miscelanea;
import com.miscelanea.cuatro.Cuatro;
import com.miscelanea.cuatro.CuatroBlock;
import com.miscelanea.cuatro.CuatroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Fichas que caen con gravedad y rebote, ficha fantasma sobre la columna apuntada, línea ganadora que late y marcador. */
public class CuatroRenderer implements BlockEntityRenderer<CuatroBlockEntity> {
    public static final ResourceLocation ROJA = new ResourceLocation(Miscelanea.MOD_ID, "block/cuatro/ficha_roja"),
            AMARILLA = new ResourceLocation(Miscelanea.MOD_ID, "block/cuatro/ficha_amarilla");
    private static final float ESC = 0.19F;

    public CuatroRenderer(BlockEntityRendererProvider.Context c) {}

    private static double cx(int c) {
        return CuatroBlockEntity.X0 + CuatroBlockEntity.CELDA * (c + 0.5);
    }

    private static double cy(double f) {
        return CuatroBlockEntity.Y0 + CuatroBlockEntity.CELDA * (f + 0.5);
    }

    @Override
    public void render(CuatroBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        if (be.getLevel() == null) return;
        Direction f = be.getBlockState().getValue(CuatroBlock.FACING);
        float t = be.getLevel().getGameTime() + pt;
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var mm = Minecraft.getInstance().getModelManager();
        BakedModel roja = mm.getModel(ROJA), amarilla = mm.getModel(AMARILLA);
        var vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180 - f.toYRot()));
        ps.translate(-0.5, 0, -0.5);
        java.util.Set<Integer> gan = new java.util.HashSet<>();
        if (be.linea != null) for (int i : be.linea) gan.add(i);
        for (int fi = 0; fi < Cuatro.FILAS; fi++)
            for (int c = 0; c < Cuatro.COLS; c++) {
                int v = be.j.t[Cuatro.idx(c, fi)];
                if (v == 0) continue;
                double y = cy(fi);
                if (c == be.ultCol && fi == be.ultFila) {
                    // caída: desde encima del bastidor con gravedad y un pequeño rebote
                    double alto = cy(Cuatro.FILAS + 0.4) - y;
                    double dur = Math.sqrt(alto) * 2.0;
                    double e = t - be.ultT;
                    if (e < dur) y += alto * (1 - (e / dur) * (e / dur));
                    else if (e < dur + 4) y += Math.sin((e - dur) / 4 * Math.PI) * 0.6;
                }
                float esc = ESC;
                if (gan.contains(Cuatro.idx(c, fi))) esc *= 1F + 0.12F * Math.abs(Mth.sin(t * 0.2F));
                ficha(mr, vc, ps, v == 1 ? roja : amarilla, cx(c), y, esc, luz);
            }
        // ficha fantasma sobre la columna apuntada
        if (CuatroCliente.apuntado == be && CuatroCliente.columna >= 0 && CuatroCliente.esMiTurno(be) && be.j.puede(CuatroCliente.columna)) {
            double y = cy(Cuatro.FILAS + 0.35) + 0.25 * Math.sin(t * 0.25);
            ficha(mr, vc, ps, be.j.turno == 1 ? roja : amarilla, cx(CuatroCliente.columna), y, ESC, 0xF000F0);
        }
        ps.popPose();

        // ---------------- marcador
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 100) return;
        List<String> l = new ArrayList<>();
        List<Integer> cs = new ArrayList<>();
        l.add(I18n.get("cuatro.miscelanea.modo." + be.modo));
        cs.add(0xFFFFE070);
        if (be.resultado == 3) {
            l.add(I18n.get("tablero.miscelanea.tablas"));
            cs.add(0xFFFFFFFF);
        } else if (be.resultado != 0) {
            l.add(I18n.get("cuatro.miscelanea.gana", be.resultado == 1 ? be.nombreR : be.nombreA));
            cs.add(be.resultado == 1 ? 0xFFFF6050 : 0xFFFFD040);
            l.add(I18n.get("cuatro.miscelanea.otra"));
            cs.add(0xFF80E0FF);
        } else if (be.rojo == null) {
            l.add(I18n.get("cuatro.miscelanea.toca"));
            cs.add(0xFFFFFFFF);
        } else {
            boolean r = be.j.turno == 1;
            String quien = r ? be.nombreR : (be.nombreA.isEmpty() ? "?" : be.nombreA);
            l.add(I18n.get(r ? "cuatro.miscelanea.turno_rojo" : "cuatro.miscelanea.turno_amarillo", quien));
            cs.add(r ? 0xFFFF6050 : 0xFFFFD040);
            if (CuatroCliente.esMiTurno(be)) {
                l.add(I18n.get("cuatro.miscelanea.pista"));
                cs.add(0xFF80E0FF);
            }
        }
        Font font = mc.font;
        ps.pushPose();
        ps.translate(0.5, 1.75 + l.size() * 0.1, 0.5);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.016F, -0.016F, 0.016F);
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < l.size(); i++)
            font.drawInBatch(l.get(i), -font.width(l.get(i)) / 2F, i * 10, cs.get(i), false, m, buf, Font.DisplayMode.NORMAL, 0x60000000, 0xF000F0);
        ps.popPose();
    }

    private static void ficha(ModelBlockRenderer mr, com.mojang.blaze3d.vertex.VertexConsumer vc, PoseStack ps, BakedModel m, double x, double y, float esc, int luz) {
        ps.pushPose();
        ps.translate(x / 16, y / 16, 0.5);
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, -0.5, -0.5);
        mr.renderModel(ps.last(), vc, null, m, 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
