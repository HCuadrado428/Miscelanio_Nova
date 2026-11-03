package com.miscelanea.client.feria;

import com.miscelanea.feria.AtraccionBlock;
import com.miscelanea.feria.AtraccionBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntUnaryOperator;

/**
 * Estructura de la noria (patas, rueda con radios y bombillas), del pulpo (base, columna, cabeza y brazos) y del tiovivo
 * (plataforma, barras, toldo).
 * <p>Rendimiento: la geometría ya no se monta pieza a pieza cada fotograma (cientos de renderModel). Se graba una vez por
 * tipo / orientación en {@link MallaFeria} (con la rueda en el ángulo 0) y cada fotograma solo se emite con el giro del
 * momento como matriz. Las bombillas son mallas aparte que se tiñen al emitir. De lejos (más de {@link #LEJOS} bloques)
 * se saltan las piezas finas: escalerillas, tirantes, zigzag, bombillas de los radios y de la estrella del buje.
 */
public class AtraccionRenderer implements BlockEntityRenderer<AtraccionBlockEntity> {
    static final double LEJOS = 48;
    private static final int PLENA = 0xF000F0;

    public AtraccionRenderer(BlockEntityRendererProvider.Context c) {}

    // ------------------------------------------------------------------ caché
    private record Clave(int tipo, Direction f, int n) {}

    /** Mallas de una atracción: fijas, las que giran con la rueda / plataforma y las de la estrella o la cabeza. */
    private static final class Piezas {
        MallaFeria fija, fijaFina, gira, giraFina, luces, lucesFinas, estrella, centro, cabeza, cabezaLuces;
    }

    private static final Map<Clave, Piezas> CACHE = new HashMap<>();
    /** Para saber si se recargaron los recursos (cambian las uv del atlas): entonces se vuelve a grabar todo. */
    private static BakedModel modeloRef;

    private static Piezas piezas(AtraccionBlockEntity be) {
        BakedModel m = Minecraft.getInstance().getModelManager().getModel(FeriaPiezas.BOMBILLA);
        if (m != modeloRef) {
            CACHE.clear();
            modeloRef = m;
        }
        return CACHE.computeIfAbsent(new Clave(be.tipo(), be.facing(), be.asientos()), k ->
                k.tipo == AtraccionBlock.NORIA ? noria(k.f, k.n) : k.tipo == AtraccionBlock.PULPO ? pulpo() : tiovivo(k.n));
    }

    /** Graba lo que dibuja {@code g} (con una pose identidad) en una malla nueva. */
    private interface Dibujo {
        void en(PoseStack ps, MallaFeria.Grabadora g);
    }

    private static MallaFeria grabar(Dibujo d) {
        MallaFeria.Grabadora g = new MallaFeria.Grabadora();
        d.en(new PoseStack(), g);
        return g.terminar();
    }

