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
 * cuerpo, llegan al objetivo con un latigazo y se recogen), destellos (anillos, rayos, siluetas y halos hechos de
 * geometría con nieve: la estática no suelta ni una partícula) y esconder a alguien un momento (arrastrar a la estática).
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

    // ------------------------------------------------------------------ destellos (geometría con nieve: nada de partículas)
    private static final ResourceLocation DESTELLO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/estatica_destello.png");
    private static final int ANILLO = 0, ARCO = 1, SILUETA = 2, HALO = 3, CUPULA = 4;

    /** a, b: puntos (o centro); r: radio / largo; dur en ticks; desde puede ser futuro (retraso). */
    private record Destello(int tipo, Vec3 a, Vec3 b, float r, long desde, int dur, int sem, float yaw, int id) {}

    private static final List<Destello> DESTELLOS = new ArrayList<>();

    private static void destello(int tipo, Vec3 a, Vec3 b, float r, int retraso, int dur, float yaw, int id) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        DESTELLOS.add(new Destello(tipo, a, b, r, l.getGameTime() + retraso, dur, l.getRandom().nextInt(), yaw, id));
        if (DESTELLOS.size() > 400) DESTELLOS.remove(0);
    }

    /** Rayitos que salen de un punto en direcciones al azar. */
    private static void rayos(Vec3 c, int n, double largo, int dur) {
        var l = Minecraft.getInstance().level;
        if (l == null) return;
        RandomSource r = l.getRandom();
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.7, r.nextGaussian());
            if (d.lengthSqr() < 1.0E-4) continue;
            d = d.normalize().scale(largo * (0.6 + r.nextDouble() * 0.6));
            destello(ARCO, c, c.add(d), 0, r.nextInt(2), dur, 0, -1);
        }
    }

    /** Estallido de estática (anillo que se abre y unos rayos). */
    static void estallido(Vec3 c, float tam) {
        destello(ANILLO, c, c, tam, 0, 9, 0, -1);
        rayos(c, 5, tam * 0.7, 4);
    }

    static void fx(EstaticaRed.Fx f) {
        Minecraft mc = Minecraft.getInstance();
        var l = mc.level;
        if (l == null) return;
        Vec3 a = new Vec3(f.x, f.y, f.z), b = new Vec3(f.x2, f.y2, f.z2);
        switch (f.tipo) {
            case EstaticaRed.Fx.ESTELA -> {
                destello(SILUETA, a, a, 1, 0, 14, f.extra, -1);
                destello(ARCO, a.add(0, 1.2, 0), b.add(0, 1.2, 0), 0, 0, 6, 0, -1);
                destello(ARCO, a.add(0, 0.5, 0), b.add(0, 0.6, 0), 0, 1, 5, 0, -1);
            }
            case EstaticaRed.Fx.FRAGMENTO -> {
                Entity e = l.getEntity(f.id);
                destello(SILUETA, a, a, 1, 0, 16, e instanceof LivingEntity le ? Math.round(le.yBodyRot) : 0, -1);
                estallido(a.add(0, 1, 0), 1.3F);
                estallido(b.add(0, 1, 0), 1.0F);
                destello(ARCO, a.add(0, 1, 0), b.add(0, 1, 0), 0, 0, 5, 0, -1);
            }
            case EstaticaRed.Fx.ESTALLIDO -> estallido(a, f.extra > 0 ? 1.6F : 1.0F);
            case EstaticaRed.Fx.CHISPAZO -> {
                rayos(a, f.extra > 0 ? 9 : 5, f.extra > 0 ? 1.3 : 0.9, 4);
                destello(ANILLO, a, a, f.extra > 0 ? 1.2F : 0.8F, 0, 6, 0, -1);
            }
            case EstaticaRed.Fx.ARCO -> {
                destello(ARCO, a, b, 0, 0, 6, 0, -1);
                destello(ARCO, a, b, 0, 1, 5, 0, -1);
                rayos(b, 3, 0.6, 4);
            }
            case EstaticaRed.Fx.ATURDIDO -> destello(HALO, a, a, 0.42F, 0, Math.max(4, f.extra), 0, f.id);
            case EstaticaRed.Fx.GOLPE_SUELO -> {
                if (mc.player != null) {
                    double d = mc.player.position().distanceTo(a);
                    if (d < 16) com.miscelanea.client.armas.TemblorCliente.empezar((float) (1.4 * (1 - d / 16)), 10);
                }
                float r = f.extra > 0 ? f.extra / 10F : 1.6F;
                destello(ANILLO, a.add(0, 0.06, 0), a, r, 0, 11, 0, -1);
                destello(CUPULA, a, a, r * 0.7F, 0, 8, 0, -1);
            }
            case EstaticaRed.Fx.NOVA -> {
                float r = Math.max(1F, f.extra / 10F);
                Vec3 pies = a.add(0, -0.85, 0);
                destello(ANILLO, pies.add(0, 0.06, 0), pies, r, 0, 12, 0, -1);
                destello(ANILLO, pies.add(0, 0.08, 0), pies, r * 0.65F, 3, 10, 0, -1);
                destello(CUPULA, pies, pies, r, 0, 12, 0, -1);
                destello(ANILLO, a.add(0, 0.4, 0), a, r * 0.9F, 1, 9, 0, -1);
                rayos(a, 12, r * 0.55, 5);
                rayos(a, 8, r * 0.35, 4);
                if (mc.player != null) {
                    double d = mc.player.position().distanceTo(pies);
                    if (d < r * 2.5) com.miscelanea.client.armas.TemblorCliente.empezar((float) (2.0 * (1 - d / (r * 2.5))), 14);
                }
            }
            case EstaticaRed.Fx.OCULTAR -> OCULTOS.put(f.id, l.getGameTime() + f.extra);
            default -> {}
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
            DESTELLOS.clear();
            return;
        }
        long t = l.getGameTime();
        CHARCOS.removeIf(c -> t > c.hasta || t < c.desde - 40);
        TENTS.removeIf(x -> t - x.desde > x.dur + 12 || t < x.desde - 40);
        OCULTOS.values().removeIf(h -> t > h);
        DESTELLOS.removeIf(d -> t > d.desde + d.dur + 1 || t < d.desde - 200);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        CHARCOS.clear();
        TENTS.clear();
        OCULTOS.clear();
        DESTELLOS.clear();
    }

    // ------------------------------------------------------------------ dibujo
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (CHARCOS.isEmpty() && TENTS.isEmpty() && DESTELLOS.isEmpty())) return;
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
        if (!DESTELLOS.isEmpty()) {
            RenderType rt = RenderType.entityTranslucent(DESTELLO);
            VertexConsumer vc = bs.getBuffer(rt);
            for (Destello d : DESTELLOS) dibujarDestello(vc, ps, l, d, t, pt, cam);
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

    // ------------------------------------------------------------------ dibujo de los destellos
    private static void vc(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 q, float u, float v, int gris, int a) {
        vc.vertex(m, (float) q.x, (float) q.y, (float) q.z).color(gris, gris, Math.min(255, gris + 12), a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0).normal(nm, 0, 1, 0).endVertex();
    }

    private static void quad(VertexConsumer c, Matrix4f m, Matrix3f nm, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float u0, float u1, float v0, float v1, int gris, int a) {
        vc(c, m, nm, p0, u0, v0, gris, a);
        vc(c, m, nm, p1, u1, v0, gris, a);
        vc(c, m, nm, p2, u1, v1, gris, a);
        vc(c, m, nm, p3, u0, v1, gris, a);
    }

    private static void dibujarDestello(VertexConsumer c, PoseStack ps, net.minecraft.client.multiplayer.ClientLevel l, Destello d, long t, float pt, Vec3 cam) {
        float edad = t - d.desde + pt;
        if (edad < 0) return;
        float k = Mth.clamp(edad / d.dur, 0F, 1F);
        if (k >= 1F) return;
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        // fotograma de la nieve (8 de 16x16 en vertical) que cambia cada tick
        int f = (int) ((t + (d.sem & 7)) % 8);
        float v0 = f / 8F, v1 = (f + 1) / 8F;
        switch (d.tipo) {
            case ANILLO -> {
                float rad = d.r * (1 - (1 - k) * (1 - k)), w = 0.16F + 0.05F * d.r;
                anillo(c, m, nm, d.a, rad, w, 0, v0, v1, (int) (230 * (1 - k)), 255);
            }
            case CUPULA -> {
                float rad = d.r * (1 - (1 - k) * (1 - k)), h = 0.3F + 2.2F * (1 - k);
                int n = Math.max(16, (int) (rad * 10));
                int a = (int) (170 * (1 - k));
                for (int j = 0; j < n; j++) {
                    double a0 = j * Mth.TWO_PI / n, a1 = (j + 1) * Mth.TWO_PI / n;
                    Vec3 p0 = d.a.add(Math.cos(a0) * rad, 0, Math.sin(a0) * rad), p1 = d.a.add(Math.cos(a1) * rad, 0, Math.sin(a1) * rad);
                    float u0 = j * 0.5F, u1 = (j + 1) * 0.5F;
                    quad(c, m, nm, p0, p1, p1.add(0, h, 0), p0.add(0, h, 0), u0, u1, v1, v0, 235, a);
                }
            }
            case HALO -> {
                Entity e = l.getEntity(d.id);
                if (e == null) return;
                Vec3 cab = posicion(e, pt).add(0, e.getBbHeight() + 0.2 + 0.04 * Mth.sin(edad * 0.8F), 0);
                float fin = Mth.clamp((d.dur - edad) / 4F, 0F, 1F);
                anillo(c, m, nm, cab, d.r * (0.9F + 0.1F * Mth.sin(edad * 1.3F)), 0.07F, edad * 0.25F, v0, v1, (int) (230 * fin), 240);
            }
            case ARCO -> {
                Vec3 dd = d.b.subtract(d.a);
                double len = dd.length();
                if (len < 0.05) return;
                Vec3 dn = dd.scale(1 / len);
                Vec3 l1 = dn.cross(new Vec3(0, 1, 0));
                if (l1.lengthSqr() < 1.0E-4) l1 = new Vec3(1, 0, 0);
                l1 = l1.normalize();
                Vec3 l2 = l1.cross(dn).normalize();
                int n = Math.max(4, (int) (len * 3));
                long tick = t;
                Vec3 prev = d.a, prevW = null;
                int a = (int) (255 * (1 - k * k));
                for (int j = 1; j <= n; j++) {
                    float s = j / (float) n;
                    // zigzag que cambia cada tick (como un rayo), sujeto en los dos extremos
                    float amp = (float) Math.min(0.35, len * 0.12) * Mth.sin(Mth.PI * s);
                    float h1 = Mascara.hash(d.sem, j, (int) tick, 17) - 0.5F, h2 = Mascara.hash(d.sem, j, (int) tick, 29) - 0.5F;
                    Vec3 q = j == n ? d.b : d.a.add(dd.scale(s)).add(l1.scale(h1 * 2 * amp)).add(l2.scale(h2 * 2 * amp));
                    Vec3 tang = q.subtract(prev);
                    Vec3 w = tang.cross(cam.subtract(q));
                    if (w.lengthSqr() < 1.0E-8) w = l1;
                    w = w.normalize().scale(0.05 + 0.03 * (1 - k));
                    if (prevW == null) prevW = w;
                    quad(c, m, nm, prev.subtract(prevW), prev.add(prevW), q.add(w), q.subtract(w), (j - 1) * 0.25F, j * 0.25F, v0, v0 + 1 / 128F, 255, a);
                    prev = q;
                    prevW = w;
                }
            }
            case SILUETA -> {
                float yaw = d.yaw * Mth.DEG_TO_RAD;
                Vec3 der = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw)), fr = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
                // cajas (derecha, arriba, ancho, alto, fondo) en bloques: cabeza, cuerpo, brazos y piernas
                double[][] cajas = {{-0.25, 1.5, 0.5, 0.5, 0.5}, {-0.25, 0.75, 0.5, 0.75, 0.25}, {-0.5, 0.75, 0.25, 0.75, 0.25},
                        {0.25, 0.75, 0.25, 0.75, 0.25}, {-0.25, 0, 0.25, 0.75, 0.25}, {0, 0, 0.25, 0.75, 0.25}};
                int a = (int) (200 * (1 - k));
                for (int i = 0; i < cajas.length; i++) {
                    double[] b = cajas[i];
                    // cada trozo se desplaza a saltos (como la imagen de una tele mal sintonizada) y se va subiendo un poco
                    float jx = (Mascara.hash(d.sem, i, (int) t, 3) - 0.5F) * 0.14F * (0.3F + k);
                    Vec3 o = d.a.add(der.scale(b[0] + jx)).add(0, b[1] + k * 0.15, 0).add(fr.scale(-b[4] / 2));
                    caja(c, m, nm, o, der.scale(b[2]), new Vec3(0, b[3], 0), fr.scale(b[4]), v0, v1, a);
                }
            }
            default -> {}
        }
    }

    private static void anillo(VertexConsumer c, Matrix4f m, Matrix3f nm, Vec3 cen, float rad, float w, float giro, float v0, float v1, int a, int gris) {
        if (rad <= 0.01F || a <= 0) return;
        int n = Math.max(14, (int) (rad * 12));
        float ri = Math.max(0, rad - w), re = rad + w;
        for (int j = 0; j < n; j++) {
            double a0 = giro + j * Mth.TWO_PI / n, a1 = giro + (j + 1) * Mth.TWO_PI / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            Vec3 i0 = cen.add(c0 * ri, 0, s0 * ri), e0 = cen.add(c0 * re, 0, s0 * re), i1 = cen.add(c1 * ri, 0, s1 * ri), e1 = cen.add(c1 * re, 0, s1 * re);
            quad(c, m, nm, i0, i1, e1, e0, j * 0.5F, (j + 1) * 0.5F, v0, v1, gris, a);
        }
    }

    /** Caja de nieve (6 caras) con origen o y aristas ex, ey, ez. */
    private static void caja(VertexConsumer c, Matrix4f m, Matrix3f nm, Vec3 o, Vec3 ex, Vec3 ey, Vec3 ez, float v0, float v1, int a) {
        Vec3 x = o.add(ex), y = o.add(ey), z = o.add(ez), xy = x.add(ey), xz = x.add(ez), yz = y.add(ez), xyz = xy.add(ez);
        float ux = (float) ex.length() * 2, uy = (float) ey.length() * 2, uz = (float) ez.length() * 2;
        quad(c, m, nm, o, x, xy, y, 0, ux, v1, v0, 220, a);
        quad(c, m, nm, z, xz, xyz, yz, 0.3F, 0.3F + ux, v1, v0, 220, a);
        quad(c, m, nm, o, z, yz, y, 0.6F, 0.6F + uz, v1, v0, 200, a);
        quad(c, m, nm, x, xz, xyz, xy, 0.1F, 0.1F + uz, v1, v0, 200, a);
        quad(c, m, nm, y, xy, xyz, yz, 0.2F, 0.2F + ux, v0, v1, 240, a);
        quad(c, m, nm, o, x, xz, z, 0.4F, 0.4F + ux, v0, v1, 170, a);
    }
}
