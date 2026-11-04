package com.miscelanea.client.baile;

import com.miscelanea.baile.CancionesBaile;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Cámara de cine del que baila (solo en su pantalla; los demás lo ven normal). Antes de empezar la canción se decide la
 * lista de planos: uno por frase (8 pulsos) en los versos, uno por compás en los estribillos, la intro con planos largos
 * y el final con una grúa que se aleja. Cada plano se mueve suave (con curva) y entre plano y plano hay corte seco,
 * siempre en un tiempo fuerte. Si el baile tiene un paso de pies, un giro o una pose, se prefiere el plano que lo luce.
 * Nunca atraviesa paredes: al empezar cada plano se busca el lado que está libre y, aun así, cada fotograma se acerca si
 * hay un bloque en medio. El que baila queda a la izquierda de la pantalla para que se vean las flechas de la derecha.
 * <p>
 * Es lo mismo que dibuja scratchpad/baile_prev.py (mismo generador de números para que los planos salgan iguales).
 */
public final class CamaraBaile {
    static final int GENERAL = 0, PRIMER_PLANO = 1, CONTRAPICADO = 2, ORBITA = 3, CENITAL = 4, HOMBRO = 5, PIES = 6, GRUA = 7;
    /** Grados que se gira la cámara para que el bailarín quede a la izquierda (las flechas están a la derecha). */
    static final float DESVIO_HUD = 14F;

    static final class Plano {
        final int tipo;
        final float desde, hasta;
        int lado;
        float extra;
        final boolean golpe;
        boolean revisado;

        Plano(int tipo, float desde, float hasta, int lado, boolean golpe) {
            this.tipo = tipo;
            this.desde = desde;
            this.hasta = hasta;
            this.lado = lado;
            this.golpe = golpe;
        }
    }

    private static List<Plano> planos;
    private static CancionesBaile.Cancion planosDe;

    private CamaraBaile() {}

    // ------------------------------------------------------------------ números (igual en baile_prev.py)
    static final class Azar {
        long s;

        Azar(String id) {
            long h = 7;
            for (int i = 0; i < id.length(); i++) h = (h * 31 + id.charAt(i)) & 0x7fffffffL;
            s = h;
        }

        int sig(int n) {
            s = (s * 1103515245L + 12345L) & 0x7fffffffL;
            return (int) ((s >> 8) % n);
        }
    }

    static String tipoSeccion(String n) {
        n = n.toLowerCase(java.util.Locale.ROOT);
        for (String k : new String[]{"intro", "verso", "estribillo", "puente", "final", "outro"}) if (n.startsWith(k)) return k;
        return "verso";
    }

    static int[] repertorio(String tipo) {
        return switch (tipo) {
            case "intro" -> new int[]{GENERAL, ORBITA};
            case "estribillo" -> new int[]{PRIMER_PLANO, CONTRAPICADO, ORBITA, CENITAL, HOMBRO, GENERAL};
            case "puente" -> new int[]{CENITAL, ORBITA, PIES};
            case "final" -> new int[]{PRIMER_PLANO, CONTRAPICADO, ORBITA, CENITAL, GENERAL, HOMBRO};
            case "outro" -> new int[]{GRUA};
            default -> new int[]{GENERAL, HOMBRO, PIES, ORBITA, CONTRAPICADO};
        };
    }

    static float largoPlano(String tipo) {
        return switch (tipo) {
            case "estribillo", "final" -> 4F;
            case "outro" -> 1000F;
            default -> 8F;
        };
    }

    /** Plano que luce mejor un paso (o -1). */
    static int preferido(String paso) {
        return switch (paso) {
            case "deslizar", "patada", "puntillas" -> PIES;
            case "giro", "giro_pose" -> CENITAL;
            case "pose", "sombrero" -> PRIMER_PLANO;
            case "saltos", "caderas" -> CONTRAPICADO;
            default -> -1;
        };
    }

