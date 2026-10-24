package com.miscelanea.client;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.medigun.MedigunCliente;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Lazos visibles entre parejas de efectos:
 * - Cupido: cinta rosa que cuelga y ondula, con latido.
 * - Enemigo: rayo rojo en zigzag que chisporrotea, más gordo cuanto más cerca están.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Lazos {
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/rayo_medico.png");

    private record Clave(int a, int b, int tipo) {}

    private static final Map<Clave, Long> LAZOS = new HashMap<>();

    private Lazos() {}

    public static void recibir(int a, int b, int tipo) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        LAZOS.put(new Clave(a, b, tipo), level.getGameTime() + (tipo == 2 ? 6 : 16));
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || LAZOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            LAZOS.clear();
            return;
        }
        long ahora = level.getGameTime();
        LAZOS.values().removeIf(t -> t < ahora);
        float pt = e.getPartialTick();
        float tiempo = ahora + pt;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType tipo = RenderType.beaconBeam(TEX, true);
        boolean algo = false;

        for (Map.Entry<Clave, Long> en : LAZOS.entrySet()) {
            Clave k = en.getKey();
            Entity a = level.getEntity(k.a()), b = level.getEntity(k.b());
            if (a == null || b == null) continue;
            // se desvanece en los últimos ticks si el servidor deja de avisar
            float vida = Mth.clamp((en.getValue() - ahora - pt) / 6.0F, 0, 1);
            Vec3 pa = a.getPosition(pt).add(0, a.getBbHeight() * 0.62, 0);
            if (k.tipo() == 2) {
                // desde la punta de la porra (delante y a la derecha del agresor)
                Vec3 mira = a.getViewVector(pt);
                Vec3 der = new Vec3(-mira.z, 0, mira.x).normalize();
                pa = a.getEyePosition(pt).add(mira.scale(0.9)).add(der.scale(0.35)).add(0, -0.35, 0);
            }
            Vec3 pb = b.getPosition(pt).add(0, b.getBbHeight() * 0.62, 0);
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer vc = buffers.getBuffer(tipo);
            if (k.tipo() == 0) cupido(pose, vc, pa, pb, cam, tiempo, vida);
            else if (k.tipo() == 1) enemigo(pose, vc, pa, pb, cam, tiempo, vida, k.a() * 31 + k.b());
            else chispazo(pose, vc, pa, pb, cam, tiempo, vida, k.a() * 17 + k.b());
            pose.popPose();
            algo = true;
        }
        if (algo) buffers.endBatch(tipo);
    }

    private static void cupido(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float t, float vida) {
        double dist = a.distanceTo(b);
        int n = Mth.clamp((int) (dist * 5), 10, 48);
        Vec3 eje = b.subtract(a);
        Vec3 lado = eje.cross(new Vec3(0, 1, 0));
        lado = lado.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : lado.normalize();
        // cuanto más lejos, más cuelga (como una cinta); con latido
        double cuelga = Math.min(1.2, dist * 0.12);
        float latido = 1.0F + 0.25F * Math.max(0, Mth.sin(t * 0.35F)) * Math.max(0, Mth.sin(t * 0.35F));
        Vec3[] p = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            double env = Math.sin(Math.PI * s);
            p[i] = a.add(eje.scale(s))
                    .add(0, -cuelga * env, 0)
                    .add(lado.scale(Math.sin(s * Math.PI * 3 - t * 0.15) * 0.12 * env));
        }
        MedigunCliente.cinta(pose, vc, p, cam, 0.06F * latido, 1.0F, 0.42F, 0.68F, 0.55F * vida, t * -0.05F, dist);
        MedigunCliente.cinta(pose, vc, p, cam, 0.02F * latido, 1.0F, 0.85F, 0.92F, 0.9F * vida, t * -0.1F, dist);
    }

    private static void enemigo(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float t, float vida, int semilla) {
        double dist = a.distanceTo(b);
        // más cerca = más rabia: más gordo y más brillante
        float rabia = (float) Mth.clamp(1.0 - dist / 5.0, 0.2, 1.0);
        int n = Mth.clamp((int) (dist * 4), 6, 30);
        Vec3 eje = b.subtract(a);
        Vec3 u = eje.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 v = eje.normalize().cross(u).normalize();
        // el zigzag cambia cada 2 ticks
        RandomSource r = RandomSource.create(semilla * 7919L + (long) (t / 2));
        Vec3[] p = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            double env = (i == 0 || i == n) ? 0 : 1;
            double amp = 0.18 * env * (0.5 + rabia);
            p[i] = a.add(eje.scale(s)).add(u.scale((r.nextDouble() - 0.5) * 2 * amp)).add(v.scale((r.nextDouble() - 0.5) * 2 * amp));
        }
        float parpadeo = 0.7F + 0.3F * r.nextFloat();
        MedigunCliente.cinta(pose, vc, p, cam, 0.08F * (0.6F + rabia), 0.9F, 0.08F, 0.08F, 0.5F * vida * parpadeo, 0, dist);
        MedigunCliente.cinta(pose, vc, p, cam, 0.025F * (0.6F + rabia), 1.0F, 0.75F, 0.7F, 0.95F * vida * parpadeo, 0, dist);
    }

    /** Arco de la porra: blanco azulado, muy nervioso, con ramas. */
    private static void chispazo(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, Vec3 cam, float t, float vida, int semilla) {
        double dist = a.distanceTo(b);
        int n = Mth.clamp((int) (dist * 6), 6, 24);
        Vec3 eje = b.subtract(a);
        Vec3 u = eje.cross(new Vec3(0, 1, 0));
        u = u.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : u.normalize();
        Vec3 v = eje.normalize().cross(u).normalize();
        RandomSource r = RandomSource.create(semilla * 104729L + (long) t);
        Vec3[] p = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            double amp = (i == 0 || i == n) ? 0 : 0.16 * Math.sin(Math.PI * s) + 0.04;
            p[i] = a.add(eje.scale(s)).add(u.scale((r.nextDouble() - 0.5) * 2 * amp)).add(v.scale((r.nextDouble() - 0.5) * 2 * amp));
        }
        MedigunCliente.cinta(pose, vc, p, cam, 0.07F, 0.45F, 0.7F, 1.0F, 0.55F * vida, 0, dist);
        MedigunCliente.cinta(pose, vc, p, cam, 0.02F, 0.9F, 0.96F, 1.0F, vida, 0, dist);
        // dos ramitas
        for (int k = 0; k < 2; k++) {
            int ini = 1 + r.nextInt(Math.max(1, n - 2));
            Vec3[] rama = new Vec3[4];
            rama[0] = p[ini];
            Vec3 dir = u.scale(r.nextDouble() - 0.5).add(v.scale(r.nextDouble() - 0.5)).add(eje.normalize().scale(0.3)).normalize();
            for (int j = 1; j < 4; j++) rama[j] = rama[j - 1].add(dir.scale(0.12)).add(u.scale((r.nextDouble() - 0.5) * 0.08));
            MedigunCliente.cinta(pose, vc, rama, cam, 0.012F, 0.8F, 0.9F, 1.0F, 0.8F * vida, 0, 0.4);
        }
    }
}
