package com.miscelanea.client.oca;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.oca.OcaBlock;
import com.miscelanea.oca.OcaBlockEntity;
import com.miscelanea.oca.OcaBotones;
import com.miscelanea.oca.OcaGeo;
import com.miscelanea.oca.OcaPartida;
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

/** Peones que avanzan casilla a casilla, saltos de oca a oca, dado, botones, casilla apuntada resaltada; el estado va en el panel de HUD. */
public class OcaRenderer implements BlockEntityRenderer<OcaBlockEntity> {
    /** Las marcas planas se apilan con un desplazamiento mínimo para que no parpadeen al pisarse. */
    private int capa;
    public static final String[] COLOR_ID = {"amarillo", "azul", "rojo", "verde"};
    public static final int[] COLORES = {0xF2C420, 0x3478E8, 0xE23A30, 0x2EB052};
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");
    private static final ResourceLocation DADO = new ResourceLocation(Miscelanea.MOD_ID, "block/imperio/dado");
    private static final ResourceLocation[] PEON = new ResourceLocation[4];
    private static final float ESC = 0.115F;

    public OcaRenderer(BlockEntityRendererProvider.Context c) {}

    public static ResourceLocation modelo(int color) {
        if (PEON[color] == null) PEON[color] = new ResourceLocation(Miscelanea.MOD_ID, "block/oca/peon_" + COLOR_ID[color]);
        return PEON[color];
    }

