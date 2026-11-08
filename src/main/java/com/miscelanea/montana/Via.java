package com.miscelanea.montana;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Geometría de la vía de la montaña rusa. Cada bloque de vía es un trozo de recorrido con su entrada (en el borde de
 * atrás del bloque, mirando hacia donde está puesta) y su salida; el siguiente trozo es el bloque al que lleva la salida
 * (o el de debajo, si es una bajada). Todo se calcula con unas pocas lecturas de bloques y cuentas sencillas: no hay
 * nada guardado ni nada que se dibuje cada fotograma (las vías son modelos de bloque normales), así que va bien en
 * ordenadores flojos.
 */
public final class Via {
    private Via() {}

    /** Altura de la vía sobre el suelo del bloque. */
    public static final double ALTO = 3 / 16.0;
    /** Rizo: radio, avance total y desplazamiento a la derecha (en bloques). */
    public static final double RIZO_R = 2.0, RIZO_AVANCE = 5, RIZO_LADO = 1;
    /** Inclinación de las curvas (grados, en el centro de la curva). */
    private static final float PERALTE = 24;

    public enum Clase {RECTA, CURVA_IZQ, CURVA_DER, SUBIDA, BAJADA, RIZO}

    /** Un trozo de vía. */
    public record Pieza(BlockPos pos, Direction dir, Clase clase, ViaMontanaBlock.Tipo tipo, boolean encendida) {
        Vec3 F() {
            return Vec3.atLowerCornerOf(dir.getNormal());
        }

        Vec3 izq() {
            return Vec3.atLowerCornerOf(dir.getCounterClockWise().getNormal());
        }

        Vec3 der() {
            return Vec3.atLowerCornerOf(dir.getClockWise().getNormal());
        }

        /** Punto de entrada (borde de atrás, a la altura de la vía; en la bajada, arriba). */
        public Vec3 entrada() {
            Vec3 c = Vec3.atBottomCenterOf(pos).add(0, ALTO + (clase == Clase.BAJADA ? 1 : 0), 0);
            return c.subtract(F().scale(0.5));
        }

        public boolean entraArriba() {
            return clase == Clase.BAJADA;
        }

        public double longitud() {
            return switch (clase) {
                case RECTA -> 1;
                case CURVA_IZQ, CURVA_DER -> Math.PI / 4;
                case SUBIDA, BAJADA -> Math.sqrt(2);
                case RIZO -> RIZO_LARGO;
            };
        }

        /** Bloque donde tiene que estar el siguiente trozo (si es una bajada, en el de debajo). */
        public BlockPos salida() {
            return switch (clase) {
                case RECTA, BAJADA -> pos.relative(dir);
                case SUBIDA -> pos.relative(dir).above();
                case CURVA_IZQ -> pos.relative(dir.getCounterClockWise());
                case CURVA_DER -> pos.relative(dir.getClockWise());
                case RIZO -> pos.relative(dir, (int) RIZO_AVANCE).relative(dir.getClockWise(), (int) RIZO_LADO);
            };
        }

        public Direction salidaDir() {
            return switch (clase) {
                case CURVA_IZQ -> dir.getCounterClockWise();
                case CURVA_DER -> dir.getClockWise();
                default -> dir;
            };
        }

        /** Punto del recorrido, t de 0 (entrada) a 1 (salida), repartido por igual a lo largo. */
        public Vec3 punto(double t) {
            Vec3 e = entrada(), F = F();
            switch (clase) {
                case RECTA:
                    return e.add(F.scale(t));
                case SUBIDA:
                    return e.add(F.scale(t)).add(0, t, 0);
                case BAJADA:
                    return e.add(F.scale(t)).add(0, -t, 0);
                case CURVA_IZQ:
                case CURVA_DER: {
                    Vec3 lado = clase == Clase.CURVA_IZQ ? izq() : der();
                    Vec3 o = e.add(lado.scale(0.5));
                    double a = t * Math.PI / 2;
                    return o.add(lado.scale(-0.5 * Math.cos(a))).add(F.scale(0.5 * Math.sin(a)));
                }
                default:
                    return rizo(e, F, der(), uRizo(t));
            }
        }

