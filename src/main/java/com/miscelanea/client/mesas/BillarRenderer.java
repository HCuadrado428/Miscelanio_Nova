package com.miscelanea.client.mesas;

import com.miscelanea.Miscelanea;
import com.miscelanea.mesas.BillarBlockEntity;
import com.miscelanea.mesas.BillarPartida;
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
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/** Bolas (suavizadas entre tics), taco y línea de puntería del que apunta, y el cartel con el marcador. */
public class BillarRenderer implements BlockEntityRenderer<BillarBlockEntity> {
    public static final ResourceLocation LISA = rl("bola_lisa"), RAYADA = rl("bola_rayada"), BLANCA = rl("bola_blanca"), TACO = rl("taco"), PUNTO = rl("punto");
    static final int[] COLOR = {0xFFFFFF, 0xF2C420, 0x2A58D8, 0xD8302A, 0x7A3AB0, 0xF07820, 0x2A9A48, 0x8A2A24, 0x141418};
    static final float Y = 12 / 16F + BillarPartida.R;

    public BillarRenderer(BlockEntityRendererProvider.Context c) {}

    public static ResourceLocation rl(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/billar/" + n);
    }

    @Override
    public boolean shouldRenderOffScreen(BillarBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(BillarBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ModelBlockRenderer mr = mc.getBlockRenderer().getModelRenderer();
        var mm = mc.getModelManager();
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        BillarPartida p = be.p;
        Direction f = be.facing();
        boolean suave = p.fase == BillarPartida.MUEVE && System.currentTimeMillis() - be.ultimoFisica < 200;
        for (int i = 0; i < 16; i++) {
            if (!p.dentro[i]) continue;
            float u = suave ? be.antesX[i] + (p.x[i] - be.antesX[i]) * pt : p.x[i];
            float v = suave ? be.antesY[i] + (p.y[i] - be.antesY[i]) * pt : p.y[i];
            Vec3 l = MesaGeo.local(f, u, v, Y);
            BakedModel m = mm.getModel(i == 0 ? BLANCA : i >= 9 ? RAYADA : LISA);
            int c = COLOR[i == 0 ? 0 : i >= 9 ? i - 8 : i];
            ps.pushPose();
            ps.translate(l.x - 0.5, l.y - 0.5, l.z - 0.5);
            mr.renderModel(ps.last(), vc, null, m, ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }
        // taco y puntería
        if (BillarCliente.apuntando == be && p.dentro[0] && mc.player != null) {
            float ang = BillarCliente.calcularAngulo(be, mc.player, pt);
            float fu = BillarCliente.fuerza(pt);
            float du = (float) Math.cos(ang), dv = (float) Math.sin(ang);
            // taco detrás de la blanca, más atrás cuanta más fuerza
            float atras = 0.03F + fu * 0.3F;
            Vec3 l = MesaGeo.local(f, p.x[0] - du * atras, p.y[0] - dv * atras, Y + 0.01);
            ps.pushPose();
            ps.translate(l.x, l.y, l.z);
            ps.mulPose(Axis.YP.rotation(MesaGeo.giro(f, -du, -dv)));
            ps.mulPose(Axis.ZP.rotationDegrees(7));
            ps.translate(-0.5, -0.5, -0.5);
            mr.renderModel(ps.last(), vc, null, mm.getModel(TACO), 1, 1, 1, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            // línea de puntos hasta el primer choque
            float[] fin = trayecto(p, du, dv);
            int n = (int) (fin[2] / 0.06F);
            for (int k = 1; k <= n; k++) {
                float d = k * 0.06F;
                Vec3 q = MesaGeo.local(f, p.x[0] + du * d, p.y[0] + dv * d, 12 / 16F + 0.004);
                punto(ps, mr, vc, mm.getModel(PUNTO), q, luz, 1F);
            }
            // bola fantasma en el punto de choque
            for (int k = 0; k < 10; k++) {
                double a = k * Math.PI / 5;
                Vec3 q = MesaGeo.local(f, fin[0] + Math.cos(a) * BillarPartida.R, fin[1] + Math.sin(a) * BillarPartida.R, 12 / 16F + 0.004);
                punto(ps, mr, vc, mm.getModel(PUNTO), q, luz, 0.7F);
            }
        }
        cartel(be, ps, buf, mc.font);
    }

    private static void punto(PoseStack ps, ModelBlockRenderer mr, VertexConsumer vc, BakedModel m, Vec3 q, int luz, float g) {
        ps.pushPose();
        ps.translate(q.x - 0.5, q.y - 0.5, q.z - 0.5);
        mr.renderModel(ps.last(), vc, null, m, g, g, g, 0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    /** Recorre la línea de la blanca: {u, v, distancia} donde tocaría la primera bola o banda. */
    static float[] trayecto(BillarPartida p, float du, float dv) {
        float r = BillarPartida.R, best = 3.5F;
        for (int i = 1; i < 16; i++) {
            if (!p.dentro[i]) continue;
            float ox = p.x[i] - p.x[0], oy = p.y[i] - p.y[0];
            float t = ox * du + oy * dv;
            if (t <= 0) continue;
            float perp2 = ox * ox + oy * oy - t * t, min = 4 * r * r;
            if (perp2 > min) continue;
            float d = t - (float) Math.sqrt(min - perp2);
            if (d < best) best = d;
        }
        // bandas
        if (du < 0) best = Math.min(best, (BillarPartida.U0 + r - p.x[0]) / du);
        if (du > 0) best = Math.min(best, (BillarPartida.U1 - r - p.x[0]) / du);
        if (dv < 0) best = Math.min(best, (BillarPartida.V0 + r - p.y[0]) / dv);
        if (dv > 0) best = Math.min(best, (BillarPartida.V1 - r - p.y[0]) / dv);
        best = Math.max(0, best);
        return new float[]{p.x[0] + du * best, p.y[0] + dv * best, best};
    }

    private void cartel(BillarBlockEntity be, PoseStack ps, MultiBufferSource buf, Font font) {
        BillarPartida p = be.p;
        List<String> l = new ArrayList<>();
        List<Integer> c = new ArrayList<>();
        if (be.jug[0] == null) {
            l.add(I18n.get("billar.miscelanea.titulo"));
            c.add(0xFFFFE070);
            l.add(I18n.get("billar.miscelanea.msg.espera"));
            c.add(0xFFFFFFFF);
        } else {
            String n0 = be.nombre[0], n1 = be.nombre[1].isEmpty() ? "?" : be.nombre[1];
            l.add(n0 + grupo(p.grupo[0]) + "  vs  " + n1 + grupo(p.grupo[1]));
            c.add(0xFFFFFFFF);
            if (p.fase == BillarPartida.FIN) {
                l.add(I18n.get("billar.miscelanea.gana", p.ganador == 0 ? n0 : n1));
                c.add(0xFFFFD23C);
                l.add(I18n.get(p.mensaje));
                c.add(0xFFC0C0C0);
                l.add(I18n.get("billar.miscelanea.otra"));
                c.add(0xFF8AE0FF);
            } else if (p.fase == BillarPartida.ESPERA) {
                l.add(I18n.get(p.mensaje));
                c.add(0xFFC0C0C0);
            } else {
                l.add(I18n.get("billar.miscelanea.turno", p.turno == 0 ? n0 : n1));
                c.add(p.turno == 0 ? 0xFFFF8080 : 0xFF80B0FF);
                l.add(I18n.get(p.mensaje));
                c.add(0xFFC0C0C0);
            }
        }
        Vec3 centro = MesaGeo.local(be.facing(), 2, 1, 1.45 + l.size() * 0.11);
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(centro.x, centro.y, centro.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-0.016F, -0.016F, 0.016F);
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < l.size(); i++)
            font.drawInBatch(l.get(i), -font.width(l.get(i)) / 2F, i * 10, c.get(i), false, m, buf, Font.DisplayMode.NORMAL, 0x80000000, 0xF000F0);
        ps.popPose();
    }

    private static String grupo(int g) {
        return g == BillarPartida.LISAS ? " (" + I18n.get("billar.miscelanea.lisas") + ")" : g == BillarPartida.RAYADAS ? " (" + I18n.get("billar.miscelanea.rayadas") + ")" : "";
    }
}
