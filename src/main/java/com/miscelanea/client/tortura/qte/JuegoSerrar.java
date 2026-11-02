package com.miscelanea.client.tortura.qte;

import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.qte.QteInicioPacket;
import com.miscelanea.tortura.qte.QteLogica;
import com.miscelanea.tortura.qte.QteResultado;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * SERRAR: el ratón (horizontal) lleva la sierra adelante y atrás. Una marca fantasma va y viene con su ritmo: si la
 * sierra va con ella (cerca y moviéndose), el corte avanza y saltan chispas; si te sales, la hoja se atasca y el corte
 * retrocede un poco. Genérico: sirve para cualquier cosa que haya que serrar (la cadena de la bola de preso, barrotes...).
 */
public class JuegoSerrar extends JuegoQte {
    private final int periodo;
    private final float tol, gana, pierde;
    private float x, xTick, prog, progO;
    private int dir, vaivenes, buenos;
    private float recorrido;
    private boolean bien;
    private long ultimoVaiven = -1000;
    private final float[][] chispas = new float[24][5];   // x, y, vx, vy, vida
    private int sigChispa;

    public JuegoSerrar(QteInicioPacket p) {
        super(p);
        periodo = QteLogica.serrarPeriodo(dif);
        tol = QteLogica.serrarTolerancia(dif);
        gana = QteLogica.serrarGanancia(dif);
        pierde = QteLogica.serrarPierde(dif);
        sonido(TorturaRegistro.QTE_TIC.get(), 0.8F, 0.8F);
    }

    @Override
    public boolean usaRaton() {
        return true;
    }

    @Override
    public void raton(double h, double v) {
        if (terminado) return;
        x = Mth.clamp(x + (float) h * 0.0045F, -1F, 1F);
    }

    /** Posición de la marca (-1..1) a los 'ms' milisegundos. */
    private float marca(long ms) {
        long t = Math.max(0, ms - QteLogica.SERRAR_PREVIO_MS);
        return (float) Math.sin(t * 2 * Math.PI / periodo);
    }

    @Override
    protected void logica() {
        progO = prog;
        for (float[] c : chispas)
            if (c[4] > 0) {
                c[0] += c[2];
                c[1] += c[3];
                c[3] += 0.35F;
                c[4]--;
            }
        long ms = ms();
        if (ms < QteLogica.SERRAR_PREVIO_MS) {
            xTick = x;
            return;
        }
        if (ms > QteLogica.SERRAR_PREVIO_MS + QteLogica.SERRAR_TIEMPO_MS) {
            acabar(QteResultado.FALLO);
            return;
        }
        float dx = x - xTick;
        xTick = x;
        // vaivén: cambio de sentido tras recorrer al menos media barra
        int s = dx > 0.004F ? 1 : dx < -0.004F ? -1 : 0;
        if (s != 0) {
            if (dir != 0 && s != dir && recorrido > 0.5F) {
                vaivenes++;
                ultimoVaiven = ms;
                sonido(TorturaRegistro.SIERRA_SERRAR.get(), dir > 0 ? 1.05F : 0.9F, bien ? 0.8F : 0.35F);
                recorrido = 0;
            }
            if (s != dir) recorrido = 0;
            dir = s;
            recorrido += Math.abs(dx);
        }
        float m = marca(ms);
        bien = Math.abs(x - m) < tol && Math.abs(dx) > 0.01F;
        if (bien) {
            buenos++;
            prog = Math.min(1F, prog + gana);
            if (ticks % 2 == 0) chispa();
        } else {
            prog = Math.max(0F, prog - pierde);
            if (Math.abs(dx) > 0.01F) sacudida = Math.max(sacudida, 1.5F);
        }
        if (prog >= 1F) acabar(QteResultado.EXITO);
    }

    private void chispa() {
        float[] c = chispas[sigChispa++ % chispas.length];
        c[0] = 0;
        c[1] = 0;
        c[2] = (mc.level.random.nextFloat() - 0.5F) * 5F - dir * 2F;
        c[3] = -mc.level.random.nextFloat() * 3.5F;
        c[4] = 6 + mc.level.random.nextInt(6);
    }

    @Override
    protected int[] datos() {
        return new int[]{vaivenes, buenos};
    }

