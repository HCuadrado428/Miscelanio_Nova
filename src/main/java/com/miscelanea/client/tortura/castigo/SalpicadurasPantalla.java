package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Tomate o huevo que te revienta en la cara (en primera persona): en vez de llenar la pantalla de trozos gigantes
 * (las partículas salían pegadas a la cámara), queda un manchurrón en pixel art hacia un borde de la pantalla, que
 * escurre unas gotas y se va en un par de segundos. Nunca tapa el centro.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class SalpicadurasPantalla {
    private SalpicadurasPantalla() {}

    private static final int MAX = 6;
    private static final long VIDA = 2600;
    private static final RandomSource R = RandomSource.create();

    private record Mancha(float x, float y, int tam, boolean huevo, long t, long semilla) {}

    private static final List<Mancha> MANCHAS = new ArrayList<>();

    /**
     * Desde el proyectil (cliente) al reventar: si revienta pegado a mi cámara en primera persona, apunta una mancha en
     * la pantalla y devuelve true (entonces no se sueltan partículas ahí).
     */
    public static boolean enMiCara(double x, double y, double z, boolean huevo) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return false;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        if (cam.distanceToSqr(x, y, z) > 1.6 * 1.6) return false;
        if (MANCHAS.size() >= MAX) MANCHAS.remove(0);
        // a un lado: izquierda/derecha y arriba/abajo, lejos del punto de mira
        float lx = R.nextBoolean() ? 0.08F + R.nextFloat() * 0.22F : 0.7F + R.nextFloat() * 0.22F;
        float ly = 0.1F + R.nextFloat() * 0.6F;
        MANCHAS.add(new Mancha(lx, ly, 14 + R.nextInt(10), huevo, System.currentTimeMillis(), R.nextLong()));
        return true;
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        if (MANCHAS.isEmpty()) return;
        long ahora = System.currentTimeMillis();
        MANCHAS.removeIf(m -> ahora - m.t > VIDA);
        GuiGraphics g = e.getGuiGraphics();
        int W = g.guiWidth(), H = g.guiHeight();
        for (Mancha m : MANCHAS) {
            float edad = (ahora - m.t) / (float) VIDA;
            float a = edad < 0.6F ? 1 : 1 - (edad - 0.6F) / 0.4F;
            int alfa = (int) (a * 215);
            if (alfa <= 4) continue;
            int base = m.huevo ? 0xF2E6B0 : 0xB8170F, luz = m.huevo ? 0xFFF8D8 : 0xE8452E, osc = m.huevo ? 0xD8B040 : 0x7A0C08;
            int cx = (int) (m.x * W), cy = (int) (m.y * H), r = m.tam, px = 2;      // "píxel" de 2 de la interfaz
            RandomSource rs = RandomSource.create(m.semilla);
            // borde irregular: radio por ángulo
            float[] rad = new float[16];
            for (int i = 0; i < 16; i++) rad[i] = r * (0.7F + rs.nextFloat() * 0.45F);
            for (int yy = -r - 4; yy <= r + 4; yy += px)
                for (int xx = -r - 4; xx <= r + 4; xx += px) {
                    double ang = Math.atan2(yy, xx);
                    int k = Math.floorMod((int) Math.round(ang / (Math.PI * 2) * 16), 16);
                    float d = Mth.sqrt(xx * xx + yy * yy);
                    if (d > rad[k]) continue;
                    int c = d > rad[k] - px * 1.5F ? osc : (xx < -r / 3 && yy < -r / 3 && d < r * 0.45F) ? luz : base;
                    g.fill(cx + xx, cy + yy, cx + xx + px, cy + yy + px, (alfa << 24) | c);
                }
            // pepitas (tomate) o yema (huevo)
            if (m.huevo) {
                for (int yy = -6; yy <= 6; yy += px)
                    for (int xx = -6; xx <= 6; xx += px)
                        if (xx * xx + yy * yy <= 36) g.fill(cx + xx + 2, cy + yy, cx + xx + 2 + px, cy + yy + px, (alfa << 24) | (xx < -1 && yy < -1 ? 0xFFE070 : 0xF0A818));
            } else for (int i = 0; i < 5; i++) {
                int sx = cx + rs.nextInt(r) - r / 2, sy = cy + rs.nextInt(r) - r / 2;
                g.fill(sx, sy, sx + px, sy + px * 2, (alfa << 24) | 0xF0D070);
            }
            // gotas que escurren
            for (int i = 0; i < 3; i++) {
                int gx = cx + (rs.nextInt(r) - r / 2) / px * px;
                int largo = (int) (edad * (16 + rs.nextInt(24))) / px * px;
                int gy = cy + r / 2;
                g.fill(gx, gy, gx + px, gy + largo, (alfa << 24) | base);
                g.fill(gx, gy + largo, gx + px * 2, gy + largo + px * 2, (alfa << 24) | osc);
            }
        }
    }
}