    static List<Plano> planificar(CancionesBaile.Cancion c) {
        List<Plano> l = new ArrayList<>();
        Azar az = new Azar(c.id);
        int ultimo = -1;
        for (CancionesBaile.Seccion sec : c.secciones) {
            String tipo = tipoSeccion(sec.nombre);
            float largo = largoPlano(tipo);
            int[] rep = repertorio(tipo);
            float t = sec.desde;
            while (t < sec.hasta - 0.01F) {
                float fin = Math.min(sec.hasta, t + largo);
                if (sec.hasta - fin < largo * 0.5F) fin = sec.hasta;
                int pref = preferido(Coreografia.pasoEn(c, t + 0.01F));
                int tp;
                if (pref >= 0 && pref != ultimo && az.sig(10) < 6) tp = pref;
                else {
                    tp = rep[az.sig(rep.length)];
                    for (int i = 0; i < 4 && tp == ultimo && rep.length > 1; i++) tp = rep[az.sig(rep.length)];
                }
                int lado = az.sig(2) == 0 ? 1 : -1;
                l.add(new Plano(tp, t, fin, lado, tipo.equals("estribillo") || tipo.equals("final")));
                ultimo = tp;
                t = fin;
            }
        }
        return l;
    }

    /** {az (grados, 0 = delante), el (grados), distancia, altura del foco, foco hacia delante} del plano en u (0..1). */
    static float[] encuadre(int tipo, int lado, float u) {
        float L = lado;
        return switch (tipo) {
            case PRIMER_PLANO -> new float[]{L * (18 + 12 * u), 4, 1.75F - 0.25F * u, 1.55F, 0};
            case CONTRAPICADO -> new float[]{L * (38 - 22 * u), -14, 2.7F, 1.35F, 0};
            case ORBITA -> new float[]{L * (-70 + 140 * u), 16, 3.6F, 1.0F, 0};
            case CENITAL -> new float[]{L * (10 + 50 * u), 74, 4.6F, 0.9F, 0};
            case HOMBRO -> new float[]{180 + L * (32 - 10 * u), 18, 2.3F, 1.3F, 1.2F};
            case PIES -> new float[]{L * (42 - 24 * u), 10, 2.1F, 0.45F, 0};
            case GRUA -> new float[]{L * (20 + 25 * u), 8 + 42 * u, 2.8F + 5 * u, 1.0F, 0};
            default -> new float[]{L * (28 - 18 * u), 14, 6.2F - 1.4F * u, 1.0F, 0};
        };
    }

    static Vec3 direccion(float frente, float azGrados, float elGrados) {
        float f = frente * Mth.DEG_TO_RAD, a = azGrados * Mth.DEG_TO_RAD, e = elGrados * Mth.DEG_TO_RAD;
        Vec3 F = new Vec3(-Mth.sin(f), 0, Mth.cos(f)), R = new Vec3(-Mth.cos(f), 0, -Mth.sin(f));
        return F.scale(Mth.cos(a)).add(R.scale(Mth.sin(a))).scale(Mth.cos(e)).add(0, Mth.sin(e), 0);
    }

    static Vec3 foco(Vec3 pies, float frente, float[] enc) {
        float f = frente * Mth.DEG_TO_RAD;
        return pies.add(-Mth.sin(f) * enc[4], enc[3], Mth.cos(f) * enc[4]);
    }

    /** Qué parte de la distancia queda libre de bloques (1 = nada en medio). */
    static double libre(BlockGetter level, Entity ent, Vec3 foco, Vec3 dir, double dist) {
        Vec3 desde = foco.add(dir.scale(0.3)), hasta = foco.add(dir.scale(dist + 0.3));
        BlockHitResult hit = level.clip(new ClipContext(desde, hasta, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, ent));
        if (hit.getType() == HitResult.Type.MISS) return 1;
        return Math.max(0, (hit.getLocation().distanceTo(foco) - 0.25) / dist);
    }

