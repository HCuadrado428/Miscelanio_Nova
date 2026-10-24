package com.miscelanea.client.nucleo;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.medigun.MedigunCliente;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.network.NucleoActivarPacket;
import com.miscelanea.nucleo.Aliento;
import com.miscelanea.nucleo.NucleoItem;
import com.miscelanea.registry.ModSounds;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente del núcleo: activación (Shift + clic derecho con la mano vacía), fases del aliento,
 * el rayo morado y negro con cuadros de error, sacudida de cámara, glitch en pantalla y el rugido en bucle.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class NucleoCliente {
    private static final ResourceLocation TEX_RAYO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/rayo_medico.png");
    private static final ResourceLocation TEX_CUADRO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/glitch_cuadro.png");

    /** Estado de cada jugador (lo manda el servidor). */
    private record Estado(long carga, long disparo, float potencia) {}

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();
    private static final Map<Integer, Rugido> RUGIDOS = new HashMap<>();
    /** Fin del enfriamiento del jugador local (para el indicador) y su duración total. */
    private static long enfriaHasta;
    private static int enfriaTotal = 1;
    /** El jugador local está manteniendo Shift + clic derecho (para avisar al soltar). */
    private static boolean manteniendo;

    private NucleoCliente() {}

    // ------------------------------------------------------------------ estado

    public static void recibir(int jugador, long carga, long disparo, float potencia) {
        if (carga < 0 && disparo < 0) {
            ESTADOS.remove(jugador);
            return;
        }
        ESTADOS.put(jugador, new Estado(carga, disparo, potencia));
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == jugador && disparo >= 0) {
            enfriaTotal = Aliento.enfriar(potencia);
            enfriaHasta = disparo + Aliento.duracion(potencia) + enfriaTotal;
        }
    }

    /** Potencia del aliento de este jugador: la del disparo, o la que lleva acumulada si está cargando. */
    public static float potencia(int id, float pt) {
        Estado e = ESTADOS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (e == null || level == null) return 0;
        if (e.disparo() >= 0) return e.potencia();
        return Aliento.potencia((long) (level.getGameTime() + pt - e.carga()));
    }

    /**
     * −1 = nada; 0–1 = cargando (llega a 1 con la carga máxima); 1–2 = disparando (1 + fracción del rayo).
     */
    public static float fase(int id, float pt) {
        Estado e = ESTADOS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (e == null || level == null) return -1;
        float ahora = level.getGameTime() + pt;
        if (e.disparo() >= 0) {
            float t = ahora - e.disparo();
            int dur = Aliento.duracion(e.potencia());
            return t < dur ? 1.0F + Math.max(0, t) / dur : -1;
        }
        return Mth.clamp((ahora - e.carga()) / Aliento.CARGA_MAX, 0.0F, 0.999F);
    }

    /** Ticks del rayo ya disparados (para el latigazo de la animación), o −1. */
    public static float ticksDisparo(int id, float pt) {
        Estado e = ESTADOS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (e == null || level == null || e.disparo() < 0) return -1;
        return level.getGameTime() + pt - e.disparo();
    }

    /** Ticks desde el final del rayo (para salir de la animación suavemente), o −1. */
    public static float desdeFin(int id, float pt) {
        Estado e = ESTADOS.get(id);
        ClientLevel level = Minecraft.getInstance().level;
        if (e == null || level == null || e.disparo() < 0) return -1;
        float t = level.getGameTime() + pt - e.disparo() - Aliento.duracion(e.potencia());
        return t >= 0 ? t : -1;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            ESTADOS.clear();
            RUGIDOS.clear();
            return;
        }
        long ahora = level.getGameTime();
        ESTADOS.entrySet().removeIf(en -> level.getEntity(en.getKey()) == null
                || (en.getValue().disparo() >= 0 && ahora - en.getValue().disparo() > Aliento.duracion(en.getValue().potencia()) + 12)
                || (en.getValue().disparo() < 0 && ahora - en.getValue().carga() > Aliento.CARGA_AUTO + 40));
        // Rugido en bucle mientras dispara (se corta justo al acabar)
        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            if (fase(en.getKey(), 0) < 1.0F) continue;
            Rugido r = RUGIDOS.get(en.getKey());
            if (r == null || r.isStopped()) {
                Entity ent = level.getEntity(en.getKey());
                if (ent == null) continue;
                r = new Rugido(ent, en.getValue().potencia());
                RUGIDOS.put(en.getKey(), r);
                mc.getSoundManager().play(r);
            }
        }
        RUGIDOS.values().removeIf(Rugido::isStopped);

        // Al soltar Shift o el clic derecho se dispara con lo cargado
        if (manteniendo && mc.player != null) {
            boolean sigue = mc.options.keyUse.isDown() && mc.player.isShiftKeyDown() && mc.screen == null;
            if (!sigue) {
                manteniendo = false;
                ModNetwork.CHANNEL.sendToServer(new NucleoActivarPacket(false));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        ESTADOS.clear();
        RUGIDOS.clear();
        manteniendo = false;
    }

    // ------------------------------------------------------------------ activación

    @SubscribeEvent
    public static void onUsar(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND) return;
        Player p = Minecraft.getInstance().player;
        if (p == null || !p.isShiftKeyDown() || !p.getMainHandItem().isEmpty() || !NucleoItem.lleva(p)) return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (!manteniendo && fase(p.getId(), 0) < 0) {
            manteniendo = true;
            ModNetwork.CHANNEL.sendToServer(new NucleoActivarPacket(true));
        }
    }

    // ------------------------------------------------------------------ rayo

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ESTADOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack pose = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType cintas = RenderType.beaconBeam(TEX_RAYO, true);
        RenderType cuadros = RenderType.entityTranslucentEmissive(TEX_CUADRO);
        boolean algo = false;
        float tiempo = level.getGameTime() + pt;

        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            float f = fase(en.getKey(), pt);
            if (f < 1.0F || !(level.getEntity(en.getKey()) instanceof Player p)) continue;
            float u = f - 1.0F; // 0–1 a lo largo del rayo
            Vec3 o;
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                var look = e.getCamera().getLookVector();
                var up = e.getCamera().getUpVector();
                o = cam.add(look.x() * 1.3, look.y() * 1.3, look.z() * 1.3).add(up.x() * -0.55, up.y() * -0.55, up.z() * -0.55);
            } else {
                o = Aliento.boca(p, pt);
            }
            Vec3 d = p.getViewVector(pt);
            float pot = potencia(p.getId(), pt);
            double alc = Aliento.alcance(pot);
            int dur = Aliento.duracion(pot);
            var hit = level.clip(new ClipContext(o, o.add(d.scale(alc)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 fin = hit.getType() == HitResult.Type.MISS ? o.add(d.scale(alc)) : hit.getLocation();
            // sale disparado en 3 ticks y se apaga en los últimos 6
            float sale = Mth.clamp(u * dur / 3.0F, 0.05F, 1.0F);
            float apaga = Mth.clamp((1.0F - u) * dur / 6.0F, 0.0F, 1.0F);
            fin = o.add(fin.subtract(o).scale(sale));
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            dibujar(pose, buffers.getBuffer(cintas), o, fin, cam, tiempo, apaga * Aliento.grosor(pot), apaga, p.getId());
            pose.popPose();
            algo = true;
        }
        if (algo) buffers.endBatch(cintas);

        // Cuadros de error que corren por el rayo (otro tipo de render, otro lote)
        algo = false;
        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            float f = fase(en.getKey(), pt);
            if (f < 1.0F || !(level.getEntity(en.getKey()) instanceof Player p)) continue;
            Vec3 o = Aliento.boca(p, pt);
            Vec3 d = p.getViewVector(pt);
            float pot = potencia(p.getId(), pt);
            double alc = Aliento.alcance(pot);
            var hit = level.clip(new ClipContext(o, o.add(d.scale(alc)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            Vec3 fin = hit.getType() == HitResult.Type.MISS ? o.add(d.scale(alc)) : hit.getLocation();
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            cuadros(pose, buffers.getBuffer(cuadros), o, fin, cam, tiempo, p.getId(), Aliento.grosor(pot));
            pose.popPose();
            algo = true;
        }
        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            float f = fase(en.getKey(), pt);
            if (f < 0 || f >= 1.0F || !(level.getEntity(en.getKey()) instanceof Player p)) continue;
            Vec3 o = Aliento.boca(p, pt);
            if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
                var look = e.getCamera().getLookVector();
                var up = e.getCamera().getUpVector();
                o = cam.add(look.x() * 1.3, look.y() * 1.3, look.z() * 1.3).add(up.x() * -0.55, up.y() * -0.55, up.z() * -0.55);
            }
            pose.pushPose();
            pose.translate(-cam.x, -cam.y, -cam.z);
            bola(pose, buffers.getBuffer(cuadros), o, cam, tiempo, p.getId(), potencia(p.getId(), pt), f);
            pose.popPose();
            algo = true;
        }
        if (algo) buffers.endBatch(cuadros);
    }

    /** Mientras carga: un enjambre de cuadros de error que gira y crece delante de la boca. */
    private static void bola(PoseStack pose, VertexConsumer vc, Vec3 o, Vec3 cam, float t, int id, float pot, float f) {
        if (f < 0.1F) return;
        Matrix4f m = pose.last().pose();
        RandomSource r = RandomSource.create(id * 131L + (long) (t / 2));
        float radio = 0.12F + 0.55F * pot;
        int cuantos = 6 + (int) (30 * pot);
        for (int k = 0; k < cuantos; k++) {
            double a1 = r.nextDouble() * Math.PI * 2 + t * 0.3, a2 = (r.nextDouble() - 0.5) * Math.PI;
            double rr = radio * (0.4 + 0.6 * r.nextDouble());
            Vec3 c = o.add(Math.cos(a1) * Math.cos(a2) * rr, Math.sin(a2) * rr, Math.sin(a1) * Math.cos(a2) * rr);
            float tam = 0.03F + r.nextFloat() * (0.05F + 0.1F * pot);
            quad(vc, m, c, cam, tam, r);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 cam, float tam, RandomSource r) {
        Vec3 aCam = cam.subtract(c).normalize();
        Vec3 der = aCam.cross(new Vec3(0, 1, 0));
        der = der.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : der.normalize();
        Vec3 arr = der.cross(aCam).normalize();
        der = der.scale(tam);
        arr = arr.scale(tam);
        float u0 = r.nextInt(2) * 0.5F, v0 = r.nextInt(2) * 0.5F;
        v(vc, m, c.subtract(der).subtract(arr), u0, v0 + 0.5F, aCam);
        v(vc, m, c.add(der).subtract(arr), u0 + 0.5F, v0 + 0.5F, aCam);
        v(vc, m, c.add(der).add(arr), u0 + 0.5F, v0, aCam);
        v(vc, m, c.subtract(der).add(arr), u0, v0, aCam);
    }

    private static void dibujar(PoseStack pose, VertexConsumer vc, Vec3 o, Vec3 fin, Vec3 cam, float t, float escala, float apaga, int id) {
        double dist = o.distanceTo(fin);
        if (dist < 0.05) return;
        int n = Mth.clamp((int) (dist * 3), 8, 80);
        Vec3 eje = fin.subtract(o).normalize();
        Vec3 a = Math.abs(eje.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = eje.cross(a).normalize(), n2 = eje.cross(n1).normalize();
        RandomSource r = RandomSource.create(id * 31L + (long) t);
        float pulso = 0.85F + 0.15F * Mth.sin(t * 1.7F);
        float g = escala * pulso;

        Vec3[] base = new Vec3[n + 1];
        Vec3[] nervio = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            base[i] = o.add(eje.scale(dist * s));
            // el núcleo del rayo tiembla a saltos (glitch)
            double j = (i == 0) ? 0 : 0.05 * (r.nextDouble() - 0.5);
            nervio[i] = base[i].add(n1.scale(j)).add(n2.scale(0.05 * (r.nextDouble() - 0.5)));
        }
        // capas de fuera a dentro: humo negro, aura violeta, núcleo magenta casi blanco
        MedigunCliente.cinta(pose, vc, base, cam, 0.5F * g, 0.05F, 0.0F, 0.08F, 0.85F * apaga, t * -0.2F, dist);
        MedigunCliente.cinta(pose, vc, nervio, cam, 0.3F * g, 0.55F, 0.05F, 0.95F, 0.85F * apaga, t * -0.35F, dist);
        MedigunCliente.cinta(pose, vc, nervio, cam, 0.12F * g, 1.0F, 0.55F, 1.0F, 0.95F * apaga, t * -0.6F, dist);
        // dos espirales negras alrededor
        for (int k = 0; k < 2; k++) {
            Vec3[] esp = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n;
                double ang = s * dist * 2.2 - t * 0.8 + k * Math.PI;
                double rad = 0.32 * g * Math.min(1.0, s * dist / 1.5);
                esp[i] = base[i].add(n1.scale(Math.cos(ang) * rad)).add(n2.scale(Math.sin(ang) * rad));
            }
            MedigunCliente.cinta(pose, vc, esp, cam, 0.05F * g, 0.12F, 0.0F, 0.2F, 0.9F * apaga, 0, dist);
        }
    }

    /** Cuadraditos de damero magenta/negro que viajan por el rayo dando saltos. */
    private static void cuadros(PoseStack pose, VertexConsumer vc, Vec3 o, Vec3 fin, Vec3 cam, float t, int id, float escala) {
        double dist = o.distanceTo(fin);
        Vec3 eje = fin.subtract(o).normalize();
        Matrix4f m = pose.last().pose();
        RandomSource r = RandomSource.create(id * 7919L + (long) (t / 2));
        int cuantos = (int) Math.min(40 + 40 * escala, dist * 1.5 * escala);
        for (int k = 0; k < cuantos; k++) {
            double s = (r.nextDouble() + t * 0.08) % 1.0;
            double esp = 0.8 * escala;
            Vec3 c = o.add(eje.scale(dist * s)).add((r.nextDouble() - 0.5) * esp, (r.nextDouble() - 0.5) * esp, (r.nextDouble() - 0.5) * esp);
            float tam = (0.06F + r.nextFloat() * 0.14F) * (0.7F + 0.5F * escala);
            Vec3 aCam = cam.subtract(c).normalize();
            Vec3 der = aCam.cross(new Vec3(0, 1, 0));
            der = der.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : der.normalize();
            Vec3 arr = der.cross(aCam).normalize();
            der = der.scale(tam);
            arr = arr.scale(tam);
            float u0 = r.nextInt(2) * 0.5F, v0 = r.nextInt(2) * 0.5F;
            v(vc, m, c.subtract(der).subtract(arr), u0, v0 + 0.5F, aCam);
            v(vc, m, c.add(der).subtract(arr), u0 + 0.5F, v0 + 0.5F, aCam);
            v(vc, m, c.add(der).add(arr), u0 + 0.5F, v0, aCam);
            v(vc, m, c.subtract(der).add(arr), u0, v0, aCam);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float vv, Vec3 n) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(1.0F, 1.0F, 1.0F, 0.9F).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal((float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    // ------------------------------------------------------------------ cámara

    @SubscribeEvent
    public static void onCamara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ESTADOS.isEmpty()) return;
        float pt = (float) e.getPartialTick();
        float t = mc.player.tickCount + pt;
        float fuerza = 0;
        float f = fase(mc.player.getId(), pt);
        float pot = potencia(mc.player.getId(), pt);
        if (f >= 0 && f < 1) fuerza = f * f * 1.4F;           // carga: tiembla cada vez más
        else if (f >= 1) fuerza = 1.0F + 2.4F * pot;          // disparo: más grande, más temblor
        // los que están cerca de un aliento también lo notan
        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            if (en.getKey() == mc.player.getId() || fase(en.getKey(), pt) < 1) continue;
            Entity otro = mc.level.getEntity(en.getKey());
            if (otro != null) fuerza = Math.max(fuerza, (float) Mth.clamp((1.2 - otro.distanceTo(mc.player) / 25.0) * (0.6 + potencia(en.getKey(), pt)), 0, 2.0));
        }
        if (fuerza <= 0) return;
        e.setPitch(e.getPitch() + (Mth.sin(t * 2.9F) + Mth.sin(t * 7.1F) * 0.4F) * fuerza * 0.5F);
        e.setYaw(e.getYaw() + Mth.sin(t * 3.7F) * fuerza * 0.4F);
        e.setRoll(e.getRoll() + Mth.sin(t * 5.3F) * fuerza * 0.6F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float f = fase(mc.player.getId(), (float) e.getPartialTick());
        if (f < 0) return;
        e.setFOV(e.getFOV() + (f < 1 ? -6.0 * f : 4.0 + 8.0 * potencia(mc.player.getId(), (float) e.getPartialTick())));
    }

    // ------------------------------------------------------------------ pantalla

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui) return;
        float f = fase(p.getId(), partial);
        RandomSource r = RandomSource.create((long) ((p.tickCount + partial) / 2) * 31L);
        RenderSystem.enableBlend();
        if (f >= 0) {
            // cuadros de error que invaden los bordes; con el rayo, desgarros horizontales por toda la pantalla
            float k = f < 1 ? f : 1.0F;
            int cuantos = (int) (k * 60);
            for (int i = 0; i < cuantos; i++) {
                int tam = 4 + r.nextInt(10);
                int x, y;
                if (r.nextBoolean()) {
                    x = r.nextBoolean() ? r.nextInt((int) (w * 0.12F * k) + 1) : w - r.nextInt((int) (w * 0.12F * k) + 1) - tam;
                    y = r.nextInt(h);
                } else {
                    x = r.nextInt(w);
                    y = r.nextBoolean() ? r.nextInt((int) (h * 0.12F * k) + 1) : h - r.nextInt((int) (h * 0.12F * k) + 1) - tam;
                }
                int col = r.nextBoolean() ? 0xC0F800F8 : 0xC0000000;
                g.fill(x, y, x + tam, y + tam, col);
            }
            if (f < 1) {
                // carga: barra de damero bajo la mira; parpadea al llegar al máximo
                float pot = potencia(p.getId(), partial);
                float cargado = Math.min(1.0F, f / 0.999F);
                int bw = 60, bx = w / 2 - bw / 2, by = h / 2 + 14;
                g.fill(bx - 1, by - 1, bx + bw + 1, by + 6, 0xB0000000);
                int lleno = Math.round(bw * cargado);
                for (int xx = 0; xx < lleno; xx += 3) {
                    boolean mm = (xx / 3 + (p.tickCount / 2)) % 2 == 0;
                    g.fill(bx + xx, by, bx + Math.min(lleno, xx + 3), by + 5, mm ? 0xFFF800F8 : 0xFF200030);
                }
                int min = Math.round(bw * Aliento.CARGA_MIN / (float) Aliento.CARGA_MAX);
                g.fill(bx + min, by - 2, bx + min + 1, by + 7, 0xFFFFFFFF);   // marca de carga mínima
                if (pot >= 1.0F && (p.tickCount / 3) % 2 == 0) g.fill(bx - 2, by - 2, bx + bw + 2, by + 7, 0x40FFFFFF);
            }
            if (f >= 1) {
                g.fill(0, 0, w, h, 0x30300050);
                for (int i = 0; i < 6; i++) {
                    int y = r.nextInt(h), alto = 1 + r.nextInt(4);
                    g.fill(0, y, w, y + alto, r.nextBoolean() ? 0x50F800F8 : 0x5000FFFF);
                }
            }
        }
        // indicador de enfriamiento (sin texto): cuadrito de damero que se llena
        if (NucleoItem.lleva(p) && mc.level != null) {
            long falta = enfriaHasta - mc.level.getGameTime();
            if (falta > 0 && f < 0) {
                float lleno = 1.0F - falta / (float) enfriaTotal;
                int x = 8, y = h - 26, s = 16;
                g.fill(x - 1, y - 1, x + s + 1, y + s + 1, 0xA0000000);
                int alto = Math.round(s * Mth.clamp(lleno, 0, 1));
                for (int yy = 0; yy < alto; yy += 4) {
                    for (int xx = 0; xx < s; xx += 4) {
                        boolean m = ((xx + yy) / 4) % 2 == 0;
                        g.fill(x + xx, y + s - yy - Math.min(4, alto - yy), x + xx + 4, y + s - yy, m ? 0xFFF800F8 : 0xFF101010);
                    }
                }
            }
        }
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ sonido

    private static final class Rugido extends AbstractTickableSoundInstance {
        private final Entity quien;

        Rugido(Entity quien, float pot) {
            super(ModSounds.NUCLEO_RAYO.get(), SoundSource.PLAYERS, RandomSource.create());
            this.quien = quien;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.7F + 0.6F * pot;
            this.pitch = 1.25F - 0.5F * pot;
            this.x = quien.getX();
            this.y = quien.getY() + 1.5;
            this.z = quien.getZ();
        }

        @Override
        public void tick() {
            if (quien.isRemoved() || fase(quien.getId(), 0) < 1.0F) {
                stop();
                return;
            }
            x = quien.getX();
            y = quien.getY() + 1.5;
            z = quien.getZ();
        }
    }
}
