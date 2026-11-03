package com.miscelanea.client.juegos;

import com.miscelanea.Miscelanea;
import com.miscelanea.juegos.Ajedrez;
import com.miscelanea.juegos.Damas;
import com.miscelanea.juegos.TableroBlock;
import com.miscelanea.juegos.TableroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
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
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/** Piezas 3D, resaltes (selección, jugadas posibles, última jugada, jaque, casilla apuntada); el estado de la partida va en el panel de HUD. */
public class TableroRenderer implements BlockEntityRenderer<TableroBlockEntity> {
    private static int capa;
    public static final String[] PIEZAS = {"peon", "caballo", "alfil", "torre", "dama", "rey", "ficha", "ficha_reina"};
    private static final ResourceLocation BLANCO = new ResourceLocation("textures/misc/white.png");

    public TableroRenderer(BlockEntityRendererProvider.Context c) {}

    private static final java.util.Map<String, ResourceLocation> RL = new java.util.HashMap<>();

    public static ResourceLocation modelo(String pieza, boolean blanca) {
        return RL.computeIfAbsent(pieza + (blanca ? "_blanca" : "_negra"), k -> new ResourceLocation(Miscelanea.MOD_ID, "block/pieza/" + k));
    }

    @Override
    public void render(TableroBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int ov) {
        capa = 0;
        if (be.estado.length == 0 || be.getLevel() == null) return;
        Direction f = be.getBlockState().getValue(TableroBlock.FACING);
        float giro = 180 - f.toYRot();
        long ahora = be.getLevel().getGameTime();
        float anim = Math.min(1F, (ahora - be.ultT + pt) / 7F);
        Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos());
        int[] t;
        boolean aj = be.ajedrez();
        boolean jaque = false;
        int rey = -1;
        if (aj) {
            Ajedrez a = be.verAjedrez();
            t = a.t;
            jaque = be.resultado == 0 && a.enJaque(a.blancas);
            if (jaque) rey = a.rey(a.blancas);
        } else t = be.piezas();
        boolean rev = be.reversi();
        com.miscelanea.juegos.Reversi rv = rev ? be.verReversi() : null;

        // ---------------- resaltes en el tablero
        VertexConsumer hv = buf.getBuffer(RenderType.entityTranslucent(BLANCO));
        double s = TableroBlockEntity.ZONA / 8;
        if (rev) {
            // tapete verde de reversi sobre el tablero
            for (int q = 0; q < 64; q++) casilla(hv, ps, be, q, base, s, 0x2E7D32, 0.92F, 0.93F, luz);
        }
        if (be.ultDe >= 0) {
            casilla(hv, ps, be, be.ultDe, base, s, 0xE0C060, 0.30F, 1F, luz);
            casilla(hv, ps, be, be.ultA, base, s, 0xE0C060, 0.38F, 1F, luz);
        }
        if (rey >= 0) casilla(hv, ps, be, rey, base, s, 0xFF3030, 0.55F, 1F, luz);
        if (be.sel >= 0) {
            casilla(hv, ps, be, be.sel, base, s, 0xFFD040, 0.6F, 1F, luz);
            List<Integer> ds = be.destinos(be.sel);
            for (int d : ds) casilla(hv, ps, be, d, base, s, t[d] != 0 ? 0xFF6040 : 0x50E070, 0.65F, t[d] != 0 ? 1F : 0.34F, luz);
        }
        boolean mio = esMiTurno(be);
        long vol = 0;
        if (rev && be.resultado == 0) {
            // jugadas posibles siempre visibles; al apuntar una, se marcan las fichas que se voltearían
            for (int m : be.legales()) casilla(hv, ps, be, m & 63, base, s, mio ? 0xFFFFFF : 0xA0A0A0, 0.35F, 0.28F, luz);
            if (mio && TableroCliente.apuntado == be && TableroCliente.casilla >= 0) vol = rv.simular(TableroCliente.casilla);
            for (int q = 0; q < 64; q++) if ((vol >> q & 1) != 0) casilla(hv, ps, be, q, base, s, 0xFFE040, 0.45F, 1F, luz);
        }
        if (TableroCliente.apuntado == be && TableroCliente.casilla >= 0) {
            float pul = 0.25F + 0.1F * Mth.sin((ahora + pt) * 0.3F);
            casilla(hv, ps, be, TableroCliente.casilla, base, s, 0xFFFFFF, pul, 1F, luz);
            if (mio && be.sel < 0 && be.resultado == 0 && !rev) {
                int sq = TableroCliente.casilla;
                List<Integer> ds = be.destinos(sq);
                if (!ds.isEmpty()) for (int d : ds) casilla(hv, ps, be, d, base, s, t[d] != 0 ? 0xFF6040 : 0x50E070, 0.35F, t[d] != 0 ? 1F : 0.26F, luz);
                else {
                    List<Integer> os = be.origenes(sq);
                    if (os.size() == 1) casilla(hv, ps, be, os.get(0), base, s, 0xFFD040, 0.45F, 1F, luz);
                }
            }
        }

