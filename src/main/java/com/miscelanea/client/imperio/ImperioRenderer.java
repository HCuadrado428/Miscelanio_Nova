package com.miscelanea.client.imperio;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.imperio.Casillas;
import com.miscelanea.imperio.ImperioBlock;
import com.miscelanea.imperio.ImperioBlockEntity;
import com.miscelanea.imperio.ImperioBotones;
import com.miscelanea.imperio.ImperioGeo;
import com.miscelanea.imperio.Partida;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.language.I18n;
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

/** Fichas, casas, dados que ruedan, botones físicos, marcas de propietario y paneles flotantes del juego de propiedades. */
public class ImperioRenderer implements BlockEntityRenderer<ImperioBlockEntity> {
    /** Las marcas planas se apilan con un desplazamiento mínimo para que no parpadeen al pisarse. */
    private int capa;
    public static final String[] FICHAS = {"carrito", "gallina", "diamante", "barca", "campana", "cohete"};
    public static final int[] COLORES = {0xE04848, 0x4878E0, 0x48C060, 0xE0C040};
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");

    public ImperioRenderer(BlockEntityRendererProvider.Context c) {}

    private static final java.util.Map<String, ResourceLocation> RL = new java.util.HashMap<>();

    public static ResourceLocation modelo(String n) {
        return RL.computeIfAbsent(n, k -> new ResourceLocation(Miscelanea.MOD_ID, "block/imperio/" + k));
    }

