package com.miscelanea.client.medigun;

import com.miscelanea.Miscelanea;
import com.miscelanea.medigun.Medigun;
import com.miscelanea.medigun.MedigunItem;
import com.miscelanea.registry.ModEffects;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Cliente de la medigun: estado de cada rayo (lo manda el servidor), el rayo ondulante en 3D,
 * el sonido en bucle y el HUD (barra de ÜberCarga y vida del paciente, sin texto).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MedigunCliente {
    private static final ResourceLocation TEX_RAYO = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/rayo_medico.png");

    private record Rayo(int paciente, long uberHasta, int color, long desde) {}

    private static final Map<Integer, Rayo> RAYOS = new HashMap<>();
    private static final Map<Integer, RayoSonido> SONIDOS = new HashMap<>();

    private MedigunCliente() {}

    // ------------------------------------------------------------------ estado

    public static void recibir(int medico, int paciente, int uber, int color) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        if (paciente < 0 && uber <= 0) {
            RAYOS.remove(medico);
        } else {
            Rayo antes = RAYOS.get(medico);
            long desde = antes != null && antes.paciente() == paciente ? antes.desde() : level.getGameTime();
            RAYOS.put(medico, new Rayo(paciente, level.getGameTime() + uber, color, desde));
        }
        RayoSonido s = SONIDOS.get(medico);
        if (paciente >= 0 && (s == null || s.isStopped())) {
            Entity e = level.getEntity(medico);
            if (e != null) {
                RayoSonido nuevo = new RayoSonido(e);
                SONIDOS.put(medico, nuevo);
                Minecraft.getInstance().getSoundManager().play(nuevo);
            }
        }
    }

    /** ¿Sigue sonando el rayo de este médico? (lo consulta el sonido en bucle) */
    static boolean curando(int medico) {
        Rayo r = RAYOS.get(medico);
        return r != null && r.paciente() >= 0;
    }

    /** Color de la ÜberCarga que protege a esta entidad, o -1. */
    public static int colorUber(Entity e) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || RAYOS.isEmpty()) return -1;
        long t = level.getGameTime();
        for (Map.Entry<Integer, Rayo> en : RAYOS.entrySet()) {
            Rayo r = en.getValue();
            if (t < r.uberHasta() && (en.getKey() == e.getId() || r.paciente() == e.getId())) return r.color();
        }
        return -1;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            RAYOS.clear();
            return;
        }
        long t = level.getGameTime();
        RAYOS.entrySet().removeIf(en -> level.getEntity(en.getKey()) == null
                || (en.getValue().paciente() < 0 && t >= en.getValue().uberHasta()));
        SONIDOS.entrySet().removeIf(en -> en.getValue().isStopped());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RAYOS.clear();
        SONIDOS.clear();
    }

    // ------------------------------------------------------------------ rayo

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || RAYOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = event.getPartialTick();
        Camera cam = event.getCamera();
        Vec3 camPos = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType tipo = RenderType.beaconBeam(TEX_RAYO, true);
        float tiempo = level.getGameTime() + pt;

        boolean dibujado = false;
        for (Map.Entry<Integer, Rayo> en : RAYOS.entrySet()) {
            Rayo r = en.getValue();
            if (r.paciente() < 0) continue;
            Entity medico = level.getEntity(en.getKey());
            Entity paciente = level.getEntity(r.paciente());
            if (!(medico instanceof Player pm) || paciente == null) continue;

            Vec3 ini = inicio(pm, pt, cam);
            Vec3 fin = paciente.getPosition(pt).add(0, paciente.getBbHeight() * 0.55, 0);
            Vec3 mira = pm.getViewVector(pt);
            pose.pushPose();
            pose.translate(-camPos.x, -camPos.y, -camPos.z);
            // al conectar, el rayo sale disparado hasta el paciente en 4 ticks
            float alcance = Mth.clamp((tiempo - r.desde()) / 4.0F, 0.05F, 1.0F);
            dibujarRayo(pose, buffers.getBuffer(tipo), ini, fin, mira, camPos, r.color(), tiempo,
                    tiempo < r.uberHasta(), alcance);
            pose.popPose();
            dibujado = true;
        }
        if (dibujado) buffers.endBatch(tipo);
    }

    /** Boca de la medigun en el mundo. */
    private static Vec3 inicio(Player p, float pt, Camera cam) {
        Minecraft mc = Minecraft.getInstance();
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            // Primera persona: abajo a la derecha de la vista, delante de la cámara
            Vec3 ojo = cam.getPosition();
            Vector3f f = cam.getLookVector(), u = cam.getUpVector(), l = cam.getLeftVector();
            boolean diestro = p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT
                    ^ !(p.getMainHandItem().getItem() instanceof MedigunItem);
            float lado = diestro ? -1.0F : 1.0F;
            return ojo.add(f.x() * 0.95, f.y() * 0.95, f.z() * 0.95)
                    .add(l.x() * 0.32 * lado, l.y() * 0.32 * lado, l.z() * 0.32 * lado)
                    .add(u.x() * -0.2, u.y() * -0.2, u.z() * -0.2);
        }
        Vec3 boca = MedigunLayer.boca(p.getId());
        if (boca != null) return boca;
        return p.getEyePosition(pt).add(p.getViewVector(pt).scale(0.8)).add(0, -0.45, 0);
    }

    private static void dibujarRayo(PoseStack pose, VertexConsumer vc, Vec3 ini, Vec3 fin, Vec3 mira, Vec3 cam,
                                    int color, float t, boolean uber, float alcance) {
        double dist = ini.distanceTo(fin) * alcance;
        if (dist < 0.05) return;
        // Curva: sale recto hacia donde miras y se dobla hasta el paciente
        Vec3 ctrl = ini.add(mira.scale(dist * 0.5));
        Vec3 eje = fin.subtract(ini).normalize();
        Vec3 a = Math.abs(eje.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 n1 = eje.cross(a).normalize(), n2 = eje.cross(n1).normalize();

        int n = Mth.clamp((int) (dist * 6), 12, 64);
        Vec3[] base = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            double s = alcance * i / (double) n, u = 1 - s;
            base[i] = ini.scale(u * u).add(ctrl.scale(2 * u * s)).add(fin.scale(s * s));
        }
        float r = ((color >> 16) & 255) / 255.0F, g = ((color >> 8) & 255) / 255.0F, b = (color & 255) / 255.0F;
        float grosor = uber ? 1.35F : 1.0F;

        // Halo ondulante
        cinta(pose, vc, ondular(base, n1, n2, t, 0.07, 1.0, 0.0), cam, 0.13F * grosor, r, g, b, 0.55F, t * -0.08F, dist);
        // Dos hilos en espiral
        for (int k = 0; k < 2; k++) {
            Vec3[] hilo = new Vec3[n + 1];
            for (int i = 0; i <= n; i++) {
                double s = i / (double) n;
                double env = Math.sin(Math.PI * s);
                double ang = s * dist * 4.0 - t * 0.45 + k * Math.PI;
                double rad = 0.09 * env * grosor;
                hilo[i] = base[i].add(n1.scale(Math.cos(ang) * rad)).add(n2.scale(Math.sin(ang) * rad));
            }
            cinta(pose, vc, hilo, cam, 0.025F * grosor, Math.min(1, r + 0.35F), Math.min(1, g + 0.35F), Math.min(1, b + 0.35F),
                    0.8F, t * -0.15F + k * 0.5F, dist);
        }
        // Núcleo brillante
        cinta(pose, vc, ondular(base, n1, n2, t, 0.03, 1.7, 1.1), cam, 0.04F * grosor,
                Mth.lerp(0.6F, r, 1), Mth.lerp(0.6F, g, 1), Mth.lerp(0.6F, b, 1), 0.95F, t * -0.25F, dist);
    }

    private static Vec3[] ondular(Vec3[] base, Vec3 n1, Vec3 n2, float t, double amp, double frec, double fase) {
        Vec3[] out = new Vec3[base.length];
        int n = base.length - 1;
        for (int i = 0; i <= n; i++) {
            double s = i / (double) n;
            double env = Math.sin(Math.PI * s);
            double o1 = Math.sin(s * 14 * frec - t * 0.35 + fase) * amp * env;
            double o2 = Math.cos(s * 10 * frec - t * 0.27 + fase) * amp * env;
            out[i] = base[i].add(n1.scale(o1)).add(n2.scale(o2));
        }
        return out;
    }

    /** Tira de quads siempre de cara a la cámara, con la textura corriendo a lo largo. */
    public static void cinta(PoseStack pose, VertexConsumer vc, Vec3[] p, Vec3 cam, float ancho,
                              float r, float g, float b, float alpha, float vOff, double largo) {
        Matrix4f m = pose.last().pose();
        int n = p.length - 1;
        Vec3[] izq = new Vec3[n + 1], der = new Vec3[n + 1];
        for (int i = 0; i <= n; i++) {
            Vec3 tan = p[Math.min(i + 1, n)].subtract(p[Math.max(i - 1, 0)]);
            Vec3 aCam = cam.subtract(p[i]);
            Vec3 lado = tan.cross(aCam);
            if (lado.lengthSqr() < 1.0E-8) lado = new Vec3(0, 1, 0);
            lado = lado.normalize().scale(ancho);
            izq[i] = p[i].subtract(lado);
            der[i] = p[i].add(lado);
        }
        float rep = (float) Math.max(1.0, largo / 1.5);
        for (int i = 0; i < n; i++) {
            float v0 = vOff + rep * i / n, v1 = vOff + rep * (i + 1) / n;
            // se desvanece en los extremos
            float a0 = alpha * Math.min(1.0F, Math.min(i, n - i) / 2.0F + 0.25F);
            float a1 = alpha * Math.min(1.0F, Math.min(i + 1, n - i - 1) / 2.0F + 0.25F);
            v(vc, m, izq[i], 0, v0, r, g, b, a0);
            v(vc, m, der[i], 1, v0, r, g, b, a0);
            v(vc, m, der[i + 1], 1, v1, r, g, b, a1);
            v(vc, m, izq[i + 1], 0, v1, r, g, b, a1);
            // cara de atrás (por si se dibuja con culling)
            v(vc, m, izq[i + 1], 0, v1, r, g, b, a1);
            v(vc, m, der[i + 1], 1, v1, r, g, b, a1);
            v(vc, m, der[i], 1, v0, r, g, b, a0);
            v(vc, m, izq[i], 0, v0, r, g, b, a0);
        }
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float u, float vv, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, vv).uv2(0xF000F0).normal(0, 1, 0).endVertex();
    }

    // ------------------------------------------------------------------ HUD

    public static void render(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.player;
        if (p == null || mc.options.hideGui) return;

        // Tinte de pantalla mientras eres invulnerable
        int cu = colorUber(p);
        if (cu >= 0 || p.hasEffect(ModEffects.UBERCARGA.get())) {
            int c = cu >= 0 ? cu : Medigun.COLOR_DEFECTO;
            float lat = 0.5F + 0.5F * Mth.sin((p.tickCount + partial) * 0.3F);
            RenderSystem.enableBlend();
            for (int i = 0; i < 10; i++) {
                int a = (int) ((0x30 + lat * 0x20) * (1 - i / 10.0F));
                int m = i * Math.max(3, w / 110);
                int col = (a << 24) | c;
                g.fill(0, 0, w, Math.max(1, m), col);
                g.fill(0, h - Math.max(1, m), w, h, col);
                g.fill(0, 0, Math.max(1, m), h, col);
                g.fill(w - Math.max(1, m), 0, w, h, col);
            }
            RenderSystem.disableBlend();
        }

        ItemStack gun = p.getMainHandItem().getItem() instanceof MedigunItem ? p.getMainHandItem()
                : p.getOffhandItem().getItem() instanceof MedigunItem ? p.getOffhandItem() : ItemStack.EMPTY;
        if (gun.isEmpty()) return;
        int color = Medigun.color(gun);
        float carga = Medigun.carga(gun) / Medigun.MAX;
        boolean lista = carga >= 1.0F && !Medigun.uberActiva(gun);
        float t = p.tickCount + partial;

        // Panel de ÜberCarga abajo a la derecha: cruz + barra
        int pw = 96, ph = 18;
        int x0 = w - pw - 8, y0 = h - ph - 8;
        g.fill(x0, y0, x0 + pw, y0 + ph, 0x90101418);
        g.fill(x0, y0, x0 + pw, y0 + 1, 0x60FFFFFF);
        // cruz
        int cx = x0 + 9, cy = y0 + ph / 2;
        g.fill(cx - 5, cy - 2, cx + 6, cy + 3, 0xFFF2F2F2);
        g.fill(cx - 2, cy - 5, cx + 3, cy + 6, 0xFFF2F2F2);
        // barra
        int bx = x0 + 20, by = y0 + 5, bw = pw - 26, bh = ph - 10;
        g.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF000000);
        g.fill(bx, by, bx + bw, by + bh, 0xFF2A2E34);
        int lleno = Math.round(bw * Math.min(1.0F, carga));
        int c = color;
        if (lista) c = Medigun.mezclar(color, 0xFFFFFF, 0.35F + 0.35F * Mth.sin(t * 0.25F));
        g.fill(bx, by, bx + lleno, by + bh, 0xFF000000 | c);
        g.fill(bx, by, bx + lleno, by + 2, 0x55FFFFFF);
        if (Medigun.uberActiva(gun)) {
            // franjas que corren mientras se gasta
            for (int x = bx + (int) (t * 1.5F) % 6 - 6; x < bx + lleno; x += 6) {
                int xa = Math.max(bx, x), xb = Math.min(bx + lleno, x + 2);
                if (xb > xa) g.fill(xa, by, xb, by + bh, 0x50FFFFFF);
            }
        }
        for (int k = 1; k < 4; k++) g.fill(bx + bw * k / 4, by, bx + bw * k / 4 + 1, by + bh, 0x40000000);

        // Vida del paciente bajo la mira
        Rayo r = RAYOS.get(p.getId());
        if (r != null && r.paciente() >= 0 && mc.level.getEntity(r.paciente()) instanceof LivingEntity pac) {
            int vw = 50, vx = w / 2 - vw / 2, vy = h / 2 + 12;
            float vida = Mth.clamp(pac.getHealth() / pac.getMaxHealth(), 0, 1);
            float extra = Mth.clamp(pac.getAbsorptionAmount() / pac.getMaxHealth(), 0, 1);
            g.fill(vx - 1, vy - 1, vx + vw + 1, vy + 4, 0xA0000000);
            g.fill(vx, vy, vx + Math.round(vw * vida), vy + 3, vida < 0.35F ? 0xFFE04A3A : 0xFF7CE07A);
            if (extra > 0) g.fill(vx, vy - 3, vx + Math.round(vw * extra), vy - 1, 0xFF000000 | Medigun.mezclar(color, 0xFFFFFF, 0.5F));
        }
    }
}
