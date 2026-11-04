package com.miscelanea.client.cuatro;

import com.miscelanea.Miscelanea;
import com.miscelanea.cuatro.Cuatro;
import com.miscelanea.cuatro.CuatroBlock;
import com.miscelanea.cuatro.CuatroBlockEntity;
import com.miscelanea.client.juegos.PanelJuego;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
import net.minecraftforge.client.model.data.ModelData;

/** Fichas que caen con gravedad y rebote, ficha fantasma sobre la columna apuntada, línea ganadora que late; el estado va en el panel de HUD. */
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

        // ---------------- panel de HUD (nada flotando sobre el bastidor)
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), CuatroCliente.apuntado == be, "block.miscelanea.cuatro", 0xFFE04838);
        if (pj == null) return;
        pj.linea(I18n.get("cuatro.miscelanea.modo." + be.modo), PanelJuego.AMARILLO);
        if (be.resultado == 3) pj.linea(I18n.get("tablero.miscelanea.tablas"), PanelJuego.BLANCO);
        else if (be.resultado != 0) pj.linea(I18n.get("cuatro.miscelanea.gana", be.resultado == 1 ? be.nombreR : be.nombreA), be.resultado == 1 ? 0xFFFF6050 : 0xFFFFD040);
        else if (be.rojo == null) pj.linea(I18n.get("panel.miscelanea.cuatro.empieza"), PanelJuego.BLANCO);
        if (be.rojo != null) {
            boolean jugando = be.resultado == 0;
            pj.jugador(be.nombreR, I18n.get("panel.miscelanea.cuatro.rojas"), 0xE23A30, jugando && be.j.turno == 1);
            pj.jugador(be.contraCpu() ? "CPU" : be.nombreA.isEmpty() ? "?" : be.nombreA, I18n.get("panel.miscelanea.cuatro.amarillas"), 0xF2C420,
                    jugando && be.j.turno != 1);
        }
        if (be.resultado != 0) {
            java.util.UUID yo = net.minecraft.client.Minecraft.getInstance().player.getUUID();
            pj.control(PanelJuego.Tecla.USAR, yo.equals(be.rojo) || yo.equals(be.amarillo) ? "panel.miscelanea.ctrl.revancha" : "panel.miscelanea.ctrl.otra");
        }
        else if (CuatroCliente.esMiTurno(be)) pj.control(PanelJuego.Tecla.USAR, "panel.miscelanea.ctrl.soltar_ficha");
        pj.control(PanelJuego.Tecla.AGACHADO_USAR, "panel.miscelanea.ctrl.cambiar_modo");
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
