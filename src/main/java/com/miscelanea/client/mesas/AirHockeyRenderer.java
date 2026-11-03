package com.miscelanea.client.mesas;

import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.client.juegos.PanelJuego;
import com.miscelanea.mesas.AirHockeyBlockEntity;
import com.miscelanea.mesas.AirHockeyPartida;
import com.miscelanea.mesas.MesaGeo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Disco, mazos y marcadores LED (a ras de la banda) del air hockey; el resto de la información va en el panel de HUD. */
public class AirHockeyRenderer implements BlockEntityRenderer<AirHockeyBlockEntity> {
    static final float MESA = 13F / 16;

    public AirHockeyRenderer(BlockEntityRendererProvider.Context c) {}

    @Override
    public boolean shouldRenderOffScreen(AirHockeyBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(AirHockeyBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        AirHockeyPartida p = be.p;
        Direction f = be.facing();
        boolean suave = System.currentTimeMillis() - be.ultimoFisica < 200;
        float[] ahora = p.estado(), d = new float[6];
        for (int i = 0; i < 6; i++) d[i] = suave ? be.antes[i] + (ahora[i] - be.antes[i]) * pt : ahora[i];
        int brillo = Math.max(luz & 0xFFFF, 0xA0) | (luz & 0xFFFF0000);
        if (p.fase != AirHockeyPartida.ESPERA) {
            Vec3 q = MesaGeo.local(f, d[0], d[1], MESA);
            disco(ps, vc, q, 0.14, 0.03, 0xE02828, brillo);
            disco(ps, vc, q.add(0, 0.03, 0), 0.1, 0.004, 0xFFD040, brillo);
        }
        for (int e = 0; e < 2; e++) {
            Vec3 q = MesaGeo.local(f, d[2 + e * 2], d[3 + e * 2], MESA);
            int c = e == 0 ? 0xD03030 : 0x3060D0;
            disco(ps, vc, q, 0.22, 0.04, c, brillo);
            disco(ps, vc, q.add(0, 0.04, 0), 0.08, 0.08, c, brillo);
            disco(ps, vc, q.add(0, 0.12, 0), 0.11, 0.04, 0xF0F0F0, brillo);
        }
        marcador(be, ps, buf, Minecraft.getInstance().font);
    }

    /** Cilindro aproximado: dos cajas cruzadas. */
    private void disco(PoseStack ps, VertexConsumer vc, Vec3 q, double diam, double alto, int col, int luz) {
        for (int k = 0; k < 2; k++) {
            ps.pushPose();
            ps.translate(q.x, q.y + alto / 2, q.z);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(k * 45));
            ps.scale((float) diam, (float) alto, (float) (diam * 0.82));
            ps.translate(-0.5, -0.5, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("trozo"), (col >> 16 & 255) / 255F, (col >> 8 & 255) / 255F, (col & 255) / 255F, luz);
            ps.popPose();
        }
    }

