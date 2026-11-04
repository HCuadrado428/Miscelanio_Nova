package com.miscelanea.client.parchis;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.parchis.ParchisBlock;
import com.miscelanea.parchis.ParchisBlockEntity;
import com.miscelanea.parchis.ParchisBotones;
import com.miscelanea.parchis.ParchisGeo;
import com.miscelanea.parchis.ParchisPartida;
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

/** Fichas que saltan casilla a casilla, fichas comidas que vuelven volando a casa, dado, botones y paneles del parchís. */
public class ParchisRenderer implements BlockEntityRenderer<ParchisBlockEntity> {
    /** Las marcas planas se apilan con un desplazamiento mínimo para que no parpadeen al pisarse. */
    private int capa;
    public static final String[] COLOR_ID = {"amarillo", "azul", "rojo", "verde"};
    public static final int[] COLORES = {0xF2C420, 0x3478E8, 0xE23A30, 0x2EB052};
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");
    private static final float ESC_FICHA = 0.075F;

    public ParchisRenderer(BlockEntityRendererProvider.Context c) {}

    private static final ResourceLocation[] FICHA_RL = new ResourceLocation[4];
    private static final ResourceLocation DADO = new ResourceLocation(Miscelanea.MOD_ID, "block/imperio/dado");

    public static ResourceLocation modelo(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "block/parchis/" + n);
    }

    private static ResourceLocation ficha(int c) {
        if (FICHA_RL[c] == null) FICHA_RL[c] = modelo("ficha_" + COLOR_ID[c]);
        return FICHA_RL[c];
    }

    /** Datos que solo cambian cuando llega un estado nuevo del servidor (cada actualización crea un objeto nuevo). */
    private static final class Cache {
        final double[][] pos = new double[16][];
        List<Integer> opciones;
        final int[] destino = new int[4];
        final int[] victima = new int[4];
    }

    private static final java.util.Map<ParchisPartida, Cache> CACHE = new java.util.WeakHashMap<>();

    private static Cache cache(ParchisPartida p) {
        Cache k = CACHE.get(p);
        if (k != null) return k;
        k = new Cache();
        for (int c = 0; c < 4; c++) if (p.enJuego(c)) for (int f = 0; f < 4; f++) k.pos[c * 4 + f] = ParchisGeo.ficha(p, c, f);
        int c0 = p.colorActual();
        k.opciones = p.fase == ParchisPartida.ELEGIR && c0 >= 0 ? p.opciones(c0, p.valor, p.esBono) : List.of();
        for (int f : k.opciones) {
            k.destino[f] = p.destino(c0, f, p.valor);
            k.victima[f] = p.victima(c0, p.prog[c0][f], k.destino[f]);
        }
        CACHE.put(p, k);
        return k;
    }

    private Vec3 local(ParchisBlockEntity be, double tx, double ty) {
        return be.mundo(tx, ty).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    @Override
    public void render(ParchisBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        capa = 0;
        if (be.getLevel() == null) return;
        ParchisPartida p = be.p;
        float t = be.getLevel().getGameTime() + pt;
        Direction f = be.getBlockState().getValue(ParchisBlock.FACING);
        float giro = 180 - f.toYRot();
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ModelManager mm = Minecraft.getInstance().getModelManager();
        VertexConsumer solido = buf.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer marca = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        boolean miro = ParchisCliente.apuntado == be;
        Minecraft mc = Minecraft.getInstance();
        int c0 = p.colorActual();
        boolean mio = mc.player != null && p.actual() != null && mc.player.getUUID().equals(p.actual().uuid);
        Cache cache = cache(p);
        List<Integer> opciones = cache.opciones;

        // ---------------- fichas
        for (int c = 0; c < 4; c++) {
            if (!p.enJuego(c)) continue;
            for (int k = 0; k < 4; k++) {
                double[] pos = cache.pos[c * 4 + k];
                double alto = 0;
                if (c == p.movColor && k == p.movFicha && t < p.tFin && t >= p.tMover) {
                    double[] r = camino(p, c, k, t);
                    pos = new double[]{r[0], r[1]};
                    alto = r[2];
                } else if (c == p.movColor && k == p.movFicha && t < p.tMover) {
                    pos = ParchisGeo.paso(c, p.movDesde, k);
                }
                if (c == p.capColor && k == p.capFicha && t < p.tCap + 16) {
                    double[] desde = ParchisGeo.paso(c, p.capDesde, k), casa = ParchisGeo.casa(c, k);
                    float e = Mth.clamp((t - p.tCap) / 16F, 0F, 1F);
                    float s = e * e * (3 - 2 * e);
                    pos = new double[]{Mth.lerp(s, desde[0], casa[0]), Mth.lerp(s, desde[1], casa[1])};
                    alto = Math.sin(e * Math.PI) * 0.28;
                }
                Vec3 l = local(be, pos[0], pos[1]).add(0, alto, 0);
                ps.pushPose();
                ps.translate(l.x, l.y, l.z);
                if (c == p.capColor && k == p.capFicha && t >= p.tCap && t < p.tCap + 16) {
                    float e = (t - p.tCap) / 16F;
                    ps.mulPose(Axis.XP.rotationDegrees(e * 360));
                }
                ps.mulPose(Axis.YP.rotationDegrees(giro));
                ps.scale(ESC_FICHA, ESC_FICHA, ESC_FICHA);
                ps.translate(-0.5, 0, -0.5);
                mr.renderModel(ps.last(), solido, null, mm.getModel(ficha(c)), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
            }
        }

        // ---------------- fichas que se pueden mover y destino de la apuntada
        Font font = mc.font;
        String verQue = null;
        int verCol = PanelJuego.BLANCO;
        if (!opciones.isEmpty() && t - p.tDado > Math.min(12, p.dadoTicks())) {
            float pulso = 0.45F + 0.25F * Mth.sin(t * 0.35F);
            int apunta = miro && mio ? ParchisGeo.elegir(p, c0, opciones, ParchisCliente.tx, ParchisCliente.ty) : -1;
            for (int k : opciones) {
                double[] q = cache.pos[c0 * 4 + k];
                anillo(marca, ps, be, q[0], q[1], 10.5, 13.5, k == apunta ? 0xFFFFFF : COLORES[c0], k == apunta ? 0.95F : pulso);
            }
            if (apunta >= 0) {
                int d = cache.destino[apunta];
                boolean come = cache.victima[apunta] >= 0;
                double[] q = ParchisGeo.paso(c0, d, apunta), a = cache.pos[c0 * 4 + apunta];
                anillo(marca, ps, be, q[0], q[1], 7, 11, come ? 0xFF3030 : 0xFFFFFF, 0.55F + 0.3F * Mth.sin(t * 0.5F));
                disco(marca, ps, be, q[0], q[1], 7, come ? 0xFF3030 : 0xFFFFFF, 0.25F);
                // línea de puntos de la ficha a su destino
                for (int i = 1; i < 8; i++) {
                    double e = i / 8.0;
                    disco(marca, ps, be, Mth.lerp(e, a[0], q[0]), Mth.lerp(e, a[1], q[1]), 1.6, 0xFFFFFF, 0.6F);
                }
                // qué pasará
                String que;
                if (come) que = I18n.get("parchis.miscelanea.ver.come");
                else if (d == ParchisPartida.META) que = I18n.get("parchis.miscelanea.ver.meta");
                else if (d >= ParchisPartida.PASILLO) que = I18n.get("parchis.miscelanea.ver.pasillo", ParchisPartida.META - d);
                else if (p.prog[c0][apunta] == ParchisPartida.CASA) que = I18n.get("parchis.miscelanea.ver.sale");
                else {
                    int g = ParchisPartida.casilla(c0, d);
                    que = I18n.get(ParchisPartida.seguro(g) ? "parchis.miscelanea.ver.seguro" : "parchis.miscelanea.ver.casilla", g == 0 ? 68 : g);
                }
                verQue = que;
                verCol = come ? PanelJuego.ROJO : PanelJuego.BLANCO;
            }
        }

        // ---------------- dado (en la esquina interior de la casa de quien tira)
        if (p.fase != ParchisPartida.LOBBY && c0 >= 0) {
            double[] dc = ParchisGeo.tex(c0, 86, 86);
            float rodar = (t - p.tDado) / (float) p.dadoTicks();
            Vec3 c = local(be, dc[0], dc[1]);
            ps.pushPose();
            if (rodar < 1 && !p.esBono) {
                float r = 1 - rodar;
                double[] ini = ParchisGeo.tex(c0, 150, 40);
                Vec3 a = local(be, ini[0], ini[1]);
                float s = 1 - r * r;
                ps.translate(Mth.lerp(s, a.x, c.x), c.y + Math.abs(Mth.sin(rodar * 8F)) * 0.10 * r, Mth.lerp(s, a.z, c.z));
                ps.mulPose(Axis.XP.rotationDegrees(r * 820));
                ps.mulPose(Axis.ZP.rotationDegrees(r * 560));
            } else ps.translate(c.x, c.y, c.z);
            ps.translate(0, 0.035, 0);
            ps.mulPose(Axis.YP.rotationDegrees(giro + c0 * 90 + 14));
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
            mr.renderModel(ps.last(), solido, null, mm.getModel(DADO), 1F, 1F, 1F, luz,
                    OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }

        // ---------------- botones con el texto hacia quien juega
        int asiento = p.fase == ParchisPartida.LOBBY || c0 < 0 ? 0 : c0;
        Direction mira = switch (asiento) {
            case 1 -> f.getClockWise();
            case 2 -> f.getOpposite();
            case 3 -> f.getCounterClockWise();
            default -> f;
        };
        for (ParchisBotones.Boton b : ParchisBotones.de(p)) {
            boolean enc = miro && b.dentro(ParchisCliente.tx, ParchisCliente.ty);
            int col = enc ? mezcla(b.color(), 0xFFFFFF, 0.3F) : b.color();
            rect(marca, ps, be, b.x(), b.y() + 2, b.w(), b.h(), 0x000000, 0.35F);
            rect(marca, ps, be, b.x(), b.y(), b.w(), b.h(), col, 0.95F);
            rect(marca, ps, be, b.x(), b.y() - b.h() / 2 + 2, b.w() - 4, 3, 0xFFFFFF, 0.35F);
            String txt = I18n.get("parchis.miscelanea.boton." + b.clave());
            if (b.w() < 70 && be.mundo(b.x(), b.y()).distanceToSqr(mc.player != null ? mc.player.getEyePosition() : Vec3.ZERO) > 64) continue;
            Vec3 c = local(be, b.x(), b.y());
            ps.pushPose();
            ps.translate(c.x, c.y + 0.004, c.z);
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-mira.toYRot()), (float) Math.toRadians(90), 0));
            float esc = (float) Math.min(0.0045, b.w() / ParchisGeo.N * ParchisGeo.LADO / Math.max(1, font.width(txt)) * 0.9);
            ps.scale(-esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
            ps.popPose();
        }

        // ---------------- panel de HUD (sustituye al cartel flotante)
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), miro, "block.miscelanea.parchis", 0xFFE0A020);
        if (pj == null) return;
        pj.linea(estado(p), PanelJuego.AMARILLO);
        if (p.fase == ParchisPartida.ELEGIR && p.esBono && c0 >= 0) pj.marcador("+" + p.valor, 0xFF000000 | COLORES[c0]);
        for (int i = 0; i < p.js.size(); i++) {
            ParchisPartida.Jugador j = p.js.get(i);
            int enMeta = 0;
            for (int k = 0; k < 4; k++) if (p.prog[j.color][k] == ParchisPartida.META) enMeta++;
            pj.jugador(j.nombre, I18n.get("parchis.miscelanea.color." + COLOR_ID[j.color]) + "  " + "★".repeat(enMeta) + "☆".repeat(4 - enMeta),
                    COLORES[j.color], i == p.turno && p.fase != ParchisPartida.LOBBY && p.fase != ParchisPartida.FIN);
        }
        if (mio && (p.fase == ParchisPartida.TIRAR || p.fase == ParchisPartida.ELEGIR))
            pj.detalle(I18n.get("parchis.miscelanea.pista." + (p.fase == ParchisPartida.TIRAR ? "tirar" : "elegir")), PanelJuego.CIAN);
        if (p.fase == ParchisPartida.LOBBY) pj.detalle(I18n.get("parchis.miscelanea.pista.color"), PanelJuego.CIAN);
        if (verQue != null) pj.detalle(verQue, verCol);
        if (!p.log.isEmpty()) pj.nota(formatear(p.log.get(p.log.size() - 1)));
        ParchisBotones.Boton bot = miro ? ParchisBotones.en(p, ParchisCliente.tx, ParchisCliente.ty) : null;
        pj.control(PanelJuego.Tecla.USAR, bot != null ? "parchis.miscelanea.boton." + bot.clave() : "panel.miscelanea.ctrl.jugar");
        if (ParchisCliente.botonVelocidad(p) != null) pj.control(PanelJuego.Tecla.AGACHADO_USAR, "panel.miscelanea.ctrl.velocidad");
    }

    /** Posición animada (tx, ty, altura) de la ficha que se está moviendo. */
    private static double[] camino(ParchisPartida p, int c, int k, float t) {
        if (p.movDesde == ParchisPartida.CASA) {
            float e = Mth.clamp((t - p.tMover) / (2F * p.movPaso), 0F, 1F);
            double[] a = ParchisGeo.casa(c, k), b = ParchisGeo.ficha(p, c, k);
            return new double[]{Mth.lerp(e, a[0], b[0]), Mth.lerp(e, a[1], b[1]), Math.sin(e * Math.PI) * 0.16};
        }
        int pasos = p.movHasta - p.movDesde;
        float e = (t - p.tMover) / p.movPaso;
        int i = Math.min(pasos, (int) e);
        float fr = Math.min(1F, e - i);
        if (i >= pasos) {
            double[] b = ParchisGeo.ficha(p, c, k);
            return new double[]{b[0], b[1], 0};
        }
        double[] a = ParchisGeo.paso(c, p.movDesde + i, k);
        double[] b = i + 1 >= pasos ? ParchisGeo.ficha(p, c, k) : ParchisGeo.paso(c, p.movDesde + i + 1, k);
        return new double[]{Mth.lerp(fr, a[0], b[0]), Mth.lerp(fr, a[1], b[1]), Math.sin(fr * Math.PI) * (p.movPaso > 2 ? 0.05 : 0.03)};
    }

    private static String estado(ParchisPartida p) {
        ParchisPartida.Jugador j = p.actual();
        return switch (p.fase) {
            case ParchisPartida.LOBBY -> I18n.get("parchis.miscelanea.estado.lobby", p.js.size());
            case ParchisPartida.TIRAR -> I18n.get("parchis.miscelanea.estado.tirar", j.nombre);
            case ParchisPartida.ELEGIR -> p.esBono ? I18n.get("parchis.miscelanea.estado.bono", j.nombre, p.valor)
                    : I18n.get("parchis.miscelanea.estado.elegir", j.nombre, p.valor);
            case ParchisPartida.MOVER -> I18n.get("parchis.miscelanea.estado.mover", j.nombre);
            default -> p.ganador >= 0 && p.deColor(p.ganador) != null ? I18n.get("parchis.miscelanea.estado.gana", p.deColor(p.ganador).nombre)
                    : I18n.get("parchis.miscelanea.estado.fin");
        };
    }

    static String formatear(String e) {
        String[] s = e.split("\\|");
        Object[] args = new Object[s.length - 1];
        System.arraycopy(s, 1, args, 0, args.length);
        return I18n.get("parchis.miscelanea.log." + s[0], args);
    }

    private void rect(VertexConsumer vc, PoseStack ps, ParchisBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        quad(vc, ps, be, new double[][]{{cx - w / 2, cy + h / 2}, {cx + w / 2, cy + h / 2}, {cx + w / 2, cy - h / 2}, {cx - w / 2, cy - h / 2}}, rgb, a);
    }

    private void anillo(VertexConsumer vc, PoseStack ps, ParchisBlockEntity be, double cx, double cy, double r0, double r1, int rgb, float a) {
        int n = 20;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            quad(vc, ps, be, new double[][]{{cx + Math.cos(a0) * r1, cy + Math.sin(a0) * r1}, {cx + Math.cos(a1) * r1, cy + Math.sin(a1) * r1},
                    {cx + Math.cos(a1) * r0, cy + Math.sin(a1) * r0}, {cx + Math.cos(a0) * r0, cy + Math.sin(a0) * r0}}, rgb, a);
        }
    }

    private void disco(VertexConsumer vc, PoseStack ps, ParchisBlockEntity be, double cx, double cy, double r, int rgb, float a) {
        int n = 16;
        for (int i = 0; i < n; i += 2) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n, a2 = (i + 2) * Math.PI * 2 / n;
            quad(vc, ps, be, new double[][]{{cx, cy}, {cx + Math.cos(a2) * r, cy + Math.sin(a2) * r}, {cx + Math.cos(a1) * r, cy + Math.sin(a1) * r},
                    {cx + Math.cos(a0) * r, cy + Math.sin(a0) * r}}, rgb, a);
        }
    }

    private void quad(VertexConsumer vc, PoseStack ps, ParchisBlockEntity be, double[][] pts, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float dy = 0.003F + (capa++ % 64) * 0.0002F;
        Vec3 q0 = local(be, pts[0][0], pts[0][1]), q1 = local(be, pts[1][0], pts[1][1]);
        Vec3 q2 = local(be, pts[2][0], pts[2][1]);
        // orientar hacia arriba sea cual sea el giro del tablero
        boolean arriba = q1.subtract(q0).cross(q2.subtract(q1)).y < 0;
        int[] orden = arriba ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
        for (int i : orden) {
            Vec3 q = local(be, pts[i][0], pts[i][1]);
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
