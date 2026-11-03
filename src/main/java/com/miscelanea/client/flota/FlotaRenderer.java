package com.miscelanea.client.flota;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.flota.FlotaBlock;
import com.miscelanea.flota.FlotaBlockEntity;
import com.miscelanea.flota.FlotaBotones;
import com.miscelanea.flota.FlotaGeo;
import com.miscelanea.flota.FlotaPartida;
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

/** Barcos 3D (solo los ve su dueño hasta que se hunden), proyectiles que caen, clavijas de agua y tocado, mira y paneles. */
public class FlotaRenderer implements BlockEntityRenderer<FlotaBlockEntity> {
    /** Las marcas planas se apilan con un desplazamiento mínimo para que no parpadeen al pisarse. */
    private int capa;
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");
    public static final ResourceLocation[] BARCO = new ResourceLocation[6];
    public static final ResourceLocation PIN_AGUA = new ResourceLocation(Miscelanea.MOD_ID, "block/flota/pin_agua"),
            PIN_TOCADO = new ResourceLocation(Miscelanea.MOD_ID, "block/flota/pin_tocado"),
            PROYECTIL = new ResourceLocation(Miscelanea.MOD_ID, "block/flota/proyectil");
    private static final float ESC_BARCO = 0.3125F;

    static {
        for (int n = 2; n <= 5; n++) BARCO[n] = new ResourceLocation(Miscelanea.MOD_ID, "block/flota/barco_" + n);
    }

    public FlotaRenderer(BlockEntityRendererProvider.Context c) {}

    private Vec3 local(FlotaBlockEntity be, double tx, double ty) {
        return be.mundo(tx, ty).subtract(Vec3.atLowerCornerOf(be.getBlockPos()));
    }

