package com.miscelanea.client.domino;

import com.miscelanea.Miscelanea;
import com.miscelanea.domino.DominoBlock;
import com.miscelanea.domino.DominoBlockEntity;
import com.miscelanea.domino.DominoBotones;
import com.miscelanea.domino.DominoGeo;
import com.miscelanea.domino.DominoPartida;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/** Cadena de fichas sobre el tapete, manos de pie (cada uno ve las suyas), pozo boca abajo, huecos donde encajar y paneles. */
public class DominoRenderer implements BlockEntityRenderer<DominoBlockEntity> {
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");
    public static final ResourceLocation[] FICHA = new ResourceLocation[28];
    private static final float ESC = 0.125F;

    static {
        for (int i = 0; i < 28; i++) FICHA[i] = new ResourceLocation(Miscelanea.MOD_ID, "block/domino/f_" + i);
    }

    public DominoRenderer(BlockEntityRendererProvider.Context c) {}

    private Vec3 local(DominoBlockEntity be, double tx, double ty) {
        return be.mundo(tx, ty).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    /** Ficha tumbada, centrada en (tx, ty) y girada ang (radianes, en el plano de la textura). */
    private void tumbada(ModelBlockRenderer mr, ModelManager mm, VertexConsumer vc, PoseStack ps, DominoBlockEntity be, float giro, int f, double tx, double ty,
                         double alto, double ang, boolean bocaAbajo, int luz) {
        Vec3 l = local(be, tx, ty);
        ps.pushPose();
        ps.translate(l.x, l.y + alto, l.z);
        ps.mulPose(Axis.YP.rotationDegrees(giro));
        ps.mulPose(Axis.YP.rotation((float) -ang));
        if (bocaAbajo) {
            ps.translate(0, 2.5F / 16 * ESC, 0);
            ps.mulPose(Axis.XP.rotationDegrees(180));
        }
        ps.scale(ESC, ESC, ESC);
        ps.translate(-0.5, 0, -0.5);
        mr.renderModel(ps.last(), vc, null, mm.getModel(FICHA[f]), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    @Override
    public void render(DominoBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        if (be.getLevel() == null) return;
        DominoPartida p = be.p;
        float t = be.getLevel().getGameTime() + pt;
        Direction f = be.getBlockState().getValue(DominoBlock.FACING);
        float giro = 180 - f.toYRot();
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ModelManager mm = Minecraft.getInstance().getModelManager();
        VertexConsumer solido = buf.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer marca = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean miro = DominoCliente.apuntado == be;
        int yo = -1;
        if (mc.player != null) for (int i = 0; i < p.js.size(); i++) if (mc.player.getUUID().equals(p.js.get(i).uuid)) yo = i;
        boolean miTurno = yo >= 0 && yo == p.turno && p.fase == DominoPartida.JUGAR;

        // ---------------- cadena
        DominoGeo.Cadena cad = DominoGeo.cadena(p);
        int ultima = -1;
        if (p.ultLado == 0) ultima = 0;
        else if (p.ultLado < 0 && !p.izq.isEmpty()) ultima = p.izq.get(p.izq.size() - 1).ficha();
        else if (p.ultLado > 0 && !p.der.isEmpty()) ultima = p.der.get(p.der.size() - 1).ficha();
        float e = Mth.clamp((t - p.tUlt) / 8F, 0F, 1F);
        for (int i = 0; i < cad.fichas.size(); i++) {
            DominoGeo.Colocada c = cad.fichas.get(i);
            double x = c.x(), y = c.y(), alto = 0;
            boolean ani = e < 1 && (p.ultLado == 0 ? i == 0 : c.ficha() == ultima);
            if (ani) {
                double s = e * e * (3 - 2 * e);
                x = Mth.lerp(s, 256, c.x());
                y = Mth.lerp(s, 256, c.y());
                alto = Math.sin(e * Math.PI) * 0.12;
            }
            tumbada(mr, mm, solido, ps, be, giro, c.ficha(), x, y, alto, c.ang() + (c.volteada() ? Math.PI : 0), false, luz);
        }

        // ---------------- huecos donde se puede encajar
        if (miTurno && !cad.fichas.isEmpty()) {
            DominoPartida.Jugador j = p.js.get(yo);
            for (int lado = -1; lado <= 1; lado += 2) {
                boolean vale = false;
                if (p.sel >= 0) vale = p.encaja(p.sel, lado);
                else for (int fi : j.mano) if (p.encaja(fi, lado)) vale = true;
                if (!vale) continue;
                double[] h = cad.hueco[lado < 0 ? 0 : 1];
                anillo(marca, ps, be, h[0], h[1], 6, 9, p.sel >= 0 ? 0x50FF70 : 0xFFFFFF, (p.sel >= 0 ? 0.7F : 0.35F) + 0.2F * Mth.sin(t * 0.4F));
                if (p.sel >= 0) {
                    String n = String.valueOf(p.extremo(lado));
                    cartel(ps, buf, font, local(be, h[0], h[1]).add(0, 0.08, 0), List.of("▼ " + n), List.of(0xFF80FF90), 0.008F);
                }
            }
        }

        // ---------------- manos de pie: la cara mira a su dueño
        for (int i = 0; i < p.js.size(); i++) {
            DominoPartida.Jugador j = p.js.get(i);
            double[] fuera = DominoGeo.haciaFuera(j.asiento);
            float theta = (float) Math.atan2(fuera[1], -fuera[0]);
            int apunta = miro && i == yo ? DominoGeo.manoEn(j, DominoCliente.tx, DominoCliente.ty) : -1;
            for (int k = 0; k < j.mano.size(); k++) {
                int fi = j.mano.get(k);
                double[] q = DominoGeo.mano(j, k);
                double alto = 0;
                boolean jugable = miTurno && i == yo && p.jugable(fi);
                if (jugable) alto = 0.012 + (fi == p.sel ? 0.03 + 0.008 * Mth.sin(t * 0.3F) : 0);
                if (k == apunta) alto += 0.01;
                Vec3 l = local(be, q[0], q[1]);
                ps.pushPose();
                ps.translate(l.x, l.y + alto, l.z);
                ps.mulPose(Axis.YP.rotationDegrees(giro));
                ps.mulPose(Axis.YP.rotation(theta));
                ps.mulPose(Axis.ZP.rotationDegrees(-8));      // un poco inclinadas hacia atrás
                ps.translate(0, 8F / 16 * ESC, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(90));
                ps.scale(ESC, ESC, ESC);
                ps.translate(-0.5, -2.5 / 16 / 2, -0.5);
                float k2 = i == yo && miTurno && !jugable ? 0.6F : 1F;
                mr.renderModel(ps.last(), solido, null, mm.getModel(FICHA[fi]), k2, k2, k2, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
                if (jugable) rect(marca, ps, be, q[0], q[1], i % 2 == 0 ? 14 : 14, 14, fi == p.sel ? 0x50FF70 : 0xFFFFFF, fi == p.sel ? 0.6F : 0.25F);
            }
            if (apunta >= 0) {
                int fi = j.mano.get(apunta);
                boolean iz = p.encaja(fi, -1), de = p.encaja(fi, 1);
                String txt = "[" + DominoPartida.A[fi] + "|" + DominoPartida.B[fi] + "] " + I18n.get(!miTurno ? "domino.miscelanea.ver.espera"
                        : iz && de ? "domino.miscelanea.ver.ambos" : iz || de ? "domino.miscelanea.ver.encaja" : "domino.miscelanea.ver.no");
                double[] q = DominoGeo.mano(j, apunta);
                cartel(ps, buf, font, local(be, q[0], q[1]).add(0, 0.16, 0), List.of(txt), List.of(iz || de ? 0xFFFFFFFF : 0xFFFF8080), 0.008F);
            }
        }

        // ---------------- pozo boca abajo
        int n = p.pozo.size();
        for (int k = 0; k < n; k++) {
            int col = k % 2, piso = k / 2 % 7, capa = k / 14;
            tumbada(mr, mm, solido, ps, be, giro, p.pozo.get(k), DominoGeo.POZO_X - 9 + col * 18, DominoGeo.POZO_Y - 24 + piso * 8, capa * 0.022,
                    Math.PI / 2, true, luz);
        }
        if (n > 0 && miro && Math.hypot(DominoCliente.tx - DominoGeo.POZO_X, DominoCliente.ty - DominoGeo.POZO_Y) < 34)
            cartel(ps, buf, font, local(be, DominoGeo.POZO_X, DominoGeo.POZO_Y).add(0, 0.18, 0), List.of(I18n.get("domino.miscelanea.pozo", n)), List.of(0xFFFFFFFF), 0.009F);

        // ---------------- botones
        int asiento = p.fase == DominoPartida.JUGAR && p.actual() != null ? p.actual().asiento : 0;
        Direction mira = switch (asiento) {
            case 1 -> f.getClockWise();
            case 2 -> f.getOpposite();
            case 3 -> f.getCounterClockWise();
            default -> f;
        };
        for (DominoBotones.Boton b : DominoBotones.de(p)) {
            boolean enc = miro && b.dentro(DominoCliente.tx, DominoCliente.ty);
            int col = enc ? mezcla(b.color(), 0xFFFFFF, 0.3F) : b.color();
            rect(marca, ps, be, b.x(), b.y() + 2, b.w(), b.h(), 0x000000, 0.35F);
            rect(marca, ps, be, b.x(), b.y(), b.w(), b.h(), col, 0.95F);
            rect(marca, ps, be, b.x(), b.y() - b.h() / 2 + 2, b.w() - 4, 3, 0xFFFFFF, 0.35F);
            String txt = I18n.get("domino.miscelanea.boton." + b.clave());
            Vec3 c = local(be, b.x(), b.y());
            ps.pushPose();
            ps.translate(c.x, c.y + 0.004, c.z);
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-mira.toYRot()), (float) Math.toRadians(90), 0));
            float esc = (float) Math.min(0.0045, b.w() / DominoGeo.N * DominoGeo.LADO / Math.max(1, font.width(txt)) * 0.9);
            ps.scale(-esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
            ps.popPose();
        }

        // ---------------- panel
        if (mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(be.getBlockPos())) > 225) return;
        List<String> lineas = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        lineas.add(estado(p));
        cols.add(0xFFFFE070);
        if (miTurno) {
            DominoPartida.Jugador j = p.js.get(yo);
            lineas.add(I18n.get(p.sel >= 0 ? "domino.miscelanea.pista.extremo" : p.puedeJugar(j) ? "domino.miscelanea.pista.ficha"
                    : !p.pozo.isEmpty() ? "domino.miscelanea.pista.robar" : "domino.miscelanea.pista.pasa"));
            cols.add(0xFF80E0FF);
        }
        int[] colores = {0xFF70B0FF, 0xFFFF9070, 0xFF90E070, 0xFFE0C060};
        for (int i = 0; i < p.js.size(); i++) {
            DominoPartida.Jugador j = p.js.get(i);
            String extra = p.fase == DominoPartida.FIN ? "  " + I18n.get("domino.miscelanea.puntos", j.puntos()) : "  " + I18n.get("domino.miscelanea.fichas", j.mano.size());
            lineas.add((i == p.turno && p.fase == DominoPartida.JUGAR ? "▶ " : "") + j.nombre + extra);
            cols.add(colores[i % 4]);
        }
        if (p.centro >= 0) {
            lineas.add(I18n.get("domino.miscelanea.extremos", p.extremo(-1), p.extremo(1)) + "   " + I18n.get("domino.miscelanea.pozo", p.pozo.size()));
            cols.add(0xFFFFFFFF);
        }
        if (!p.log.isEmpty()) {
            lineas.add(formatear(p.log.get(p.log.size() - 1)));
            cols.add(0xFFD0D0D0);
        }
        cartel(ps, buf, font, new Vec3(0.5, 1.1 + lineas.size() * 0.12, 0.5), lineas, cols, 0.016F);
    }

    private static String estado(DominoPartida p) {
        return switch (p.fase) {
            case DominoPartida.LOBBY -> I18n.get("domino.miscelanea.estado.lobby", p.js.size());
            case DominoPartida.JUGAR -> I18n.get("domino.miscelanea.estado.turno", p.actual().nombre);
            default -> p.ganador >= 0 ? I18n.get("domino.miscelanea.estado.gana", p.js.get(p.ganador).nombre) : I18n.get("domino.miscelanea.estado.fin");
        };
    }

    static String formatear(String e) {
        String[] s = e.split("\\|");
        Object[] args = new Object[s.length - 1];
        System.arraycopy(s, 1, args, 0, args.length);
        return I18n.get("domino.miscelanea.log." + s[0], args);
    }

    private void anillo(VertexConsumer vc, PoseStack ps, DominoBlockEntity be, double cx, double cy, double r0, double r1, int rgb, float a) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            quad(vc, ps, be, new double[][]{{cx + Math.cos(a0) * r1, cy + Math.sin(a0) * r1}, {cx + Math.cos(a1) * r1, cy + Math.sin(a1) * r1},
                    {cx + Math.cos(a1) * r0, cy + Math.sin(a1) * r0}, {cx + Math.cos(a0) * r0, cy + Math.sin(a0) * r0}}, rgb, a);
        }
    }

    private void rect(VertexConsumer vc, PoseStack ps, DominoBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        quad(vc, ps, be, new double[][]{{cx - w / 2, cy + h / 2}, {cx + w / 2, cy + h / 2}, {cx + w / 2, cy - h / 2}, {cx - w / 2, cy - h / 2}}, rgb, a);
    }

    private void quad(VertexConsumer vc, PoseStack ps, DominoBlockEntity be, double[][] pts, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        for (double[] pt : pts) {
            Vec3 q = local(be, pt[0], pt[1]);
            vc.vertex(m, (float) q.x, (float) q.y + 0.003F, (float) q.z).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                    .normal(n, 0, 1, 0).endVertex();
        }
    }

    private static void cartel(PoseStack ps, MultiBufferSource buf, Font font, Vec3 donde, List<String> lineas, List<Integer> cols, float esc) {
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(donde.x, donde.y, donde.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ps.scale(-esc, -esc, esc);
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < lineas.size(); i++) {
            String s = lineas.get(i);
            font.drawInBatch(s, -font.width(s) / 2F, i * 10, cols.get(i), false, m, buf, Font.DisplayMode.NORMAL, 0x80000000, 0xF000F0);
        }
        ps.popPose();
    }

    private static int mezcla(int a, int b, float k) {
        int r = (int) Mth.lerp(k, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(k, (a >> 8) & 255, (b >> 8) & 255), bb = (int) Mth.lerp(k, a & 255, b & 255);
        return (r << 16) | (g << 8) | bb;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