    private Vec3 local(OcaBlockEntity be, double tx, double ty) {
        return be.mundo(tx, ty).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    @Override
    public void render(OcaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        capa = 0;
        if (be.getLevel() == null) return;
        OcaPartida p = be.p;
        float t = be.getLevel().getGameTime() + pt;
        Direction f = be.getBlockState().getValue(OcaBlock.FACING);
        float giro = 180 - f.toYRot();
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ModelManager mm = Minecraft.getInstance().getModelManager();
        VertexConsumer solido = buf.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer marca = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean miro = OcaCliente.apuntado == be;
        OcaPartida.Jugador act = p.actual();
        boolean mio = mc.player != null && act != null && mc.player.getUUID().equals(act.uuid);

        // ---------------- peones
        for (int i = 0; i < p.js.size(); i++) {
            OcaPartida.Jugador j = p.js.get(i);
            double[] pos = OcaGeo.hueco(j.pos, j.color);
            double alto = 0;
            float vuelta = 0;
            if (i == p.movJ && t < p.tFin && p.camino.length > 0) {
                int n = p.camino.length - 1;
                if (t < p.tMover) pos = OcaGeo.hueco(p.camino[0], j.color);
                else {
                    float e = (t - p.tMover) / p.paso();
                    int k = Math.min(n, (int) e);
                    float fr = Math.min(1F, e - k);
                    if (k >= n) {
                        pos = OcaGeo.hueco(p.camino[n], j.color);
                        if (p.saltoA >= 0 && t >= p.tSalto) {
                            float dur = 14F * p.paso() / 5F;
                            float s = Mth.clamp((t - p.tSalto) / dur, 0F, 1F);
                            double[] a = OcaGeo.hueco(p.saltoDesde, j.color), b = OcaGeo.hueco(p.saltoA, j.color);
                            float g = s * s * (3 - 2 * s);
                            pos = new double[]{Mth.lerp(g, a[0], b[0]), Mth.lerp(g, a[1], b[1])};
                            double dist = Math.hypot(b[0] - a[0], b[1] - a[1]);
                            alto = Math.sin(s * Math.PI) * (0.12 + dist / 512 * 0.5);
                            vuelta = s * 360;
                        }
                    } else {
                        double[] a = OcaGeo.hueco(p.camino[k], j.color), b = OcaGeo.hueco(p.camino[k + 1], j.color);
                        pos = new double[]{Mth.lerp(fr, a[0], b[0]), Mth.lerp(fr, a[1], b[1])};
                        alto = Math.sin(fr * Math.PI) * 0.06;
                    }
                }
            }
            // castigados: el peón se ve tumbado
            boolean castigado = j.castigo > 0 && !(i == p.movJ && t < p.tFin);
            Vec3 l = local(be, pos[0], pos[1]).add(0, alto, 0);
            ps.pushPose();
            ps.translate(l.x, l.y, l.z);
            ps.mulPose(Axis.YP.rotationDegrees(giro + vuelta));
            if (castigado) {
                ps.translate(0, 0.012, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(80));
            }
            if (i == p.turno && p.fase == OcaPartida.TIRAR) ps.translate(0, 0.012 + 0.01 * Mth.sin(t * 0.25F), 0);
            ps.scale(ESC, ESC, ESC);
            ps.translate(-0.5, 0, -0.5);
            mr.renderModel(ps.last(), solido, null, mm.getModel(modelo(j.color)), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            if (i == p.turno && p.fase == OcaPartida.TIRAR) anillo(marca, ps, be, pos[0], pos[1], 9, 12, COLORES[j.color], 0.5F + 0.25F * Mth.sin(t * 0.3F));
        }

        // ---------------- dado (en el marco, abajo en el centro)
        if (p.fase != OcaPartida.LOBBY) {
            float rodar = (t - p.tDado) / (float) p.dadoTicks();
            Vec3 c = local(be, 256, 496);
            ps.pushPose();
            if (rodar < 1) {
                float r = 1 - rodar;
                Vec3 a = local(be, 256, 380);
                float s = 1 - r * r;
                ps.translate(Mth.lerp(s, a.x, c.x), c.y + Math.abs(Mth.sin(rodar * 8F)) * 0.12 * r, Mth.lerp(s, a.z, c.z));
                ps.mulPose(Axis.XP.rotationDegrees(r * 820));
                ps.mulPose(Axis.ZP.rotationDegrees(r * 560));
            } else ps.translate(c.x, c.y, c.z);
            ps.translate(0, 0.035, 0);
            ps.mulPose(Axis.YP.rotationDegrees(giro + 12));
            switch (p.dado) {
                case 6 -> ps.mulPose(Axis.XP.rotationDegrees(180));
                case 2 -> ps.mulPose(Axis.XP.rotationDegrees(90));
                case 5 -> ps.mulPose(Axis.XP.rotationDegrees(-90));
                case 3 -> ps.mulPose(Axis.ZP.rotationDegrees(90));
                case 4 -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
                default -> {}
            }
            ps.scale(0.07F, 0.07F, 0.07F);
            ps.translate(-0.5, -0.5, -0.5);
            mr.renderModel(ps.last(), solido, null, mm.getModel(DADO), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }

        // ---------------- botones
        for (OcaBotones.Boton b : OcaBotones.de(p)) {
            boolean enc = miro && b.dentro(OcaCliente.tx, OcaCliente.ty);
            int col = enc ? mezcla(b.color(), 0xFFFFFF, 0.3F) : b.color();
            rect(marca, ps, be, b.x(), b.y() + 2, b.w(), b.h(), 0x000000, 0.35F);
            rect(marca, ps, be, b.x(), b.y(), b.w(), b.h(), col, 0.95F);
            rect(marca, ps, be, b.x(), b.y() - b.h() / 2 + 2, b.w() - 4, 3, 0xFFFFFF, 0.35F);
            if (b.w() < 100 && mc.player != null && be.mundo(b.x(), b.y()).distanceToSqr(mc.player.getEyePosition()) > 64) continue;
            String txt = I18n.get("oca.miscelanea.boton." + b.clave());
            Vec3 c = local(be, b.x(), b.y());
            ps.pushPose();
            ps.translate(c.x, c.y + 0.004, c.z);
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-f.toYRot()), (float) Math.toRadians(90), 0));
            float esc = (float) Math.min(0.0045, b.w() / OcaGeo.N * OcaGeo.LADO / Math.max(1, font.width(txt)) * 0.9);
            ps.scale(-esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
            ps.popPose();
        }

        // ---------------- casilla apuntada: se resalta en el tablero y se explica en el panel de HUD
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), miro, "block.miscelanea.oca", 0xFF3FA8D8);
        OcaBotones.Boton bot = miro ? OcaBotones.en(p, OcaCliente.tx, OcaCliente.ty) : null;
        int cas = miro && bot == null ? casillaEn(OcaCliente.tx, OcaCliente.ty) : -1;
        if (cas >= 0) {
            double[] c = OcaGeo.centro(cas);
            rect(marca, ps, be, c[0], c[1], OcaGeo.CELDA - 4, OcaGeo.CELDA - 4, 0xFFFFFF, 0.18F);
        }
        if (pj == null) return;
        pj.linea(estado(p), PanelJuego.AMARILLO);
        if (p.fase == OcaPartida.MOVER) pj.marcador(String.valueOf(p.dado), PanelJuego.BLANCO);
        for (int i = 0; i < p.js.size(); i++) {
            OcaPartida.Jugador j = p.js.get(i);
            String extra = I18n.get("oca.miscelanea.casilla", j.pos) + (j.castigo > 0 ? "  " + I18n.get("oca.miscelanea.castigo", j.castigo) : "");
            pj.jugador(j.nombre, extra, COLORES[j.color], i == p.turno && p.fase != OcaPartida.LOBBY && p.fase != OcaPartida.FIN);
        }
        if (mio && p.fase == OcaPartida.TIRAR) pj.detalle(I18n.get("oca.miscelanea.pista"), PanelJuego.CIAN);
        if (cas >= 0) {
            String tipo = OcaPartida.tipo(cas);
            pj.detalle(I18n.get("oca.miscelanea.casilla", cas) + (tipo.isEmpty() ? "" : " · " + I18n.get("oca.miscelanea.tipo." + tipo)), PanelJuego.AMARILLO);
            if (!tipo.isEmpty()) pj.detalle(I18n.get("oca.miscelanea.regla." + tipo), PanelJuego.BLANCO);
        }
        if (!p.log.isEmpty()) pj.nota(formatear(p.log.get(p.log.size() - 1)));
        pj.control(PanelJuego.Tecla.USAR, bot != null ? "oca.miscelanea.boton." + bot.clave() : "panel.miscelanea.ctrl.jugar");
        if (OcaCliente.botonVelocidad(p) != null) pj.control(PanelJuego.Tecla.AGACHADO_USAR, "panel.miscelanea.ctrl.velocidad");
    }

    public static int casillaEn(double tx, double ty) {
        int c = (int) Math.floor((tx - OcaGeo.MARCO) / OcaGeo.CELDA), f = (int) Math.floor((ty - OcaGeo.MARCO) / OcaGeo.CELDA);
        if (c < 0 || c > 7 || f < 0 || f > 7) return -1;
        for (int i = 0; i < 64; i++) if (OcaGeo.CELDAS[i][0] == c && OcaGeo.CELDAS[i][1] == f) return i;
        return -1;
    }

    private static String estado(OcaPartida p) {
        OcaPartida.Jugador j = p.actual();
        return switch (p.fase) {
            case OcaPartida.LOBBY -> I18n.get("oca.miscelanea.estado.lobby", p.js.size());
            case OcaPartida.TIRAR -> I18n.get("oca.miscelanea.estado.tirar", j.nombre);
            case OcaPartida.MOVER -> I18n.get("oca.miscelanea.estado.mover", j.nombre, p.dado);
            default -> p.ganador >= 0 && p.ganador < p.js.size() ? I18n.get("oca.miscelanea.estado.gana", p.js.get(p.ganador).nombre)
                    : I18n.get("oca.miscelanea.estado.fin");
        };
    }

    static String formatear(String e) {
        String[] s = e.split("\\|");
        Object[] args = new Object[s.length - 1];
        System.arraycopy(s, 1, args, 0, args.length);
        return I18n.get("oca.miscelanea.log." + s[0], args);
    }

    private void rect(VertexConsumer vc, PoseStack ps, OcaBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        quad(vc, ps, be, new double[][]{{cx - w / 2, cy + h / 2}, {cx + w / 2, cy + h / 2}, {cx + w / 2, cy - h / 2}, {cx - w / 2, cy - h / 2}}, rgb, a);
    }

    private void anillo(VertexConsumer vc, PoseStack ps, OcaBlockEntity be, double cx, double cy, double r0, double r1, int rgb, float a) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            quad(vc, ps, be, new double[][]{{cx + Math.cos(a0) * r1, cy + Math.sin(a0) * r1}, {cx + Math.cos(a1) * r1, cy + Math.sin(a1) * r1},
                    {cx + Math.cos(a1) * r0, cy + Math.sin(a1) * r0}, {cx + Math.cos(a0) * r0, cy + Math.sin(a0) * r0}}, rgb, a);
        }
    }

    private void quad(VertexConsumer vc, PoseStack ps, OcaBlockEntity be, double[][] pts, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float dy = 0.003F + (capa++ % 64) * 0.0002F;
        for (double[] pt : pts) {
            Vec3 q = local(be, pt[0], pt[1]);
            vc.vertex(m, (float) q.x, (float) q.y + dy, (float) q.z).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                    .normal(n, 0, 1, 0).endVertex();
        }
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
