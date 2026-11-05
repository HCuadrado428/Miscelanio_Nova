package com.miscelanea.client.tortura.qte;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Forzar cerraduras como en Skyrim: el ratón mueve la ganzúa (solo con la cerradura en reposo); mantener IZQUIERDA (A)
 * gira la cerradura. Gira más cuanto más cerca estés del punto dulce; en el punto dulce gira del todo y se abre.
 * Si sigues forzando contra el tope, la ganzúa tiembla, se daña y acaba rompiéndose.
 */
public class JuegoGanzua extends JuegoQte {
    private static final ResourceLocation PLACA = tex("cerradura"), CILINDRO = tex("cilindro"), GANZUA = tex("ganzua"), TENSOR = tex("tensor");

    private static ResourceLocation tex(String n) {
        return new ResourceLocation(Miscelanea.MOD_ID, "textures/gui/tortura/" + n + ".png");
    }

    private float pick, pickO, giro, giroO, dano;
    private boolean forzando;
    private int sonidoGiro;

    public JuegoGanzua(QteInicioPacket p) {
        super(p);
        sonido(TorturaRegistro.GANZUA_GIRAR.get(), 0.7F, 0.8F);
    }

    @Override
    public boolean usaRaton() {
        return true;
    }

    @Override
    public void raton(double h, double v) {
        if (terminado || giro > 1.5F) return;
        float antes = pick;
        pick = Mth.clamp(pick + (float) h * 0.15F * 0.7F, -90F, 90F);
        if (Math.abs(pick - antes) > 0.01F && (sonidoGiro++ % 9) == 0) sonido(TorturaRegistro.GANZUA_GIRAR.get(), 1.8F + mc.level.random.nextFloat() * 0.4F, 0.15F);
    }

    @Override
    protected void logica() {
        pickO = pick;
        giroO = giro;
        if (ms() < QteLogica.GAN_PREVIO_MS) return;
        if (ms() > QteLogica.GAN_TIEMPO_MS) {
            acabar(QteResultado.FALLO);
            return;
        }
        boolean girar = com.miscelanea.client.TeclasMod.direccionPulsada(2);
        forzando = false;
        if (girar) {
            float max = QteLogica.ganMaxGiro(pick, semilla, dif);
            if (giro < max - 0.01F) {
                giro = Math.min(max, giro + QteLogica.GAN_GIRO_TICK);
                if (ticks % 3 == 0) sonido(TorturaRegistro.GANZUA_GIRAR.get(), 0.8F + giro / 180F, 0.45F);
            } else if (max < 90F) {
                forzando = true;
                dano += QteLogica.ganDanoTick(dif);
                sacudida = Math.max(sacudida, 2.5F);
                if (ticks % 4 == 0) sonido(TorturaRegistro.GANZUA_GIRAR.get(), 1.4F + mc.level.random.nextFloat() * 0.3F, 0.6F);
                if (dano >= 1F) {
                    acabar(QteResultado.ROTO);
                    return;
                }
            }
            if (giro >= 90F) {
                giro = 90F;
                sonido(TorturaRegistro.CERRADURA_ABRIR.get(), 1F, 1F);
                acabar(QteResultado.EXITO);
            }
        } else {
            giro = Math.max(0, giro - QteLogica.GAN_VUELTA_TICK);
        }
    }

    @Override
    protected int[] datos() {
        return new int[]{Math.round(pick * 10F)};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        float cx = w / 2F, cy = h * 0.52F;
        float gi = Mth.lerp(pt, giroO, giro), pk = Mth.lerp(pt, pickO, pick);
        float tx = forzando ? (mc.level.random.nextFloat() - 0.5F) * 2.4F : temblorX();
        float ty = forzando ? (mc.level.random.nextFloat() - 0.5F) * 2.4F : 0;
        // halo oscuro detrás
        Dibujo.arco(g, cx, cy, 58, 86, 0, 360, 0xB0000000, 0x00000000);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int S = 128;
        g.blit(PLACA, (int) (cx - S / 2F), (int) (cy - S / 2F), S, S, 0, 0, 256, 256, 256, 256);
        // cilindro (gira con la cerradura)
        g.pose().pushPose();
        g.pose().translate(cx + tx * 0.4F, cy + ty * 0.4F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(gi));
        g.blit(CILINDRO, -34, -34, 68, 68, 0, 0, 128, 128, 128, 128);
        g.pose().popPose();
        // destornillador de tensión: abajo, gira con la cerradura
        g.pose().pushPose();
        g.pose().translate(cx + tx * 0.4F, cy + ty * 0.4F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(gi));
        g.blit(TENSOR, -7, 6, 14, 58, 0, 0, 32, 128, 32, 128);
        g.pose().popPose();
        // ganzúa: sale del bombín hacia arriba, girada 'pk'; se pone roja al dañarse
        g.pose().pushPose();
        g.pose().translate(cx + tx, cy + ty - 2, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(pk));
        float r = 1F, gg = 1F - dano * 0.55F, b = 1F - dano * 0.65F;
        g.setColor(r, gg, b, 1F);
        g.blit(GANZUA, -5, -92, 10, 92, 0, 0, 32, 256, 32, 256);
        g.setColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
        RenderSystem.disableBlend();
        // estado de la ganzúa
        int bw = 70;
        int x0 = (int) (cx - bw / 2F), y0 = (int) (cy + S / 2F + 4);
        Dibujo.barra(g, x0, y0, bw, 3, 1F - dano, 0xFFE8D8B0, 0xFF907850);
        marco(g, cx, cy - S / 2F, y0 + 4, Component.translatable("qte.miscelanea.ayuda.ganzua", nombreTecla(com.miscelanea.client.TeclasMod.MJ_IZQ)));
    }
}