    private Vec3 local(ImperioBlockEntity be, double tx, double ty) {
        return be.mundo(tx, ty).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    @Override
    public void render(ImperioBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        capa = 0;
        if (be.getLevel() == null) return;
        Partida p = be.p;
        long ahora = be.getLevel().getGameTime();
        float t = ahora + pt;
        Direction f = be.getBlockState().getValue(ImperioBlock.FACING);
        float giro = 180 - f.toYRot();
        var mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var mm = Minecraft.getInstance().getModelManager();
        VertexConsumer solido = buf.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer marca = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        boolean miro = ImperioCliente.apuntado == be;
        int hover = miro ? ImperioGeo.casillaEn(ImperioCliente.tx, ImperioCliente.ty) : -1;

        // ---------------- propietarios, hipotecas, selección y casilla apuntada
        for (int c = 0; c < 40; c++) {
            if (p.dueno[c] >= 0) {
                double[] b = ImperioGeo.banda(c);
                double[] ce = ImperioGeo.centro(c);
                // tira de color del dueño en el borde exterior
                double ox = ce[0] + (ce[0] - b[0]) * 0.75, oy = ce[1] + (ce[1] - b[1]) * 0.75;
                rect(marca, ps, be, ox, oy, b[2] != 0 ? 34 : 6, b[2] != 0 ? 6 : 34, COLORES[p.dueno[c] % 4], 0.85F);
                if (p.hipo[c]) rect(marca, ps, be, ce[0], ce[1] - (b[1] - ce[1]) * 0, 38, 38, 0x202020, 0.45F);
            }
            if (c == p.sel) rect(marca, ps, be, ImperioGeo.centro(c)[0], ImperioGeo.centro(c)[1], 40, 40, 0xFFE040, 0.35F + 0.15F * Mth.sin(t * 0.3F));
        }
        if (hover >= 0) rect(marca, ps, be, ImperioGeo.centro(hover)[0], ImperioGeo.centro(hover)[1], 40, 40, 0xFFFFFF, 0.25F);

        // ---------------- casas y hoteles en la banda de color
        for (int c = 0; c < 40; c++) {
            int n = p.casas[c];
            if (n == 0) continue;
            double[] b = ImperioGeo.banda(c);
            if (n == 5) pieza(mr, mm, solido, ps, local(be, b[0], b[1]), "hotel", 0.075F, giro + (b[2] != 0 ? 0 : 90), luz);
            else for (int k = 0; k < n; k++) {
                double off = (k - (n - 1) / 2.0) * 9;
                pieza(mr, mm, solido, ps, local(be, b[0] + b[2] * off, b[1] + b[3] * off), "casa", 0.04F, giro, luz);
            }
        }

        // ---------------- fichas
        for (int i = 0; i < p.js.size(); i++) {
            Partida.Jugador j = p.js.get(i);
            if (j.fuera) continue;
            double[] pos;
            double salto = 0;
            if (p.fase == Partida.MOVER && i == p.turno && t >= p.tMover) {
                float e = (t - p.tMover) / p.paso();
                int paso = Math.min(p.movPasos, (int) e);
                float fr = Math.min(1F, e - paso);
                int a = p.movAtras ? Math.floorMod(p.movDesde - paso, 40) : (p.movDesde + paso) % 40;
                int b = p.movAtras ? Math.floorMod(a - 1, 40) : (a + 1) % 40;
                double[] pa = ImperioGeo.centro(a), pb = ImperioGeo.centro(b);
                if (paso >= p.movPasos) pos = pa;
                else {
                    pos = new double[]{Mth.lerp(fr, pa[0], pb[0]), Mth.lerp(fr, pa[1], pb[1])};
                    salto = Math.sin(fr * Math.PI) * 0.08;
                }
            } else {
                pos = ImperioGeo.centro(j.pos).clone();
                if (j.pos == 10 && j.carcel > 0) pos = new double[]{48, 470};
            }
            double ox = (i % 2 == 0 ? -7 : 7), oy = (i < 2 ? -7 : 7);
            Vec3 l = local(be, pos[0] + ox, pos[1] + oy).add(0, salto, 0);
            pieza(mr, mm, solido, ps, l, FICHAS[j.ficha % FICHAS.length], 0.13F, giro + ImperioGeo.asiento(i) * 90, luz);
            // anillo del color del jugador
            rect(marca, ps, be, pos[0] + ox, pos[1] + oy, 15, 15, COLORES[i % 4], 0.55F);
        }

        // ---------------- dados
        if (p.fase != Partida.LOBBY) {
            float rodar = (t - p.tDados) / Math.max(6F, p.dadoTicks());
            for (int k = 0; k < 2; k++) {
                int v = k == 0 ? p.d1 : p.d2;
                Vec3 c = local(be, 236 + k * 40, 255);
                ps.pushPose();
                if (rodar < 1) {
                    float r = 1 - rodar;
                    ps.translate(c.x, c.y + Math.abs(Mth.sin(rodar * 9F)) * 0.12 * r, c.z);
                    ps.mulPose(Axis.XP.rotationDegrees(r * 900 + k * 70));
                    ps.mulPose(Axis.ZP.rotationDegrees(r * 640 + k * 110));
                } else ps.translate(c.x, c.y, c.z);
                ps.translate(0, 0.035, 0);
                ps.mulPose(Axis.YP.rotationDegrees(giro + 12 * (k * 2 - 1)));
                switch (v) {
                    case 6 -> ps.mulPose(Axis.XP.rotationDegrees(180));
                    case 2 -> ps.mulPose(Axis.XP.rotationDegrees(90));
                    case 5 -> ps.mulPose(Axis.XP.rotationDegrees(-90));
                    case 3 -> ps.mulPose(Axis.ZP.rotationDegrees(90));
                    case 4 -> ps.mulPose(Axis.ZP.rotationDegrees(-90));
                    default -> {}
                }
                float s = 0.07F;
                ps.scale(s, s, s);
                ps.translate(-0.5, -0.5, -0.5);
                mr.renderModel(ps.last(), solido, null, mm.getModel(modelo("dado")), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
            }
        }

        // ---------------- botones (placas en el centro) con su texto mirando al jugador de turno
        Font font = Minecraft.getInstance().font;
        int asiento = p.fase == Partida.LOBBY || p.actual() == null ? 0 : ImperioGeo.asiento(p.turno);
        Direction mira = switch (asiento) {
            case 1 -> f.getClockWise();
            case 2 -> f.getOpposite();
            case 3 -> f.getCounterClockWise();
            default -> f;
        };
        for (ImperioBotones.Boton b : ImperioBotones.de(p)) {
            boolean enc = miro && b.dentro(ImperioCliente.tx, ImperioCliente.ty);
            int col = enc ? mezcla(b.color(), 0xFFFFFF, 0.3F) : b.color();
            rect(marca, ps, be, b.x(), b.y() + 2, b.w(), b.h(), 0x000000, 0.35F);           // sombra
            rect(marca, ps, be, b.x(), b.y(), b.w(), b.h(), col, 0.95F);
            rect(marca, ps, be, b.x(), b.y() - b.h() / 2 + 2, b.w() - 4, 3, 0xFFFFFF, 0.35F);   // brillo
            String txt = I18n.get("imperio.miscelanea.boton." + b.clave(), b.args());
            Vec3 c = local(be, b.x(), b.y());
            ps.pushPose();
            ps.translate(c.x, c.y + 0.004, c.z);
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-mira.toYRot()), (float) Math.toRadians(90), 0));
            float esc = (float) Math.min(0.0045, b.w() / ImperioGeo.N * ImperioGeo.LADO / Math.max(1, font.width(txt)) * 0.9);
            ps.scale(-esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
            ps.popPose();
        }

        // ---------------- panel de HUD (antes eran tres carteles flotantes sobre el tablero)
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), miro, "block.miscelanea.imperio", 0xFF40B060);
        if (pj == null) return;
        Minecraft mc = Minecraft.getInstance();
        pj.linea(estado(p), PanelJuego.AMARILLO);
        if (p.fase == Partida.MOVER) pj.marcador(p.d1 + "+" + p.d2, PanelJuego.BLANCO);
        for (int i = 0; i < p.js.size(); i++) {
            Partida.Jugador j = p.js.get(i);
            String extra = j.dinero + " ◆" + (j.fuera ? " ✖" : (j.carcel > 0 ? " ⛓" : ""));
            pj.jugador(j.nombre, extra, j.fuera ? 0x808080 : COLORES[i % 4], i == p.turno && p.fase != Partida.LOBBY);
        }
        Partida.Jugador yo = p.actual();
        if (yo != null && mc.player != null && mc.player.getUUID().equals(yo.uuid) && p.fase >= Partida.TIRAR && p.fase <= Partida.DEUDA && p.fase != Partida.MOVER)
            pj.detalle(I18n.get("imperio.miscelanea.pista." + p.fase), PanelJuego.CIAN);
        if (p.carta.length() > 0 && t - p.tCarta < 90) {
            boolean cofre = p.carta.startsWith("cofre");
            pj.detalle(I18n.get(cofre ? "imperio.miscelanea.cofre" : "imperio.miscelanea.destino"), cofre ? 0xFFF0B040 : 0xFFFF8040);
            pj.detalle(I18n.get("imperio.miscelanea.carta." + p.carta), PanelJuego.BLANCO);
        }
        if (hover >= 0) {
            int g = Casillas.grupo(hover);
            pj.detalle(I18n.get(Casillas.clave(hover)), g >= 0 ? 0xFF000000 | Casillas.COLOR[g] : PanelJuego.BLANCO);
            if (Casillas.comprable(hover)) {
                pj.detalle(I18n.get("imperio.miscelanea.info.precio", Casillas.precio(hover)), PanelJuego.BLANCO);
                if (Casillas.tipo(hover) == Casillas.PROP) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k <= 5; k++) sb.append(k == 0 ? "" : " / ").append(Casillas.alquilerCalle(hover, k));
                    pj.detalle(I18n.get("imperio.miscelanea.info.alquiler", sb.toString()), 0xFFC0E0C0);
                    pj.detalle(I18n.get("imperio.miscelanea.info.casa", Casillas.casa(hover)), 0xFFC0C0C0);
                } else
                    pj.detalle(I18n.get(Casillas.tipo(hover) == Casillas.ESTACION ? "imperio.miscelanea.info.estacion" : "imperio.miscelanea.info.util"), 0xFFC0E0C0);
                int d = p.dueno[hover];
                if (d >= 0 && d < p.js.size()) {
                    pj.detalle(I18n.get("imperio.miscelanea.info.dueno", p.js.get(d).nombre) + (p.hipo[hover] ? " · " + I18n.get("imperio.miscelanea.info.hipotecada") : "")
                            + (p.casas[hover] > 0 ? " · " + (p.casas[hover] == 5 ? I18n.get("imperio.miscelanea.info.hotel") : I18n.get("imperio.miscelanea.info.casas", p.casas[hover])) : ""),
                            0xFF000000 | COLORES[d % 4]);
                    if (!p.hipo[hover]) pj.detalle(I18n.get("imperio.miscelanea.info.cobra", p.alquiler(hover)), PanelJuego.AMARILLO);
                } else pj.detalle(I18n.get("imperio.miscelanea.info.libre"), PanelJuego.VERDE);
            } else pj.detalle(I18n.get("imperio.miscelanea.info." + Casillas.ID[hover]), 0xFFC0C0C0);
        }
        if (!p.log.isEmpty()) pj.nota(formatear(p.log.get(p.log.size() - 1)));
        ImperioBotones.Boton bot = miro ? ImperioBotones.en(p, ImperioCliente.tx, ImperioCliente.ty) : null;
        if (bot != null) pj.control(PanelJuego.Tecla.USAR, "imperio.miscelanea.boton." + bot.clave(), bot.args());
        else pj.control(PanelJuego.Tecla.USAR, hover >= 0 ? "panel.miscelanea.ctrl.elegir_calle" : "panel.miscelanea.ctrl.jugar");
        if (ImperioCliente.botonVelocidad(p) != null) pj.control(PanelJuego.Tecla.AGACHADO_USAR, "panel.miscelanea.ctrl.velocidad");
    }

    private static String estado(Partida p) {
        Partida.Jugador j = p.actual();
        return switch (p.fase) {
            case Partida.LOBBY -> I18n.get("imperio.miscelanea.estado.lobby", p.js.size());
            case Partida.TIRAR -> I18n.get(j.carcel > 0 ? "imperio.miscelanea.estado.carcel" : "imperio.miscelanea.estado.tirar", j.nombre)
                    + (Partida.LIMITES[p.limite] > 0 ? "  (" + I18n.get("imperio.miscelanea.ronda", p.ronda, Partida.LIMITES[p.limite]) + ")" : "");
            case Partida.MOVER -> I18n.get("imperio.miscelanea.estado.mover", j.nombre, p.d1 + p.d2);
            case Partida.COMPRAR -> I18n.get("imperio.miscelanea.estado.comprar", j.nombre, I18n.get(Casillas.clave(j.pos)), Casillas.precio(j.pos));
            case Partida.GESTION -> I18n.get("imperio.miscelanea.estado.gestion", j.nombre);
            case Partida.DEUDA -> I18n.get("imperio.miscelanea.estado.deuda", j.nombre, -j.dinero);
            default -> I18n.get("imperio.miscelanea.estado.fin");
        };
    }

    static String formatear(String e) {
        String[] s = e.split("\\|");
        Object[] args = new Object[s.length - 1];
        for (int i = 1; i < s.length; i++) {
            String a = s[i];
            int idx = java.util.Arrays.asList(Casillas.ID).indexOf(a);
            args[i - 1] = idx >= 0 ? I18n.get(Casillas.clave(idx)) : a;
        }
        return I18n.get("imperio.miscelanea.log." + s[0], args);
    }

    private static void pieza(net.minecraft.client.renderer.block.ModelBlockRenderer mr, net.minecraft.client.resources.model.ModelManager mm, VertexConsumer vc,
                              PoseStack ps, Vec3 l, String n, float esc, float rot, int luz) {
        ps.pushPose();
        ps.translate(l.x, l.y, l.z);
        ps.mulPose(Axis.YP.rotationDegrees(rot));
        ps.scale(esc, esc, esc);
        ps.translate(-0.5, 0, -0.5);
        mr.renderModel(ps.last(), vc, null, mm.getModel(modelo(n)), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        ps.popPose();
    }

    private void rect(VertexConsumer vc, PoseStack ps, ImperioBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        Vec3 p00 = local(be, cx - w / 2, cy - h / 2), p10 = local(be, cx + w / 2, cy - h / 2), p11 = local(be, cx + w / 2, cy + h / 2), p01 = local(be, cx - w / 2, cy + h / 2);
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float dy = 0.003F + (capa++ % 64) * 0.0002F;
        float y = 0.003F;
        for (Vec3 q : new Vec3[]{p01, p11, p10, p00})
            vc.vertex(m, (float) q.x, (float) q.y + dy, (float) q.z).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
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