    @Override
    public void dibujar(GuiGraphics g, int w, int h, float pt) {
        float cx = w / 2F + temblorX(), cy = h * 0.56F;
        int ancho = 190;
        int x0 = (int) (cx - ancho / 2F), y0 = (int) cy - 46, y1 = (int) cy + 46;
        Dibujo.panel(g, x0 - 8, y0, x0 + ancho + 8, y1, 0xFFC8A060);
        float p = Mth.lerp(pt, progO, prog);
        // ---- eslabón de la cadena (de canto), con la muesca que va abriendo la sierra
        float ex = cx, ey = cy - 6;
        int hierro = 0xFF6E6A66, hierroC = 0xFFA8A29A, hierroO = 0xFF3A3632;
        g.pose().pushPose();
        g.pose().translate(ex, ey, 0);
        g.pose().scale(1.7F, 1F, 1F);
        Dibujo.arco(g, 0, 0, 9, 16, 0, 360, hierroO);
        Dibujo.arco(g, 0, 0, 10, 15, 0, 360, hierro, hierroC);
        g.pose().popPose();
        // eslabones vecinos (de frente, más oscuros)
        g.fill((int) ex - 46, (int) ey - 3, (int) ex - 26, (int) ey + 3, hierroO);
        g.fill((int) ex + 26, (int) ey - 3, (int) ex + 46, (int) ey + 3, hierroO);
        g.fill((int) ex - 46, (int) ey - 3, (int) ex - 26, (int) ey - 2, 0xFF8A847C);
        g.fill((int) ex + 26, (int) ey - 3, (int) ex + 46, (int) ey - 2, 0xFF8A847C);
        // muesca: cada vez más honda en la parte de arriba del eslabón
        int prof = Math.round(p * 7F);
        if (prof > 0) {
            g.fill((int) ex - 2, (int) ey - 16, (int) ex + 2, (int) ey - 16 + prof, 0xFF141210);
            g.fill((int) ex - 3, (int) ey - 16, (int) ex - 2, (int) ey - 16 + prof, 0xFFE0D8C8);
            g.fill((int) ex + 2, (int) ey - 16, (int) ex + 3, (int) ey - 16 + prof, 0xFFE0D8C8);
        }
        // ---- la sierra: sigue al ratón; la hoja apoya en la muesca
        // (la sierra se pinta sin interpolar: va pegada al ratón, que se lee cada fotograma)
        float hx = ex + x * 34F, hy = ey - 17 + prof;
        int acero = 0xFFC9CED6, aceroO = 0xFF7C828C;
        // hoja con dientes
        g.fill((int) (hx - 44), (int) hy - 3, (int) (hx + 44), (int) hy, acero);
        g.fill((int) (hx - 44), (int) hy - 3, (int) (hx + 44), (int) hy - 2, 0xFFEFF2F6);
        for (int i = -44; i < 44; i += 3) Dibujo.triangulo(g, hx + i, hy, hx + i + 3, hy, hx + i + 1.5F, hy + 2.2F, aceroO);
        // arco de la sierra (marco en C)
        Dibujo.linea(g, hx - 44, hy - 2, hx - 44, hy - 18, 2.4F, 0xFF3E4248);
        Dibujo.linea(g, hx + 40, hy - 2, hx + 40, hy - 18, 2.4F, 0xFF3E4248);
        Dibujo.linea(g, hx - 45, hy - 18, hx + 41, hy - 18, 2.4F, 0xFF4C5058);
        Dibujo.linea(g, hx - 45, hy - 19, hx + 41, hy - 19, 0.8F, 0xFF8C9098);
        // mango de madera
        g.fill((int) hx + 38, (int) hy - 16, (int) hx + 58, (int) hy - 5, 0xFF5A3418);
        g.fillGradient((int) hx + 39, (int) hy - 15, (int) hx + 57, (int) hy - 6, 0xFF9A6236, 0xFF6E4220);
        for (int i = 0; i < 4; i++) g.fill((int) hx + 42 + i * 4, (int) hy - 15, (int) hx + 43 + i * 4, (int) hy - 6, 0x40000000);
        // chispas
        for (float[] c : chispas)
            if (c[4] > 0) {
                float k = c[4] / 10F;
                Dibujo.disco(g, ex + c[0], ey - 16 + prof + c[1], 0.9F + k * 0.6F, Dibujo.alfa(0xFFFFD070, Math.min(1F, k * 1.5F)));
            }
        // ---- pista del vaivén: la marca fantasma y la sierra
        int ty = (int) cy + 20, tw = ancho - 30, tx = (int) (cx - tw / 2F);
        g.fill(tx - 1, ty - 1, tx + tw + 1, ty + 7, 0xFF000000);
        g.fillGradient(tx, ty, tx + tw, ty + 6, 0xFF22201C, 0xFF2E2A24);
        float m = marca(ms());
        float zona = tol * tw / 2F;
        float mxp = tx + tw / 2F + m * tw / 2F;
        g.fill((int) (mxp - zona), ty, (int) (mxp + zona), ty + 6, bien ? 0x9060E070 : 0x60E0C080);
        g.fill((int) mxp - 1, ty - 2, (int) mxp + 1, ty + 8, 0xFFFFE6A0);
        float cxp = tx + tw / 2F + x * tw / 2F;
        Dibujo.triangulo(g, cxp - 4, ty + 12, cxp + 4, ty + 12, cxp, ty + 7, bien ? 0xFF70F080 : 0xFFE0E0E0);
        // ---- progreso del corte
        int c0 = Dibujo.mezcla(0xFFC08040, 0xFF60E070, p);
        Dibujo.barra(g, tx, ty + 16, tw, 5, p, Dibujo.mezcla(c0, 0xFFFFFFFF, 0.2F), Dibujo.mezcla(c0, 0xFF000000, 0.3F));
        if (ms() - ultimoVaiven < 120 && bien) Dibujo.brillo(g, ex, ey - 16 + prof, 3, 5, 0x90FFE080);
        marco(g, cx, y0, y1, Component.translatable("qte.miscelanea.ayuda.serrar"));
    }
}