        // ---------------- piezas
        var mr = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var mm = Minecraft.getInstance().getModelManager();
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        for (int sq = 0; sq < 64; sq++) {
            int p = t[sq];
            if (p == 0) continue;
            String nombre;
            boolean blanca;
            BakedModel m;
            if (aj) {
                nombre = PIEZAS[Ajedrez.tipo(p) - 1];
                blanca = !Ajedrez.esNegra(p);
            } else {
                nombre = Damas.esDama(p) ? "ficha_reina" : "ficha";
                blanca = Damas.esBlanca(p);
            }
            m = mm.getModel(modelo(nombre, blanca));
            Vec3 c = be.centroCasilla(sq).subtract(base);
            double alto = 0;
            if (sq == be.ultA && anim < 1 && be.ultDe >= 0) {
                Vec3 o = be.centroCasilla(be.ultDe).subtract(base);
                float k = anim * anim * (3 - 2 * anim);
                c = o.lerp(c, k);
                alto = Math.sin(anim * Math.PI) * 0.18;
            }
            float flotar = sq == be.sel ? 0.03F + 0.015F * Mth.sin((ahora + pt) * 0.25F) : 0;
            float voltear = 0;
            if (rev && anim < 1 && (rv.volteadas >> sq & 1) != 0) {
                // la ficha da la vuelta: primero se ve el color anterior
                voltear = anim * 180;
                if (anim < 0.5F) blanca = !blanca;
                alto = Math.sin(anim * Math.PI) * 0.06;
                m = mm.getModel(modelo(nombre, blanca));
            }
            ps.pushPose();
            ps.translate(c.x, c.y + alto + flotar, c.z);
            ps.mulPose(Axis.YP.rotationDegrees(giro + (blanca ? 0 : 180)));
            if (voltear != 0) {
                ps.translate(0, s * 0.12, 0);
                ps.mulPose(Axis.XP.rotationDegrees(voltear));
                ps.translate(0, -s * 0.12, 0);
            }
            float esc = (float) (s * 0.94);
            ps.scale(esc, esc, esc);
            ps.translate(-0.5, 0, -0.5);
            mr.renderModel(ps.last(), vc, null, m, 1F, 1F, 1F, luz, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }

        // ---------------- panel de HUD (antes era un cartel flotante encima del tablero)
        PanelJuego.Panel pj = PanelJuego.ofrecer(be.getBlockPos(), TableroCliente.apuntado == be, "block.miscelanea.tablero", 0xFFC89A5A);
        if (pj == null) return;
        pj.linea(I18n.get("tablero.miscelanea.modo." + be.modo), PanelJuego.AMARILLO);
        if (rev) pj.marcador("○" + rv.cuenta(1) + " ●" + rv.cuenta(3), PanelJuego.BLANCO);
        if (be.resultado == 3) pj.linea(I18n.get("tablero.miscelanea.tablas"), PanelJuego.BLANCO);
        else if (be.resultado != 0) pj.linea(I18n.get("tablero.miscelanea.gana", be.resultado == 1 ? be.nombreB : be.nombreN), PanelJuego.VERDE);
        else if (be.blancas == null) pj.linea(I18n.get("tablero.miscelanea.toca"), PanelJuego.BLANCO);
        if (be.blancas != null) {
            boolean enJuego = be.resultado == 0, tb = be.turnoBlancas();
            pj.jugador(be.nombreB, I18n.get("panel.miscelanea.tablero.blancas"), 0xF0E8D8, enJuego && tb);
            pj.jugador(be.nombreN.isEmpty() ? "?" : be.nombreN, I18n.get("panel.miscelanea.tablero.negras"), 0x303030, enJuego && !tb);
        }
        if (jaque) pj.linea(I18n.get("tablero.miscelanea.jaque"), 0xFFFF6060);
        if (mio) pj.detalle(I18n.get(rev ? "tablero.miscelanea.pista_reversi" : "tablero.miscelanea.pista"), PanelJuego.CIAN);
        if (be.resultado != 0) pj.control(PanelJuego.Tecla.USAR, "panel.miscelanea.ctrl.otra");
        else pj.control(PanelJuego.Tecla.USAR, rev ? "panel.miscelanea.ctrl.colocar" : "panel.miscelanea.ctrl.mover_pieza");
        pj.control(PanelJuego.Tecla.AGACHADO_USAR, "panel.miscelanea.ctrl.cambiar_juego");
    }

    static boolean esMiTurno(TableroBlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || be.resultado != 0 || be.blancas == null || be.estado.length == 0) return false;
        java.util.UUID u = mc.player.getUUID();
        return be.turnoBlancas() ? u.equals(be.blancas) : (!be.contraCpu() && u.equals(be.negras));
    }

    /** Cuadrado de color sobre una casilla (tam &lt; 1 = punto en el centro). */
    private static void casilla(VertexConsumer vc, PoseStack ps, TableroBlockEntity be, int sq, Vec3 base, double s, int rgb, float a, float tam, int luz) {
        Vec3 c = be.centroCasilla(sq).subtract(base);
        float h = (float) (s / 2 * tam * 0.96);
        float y = (float) c.y + 0.003F + (capa++ % 64) * 0.0002F;
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
        float x0 = (float) c.x - h, x1 = (float) c.x + h, z0 = (float) c.z - h, z1 = (float) c.z + h;
        vc.vertex(m, x0, y, z1).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y, z1).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x1, y, z0).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
        vc.vertex(m, x0, y, z0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public int getViewDistance() {
        return 48;
    }
}
