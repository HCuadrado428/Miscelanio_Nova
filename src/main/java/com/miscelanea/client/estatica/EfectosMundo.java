package com.miscelanea.client.estatica;

import com.miscelanea.Miscelanea;
import com.miscelanea.estatica.EstaticaRed;
import com.miscelanea.estatica.EstaticaRegistro;
import com.miscelanea.estatica.Poder;
import com.miscelanea.estatica.Tentaculos;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que la estática pinta en el mundo: charcos (no son bloques), tentáculos (cintas de nieve onduladas que salen de su
 * cuerpo, llegan al objetivo con un latigazo y se recogen), estelas y estallidos de partículas, y esconder a alguien un
 * momento (arrastrar a la estática).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EfectosMundo {
    private EfectosMundo() {}

    private static final ResourceLocation CHARCO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/estatica_charco.png");
    private static final ResourceLocation TENTACULO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/estatica_tentaculo.png");

    private record Charco(Vec3 pos, long desde, long hasta, int semilla) {}

    private static final class Tent {
        final int atk, obj, dur;
        final Poder pd;
        final long desde;
        /** Dónde estaba el objetivo al soltarlo (la punta se queda ahí mientras se recoge). */
        @Nullable Vec3 suelta;

        Tent(int atk, int obj, Poder pd, int dur, long desde) {
            this.atk = atk;
            this.obj = obj;
            this.pd = pd;
            this.dur = dur;
            this.desde = desde;
        }
    }

    private static final List<Charco> CHARCOS = new ArrayList<>();
    private static final List<Tent> TENTS = new ArrayList<>();
    private static final Map<Integer, Long> OCULTOS = new HashMap<>();

    // ------------------------------------------------------------------ recibir
    static void charco(EstaticaRed.Charco p) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        CHARCOS.add(new Charco(new Vec3(p.x, p.y, p.z), l.getGameTime(), l.getGameTime() + p.vida, p.semilla));
        if (CHARCOS.size() > 256) CHARCOS.remove(0);
    }

    static void tentaculo(EstaticaRed.Tentaculo p) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        TENTS.removeIf(t -> t.atk == p.atacante && t.obj == p.objetivo);
        TENTS.add(new Tent(p.atacante, p.objetivo, Poder.de(p.tipo), p.duracion, l.getGameTime()));
    }

    public static boolean agarrado(int id) {
        if (TENTS.isEmpty()) return false;
        var l = Minecraft.getInstance().level;
        if (l == null) return false;
        for (Tent t : TENTS) {
            long k = l.getGameTime() - t.desde;
            if (t.obj == id && k >= Tentaculos.AGARRA && k < suelta(t)) return true;
        }
        return false;
    }

    static boolean oculto(int id) {
        if (OCULTOS.isEmpty()) return false;
        Long h = OCULTOS.get(id);
        var l = Minecraft.getInstance().level;
        return h != null && l != null && l.getGameTime() < h;
    }

    /** Tick (relativo al inicio) en que los tentáculos sueltan a la víctima. */
    private static int suelta(Tent t) {
        return switch (t.pd) {
            case LANZAR -> 11;
            case ESTAMPAR -> 30;
            default -> t.dur - 3;
        };
    }

    static void fx(EstaticaRed.Fx f) {
        Minecraft mc = Minecraft.getInstance();
        var l = mc.level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        Vec3 a = new Vec3(f.x, f.y, f.z), b = new Vec3(f.x2, f.y2, f.z2);
        switch (f.tipo) {
            case EstaticaRed.Fx.ESTELA -> {
                silueta(a, f.extra);
                double d = a.distanceTo(b);
                for (double s = 0; s <= d; s += 0.2) {
                    Vec3 q = a.lerp(b, s / Math.max(0.01, d)).add(0, 0.4 + r.nextFloat() * 1.2, 0);
                    l.addParticle(EstaticaRegistro.CHISPA.get(), q.x, q.y, q.z, r.nextGaussian() * 0.01, 0.002, r.nextGaussian() * 0.01);
                }
            }
            case EstaticaRed.Fx.FRAGMENTO -> {
                Entity e = l.getEntity(f.id);
                silueta(a, e instanceof LivingEntity le ? Math.round(le.yBodyRot) : 0);
                estallido(a.add(0, 1, 0), 40, 0.25);
                estallido(b.add(0, 1, 0), 30, 0.18);
            }
            case EstaticaRed.Fx.ESTALLIDO -> estallido(a, f.extra > 0 ? 45 : 20, 0.22);
            case EstaticaRed.Fx.CHISPAZO -> {
                for (int i = 0; i < 18; i++)
                    l.addParticle(EstaticaRegistro.CHISPA.get(), a.x + r.nextGaussian() * 0.3, a.y + r.nextGaussian() * 0.4, a.z + r.nextGaussian() * 0.3,
                            r.nextGaussian() * 0.06, r.nextGaussian() * 0.06, r.nextGaussian() * 0.06);
            }
            case EstaticaRed.Fx.GOLPE_SUELO -> {
                if (mc.player != null) {
                    double d = mc.player.position().distanceTo(a);
                    if (d < 16) com.miscelanea.client.armas.TemblorCliente.empezar((float) (1.4 * (1 - d / 16)), 10);
                }
                estallido(a.add(0, 0.2, 0), 40, 0.3);
            }
            case EstaticaRed.Fx.OCULTAR -> OCULTOS.put(f.id, l.getGameTime() + f.extra);
            default -> {}
        }
    }

    private static void estallido(Vec3 c, int n, double v) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        for (int i = 0; i < n; i++)
            l.addParticle(EstaticaRegistro.CHISPA.get(), c.x, c.y, c.z, r.nextGaussian() * v, r.nextGaussian() * v * 0.8, r.nextGaussian() * v);
    }

    /** La silueta del cuerpo hecha de nieve que se queda un momento donde estaba (velocidad 0 = partícula quieta y larga). */
    private static void silueta(Vec3 pies, int yawGrados) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        float yaw = yawGrados * Mth.DEG_TO_RAD;
        Vec3 der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        // cajas (derecha, arriba, ancho, alto) en bloques
        double[][] cajas = {{-0.25, 1.5, 0.5, 0.5}, {-0.25, 0.75, 0.5, 0.75}, {-0.5, 0.75, 0.25, 0.75}, {0.25, 0.75, 0.25, 0.75}, {-0.25, 0, 0.5, 0.75}};
        for (double[] c : cajas) {
            int n = (int) (c[2] * c[3] * 90);
            for (int i = 0; i < n; i++) {
                double x = c[0] + r.nextDouble() * c[2], y = c[1] + r.nextDouble() * c[3];
                Vec3 q = pies.add(der.scale(x)).add(0, y, 0);
                l.addParticle(EstaticaRegistro.CHISPA.get(), q.x, q.y, q.z, 0, 0, 0);
            }
        }
    }

    @Nullable
    static Entity mirado(Minecraft mc, double alcance) {
        if (mc.player == null || mc.level == null) return null;
        Vec3 ojo = mc.player.getEyePosition(), fin = ojo.add(mc.player.getLookAngle().scale(alcance));
        Entity mejor = null;
        double md = alcance;
        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().expandTowards(mc.player.getLookAngle().scale(alcance)).inflate(1.5),
                x -> x instanceof LivingEntity && x.isAlive() && !x.isSpectator())) {
            var c = e.getBoundingBox().inflate(0.4).clip(ojo, fin);
            if (c.isPresent() && c.get().distanceTo(ojo) < md) {
                md = c.get().distanceTo(ojo);
                mejor = e;
            }
        }
        return mejor;
    }

    // ------------------------------------------------------------------ tick y limpieza
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        var l = Minecraft.getInstance().level;
        if (l == null) {
            CHARCOS.clear();
            TENTS.clear();
            OCULTOS.clear();
            return;
        }
        long t = l.getGameTime();
        CHARCOS.removeIf(c -> t > c.hasta || t < c.desde - 40);
        TENTS.removeIf(x -> t - x.desde > x.dur + 12 || t < x.desde - 40);
        OCULTOS.values().removeIf(h -> t > h);
        RandomSource r = l.getRandom();
        for (Charco c : CHARCOS)
            if (r.nextInt(8) == 0)
                l.addParticle(EstaticaRegistro.CHISPA.get(), c.pos.x + r.nextGaussian() * 0.25, c.pos.y + 0.05, c.pos.z + r.nextGaussian() * 0.25, 0, 0.01, 0);
        // los tentáculos chisporrotean por donde pasan
        for (Tent x : TENTS) {
            Entity v = l.getEntity(x.obj);
            if (v != null && r.nextInt(2) == 0) {
                Vec3 c = v.getBoundingBox().getCenter();
                l.addParticle(EstaticaRegistro.CHISPA.get(), c.x + r.nextGaussian() * 0.3, c.y + r.nextGaussian() * 0.3, c.z + r.nextGaussian() * 0.3, 0, 0, 0);
            }
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        CHARCOS.clear();
        TENTS.clear();
        OCULTOS.clear();
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (CHARCOS.isEmpty() && TENTS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        var l = mc.level;
        if (l == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        long t = l.getGameTime();
        int cuadro = (int) ((t / 2) % 8);
        if (!CHARCOS.isEmpty()) {
            RenderType rt = RenderType.entityTranslucent(CHARCO);
            VertexConsumer vc = bs.getBuffer(rt);
            Matrix4f m = ps.last().pose();
            Matrix3f nm = ps.last().normal();
            for (Charco c : CHARCOS) {
                if (c.pos.distanceToSqr(cam) > 64 * 64) continue;
                float edad = t - c.desde + pt, queda = c.hasta - t - pt;
                float al = Mth.clamp(edad / 10F, 0F, 1F) * Mth.clamp(queda / 40F, 0F, 1F);
                float tam = 0.55F + 0.25F * Mascara.hash(c.semilla, 1, 2, 3);
                float ang = Mascara.hash(c.semilla, 4, 5, 6) * Mth.TWO_PI;
                float ca = Mth.cos(ang) * tam, sa = Mth.sin(ang) * tam;
                int f = (cuadro + (c.semilla & 7)) % 8;
                float v0 = f / 8F, v1 = (f + 1) / 8F;
                float y = (float) c.pos.y + 0.015F;
                float x = (float) c.pos.x, z = (float) c.pos.z;
                int a = (int) (al * 255);
                vertice(vc, m, nm, x - ca + sa, y, z - sa - ca, 0, v0, a);
                vertice(vc, m, nm, x - ca - sa, y, z - sa + ca, 0, v1, a);
                vertice(vc, m, nm, x + ca - sa, y, z + sa + ca, 1, v1, a);
                vertice(vc, m, nm, x + ca + sa, y, z + sa - ca, 1, v0, a);
            }
            bs.endBatch(rt);
        }
        if (!TENTS.isEmpty()) {
            RenderType rt = RenderType.entityTranslucent(TENTACULO);
            VertexConsumer vc = bs.getBuffer(rt);
            for (Tent x : TENTS) dibujarTentaculos(vc, ps, l, x, t, pt, cam, cuadro);
            bs.endBatch(rt);
        }
        ps.popPose();
    }

    private static void vertice(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, float u, float v, int a) {
        vc.vertex(m, x, y, z).color(255, 255, 255, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(nm, 0, 1, 0).endVertex();
    }

    private static Vec3 posicion(Entity e, float pt) {
        return new Vec3(Mth.lerp(pt, e.xo, e.getX()), Mth.lerp(pt, e.yo, e.getY()), Mth.lerp(pt, e.zo, e.getZ()));
    }

    /** Desde dónde (0..1 del largo) se usa la punta deshilachada de la textura. */
    private static final float PUNTA = 0.72F;

    private static void dibujarTentaculos(VertexConsumer vc, PoseStack ps, net.minecraft.client.multiplayer.ClientLevel l, Tent x, long t, float pt, Vec3 cam, int cuadro) {
        Entity a = l.getEntity(x.atk), v = l.getEntity(x.obj);
        if (!(a instanceof LivingEntity la) || v == null) return;
        float k = t - x.desde + pt;
        int suelta = suelta(x);
        // cuánto llega (0..1): sale deprisa, agarra, y al soltar se recoge con un latigazo
        float ext;
        if (k < Tentaculos.AGARRA) {
            float q = k / Tentaculos.AGARRA;
            ext = 1 - (1 - q) * (1 - q) * (1 - q);
        } else if (k < suelta) ext = 1;
        else ext = Mth.clamp(1 - (k - suelta) / 9F, 0F, 1F);
        if (ext <= 0.01F) return;
        float latigo = k >= suelta ? Mth.clamp((k - suelta) / 9F, 0, 1) : 0;
        float yaw = Mth.rotLerp(pt, la.yBodyRotO, la.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), fr = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        int lado = Mascara.lado(EstaticaCliente.semilla(a.getId()));
        Vec3 pa = posicion(a, pt), pv = posicion(v, pt).add(0, v.getBbHeight() * 0.55, 0);
        double[][] raices = {{0.32 * lado, 1.38, -0.12}, {0.18 * lado, 1.05, -0.2}, {-0.1 * lado, 1.25, -0.2}};
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        // textura: 8 fotogramas de 16 de ancho; en cada uno, el cuerpo (8, se repite a lo largo) y la punta (8, deshilachada)
        int f = (cuadro + x.atk) % 8;
        float u0 = f / 8F, um = (f + 0.5F) / 8F, u1 = (f + 1) / 8F;
        for (int i = 0; i < 3; i++) {
            Vec3 raiz = pa.add(der.scale(raices[i][0])).add(0, raices[i][1], 0).add(fr.scale(raices[i][2]));
            // cada tentáculo se agarra a un punto distinto del objetivo
            double ang = i * 2.1 + x.obj;
            Vec3 centroV = pv;
            if (k >= suelta) {
                if (x.suelta == null) x.suelta = pv;
                centroV = x.suelta;
            }
            Vec3 punta = centroV.add(Math.cos(ang) * v.getBbWidth() * 0.45, (i - 1) * 0.25, Math.sin(ang) * v.getBbWidth() * 0.45);
            // al soltar, la punta sigue un poco hacia delante (latigazo) antes de recogerse
            if (latigo > 0) punta = punta.add(punta.subtract(raiz).normalize().scale(Mth.sin(latigo * Mth.PI) * 0.8));
            Vec3 d = punta.subtract(raiz);
            double len = d.length();
            if (len < 0.05) continue;
            Vec3 dn = d.scale(1 / len);
            Vec3 lat = dn.cross(new Vec3(0, 1, 0));
            if (lat.lengthSqr() < 1.0E-4) lat = new Vec3(1, 0, 0);
            lat = lat.normalize();
            Vec3 arr = lat.cross(dn).normalize();
            // curva: sube y se abre hacia fuera de la espalda antes de ir al objetivo
            Vec3 ctrl = raiz.add(d.scale(0.4)).add(arr.scale(len * 0.28 + 0.3)).add(lat.scale((i - 1) * 0.9)).add(arr.scale((i - 1) * 0.25)).add(fr.scale(-0.35));
            int N = 22;
            Vec3 prev = null, prevW = null;
            float vPrev = 0, vtPrev = 0;
            for (int j = 0; j <= N; j++) {
                float s = j / (float) N * ext;
                float om = 1 - s;
                Vec3 q = raiz.scale(om * om).add(ctrl.scale(2 * om * s)).add(punta.scale(s * s));
                float onda = Mth.sin(s * 11F - k * 0.9F + i * 2.3F) * 0.16F * Mth.sin(Mth.PI * Math.min(1F, s / Math.max(0.05F, ext))) * (1 + latigo * 2.5F);
                float onda2 = Mth.cos(s * 7F - k * 0.6F + i) * 0.09F * Mth.sin(Mth.PI * s);
                q = q.add(lat.scale(onda)).add(arr.scale(onda2));
                Vec3 vista = cam.subtract(q);
                Vec3 tang = prev == null ? d : q.subtract(prev);
                Vec3 w = tang.cross(vista);
                if (w.lengthSqr() < 1.0E-8) w = lat;
                float ancho = 0.15F * (1 - s * 0.55F) * (0.85F + 0.15F * Mth.sin(k * 0.7F + j));
                w = w.normalize().scale(ancho);
                // a lo largo: unos 2 texels por píxel (64 texels = 2 bloques) y deslizándose; la punta, de 0 a 1 en su tramo
                float vv = (float) (s * len * 0.5) + k * 0.05F;
                float sr = j / (float) N, vt = Math.max(0F, (sr - PUNTA) / (1 - PUNTA));
                if (prev != null) {
                    int al = (int) (235 * Mth.clamp(ext * 1.4F, 0, 1));
                    boolean enPunta = sr > PUNTA;
                    float ua = enPunta ? um : u0, ub = enPunta ? u1 : um, va = enPunta ? vtPrev : vPrev, vb = enPunta ? vt : vv;
                    vertice(vc, m, nm, (float) (prev.x - prevW.x), (float) (prev.y - prevW.y), (float) (prev.z - prevW.z), ua, va, al);
                    vertice(vc, m, nm, (float) (prev.x + prevW.x), (float) (prev.y + prevW.y), (float) (prev.z + prevW.z), ub, va, al);
                    vertice(vc, m, nm, (float) (q.x + w.x), (float) (q.y + w.y), (float) (q.z + w.z), ub, vb, al);
                    vertice(vc, m, nm, (float) (q.x - w.x), (float) (q.y - w.y), (float) (q.z - w.z), ua, vb, al);
                }
                prev = q;
                prevW = w;
                vPrev = vv;
                vtPrev = vt;
            }
        }
    }
}
