package com.miscelanea.client.bolos;

import com.miscelanea.bolos.BoleraBlockEntity;
import com.miscelanea.bolos.Marcador;
import com.miscelanea.client.feria.FeriaPiezas;
import com.miscelanea.feria.CasetaTiroBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.language.I18n;

import static com.miscelanea.bolos.BoleraBlockEntity.*;

/** Los diez bolos (de pie o cayendo), el telón del fondo y el marcador con los frames. */
public class BoleraRenderer implements BlockEntityRenderer<BoleraBlockEntity> {
    private final Font font;

    public BoleraRenderer(BlockEntityRendererProvider.Context c) {
        font = c.getFont();
    }

    @Override
    public boolean shouldRenderOffScreen(BoleraBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(BoleraBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        float t = be.getLevel().getGameTime() + pt;
        int brillo = Math.max(luz & 0xFFFF, 0xC0) | (luz & 0xFFFF0000);
        ps.pushPose();
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotation(CasetaTiroBlockEntity.giro(be.facing())));
        for (int k = 0; k < N; k++) {
            if (be.estado[k] == 2) continue;
            ps.pushPose();
            ps.translate(BX[k], 2 / 16F, BZ[k]);
            if (be.estado[k] == 1) {
                float f = Math.min(1, (t - be.caida[k]) / CAIDA);
                f = f * f;
                ps.mulPose(Axis.YP.rotation(-be.dir[k]));
                ps.translate(0.12 * f, 0, 0);
                ps.mulPose(Axis.ZP.rotationDegrees(-86 * f));
            }
            ps.translate(-0.5, 0, -0.5);
            FeriaPiezas.modelo(ps, vc, FeriaPiezas.rl("bolo"), 1, 1, 1, brillo);
            ps.popPose();
        }
        // telón y foco
        FeriaPiezas.caja(ps, vc, "negro", 0, 0.75, 0.56, 1.0, 1.5, 0.06, luz);
        FeriaPiezas.caja(ps, vc, "lona_roja", 0, 1.32, 0.42, 1.0, 0.36, 0.06, luz);
        for (int k = 0; k < 6; k++) FeriaPiezas.bombilla(ps, vc, -0.42 + k * 0.168, 1.12, 0.38, 0.05F, 0xFFF2C0);
        // marcador (las letras solo a menos de 32 bloques: más lejos no se leen y la bolera se ve desde 96)
        boolean letras = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY() + 1.5, be.getBlockPos().getZ() + 0.5) < 32 * 32;
        ps.pushPose();
        ps.translate(0, 1.85, 0.4);
        // no más ancho que el bloque: con dos calles juntas los marcadores no se pisan
        FeriaPiezas.caja(ps, vc, "negro", 0, 0, 0.03, 0.98, 0.5, 0.04, luz);
        if (!letras) {
            ps.popPose();
            ps.popPose();
            return;
        }
        ps.mulPose(Axis.YP.rotationDegrees(180));
        ps.scale(0.0085F, -0.0085F, 0.0085F);
        String[] fr = Marcador.frames(be.tiradas);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) sb.append(fr[i].isEmpty() ? "·" : fr[i]).append(i < 9 ? " " : "");
        String l1 = be.nombre.isEmpty() ? I18n.get("miscelanea.bolos.cartel") : be.nombre + "  " + Marcador.total(be.tiradas);
        String l2 = sb.toString();
        String l3 = be.tiradas.isEmpty() ? I18n.get("miscelanea.bolos.ayuda") : I18n.get("miscelanea.bolos.frame", Marcador.frame(be.tiradas));
        linea(ps, buf, l1, -24, 0xFFF2C14E);
        linea(ps, buf, l2, -8, 0xFFFFFFFF);
        linea(ps, buf, l3, 8, 0xFF9FD8FF);
        ps.popPose();
        ps.popPose();
    }

    private void linea(PoseStack ps, MultiBufferSource buf, String s, float y, int c) {
        float esc = Math.min(1, 112F / Math.max(1, font.width(s)));
        ps.pushPose();
        ps.scale(esc, esc, esc);
        font.drawInBatch(s, -font.width(s) / 2F, y / esc, c, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        ps.popPose();
    }
}
