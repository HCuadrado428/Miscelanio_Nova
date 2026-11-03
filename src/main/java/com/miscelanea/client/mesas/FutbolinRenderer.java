package com.miscelanea.client.mesas;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.mesas.FutbolinBlockEntity;
import com.miscelanea.mesas.FutbolinPartida;
import com.miscelanea.mesas.MesaGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/** Barras con sus muñecos (que giran al chutar), la pelota y el marcador encima de la mesa. */
public class FutbolinRenderer implements BlockEntityRenderer<FutbolinBlockEntity> {
    public static final ResourceLocation BARRA = rl("barra"), MANGO = rl("mango"), ROJO = rl("muneco_rojo"), AZUL = rl("muneco_azul"), PELOTA = rl("pelota");
    static final float CAMPO = 9 / 16F, BARRA_Y = 14 / 16F;

    public FutbolinRenderer(BlockEntityRendererProvider.Context c) {}

    public static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/futbolin/" + n);
    }

    @Override
    public boolean shouldRenderOffScreen(FutbolinBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(FutbolinBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer mr = mc.getBlockRenderer().getModelRenderer();
        var mm = mc.getModelManager();
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        FutbolinPartida p = be.p;
        Direction f = be.facing();
        boolean suave = System.currentTimeMillis() - be.ultimoFisica < 200;
        float s0 = suave ? be.antesS0 + (p.s[0] - be.antesS0) * pt : p.s[0];
        float s1 = suave ? be.antesS1 + (p.s[1] - be.antesS1) * pt : p.s[1];
        float giroV = MesaGeo.giro(f, 0, 1);
        for (int k = 0; k < 8; k++) {
            int e = FutbolinPartida.EQUIPO[k];
            float s = e == 0 ? s0 : s1;
            float u = FutbolinPartida.barraU(k), desp = s * FutbolinPartida.limite(k);
            // barra: atraviesa la mesa a lo ancho y sale por el lado de su equipo
            Vec3 a = MesaGeo.local(f, u, FutbolinPartida.ANCHO / 2 + desp + (e == 0 ? -0.15 : 0.15), BARRA_Y);
            ps.pushPose();
            ps.translate(a.x, a.y, a.z);
            ps.mulPose(Axis.YP.rotation(giroV));
            ps.scale(2.35F, 1, 1);
            ps.translate(-0.5, -0.5, -0.5);
            mr.renderModel(ps.last(), vc, null, mm.getModel(BARRA), 1, 1, 1, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            Vec3 m = MesaGeo.local(f, u, desp + (e == 0 ? -0.45 : 2.45), BARRA_Y);
            ps.pushPose();
            ps.translate(m.x, m.y, m.z);
            ps.mulPose(Axis.YP.rotation(giroV));
            ps.translate(-0.5, -0.5, -0.5);
            mr.renderModel(ps.last(), vc, null, mm.getModel(MANGO), 1, 1, 1, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            // muñecos
            float chut = p.chut[e] > 0 ? p.chut[e] - pt : 0;
            float fase = chut > 0 ? (9 - chut) / 9F : 0;
            float ang = (float) Math.sin(Math.max(0, fase) * Math.PI) * 75F * (e == 0 ? -1 : 1);
            for (int i = 0; i < FutbolinPartida.MUNECOS[k]; i++) {
                float mv = FutbolinPartida.ANCHO / 2 + desp + (i - (FutbolinPartida.MUNECOS[k] - 1) / 2F) * FutbolinPartida.SEPARA[k];
                Vec3 q = MesaGeo.local(f, u, mv, BARRA_Y);
                ps.pushPose();
                ps.translate(q.x, q.y, q.z);
                ps.mulPose(Axis.YP.rotation(giroV));
                ps.mulPose(Axis.XP.rotationDegrees(ang));
                if (e == 1) ps.mulPose(Axis.YP.rotationDegrees(180));
                ps.translate(-0.5, -0.5, -0.5);
                mr.renderModel(ps.last(), vc, null, mm.getModel(e == 0 ? ROJO : AZUL), 1, 1, 1, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
            }
        }
        if (p.fase == FutbolinPartida.JUEGA || p.fase == FutbolinPartida.FIN) {
            float bu = suave ? be.antesU + (p.bu - be.antesU) * pt : p.bu, bv = suave ? be.antesV + (p.bv - be.antesV) * pt : p.bv;
            Vec3 b = MesaGeo.local(f, bu, bv, CAMPO + FutbolinPartida.R);
            ps.pushPose();
            ps.translate(b.x - 0.5, b.y - 0.5, b.z - 0.5);
            mr.renderModel(ps.last(), vc, null, mm.getModel(PELOTA), 1, 1, 1, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }
        marcador(be, ps, buf, mc.font);
    }

    /** Marcador y controles en el panel de HUD (antes era un cartel flotante encima del campo). */
    private void marcador(FutbolinBlockEntity be, PoseStack ps, MultiBufferSource buf, Font font) {
        FutbolinPartida p = be.p;
        boolean juego = FutbolinCliente.jugando == be;
        Vec3 c = Vec3.atLowerCornerOf(be.getBlockPos()).add(MesaGeo.local(be.facing(), 1.5, 1, 1));
        PanelJuego.Panel pj = PanelJuego.ofrecer(c, juego || AirHockeyRenderer.mirando(be), "block.miscelanea.futbolin", 0xFF4CC060);
        if (pj == null) return;
        String n0 = be.jug[0] != null ? be.nombre[0] : p.cpu[0] ? "CPU" : "-", n1 = be.jug[1] != null ? be.nombre[1] : p.cpu[1] ? "CPU" : "-";
        if (p.fase == FutbolinPartida.ESPERA) pj.linea(I18n.get("futbolin.miscelanea.espera"), PanelJuego.BLANCO);
        else {
            pj.marcador(p.goles[0] + " - " + p.goles[1], p.fase == FutbolinPartida.GOL ? 0xFFFFD23C : PanelJuego.BLANCO);
            pj.linea(p.fase == FutbolinPartida.GOL ? I18n.get("futbolin.miscelanea.gol_grande") : p.fase == FutbolinPartida.FIN
                    ? I18n.get("futbolin.miscelanea.gana", p.goles[0] > p.goles[1] ? I18n.get("futbolin.miscelanea.equipo0") : I18n.get("futbolin.miscelanea.equipo1"))
                    : I18n.get("futbolin.miscelanea.a_cinco"), p.fase == FutbolinPartida.GOL ? 0xFFFFD23C : PanelJuego.AMARILLO);
            pj.jugador(n0, I18n.get("futbolin.miscelanea.equipo0"), 0xD03030, false);
            pj.jugador(n1, I18n.get("futbolin.miscelanea.equipo1"), 0x3060D0, false);
        }
        if (juego) {
            pj.control(PanelJuego.Tecla.ADELANTE_ATRAS, "panel.miscelanea.ctrl.barras");
            pj.control(PanelJuego.Tecla.ATACAR, "panel.miscelanea.ctrl.chutar");
            pj.control(PanelJuego.Tecla.AGACHARSE, "panel.miscelanea.ctrl.soltar_mesa");
        } else pj.control(PanelJuego.Tecla.USAR, "panel.miscelanea.ctrl.unirse_lado");
    }
}