    /** Al empezar un plano: el lado/giro con el que la cámara no choca con nada durante todo el plano. */
    static void revisar(Plano p, BlockGetter level, Entity ent, Vec3 pies, float frente) {
        p.revisado = true;
        int[][] variantes = {{p.lado, 0}, {-p.lado, 0}, {p.lado, 90}, {p.lado, -90}, {p.lado, 180}};
        double mejor = -1;
        int[] elegida = variantes[0];
        for (int[] v : variantes) {
            double peor = 1;
            for (float u : new float[]{0, 0.5F, 1}) {
                float[] enc = encuadre(p.tipo, v[0], u);
                peor = Math.min(peor, libre(level, ent, foco(pies, frente, enc), direccion(frente, enc[0] + v[1], enc[1]), enc[2]));
            }
            if (peor >= 0.85) {
                elegida = v;
                break;
            }
            if (peor > mejor) {
                mejor = peor;
                elegida = v;
            }
        }
        p.lado = elegida[0];
        p.extra = elegida[1];
    }

    /** Posición y giro de la cámara {x, y, z, yaw, pitch}, o null si no toca (desde CameraMixin, en tercera persona). */
    @Nullable
    public static double[] colocar(BlockGetter level, Entity entity, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player || !BaileCliente.camaraActiva()) return null;
        Vec3 pies = entity.getPosition(pt);
        float frente = BaileCliente.frente;
        float reloj = (System.currentTimeMillis() % 1000000L) / 1000F;
        CancionesBaile.Cancion c = BaileCliente.cancionEnMarcha();
        float[] enc;
        float extra = 0;
        boolean desvio = true;
        if (c == null) {
            // en el menú: plano general de frente que se mece despacio
            enc = new float[]{20 * Mth.sin(reloj * 0.25F), 12, 4.2F, 1.0F, 0};
            desvio = false;
        } else {
            if (planosDe != c || planos == null) {
                planos = planificar(c);
                planosDe = c;
            }
            float pulso = c.pulso(BaileCliente.segundoCancion());
            Plano p = null;
            for (Plano q : planos) if (pulso < q.hasta) {
                p = q;
                break;
            }
            if (p == null && !planos.isEmpty()) p = planos.get(planos.size() - 1);
            if (p == null || pulso < 0) enc = encuadre(GENERAL, 1, 0);
            else {
                if (!p.revisado) revisar(p, level, entity, pies, frente);
                float u = Mth.clamp((pulso - p.desde) / Math.max(0.01F, p.hasta - p.desde), 0, 1);
                u = u * u * (3 - 2 * u);
                enc = encuadre(p.tipo, p.lado, u);
                extra = p.extra;
                // en los estribillos la cámara da un golpecito hacia dentro en cada tiempo
                if (p.golpe) enc[2] *= 1 - 0.05F * (float) Math.exp(-(pulso - Mth.floor(pulso)) * 6);
            }
        }
        // cámara en mano: un vaivén muy pequeño
        float az = enc[0] + extra + 0.6F * Mth.sin(reloj * 1.3F), el = enc[1] + 0.4F * Mth.sin(reloj * 1.7F);
        Vec3 centro = foco(pies, frente, enc), dir = direccion(frente, az, el);
        double dist = enc[2] * Math.min(1, Math.max(0.15, libre(level, entity, centro, dir, enc[2])));
        Vec3 cam = centro.add(dir.scale(Math.max(0.4, dist)));
        Vec3 d = centro.subtract(cam);
        double hz = Math.sqrt(d.x * d.x + d.z * d.z);
        double yaw = Math.toDegrees(Math.atan2(-d.x, d.z)) + (desvio ? DESVIO_HUD : 0);
        double pitch = -Math.toDegrees(Math.atan2(d.y, Math.max(1.0E-4, hz)));
        return new double[]{cam.x, cam.y, cam.z, yaw, pitch};
    }

    /** Al empezar o acabar una canción se vuelven a decidir los planos. */
    static void olvidar() {
        planos = null;
        planosDe = null;
    }
}