    /**
     * Marcadores LED bajos en los dos lados largos de la mesa (en el modelo, a ras de la banda): los goles de cada
     * portería se pintan sobre la pantalla, cada número del lado de su portería, sin nada que tape la mesa.
     */
    private void marcador(AirHockeyBlockEntity be, PoseStack ps, MultiBufferSource buf, Font font) {
        AirHockeyPartida p = be.p;
        Direction f = be.facing();
        Minecraft mc = Minecraft.getInstance();
        long t = be.getLevel() != null ? be.getLevel().getGameTime() : 0;
        boolean espera = p.fase == AirHockeyPartida.ESPERA;
        boolean parpadeo = p.fase == AirHockeyPartida.GOL && (t / 5) % 2 == 0;
        int gana = p.fase == AirHockeyPartida.FIN ? (p.goles[0] > p.goles[1] ? 0 : 1) : -1;
        // cara exterior de cada pantalla: v = -0.4/16 (lado de la parte 1) y v = 2 + 0.4/16 (lado de la parte 4)
        for (int lado = 0; lado < 2; lado++) {
            double v = lado == 0 ? -0.4 / 16 - 0.002 : 2 + 0.4 / 16 + 0.002;
            Direction fuera = lado == 0 ? f.getOpposite() : f;
            double y = 16.6 / 16;
            for (int e = 0; e < 3; e++) {
                String txt;
                int col;
                double u;
                if (e == 2) {
                    txt = ":";
                    col = 0xFFB0B0B0;
                    u = 1.5;
                } else {
                    txt = espera ? "-" : String.valueOf(p.goles[e]);
                    int base = e == 0 ? 0xFFFF4A3A : 0xFF4A90FF;
                    col = espera ? 0xFF606060 : parpadeo || (gana == e && (t / 6) % 2 == 0) ? 0xFFFFFFFF : base;
                    u = e == 0 ? 1.5 - 0.2 : 1.5 + 0.2;
                }
                Vec3 q = MesaGeo.local(f, u, v, y);
                ps.pushPose();
                ps.translate(q.x, q.y, q.z);
                ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-fuera.toYRot()));
                float esc = 0.0165F;
                ps.scale(esc, -esc, esc);
                Matrix4f m = ps.last().pose();
                font.drawInBatch(txt, -font.width(txt) / 2F, -4, col, false, m, buf, Font.DisplayMode.POLYGON_OFFSET, 0, 0xF000F0);
                ps.popPose();
            }
        }
        panel(be, mc);
    }

    /** Panel de HUD: marcador, quién juega en cada portería y controles. */
    private static void panel(AirHockeyBlockEntity be, Minecraft mc) {
        AirHockeyPartida p = be.p;
        boolean juego = AirHockeyCliente.jugando == be;
        Vec3 centro = Vec3.atLowerCornerOf(be.getBlockPos()).add(MesaGeo.local(be.facing(), 1.5, 1, 1));
        PanelJuego.Panel pj = PanelJuego.ofrecer(centro, juego || mirando(be), "block.miscelanea.air_hockey", 0xFF5ADCFF);
        if (pj == null) return;
        String n0 = be.jug[0] != null ? be.nombre[0] : p.cpu[0] ? "CPU" : "-", n1 = be.jug[1] != null ? be.nombre[1] : p.cpu[1] ? "CPU" : "-";
        if (p.fase == AirHockeyPartida.ESPERA) pj.linea(I18n.get("airhockey.miscelanea.espera"), PanelJuego.BLANCO);
        else {
            pj.marcador(p.goles[0] + " - " + p.goles[1], p.fase == AirHockeyPartida.GOL ? 0xFFFFD23C : PanelJuego.BLANCO);
            pj.linea(p.fase == AirHockeyPartida.GOL ? I18n.get("airhockey.miscelanea.gol_grande") : p.fase == AirHockeyPartida.FIN
                    ? I18n.get("airhockey.miscelanea.gana", p.goles[0] > p.goles[1] ? n0 : n1) : I18n.get("airhockey.miscelanea.a_siete"),
                    p.fase == AirHockeyPartida.GOL ? 0xFFFFD23C : PanelJuego.AMARILLO);
            pj.jugador(n0, I18n.get("airhockey.miscelanea.equipo0"), 0xE02828, false);
            pj.jugador(n1, I18n.get("airhockey.miscelanea.equipo1"), 0x3060D0, false);
        }
        if (juego) {
            pj.control(PanelJuego.Tecla.MOVER, "panel.miscelanea.ctrl.mazo");
            if (p.fase == AirHockeyPartida.FIN) pj.control(PanelJuego.Tecla.USAR, "panel.miscelanea.ctrl.otra");
            pj.control(PanelJuego.Tecla.AGACHARSE, "panel.miscelanea.ctrl.soltar_mesa");
        } else pj.control(PanelJuego.Tecla.USAR, "panel.miscelanea.ctrl.unirse_extremo");
    }

    /** Si el jugador apunta a cualquier bloque de esta mesa grande. */
    static boolean mirando(net.minecraft.world.level.block.entity.BlockEntity be) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh)
                || bh.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        var s = mc.level.getBlockState(bh.getBlockPos());
        return s.getBlock() instanceof com.miscelanea.mesas.MesaGrandeBlock mg && mg.principal(s, bh.getBlockPos()).equals(be.getBlockPos());
    }
}