    // ------------------------------------------------------------------ dibujo por fotograma
    @Override
    public boolean shouldRenderOffScreen(AtraccionBlockEntity be) {
        // la estructura es mucho más grande que su sección de chunk: si no, desaparecería al no ver el bloque de la base
        // (el recorte por el frustum lo hace igualmente con getRenderBoundingBox, que se ajusta a cada atracción)
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public void render(AtraccionBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        if (be.getLevel() == null || !(be.getBlockState().getBlock() instanceof AtraccionBlock)) return;
        VertexConsumer vc = buf.getBuffer(Sheets.cutoutBlockSheet());
        double tt = be.getLevel().getGameTime() + (double) pt;
        float t = (float) (be.getLevel().getGameTime() % 100000L) + pt;
        float ang = be.angulo(tt);
        int brillo = Math.max(luz & 0xFFFF, 0xA0) | (luz & 0xFFFF0000);
        BlockPos bp = be.getBlockPos();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double cy = be.tipo() == AtraccionBlock.NORIA ? AtraccionBlockEntity.CY_NORIA : 2;
        boolean lejos = cam.distanceToSqr(bp.getX() + 0.5, bp.getY() + cy, bp.getZ() + 0.5) > LEJOS * LEJOS;
        Piezas p = piezas(be);
        if (be.tipo() == AtraccionBlock.NORIA) noria(be, p, ps, vc, t, ang, brillo, lejos);
        else if (be.tipo() == AtraccionBlock.PULPO) pulpo(be, p, ps, vc, t, ang, brillo, lejos);
        else tiovivo(be, p, ps, vc, t, ang, brillo, lejos);
    }

    /** Emite la malla girada {@code ang} rad alrededor del eje (ax, ay, az) que pasa por (cx, cy, cz). */
    private static void girada(MallaFeria m, PoseStack ps, VertexConsumer vc, double cx, double cy, double cz, float ax, float ay, float az,
                               float ang, int luz, IntUnaryOperator color) {
        ps.pushPose();
        ps.translate(cx, cy, cz);
        ps.mulPose(new Quaternionf().rotationAxis(ang, ax, ay, az));
        ps.translate(-cx, -cy, -cz);
        m.emitir(vc, ps.last(), luz, color);
        ps.popPose();
    }

    private static void noria(AtraccionBlockEntity be, Piezas p, PoseStack ps, VertexConsumer vc, float t, float ang, int luz, boolean lejos) {
        Direction u = be.facing().getClockWise();
        // eje de la rueda: u x (0, 1, 0); girar ang lleva u hacia arriba, como el cos/sin de los radios
        float ax = -u.getStepZ(), az = u.getStepX();
        double cy = AtraccionBlockEntity.CY_NORIA;
        boolean gira = be.gira;
        p.fija.emitir(vc, ps.last(), luz, null);
        girada(p.gira, ps, vc, 0.5, cy, 0.5, ax, 0, az, ang, luz, null);
        girada(p.luces, ps, vc, 0.5, cy, 0.5, ax, 0, az, ang, PLENA, k -> FeriaPiezas.luz(k, t, gira));
        p.centro.emitir(vc, ps.last(), PLENA, k -> FeriaPiezas.luz(k, t, gira));
        if (lejos) return;
        p.fijaFina.emitir(vc, ps.last(), luz, null);
        girada(p.giraFina, ps, vc, 0.5, cy, 0.5, ax, 0, az, ang, luz, null);
        girada(p.lucesFinas, ps, vc, 0.5, cy, 0.5, ax, 0, az, ang, PLENA, k -> FeriaPiezas.luz(k, t * 0.7F, gira));
        girada(p.estrella, ps, vc, 0.5, cy, 0.5, ax, 0, az, -ang * 0.5F, PLENA, k -> FeriaPiezas.luz(k, t * 1.3F, gira));
    }

    private static void pulpo(AtraccionBlockEntity be, Piezas p, PoseStack ps, VertexConsumer vc, float t, float ang, int luz, boolean lejos) {
        double cx = 0.5, cz = 0.5, hub = AtraccionBlockEntity.HUB_PULPO;
        p.fija.emitir(vc, ps.last(), luz, null);
        // cabeza del pulpo: cúpula con ojos que gira
        ps.pushPose();
        ps.translate(cx, hub, cz);
        ps.mulPose(Axis.YP.rotation(-ang));
        p.cabeza.emitir(vc, ps.last(), luz, null);
        boolean gira = be.gira;
        p.cabezaLuces.emitir(vc, ps.last(), PLENA, k -> FeriaPiezas.luz(k, t, gira));
        ps.popPose();
        // brazos: suben y bajan cada uno a su aire, se siguen dibujando al vuelo (son pocos)
        int n = be.asientos();
        for (int i = 0; i < n; i++) {
            double[] e = be.brazo(i, be.getLevel().getGameTime() + (double) (t - (float) Math.floor(t)));
            double ex = cx + e[0], ey = e[1], ez = cz + e[2];
            FeriaPiezas.barra(ps, vc, "metal_blanco", cx, hub + 0.2, cz, ex, ey, ez, 0.32F, 0.32F, luz);
            FeriaPiezas.barra(ps, vc, "dorado", cx, hub + 0.45, cz, ex, ey + 0.1, ez, 0.12F, 0.12F, luz);
            FeriaPiezas.barra(ps, vc, "dorado", ex, ey, ez, ex, ey - AtraccionBlockEntity.CUELGA_CESTA + 0.9, ez, 0.14F, 0.14F, luz);
            if (lejos) continue;
            for (int j = 1; j <= 6; j++) {
                double k = j / 7.0;
                FeriaPiezas.bombilla(ps, vc, cx + (ex - cx) * k, hub + 0.42 + (ey - hub - 0.42) * k, cz + (ez - cz) * k, 0.12F, FeriaPiezas.luz(i * 3 + j, t, be.gira));
            }
        }
    }

    private static void tiovivo(AtraccionBlockEntity be, Piezas p, PoseStack ps, VertexConsumer vc, float t, float ang, int luz, boolean lejos) {
        boolean gira = be.gira;
        // la plataforma gira -ang en el eje y: lleva (cos b, sin b) a (cos(b + ang), sin(b + ang))
        p.fija.emitir(vc, ps.last(), luz, null);
        girada(p.gira, ps, vc, 0.5, 0, 0.5, 0, 1, 0, -ang, luz, null);
        girada(p.luces, ps, vc, 0.5, 0, 0.5, 0, 1, 0, -ang, PLENA, k -> FeriaPiezas.luz(k, t, gira));
        p.centro.emitir(vc, ps.last(), PLENA, k -> FeriaPiezas.luz(k, t, gira));
        if (!lejos) girada(p.giraFina, ps, vc, 0.5, 0, 0.5, 0, 1, 0, -ang, luz, null);
    }

    // ------------------------------------------------------------------ grabación (una vez por tipo / orientación)
    /** Pasa una malla de {@link MallaRecurso} (hecha mirando al sur) puesta en (x, y, z) y girada hacia {@code f}. */
    private static void malla(PoseStack ps, VertexConsumer vc, String nombre, Direction f, double x, double y, double z, boolean luces) {
        ps.pushPose();
        ps.translate(x, y, z);
        ps.mulPose(Axis.YP.rotationDegrees(-f.toYRot()));
        MallaRecurso.dibujar(ps, vc, nombre, 0, luces);
        ps.popPose();
    }

    /** Las mismas piezas que antes se dibujaban cada fotograma, con la rueda en el ángulo 0 y la luz sin poner. */
    private static Piezas noria(Direction f, int n) {
        Direction u = f.getClockWise();
        double ux = u.getStepX(), uz = u.getStepZ(), fx = f.getStepX(), fz = f.getStepZ();
        double cx = 0.5, cy = AtraccionBlockEntity.CY_NORIA, cz = 0.5, R = AtraccionBlockEntity.R_NORIA, w = AtraccionBlockEntity.MEDIA_RUEDA;
        double ang = 0;
        int luz = PLENA;
        Piezas p = new Piezas();
        // patas en A a cada lado de la rueda, con travesaños; eje y buje
        p.fija = grabar((ps, vc) -> {
            for (int lado = -1; lado <= 1; lado += 2) {
                double ox = fx * lado * (w + 0.45), oz = fz * lado * (w + 0.45);
                for (int pie = -1; pie <= 1; pie += 2) {
                    FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * pie * 5.6, 0, cz + oz + uz * pie * 5.6, cx + ox, cy, cz + oz, 0.45F, 0.45F, luz);
                    FeriaPiezas.caja(ps, vc, "metal_blanco", cx + ox + ux * pie * 5.6, 0.25, cz + oz + uz * pie * 5.6, 1.1, 0.5, 1.1, luz);
                }
                for (double h : new double[]{4.5, 9})
                    FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox - ux * 5.6 * (1 - h / cy), h, cz + oz - uz * 5.6 * (1 - h / cy),
                            cx + ox + ux * 5.6 * (1 - h / cy), h, cz + oz + uz * 5.6 * (1 - h / cy), 0.22F, 0.22F, luz);
            }
            FeriaPiezas.barra(ps, vc, "dorado", cx - fx * (w + 0.8), cy, cz - fz * (w + 0.8), cx + fx * (w + 0.8), cy, cz + fz * (w + 0.8), 0.55F, 0.55F, luz);
            FeriaPiezas.barra(ps, vc, "metal_rojo", cx - fx * (w + 0.1), cy, cz - fz * (w + 0.1), cx + fx * (w + 0.1), cy, cz + fz * (w + 0.1), 1.1F, 1.1F, luz);
            // tarima de embarque con barandillas, taquilla y farolas
            malla(ps, vc, "noria_base", f, cx, 0, cz, false);
        });
        // escalera en zigzag de las patas
        p.fijaFina = grabar((ps, vc) -> {
            for (int lado = -1; lado <= 1; lado += 2) {
                double ox = fx * lado * (w + 0.45), oz = fz * lado * (w + 0.45);
                for (int k = 0; k < 6; k++) {
                    double h0 = 0.5 + k * 2, h1 = h0 + 2;
                    FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox - ux * 5.6 * (1 - h0 / cy), h0, cz + oz - uz * 5.6 * (1 - h0 / cy),
                            cx + ox + ux * 5.6 * (1 - h1 / cy), h1, cz + oz + uz * 5.6 * (1 - h1 / cy), 0.12F, 0.12F, luz);
                }
            }
        });
        // rueda: dos caras con aro exterior, aro interior y radios; barras doradas entre las caras
        p.gira = grabar((ps, vc) -> {
            int seg = 40;
            for (int cara = -1; cara <= 1; cara += 2) {
                double ox = fx * cara * w, oz = fz * cara * w;
                for (int k = 0; k < seg; k++) {
                    double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                    for (double r : new double[]{R, R * 0.93})
                        FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox + ux * Math.cos(a0) * r, cy + Math.sin(a0) * r, cz + oz + uz * Math.cos(a0) * r,
                                cx + ox + ux * Math.cos(a1) * r, cy + Math.sin(a1) * r, cz + oz + uz * Math.cos(a1) * r, 0.22F, 0.22F, luz);
                    double ri = R * 0.45;
                    FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(a0) * ri, cy + Math.sin(a0) * ri, cz + oz + uz * Math.cos(a0) * ri,
                            cx + ox + ux * Math.cos(a1) * ri, cy + Math.sin(a1) * ri, cz + oz + uz * Math.cos(a1) * ri, 0.16F, 0.16F, luz);
                }
                for (int k = 0; k < n; k++) {
                    double a = ang + 2 * Math.PI * k / n;
                    FeriaPiezas.barra(ps, vc, "metal_blanco", cx + ox, cy, cz + oz, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R, 0.14F, 0.14F, luz);
                }
            }
            for (int k = 0; k < n; k++) {
                double a = ang + 2 * Math.PI * k / n;
                double px = cx + ux * Math.cos(a) * R, py = cy + Math.sin(a) * R, pz = cz + uz * Math.cos(a) * R;
                FeriaPiezas.barra(ps, vc, "dorado", px - fx * w, py, pz - fz * w, px + fx * w, py, pz + fz * w, 0.2F, 0.2F, luz);
            }
            // rosetas de pétalos en las dos caras del buje (la malla está hecha para 10 radios)
            if (n == 10) malla(ps, vc, "noria_buje", f, cx, cy, cz, false);
        });
        // zigzag entre aros y tirantes finos entre las caras
        p.giraFina = grabar((ps, vc) -> {
            for (int cara = -1; cara <= 1; cara += 2) {
                double ox = fx * cara * w, oz = fz * cara * w;
                for (int k = 0; k < n; k++) {
                    double a = ang + 2 * Math.PI * k / n, b = a + Math.PI / n;
                    FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(a) * R * 0.45, cy + Math.sin(a) * R * 0.45, cz + oz + uz * Math.cos(a) * R * 0.45,
                            cx + ox + ux * Math.cos(b) * R * 0.93, cy + Math.sin(b) * R * 0.93, cz + oz + uz * Math.cos(b) * R * 0.93, 0.1F, 0.1F, luz);
                    FeriaPiezas.barra(ps, vc, "metal_rojo", cx + ox + ux * Math.cos(b) * R * 0.93, cy + Math.sin(b) * R * 0.93, cz + oz + uz * Math.cos(b) * R * 0.93,
                            cx + ox + ux * Math.cos(a + 2 * Math.PI / n) * R * 0.45, cy + Math.sin(a + 2 * Math.PI / n) * R * 0.45, cz + oz + uz * Math.cos(a + 2 * Math.PI / n) * R * 0.45, 0.1F, 0.1F, luz);
                }
            }
            // tirantes entre las dos caras: en el aro interior (a 0,93 R chocaban con las cápsulas, que cuelgan 3,4 bloques
            // del gancho y barren un círculo de ese radio alrededor de él)
            for (int k = 0; k < n; k++) {
                double b = ang + 2 * Math.PI * k / n + Math.PI / n;
                double qx = cx + ux * Math.cos(b) * R * 0.45, qy = cy + Math.sin(b) * R * 0.45, qz = cz + uz * Math.cos(b) * R * 0.45;
                FeriaPiezas.barra(ps, vc, "metal_blanco", qx - fx * w, qy, qz - fz * w, qx + fx * w, qy, qz + fz * w, 0.12F, 0.12F, luz);
            }
        });
        // bombillas (en blanco, con su número de la ristra como etiqueta)
        p.luces = grabar((ps, vc) -> {
            for (int cara = -1; cara <= 1; cara += 2) {
                double ox = fx * cara * (w + 0.14), oz = fz * cara * (w + 0.14);
                for (int k = 0; k < 80; k++) {
                    double a = ang + 2 * Math.PI * k / 80;
                    vc.etiqueta = k;
                    FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * R, cy + Math.sin(a) * R, cz + oz + uz * Math.cos(a) * R, 0.2F, 0xFFFFFF);
                }
            }
            if (n == 10) malla(ps, vc, "noria_buje", f, cx, cy, cz, true);
        });
        p.lucesFinas = grabar((ps, vc) -> {
            for (int cara = -1; cara <= 1; cara += 2) {
                double ox = fx * cara * (w + 0.14), oz = fz * cara * (w + 0.14);
                for (int k = 0; k < n; k++)
                    for (int j = 1; j <= 6; j++) {
                        double a = ang + 2 * Math.PI * k / n, r = R * j / 7.0;
                        vc.etiqueta = j * 2 + k;
                        FeriaPiezas.bombilla(ps, vc, cx + ox + ux * Math.cos(a) * r, cy + Math.sin(a) * r, cz + oz + uz * Math.cos(a) * r, 0.16F, 0xFFFFFF);
                    }
            }
        });
        // estrella del buje (gira al revés y a media velocidad) y la bombilla gorda del centro
        p.estrella = grabar((ps, vc) -> {
            for (int cara = -1; cara <= 1; cara += 2)
                for (int k = 0; k < 8; k++) {
                    double a = Math.PI * k / 4;
                    vc.etiqueta = k;
                    FeriaPiezas.bombilla(ps, vc, cx + fx * cara * (w + 0.62) + ux * Math.cos(a) * 0.5, cy + Math.sin(a) * 0.5,
                            cz + fz * cara * (w + 0.62) + uz * Math.cos(a) * 0.5, 0.18F, 0xFFFFFF);
                }
        });
        p.centro = grabar((ps, vc) -> {
            for (int cara = -1; cara <= 1; cara += 2) {
                vc.etiqueta = cara + 2;
                FeriaPiezas.bombilla(ps, vc, cx + fx * cara * (w + 0.62), cy, cz + fz * cara * (w + 0.62), 0.6F, 0xFFFFFF);
            }
            // bombillas de la taquilla y farolas
            malla(ps, vc, "noria_base", f, cx, 0, cz, true);
        });
        return p;
    }

    private static Piezas pulpo() {
        double cx = 0.5, cz = 0.5, hub = AtraccionBlockEntity.HUB_PULPO;
        int luz = PLENA;
        Piezas p = new Piezas();
        // base octogonal y columna
        p.fija = grabar((ps, vc) -> {
            for (int k = 0; k < 8; k++) {
                double a0 = k * Math.PI / 4, a1 = (k + 1) * Math.PI / 4;
                FeriaPiezas.barra(ps, vc, k % 2 == 0 ? "metal_rojo" : "dorado", cx + Math.cos(a0) * 1.6, 0.15, cz + Math.sin(a0) * 1.6, cx + Math.cos(a1) * 1.6, 0.15, cz + Math.sin(a1) * 1.6, 0.3F, 0.3F, luz);
                FeriaPiezas.barra(ps, vc, "metal_blanco", cx, 0.12, cz, cx + Math.cos(a0 + 0.39) * 1.55, 0.12, cz + Math.sin(a0 + 0.39) * 1.55, 0.2F, 0.9F, luz);
            }
            FeriaPiezas.barra(ps, vc, "metal_rojo", cx, 0.2, cz, cx, hub, cz, 0.9F, 0.9F, luz);
            for (int k = 0; k < 7; k++) FeriaPiezas.barra(ps, vc, "dorado", cx, 0.6 + k * 0.6, cz, cx, 0.75 + k * 0.6, cz, 1.0F, 1.0F, luz);
        });
        // cabeza (en coordenadas de la cabeza: se emite trasladada al buje y girada): pulpo con ojos, faldón y gorra
        p.cabeza = grabar((ps, vc) -> MallaRecurso.dibujar(ps, vc, "pulpo_cabeza", 0, false));
        p.cabezaLuces = grabar((ps, vc) -> MallaRecurso.dibujar(ps, vc, "pulpo_cabeza", 0, true));
        return p;
    }

    private static Piezas tiovivo(int n) {
        double cx = 0.5, cz = 0.5, R = 3.4, RT = 3.8, techo = 4.2, punta = 5.3;
        int seg = 16;
        double ang = 0;
        int luz = PLENA;
        Piezas p = new Piezas();
        // columna central (no gira): tambor de espejos, paneles pintados y cornisas doradas con bombillas
        p.fija = grabar((ps, vc) -> malla(ps, vc, "tiovivo_columna", Direction.SOUTH, cx, 0, cz, false));
        p.gira = grabar((ps, vc) -> {
            // plataforma de tablas: porciones de tarta que no se pisan (sin parpadeo)
            for (int k = 0; k < seg; k++) {
                double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                FeriaPiezas.cuna(ps, vc, k % 2 == 0 ? "madera_a" : "madera_b", cx, cz, a0, a1, R, 0.01, 0.23, luz);
            }
            for (int k = 0; k < seg; k++) {
                double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                FeriaPiezas.barra(ps, vc, "dorado", cx + Math.cos(a0) * R, 0.2, cz + Math.sin(a0) * R, cx + Math.cos(a1) * R, 0.2, cz + Math.sin(a1) * R, 0.3F, 0.12F, luz);
            }
            // toldo en cono, también con porciones
            for (int k = 0; k < seg; k++) {
                double a0 = ang + 2 * Math.PI * k / seg, a1 = ang + 2 * Math.PI * (k + 1) / seg;
                FeriaPiezas.cuna(ps, vc, k % 2 == 0 ? "lona_roja" : "lona_blanca", cx, cz, a0, a1, RT, techo - 0.04, techo + 0.04, punta - techo, luz);
            }
            // cenefa festoneada con espejitos y crestería dorada alrededor del toldo
            malla(ps, vc, "tiovivo_cenefa", Direction.SOUTH, cx, 0, cz, false);
            // barras doradas de los caballitos (los caballitos son las entidades que suben y bajan)
            for (int k = 0; k < n; k++) {
                double a = ang + 2 * Math.PI * k / n;
                double x = cx + Math.cos(a) * 2.6, z = cz + Math.sin(a) * 2.6;
                FeriaPiezas.barra(ps, vc, "dorado", x, 0.25, z, x, techo - 0.1, z, 0.1F, 0.1F, luz);
            }
        });
        // los collarines de latón de las barras de los caballitos (de cerca)
        p.giraFina = grabar((ps, vc) -> {
            for (int k = 0; k < n; k++) {
                double a = ang + 2 * Math.PI * k / n;
                double x = cx + Math.cos(a) * 2.6, z = cz + Math.sin(a) * 2.6;
                FeriaPiezas.caja(ps, vc, "dorado", x, 0.3, z, 0.2, 0.1, 0.2, luz);
                FeriaPiezas.caja(ps, vc, "dorado", x, techo - 0.2, z, 0.2, 0.12, 0.2, luz);
            }
        });
        p.luces = grabar((ps, vc) -> {
            for (int k = 0; k < 32; k++) {
                double a = ang + 2 * Math.PI * k / 32;
                vc.etiqueta = k;
                FeriaPiezas.bombilla(ps, vc, cx + Math.cos(a) * (RT + 0.06), techo - 0.6, cz + Math.sin(a) * (RT + 0.06), 0.15F, 0xFFFFFF);
            }
        });
        p.centro = grabar((ps, vc) -> {
            vc.etiqueta = 1;
            FeriaPiezas.bombilla(ps, vc, cx, punta + 0.25, cz, 0.45F, 0xFFFFFF);
            malla(ps, vc, "tiovivo_columna", Direction.SOUTH, cx, 0, cz, true);
        });
        return p;
    }
}