    @Override
    public void render(FlotaBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        capa = 0;
        if (be.getLevel() == null) return;
        FlotaPartida p = be.p;
        float t = be.getLevel().getGameTime() + pt;
        Direction f = be.getBlockState().getValue(FlotaBlock.FACING);
        float giro = 180 - f.toYRot();
        ModelBlockRenderer mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        ModelManager mm = Minecraft.getInstance().getModelManager();
        VertexConsumer solido = buf.getBuffer(Sheets.cutoutBlockSheet());
        VertexConsumer marca = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean miro = FlotaCliente.apuntado == be;
        int yo = -1;
        if (mc.player != null) for (int i = 0; i < p.js.size(); i++) if (mc.player.getUUID().equals(p.js.get(i).uuid)) yo = i;
        boolean cayendo = p.ultCelda >= 0 && t < p.tDisparo + FlotaPartida.VUELO;

        for (int m = 0; m < p.js.size(); m++) {
            FlotaPartida.Jugador j = p.js.get(m);
            boolean[] conBarco = new boolean[100];
            // ---------------- barcos
            for (int i = 0; i < FlotaPartida.TAM.length; i++) {
                int tam = FlotaPartida.TAM[i];
                boolean hund = p.hundido(j, i) && !(cayendo && p.ultHundido == i && 1 - p.ultTirador == m);
                boolean ver = m == yo || p.fase == FlotaPartida.FIN || hund;
                int[] cs = FlotaPartida.celdas(j.barcos[i], tam);
                if (!ver) continue;
                for (int c : cs) if (c >= 0 && c < 100) conBarco[c] = true;
                double[] a = FlotaGeo.centro(m, cs[0]), b = FlotaGeo.centro(m, cs[tam - 1]);
                Vec3 l = local(be, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2);
                float bal = hund ? 0 : 1.5F * Mth.sin(t * 0.06F + i * 1.3F + m);
                ps.pushPose();
                ps.translate(l.x, l.y - (hund ? 0.012 : 0), l.z);
                ps.mulPose(Axis.YP.rotationDegrees(giro + (j.barcos[i][2] == 1 ? 0 : 90)));
                ps.mulPose(Axis.XP.rotationDegrees(bal));
                if (hund) ps.mulPose(Axis.ZP.rotationDegrees(6));
                ps.scale(ESC_BARCO, ESC_BARCO, ESC_BARCO);
                ps.translate(-0.5, 0, -0.5);
                float k = hund ? 0.42F : 1F;
                mr.renderModel(ps.last(), solido, null, mm.getModel(BARCO[tam]), k, k, k, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
                if (p.fase == FlotaPartida.COLOCAR && m == yo && j.sel == i)
                    marco(marca, ps, be, (a[0] + b[0]) / 2, (a[1] + b[1]) / 2, Math.abs(b[0] - a[0]) + FlotaGeo.CELDA, Math.abs(b[1] - a[1]) + FlotaGeo.CELDA,
                            0xFFE040, 0.6F + 0.3F * Mth.sin(t * 0.4F));
            }
            // ---------------- clavijas
            for (int c = 0; c < 100; c++) {
                int v = j.recibidos[c];
                if (v == 0) continue;
                if (cayendo && c == p.ultCelda && 1 - p.ultTirador == m) continue;
                double[] q = FlotaGeo.centro(m, c);
                Vec3 l = local(be, q[0], q[1]).add(0, conBarco[c] ? 0.03 : 0, 0);
                ps.pushPose();
                ps.translate(l.x, l.y, l.z);
                ps.scale(0.11F, 0.11F, 0.11F);
                ps.translate(-0.5, 0, -0.5);
                mr.renderModel(ps.last(), solido, null, mm.getModel(v == FlotaPartida.AGUA ? PIN_AGUA : PIN_TOCADO), 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY,
                        ModelData.EMPTY, null);
                ps.popPose();
                if (v == FlotaPartida.TOCADO) rect(marca, ps, be, q[0], q[1], 16, 16, 0xFF3020, 0.35F);
            }
            // ---------------- flota restante en el lateral
            double colX = m == 0 ? 78 : 434;
            for (int i = 0; i < FlotaPartida.TAM.length; i++) {
                boolean hund = p.hundido(j, i);
                double y = m == 0 ? 304 + i * 36 : 208 - i * 36;
                Vec3 l = local(be, colX, y);
                ps.pushPose();
                ps.translate(l.x, l.y, l.z);
                ps.mulPose(Axis.YP.rotationDegrees(giro));
                if (hund) ps.mulPose(Axis.ZP.rotationDegrees(14));
                ps.scale(0.22F, 0.22F, 0.22F);
                ps.translate(-0.5, 0, -0.5);
                float k = hund ? 0.35F : 1F;
                mr.renderModel(ps.last(), solido, null, mm.getModel(BARCO[FlotaPartida.TAM[i]]), k, k, k, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                ps.popPose();
                if (hund) rect(marca, ps, be, colX, y, 120, 3, 0xD02020, 0.85F);
            }
        }

        // ---------------- proyectil cayendo
        if (cayendo && t >= p.tDisparo) {
            double[] q = FlotaGeo.centro(1 - p.ultTirador, p.ultCelda);
            float e = (t - p.tDisparo) / FlotaPartida.VUELO;
            Vec3 l = local(be, q[0], q[1]).add(0, 1.3 * (1 - e * e), 0);
            ps.pushPose();
            ps.translate(l.x, l.y, l.z);
            ps.mulPose(Axis.YP.rotationDegrees(t * 20));
            ps.scale(0.09F, 0.09F, 0.09F);
            ps.translate(-0.5, 0, -0.5);
            mr.renderModel(ps.last(), solido, null, mm.getModel(PROYECTIL), 1F, 1F, 1F, 0xF000F0, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
            // sombra que crece en la casilla
            rect(marca, ps, be, q[0], q[1], 6 + 10 * e, 6 + 10 * e, 0x000000, 0.25F + 0.3F * e);
        }

        // ---------------- mira sobre el mar rival / casilla propia al colocar
        int[] ap = miro ? FlotaGeo.en(FlotaCliente.tx, FlotaCliente.ty) : null;
        String mira = null;
        int miraCol = PanelJuego.BLANCO;
        if (ap != null && yo >= 0) {
            double[] q = FlotaGeo.centro(ap[0], ap[1]);
            if (p.fase == FlotaPartida.JUGAR && p.turno == yo && ap[0] != yo) {
                boolean libre = p.js.get(ap[0]).recibidos[ap[1]] == 0;
                int col = libre ? 0xFF3030 : 0x808080;
                marco(marca, ps, be, q[0], q[1], 18, 18, col, 0.9F);
                rect(marca, ps, be, q[0], q[1], 22, 1.5, col, 0.8F);
                rect(marca, ps, be, q[0], q[1], 1.5, 22, col, 0.8F);
                String coord = (char) ('A' + ap[1] % 10) + "" + (ap[1] / 10 + 1);
                mira = I18n.get("panel.miscelanea.flota.mira", libre ? coord : coord + " ✔");
                miraCol = libre ? 0xFFFF6060 : 0xFFA0A0A0;
            } else if (p.fase == FlotaPartida.COLOCAR && ap[0] == yo) rect(marca, ps, be, q[0], q[1], 18, 18, 0xFFFFFF, 0.25F);
        }

        // ---------------- botones
        int asiento = p.fase == FlotaPartida.JUGAR ? p.turno : 0;
        Direction mira = asiento == 1 ? f.getOpposite() : f;
        for (FlotaBotones.Boton b : FlotaBotones.de(p)) {
            boolean enc = miro && b.dentro(FlotaCliente.tx, FlotaCliente.ty);
            int col = enc ? mezcla(b.color(), 0xFFFFFF, 0.3F) : b.color();
            if (b.id() == FlotaBotones.LISTO && yo >= 0 && p.js.get(yo).listo) col = 0x405040;
            rect(marca, ps, be, b.x(), b.y() + 2, b.w(), b.h(), 0x000000, 0.35F);
            rect(marca, ps, be, b.x(), b.y(), b.w(), b.h(), col, 0.95F);
            rect(marca, ps, be, b.x(), b.y() - b.h() / 2 + 2, b.w() - 4, 3, 0xFFFFFF, 0.35F);
            String txt = I18n.get("flota.miscelanea.boton." + b.clave());
            Vec3 c = local(be, b.x(), b.y());
            ps.pushPose();
            ps.translate(c.x, c.y + 0.004, c.z);
            ps.mulPose(new Quaternionf().rotationYXZ((float) Math.toRadians(-mira.toYRot()), (float) Math.toRadians(90), 0));
            float esc = (float) Math.min(0.0045, b.w() / FlotaGeo.N * FlotaGeo.LADO / Math.max(1, font.width(txt)) * 0.9);
            ps.scale(-esc, -esc, esc);
            font.drawInBatch(txt, -font.width(txt) / 2F, -4, 0xFFFFFFFF, false, ps.last().pose(), buf, Font.DisplayMode.POLYGON_OFFSET, 0, luz);
            ps.popPose();
        }

        // ---------------- panel de HUD (sustituye al cartel flotante)
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), miro, "block.miscelanea.flota", 0xFF3A7FD0);
        if (pj == null) return;
        pj.linea(estado(p), PanelJuego.AMARILLO);
        for (int i = 0; i < p.js.size(); i++) {
            FlotaPartida.Jugador j = p.js.get(i);
            int vivos = 0;
            for (int k = 0; k < FlotaPartida.TAM.length; k++) if (!p.hundido(j, k)) vivos++;
            String extra = p.fase == FlotaPartida.COLOCAR ? (j.listo ? "✔" : "…") : I18n.get("flota.miscelanea.barcos", vivos);
            pj.jugador(j.nombre, extra, i == 0 ? 0x70B0FF : 0xFF9070, i == p.turno && p.fase == FlotaPartida.JUGAR);
        }
        String pista = null;
        if (yo >= 0 && p.fase == FlotaPartida.COLOCAR && !p.js.get(yo).listo) pista = "flota.miscelanea.pista.colocar";
        else if (yo >= 0 && p.fase == FlotaPartida.JUGAR && p.turno == yo) pista = "flota.miscelanea.pista.disparar";
        if (pista != null) pj.detalle(I18n.get(pista), PanelJuego.CIAN);
        if (mira != null) pj.detalle(mira, miraCol);
        if (!p.log.isEmpty() && !cayendo) pj.nota(formatear(p.log.get(p.log.size() - 1)));
        FlotaBotones.Boton bot = miro ? FlotaBotones.en(p, FlotaCliente.tx, FlotaCliente.ty) : null;
        pj.control(PanelJuego.Tecla.USAR, bot != null ? "flota.miscelanea.boton." + bot.clave()
                : p.fase == FlotaPartida.JUGAR ? "panel.miscelanea.ctrl.disparar" : "panel.miscelanea.ctrl.jugar");
    }

    private static String estado(FlotaPartida p) {
        return switch (p.fase) {
            case FlotaPartida.LOBBY -> I18n.get("flota.miscelanea.estado.lobby", p.js.size());
            case FlotaPartida.COLOCAR -> I18n.get("flota.miscelanea.estado.colocar");
            case FlotaPartida.JUGAR -> I18n.get("flota.miscelanea.estado.turno", p.actual().nombre);
            default -> p.ganador >= 0 ? I18n.get("flota.miscelanea.estado.gana", p.js.get(p.ganador).nombre) : I18n.get("flota.miscelanea.estado.fin");
        };
    }

    static String formatear(String e) {
        String[] s = e.split("\\|");
        Object[] args = new Object[s.length - 1];
        System.arraycopy(s, 1, args, 0, args.length);
        return I18n.get("flota.miscelanea.log." + s[0], args);
    }

    private void marco(VertexConsumer vc, PoseStack ps, FlotaBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        double g = 1.6;
        rect(vc, ps, be, cx, cy - h / 2, w, g, rgb, a);
        rect(vc, ps, be, cx, cy + h / 2, w, g, rgb, a);
        rect(vc, ps, be, cx - w / 2, cy, g, h, rgb, a);
        rect(vc, ps, be, cx + w / 2, cy, g, h, rgb, a);
    }

    private void rect(VertexConsumer vc, PoseStack ps, FlotaBlockEntity be, double cx, double cy, double w, double h, int rgb, float a) {
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float dy = 0.003F + (capa++ % 64) * 0.0002F;
        double[][] pts = {{cx - w / 2, cy + h / 2}, {cx + w / 2, cy + h / 2}, {cx + w / 2, cy - h / 2}, {cx - w / 2, cy - h / 2}};
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
