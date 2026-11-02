package com.miscelanea.client.mesas;

import com.miscelanea.client.feria.FeriaPiezas;
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

/** Disco, mazos y marcador del air hockey. */
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

    private void marcador(AirHockeyBlockEntity be, PoseStack ps, MultiBufferSource buf, Font font) {
        AirHockeyPartida p = be.p;
        String n0 = be.jug[0] != null ? be.nombre[0] : p.cpu[0] ? "CPU" : "-", n1 = be.jug[1] != null ? be.nombre[1] : p.cpu[1] ? "CPU" : "-";
        String[] l;
        int[] c;
        if (p.fase == AirHockeyPartida.ESPERA) {
            l = new String[]{I18n.get("airhockey.miscelanea.titulo"), I18n.get("airhockey.miscelanea.espera")};
            c = new int[]{0xFF5ADCFF, 0xFFFFFFFF};
        } else {
            String abajo = p.fase == AirHockeyPartida.GOL ? I18n.get("airhockey.miscelanea.gol_grande") : p.fase == AirHockeyPartida.FIN
                    ? I18n.get("airhockey.miscelanea.gana", p.goles[0] > p.goles[1] ? n0 : n1) : I18n.get("airhockey.miscelanea.a_siete");
            l = new String[]{"§c" + n0 + "§r   " + p.goles[0] + " - " + p.goles[1] + "   §9" + n1, abajo};
            c = new int[]{0xFFFFFFFF, p.fase == AirHockeyPartida.GOL ? 0xFFFFD23C : 0xFFC0C0C0};
        }
        Vec3 centro = MesaGeo.local(be.facing(), 1.5, 1, 1.45 + l.length * 0.11);
        Minecraft mc = Minecraft.getInstance();
        ps.pushPose();
        ps.translate(centro.x, centro.y, centro.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float esc = p.fase == AirHockeyPartida.GOL ? 0.02F : 0.016F;
        ps.scale(-esc, -esc, esc);
        Matrix4f m = ps.last().pose();
        for (int i = 0; i < l.length; i++)
            font.drawInBatch(l[i], -font.width(l[i]) / 2F, i * 10, c[i], false, m, buf, Font.DisplayMode.NORMAL, 0x80000000, 0xF000F0);
        ps.popPose();
    }
}