        /** Dirección del recorrido (unitaria) en t. */
        public Vec3 tangente(double t) {
            Vec3 F = F();
            switch (clase) {
                case RECTA:
                    return F;
                case SUBIDA:
                    return F.add(0, 1, 0).normalize();
                case BAJADA:
                    return F.add(0, -1, 0).normalize();
                case CURVA_IZQ:
                case CURVA_DER: {
                    Vec3 lado = clase == Clase.CURVA_IZQ ? izq() : der();
                    double a = t * Math.PI / 2;
                    return lado.scale(Math.sin(a)).add(F.scale(Math.cos(a))).normalize();
                }
                default: {
                    double u = uRizo(t), th = u * Math.PI * 2;
                    return F.scale(RIZO_R * Math.cos(th) + RIZO_K).add(0, RIZO_R * Math.sin(th), 0).add(der().scale(RIZO_LADO / (Math.PI * 2))).normalize();
                }
            }
        }

        /** Hacia dónde queda "arriba" del vagón (con el peralte en las curvas; boca abajo en lo alto del rizo). */
        public Vec3 arriba(double t) {
            Vec3 up = new Vec3(0, 1, 0);
            switch (clase) {
                case CURVA_IZQ:
                case CURVA_DER: {
                    Vec3 dentro = clase == Clase.CURVA_IZQ ? izq() : der();
                    // el centro de la curva queda hacia dentro y hacia delante: se inclina hacia ese lado
                    Vec3 tg = tangente(t);
                    Vec3 hacia = dentro.subtract(tg.scale(dentro.dot(tg))).normalize();
                    float al = PERALTE * Mth.DEG_TO_RAD * Mth.sin((float) (t * Math.PI));
                    return up.scale(Mth.cos(al)).add(hacia.scale(Mth.sin(al)));
                }
                case RIZO: {
                    double th = uRizo(t) * Math.PI * 2;
                    return F().scale(-Math.sin(th)).add(0, Math.cos(th), 0).normalize();
                }
                case SUBIDA:
                case BAJADA: {
                    Vec3 tg = tangente(t);
                    return up.subtract(tg.scale(tg.y)).normalize();
                }
                default:
                    return up;
            }
        }
    }

    // ------------------------------------------------------------------ rizo (por longitud de arco)
    private static final double RIZO_K = RIZO_AVANCE / (Math.PI * 2);
    private static final int N_RIZO = 128;
    /** u (parámetro del rizo, 0..1) para cada fracción de longitud; y la longitud total. */
    private static final double[] U_RIZO = new double[N_RIZO + 1];
    static final double RIZO_LARGO;

    static {
        double[] largo = new double[N_RIZO + 1];
        Vec3 o = Vec3.ZERO, F = new Vec3(0, 0, 1), R = new Vec3(1, 0, 0);
        Vec3 antes = rizo(o, F, R, 0);
        for (int i = 1; i <= N_RIZO; i++) {
            Vec3 p = rizo(o, F, R, i / (double) N_RIZO);
            largo[i] = largo[i - 1] + p.distanceTo(antes);
            antes = p;
        }
        RIZO_LARGO = largo[N_RIZO];
        int j = 0;
        for (int i = 0; i <= N_RIZO; i++) {
            double objetivo = RIZO_LARGO * i / N_RIZO;
            while (j < N_RIZO - 1 && largo[j + 1] < objetivo) j++;
            double f = (objetivo - largo[j]) / Math.max(1e-9, largo[j + 1] - largo[j]);
            U_RIZO[i] = (j + Mth.clamp(f, 0, 1)) / N_RIZO;
        }
    }

    static Vec3 rizo(Vec3 o, Vec3 F, Vec3 R, double u) {
        double th = u * Math.PI * 2;
        return o.add(F.scale(RIZO_R * Math.sin(th) + RIZO_K * th)).add(0, RIZO_R * (1 - Math.cos(th)), 0).add(R.scale(RIZO_LADO * u));
    }

    static double uRizo(double t) {
        double x = Mth.clamp(t, 0, 1) * N_RIZO;
        int i = Math.min(N_RIZO - 1, (int) x);
        return Mth.lerp(x - i, U_RIZO[i], U_RIZO[i + 1]);
    }

    // ------------------------------------------------------------------ buscar trozos
    @Nullable
    public static Pieza en(BlockGetter l, BlockPos pos) {
        BlockState s = l.getBlockState(pos);
        if (s.getBlock() instanceof ViaMontanaBlock v) {
            Clase c = switch (s.getValue(ViaMontanaBlock.FORMA)) {
                case RECTA -> Clase.RECTA;
                case CURVA_IZQ -> Clase.CURVA_IZQ;
                case CURVA_DER -> Clase.CURVA_DER;
                case SUBIDA -> Clase.SUBIDA;
                case BAJADA -> Clase.BAJADA;
            };
            return new Pieza(pos.immutable(), s.getValue(ViaMontanaBlock.FACING), c, v.tipo, s.getValue(ViaMontanaBlock.POWERED));
        }
        if (s.getBlock() instanceof RizoBlock)
            return new Pieza(pos.immutable(), s.getValue(RizoBlock.FACING), Clase.RIZO, ViaMontanaBlock.Tipo.NORMAL, false);
        return null;
    }

    /** El trozo que va después (o null si la vía se acaba). */
    @Nullable
    public static Pieza siguiente(BlockGetter l, Pieza p) {
        BlockPos b = p.salida();
        Direction d = p.salidaDir();
        Pieza q = en(l, b);
        if (q != null && q.dir() == d && !q.entraArriba()) return q;
        q = en(l, b.below());
        if (q != null && q.dir() == d && q.entraArriba()) return q;
        return null;
    }

    /** El trozo que va antes (o null). */
    @Nullable
    public static Pieza anterior(BlockGetter l, Pieza p) {
        BlockPos b = p.entraArriba() ? p.pos().above() : p.pos();
        Direction d = p.dir();
        // detrás (recta, bajada o curva que gira hacia aquí), detrás y abajo (subida) o el rizo
        BlockPos[] cand = {b.relative(d.getOpposite()), b.relative(d.getOpposite()).below(),
                b.relative(d.getOpposite(), (int) RIZO_AVANCE).relative(d.getCounterClockWise(), (int) RIZO_LADO)};
        for (BlockPos c : cand) {
            Pieza q = en(l, c);
            if (q == null) continue;
            Pieza s = siguiente(l, q);
            if (s != null && s.pos().equals(p.pos())) return q;
        }
        return null;
    }

    /** Dónde se está: un trozo y la fracción recorrida de él. */
    public record Sitio(Pieza pieza, double t) {
        public Vec3 punto() {
            return pieza.punto(t);
        }
    }

    /**
     * Avanza (o retrocede, con dist &lt; 0) por la vía. Si la vía se acaba, se queda en el final y {@code tope[0]} se pone
     * a true.
     */
    public static Sitio avanzar(BlockGetter l, Sitio s, double dist, @Nullable boolean[] tope) {
        Pieza p = s.pieza();
        double t = s.t();
        for (int vueltas = 0; vueltas < 64; vueltas++) {
            double len = p.longitud();
            double nt = t + dist / len;
            if (nt > 1) {
                Pieza q = siguiente(l, p);
                if (q == null) {
                    if (tope != null) tope[0] = true;
                    return new Sitio(p, 1);
                }
                dist = (nt - 1) * len;
                p = q;
                t = 0;
            } else if (nt < 0) {
                Pieza q = anterior(l, p);
                if (q == null) {
                    if (tope != null) tope[0] = true;
                    return new Sitio(p, 0);
                }
                dist = nt * len;
                p = q;
                t = 1;
            } else return new Sitio(p, nt);
        }
        return new Sitio(p, Mth.clamp(t, 0, 1));
    }

    /** El trozo que hay dentro del bloque (las bajadas también se reconocen desde arriba). */
    @Nullable
    public static Pieza buscar(BlockGetter l, BlockPos pos) {
        Pieza p = en(l, pos);
        return p != null ? p : en(l, pos.below());
    }
}
