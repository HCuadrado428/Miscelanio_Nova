package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PalmadaPacket;
import com.miscelanea.pociones.PocionesRed;
import com.miscelanea.pociones.PocionesRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ecolocalización (Daredevil / el warden) en el cliente de quien la lleva. Todo se ve negro (niebla negra pegada a la
 * cara; el HUD se sigue viendo) y cada sonido que suena cerca (tus pasos, los mobs, los bloques) lanza desde su origen
 * una onda esférica que, al pasar por los bloques y las entidades, los deja un momento dibujados como aristas y
 * siluetas azul-blancas (como el sculk). Agacharse da una palmada (sonar propio) cada 2 s como mucho. Los demás
 * jugadores NO salen con las ondas: con Simple Voice Chat se ven solo mientras hablan (su silueta, fija mientras
 * hablan y que se apaga poco a poco al callarse, y una onda que sale de su boca) y tu propia voz también lanza ondas.
 * Rendimiento: ondas de 16 bloques, se revelan bloques hasta 13 y solo las caras expuestas (cada bloque se mira una
 * sola vez: cuando el frente de la onda pasa por él), con tope de aristas vivas.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class EcoCliente {
    private EcoCliente() {}

    private static final ResourceLocation BLANCO = new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/blanco.png");
    /** Para las capas de las siluetas que no se dibujan. */
    private static final VertexConsumer NADA = new Nada();
    private static final float VEL = 0.55F, RADIO_MAX = 16F, RADIO_REVELA = 13F;
    private static final int DURA_BLOQUE = 30, DURA_ENTE = 32, MAX_BLOQUES = 4500, MAX_ONDAS = 20, ENFRIAMIENTO = 40;
    private static final float CR = 0.55F, CG = 0.86F, CB = 1.0F;

    private static final class Onda {
        final double x, y, z;
        float r, rAntes;

        Onda(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private record Bloque(AABB caja, int desde) {}

    /** Entidad que se ve: desde cuándo (para que se vaya apagando) y cuándo empezó a verse (para encenderse suave). */
    private record Ente(Entity e, int desde, int aparecio) {}

    /** La deja vista: si ya se veía, sigue encendida sin volver a dar el fogonazo (así no parpadea). */
    private static void ver(Entity en) {
        Ente antes = ENTES.get(en.getId());
        boolean seVeia = antes != null && reloj - antes.desde() < DURA_ENTE - 4;
        ENTES.put(en.getId(), new Ente(en, reloj, seVeia ? antes.aparecio() : reloj));
    }

    /** Opacidad de una entidad vista: se enciende en 3 ticks y se apaga poco a poco. */
    private static float alfa(Ente en, float pt) {
        float sube = Mth.clamp((reloj - en.aparecio() + pt) / 3F, 0F, 1F);
        float baja = Mth.clamp(1F - (reloj - en.desde() + pt) / DURA_ENTE, 0F, 1F);
        return Math.min(sube, baja);
    }

    private static final List<Onda> ONDAS = new ArrayList<>();
    private static final LinkedHashMap<Long, Bloque> BLOQUES = new LinkedHashMap<>();
    private static final Map<Integer, Ente> ENTES = new HashMap<>();
    private static int reloj, enfriamiento;
    /** Quién está hablando (voz del voicechat) y cuándo se le oyó por última vez (ms). Lo rellena el hilo de audio. */
    private static final Map<java.util.UUID, Long> HABLAN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<java.util.UUID, Integer> ULT_ONDA = new HashMap<>();
    private static volatile long yoHablo;
    private static int ultOndaYo;
    /** Volumen mínimo para que cuente como hablar (no el ruido de fondo del micro). */
    private static final float UMBRAL_VOZ = 0.03F;   // por debajo es ruido de fondo del micro, no voz

    /** Desde el plugin del voicechat (hilo de audio): alguien habla con este volumen. */
    public static void habla(java.util.UUID quien, float nivel) {
        if (quien != null && nivel > UMBRAL_VOZ) HABLAN.put(quien, System.currentTimeMillis());
    }

    /** ¿Está hablando ahora (se le ha oído hace menos de un tercio de segundo)? Lo usa también el móvil en las llamadas. */
    public static boolean hablando(java.util.UUID quien) {
        Long t = quien == null ? null : HABLAN.get(quien);
        return t != null && System.currentTimeMillis() - t < 350;
    }

    public static boolean yoHablando() {
        return System.currentTimeMillis() - yoHablo < 350;
    }

    /** Desde el plugin del voicechat (hilo de audio): hablo yo. */
    public static void hablaYo(float nivel) {
        if (nivel > UMBRAL_VOZ) yoHablo = System.currentTimeMillis();
    }
    private static boolean agachadoAntes, estaba;
    /** El nivel donde están las ondas y lo visto (no se usa para nada más; se suelta al salir). */
    private static ClientLevel nivel;

    public static boolean activo() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p != null && p.hasEffect(PocionesRegistro.ECOLOCALIZACION.get());
    }

    // ------------------------------------------------------------------ cada sonido es una onda
    @SubscribeEvent
    public static void sonido(PlaySoundEvent e) {
        if (!activo()) return;
        SoundInstance s = e.getSound();
        Minecraft mc = Minecraft.getInstance();
        if (s == null || s.isRelative() || mc.player == null) return;
        oir(mc, s.getX(), s.getY(), s.getZ());
    }

    /** Un ruido en (x, y, z): si está cerca, una onda (si ya sale otra del mismo sitio, no). */
    private static void oir(Minecraft mc, double x, double y, double z) {
        if (mc.player.distanceToSqr(x, y, z) > (RADIO_MAX + 6) * (RADIO_MAX + 6)) return;
        // varios sonidos a la vez en el mismo sitio (un paso y su bloque): una sola onda
        for (Onda o : ONDAS)
            if (o.r < VEL * 3 && Math.abs(o.x - x) + Math.abs(o.y - y) + Math.abs(o.z - z) < 0.8) return;
        if (ONDAS.size() >= MAX_ONDAS) ONDAS.remove(0);
        ONDAS.add(new Onda(x, y, z));
    }

    // ------------------------------------------------------------------ tick: avanzan las ondas y revelan lo que tocan
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        long ahoraMs = System.currentTimeMillis();
        HABLAN.values().removeIf(t -> ahoraMs - t > 4000);
        // al salir del mundo, cambiar de dimensión o reaparecer: lo de antes era de otro mundo (entidades de otro nivel,
        // con ids que en el nuevo son de otras; bloques y ondas en sitios que aquí no son). Fuera, y sin quedarse con
        // el nivel viejo en memoria
        if (mc.level != nivel) {
            if (estaba) limpiar();
            nivel = mc.level;
        }
        if (mc.level == null || mc.player == null) return;
        if (!activo()) {
            if (estaba) limpiar();
            return;
        }
        estaba = true;
        if (mc.isPaused()) return;
        reloj++;
        if (enfriamiento > 0) enfriamiento--;
        boolean agachado = mc.options.keyShift.isDown() && mc.screen == null;
        if (agachado && !agachadoAntes && enfriamiento == 0) {
            enfriamiento = ENFRIAMIENTO;
            PocionesRed.CANAL.sendToServer(new PalmadaPacket());
        }
        agachadoAntes = agachado;

        for (Iterator<Onda> it = ONDAS.iterator(); it.hasNext(); ) {
            Onda o = it.next();
            o.rAntes = o.r;
            o.r += VEL;
            if (o.rAntes < RADIO_REVELA) revelar(mc.level, o, o.rAntes, Math.min(o.r, RADIO_REVELA));
            if (o.r > RADIO_MAX) it.remove();
        }
        voces(mc);
        BLOQUES.values().removeIf(b -> reloj - b.desde() > DURA_BLOQUE);
        ENTES.values().removeIf(en -> reloj - en.desde() > DURA_ENTE || en.e().isRemoved());
    }

    /** Los que hablan se ven mientras hablan, y cada medio segundo de voz suelta una onda desde su boca. */
    private static void voces(Minecraft mc) {
        long ahora = System.currentTimeMillis();
        for (Map.Entry<java.util.UUID, Long> h : HABLAN.entrySet()) {
            if (ahora - h.getValue() > 350) continue;          // ya no habla
            net.minecraft.world.entity.player.Player p = mc.level.getPlayerByUUID(h.getKey());
            if (p == null || p == mc.player || p.distanceToSqr(mc.player) > 48 * 48) continue;
            ver(p);
            Integer u = ULT_ONDA.get(h.getKey());
            if (u == null || reloj - u >= 10) {
                ULT_ONDA.put(h.getKey(), reloj);
                onda(p.getX(), p.getEyeY(), p.getZ());
            }
        }
        if (ahora - yoHablo < 350 && reloj - ultOndaYo >= 12) {
            ultOndaYo = reloj;
            onda(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        }
    }

    private static void onda(double x, double y, double z) {
        if (ONDAS.size() >= MAX_ONDAS) ONDAS.remove(0);
        ONDAS.add(new Onda(x, y, z));
    }

    private static void limpiar() {
        ONDAS.clear();
        BLOQUES.clear();
        ENTES.clear();
        ULT_ONDA.clear();
        estaba = false;
        agachadoAntes = false;
    }

    /** Los bloques (caras expuestas) y las entidades cuyo centro queda entre r0 y r1 del origen. */
    private static void revelar(ClientLevel l, Onda o, float r0, float r1) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int alcance = Mth.ceil(r1);
        int bx0 = Mth.floor(o.x), by0 = Mth.floor(o.y), bz0 = Mth.floor(o.z);
        double r0c = r0 * r0, r1c = r1 * r1;
        for (int dx = -alcance; dx <= alcance; dx++) {
            double a = (bx0 + dx + 0.5 - o.x);
            a *= a;
            if (a > r1c) continue;
            for (int dy = -alcance; dy <= alcance; dy++) {
                double b = (by0 + dy + 0.5 - o.y);
                b *= b;
                double rem1 = r1c - a - b;
                if (rem1 < 0) continue;
                double rem0 = r0c - a - b;
                double zmax = Math.sqrt(rem1), zmin = rem0 > 0 ? Math.sqrt(rem0) : 0;
                // dos tramos en z: [-zmax, -zmin] y [zmin, zmax] (medidos desde el origen de la onda)
                for (int lado = -1; lado <= 1; lado += 2) {
                    double z0 = o.z + lado * zmin, z1 = o.z + lado * zmax;
                    int za = Mth.floor(Math.min(z0, z1) - 0.5), zb = Mth.ceil(Math.max(z0, z1) - 0.5);
                    for (int bz = za; bz <= zb; bz++) {
                        double c = (bz + 0.5 - o.z);
                        c *= c;
                        double d = a + b + c;
                        if (d <= r0c || d > r1c) continue;
                        m.set(bx0 + dx, by0 + dy, bz);
                        revelarBloque(l, m);
                    }
                    if (zmin == 0) break;   // los dos tramos son el mismo
                }
            }
        }
        // entidades
        Minecraft mc = Minecraft.getInstance();
        AABB zona = new AABB(o.x - r1, o.y - r1, o.z - r1, o.x + r1, o.y + r1, o.z + r1);
        for (Entity en : l.getEntities(mc.player, zona)) {
            Vec3 c = en.position().add(0, en.getBbHeight() * 0.5, 0);
            double d = c.distanceTo(new Vec3(o.x, o.y, o.z));
            // los jugadores solo se ven cuando hablan (si no, cada paso o ruido los haría parpadear)
            if (en instanceof net.minecraft.world.entity.player.Player) continue;
            if (d > r0 && d <= r1) ver(en);
        }
    }

    private static void revelarBloque(ClientLevel l, BlockPos.MutableBlockPos m) {
        BlockState st = l.getBlockState(m);
        if (st.isAir()) return;
        VoxelShape forma = st.getShape(l, m);
        if (forma.isEmpty()) return;
        boolean expuesto = false;
        for (Direction dir : Direction.values()) {
            BlockPos v = m.relative(dir);
            if (!l.getBlockState(v).isSolidRender(l, v)) {
                expuesto = true;
                break;
            }
        }
        if (!expuesto) return;
        long clave = m.asLong();
        if (BLOQUES.size() >= MAX_BLOQUES && !BLOQUES.containsKey(clave)) {
            Iterator<Long> it = BLOQUES.keySet().iterator();
            it.next();
            it.remove();
        }
        BLOQUES.put(clave, new Bloque(forma.bounds().move(m.immutable()), reloj));
    }

    /**
     * A ciegas no hay nombres: el de los jugadores se dibuja sin niebla (se ve a través de todo) y delataba dónde está
     * cada uno aunque no hablara; y en la silueta salía como un borrón.
     */
    @SubscribeEvent
    public static void nombre(net.minecraftforge.client.event.RenderNameTagEvent e) {
        if (activo()) e.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    /** El cielo (sol, luna y estrellas no tienen niebla) no se ve, como con ceguera. Lo pregunta LevelRendererMixin. */
    public static boolean tapaCielo() {
        return activo();
    }

    // ------------------------------------------------------------------ todo negro
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void niebla(ViewportEvent.RenderFog e) {
        if (!activo()) return;
        e.setNearPlaneDistance(0F);
        e.setFarPlaneDistance(0.02F);
        e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        if (!activo()) return;
        e.setRed(0F);
        e.setGreen(0F);
        e.setBlue(0F);
    }

    // ------------------------------------------------------------------ dibujar ondas, aristas y siluetas (sin niebla)
    @SubscribeEvent
    public static void dibujar(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || !activo()) return;
        if (ONDAS.isEmpty() && BLOQUES.isEmpty() && ENTES.isEmpty()) return;
        // con shaders, Oculus vuelve a lanzar esta etapa al dibujar el mapa de sombras: ahí no (ondas y siluetas
        // salían como sombras y se dibujaba todo dos veces)
        if (pasadaSombras()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        float nieblaIni = RenderSystem.getShaderFogStart(), nieblaFin = RenderSystem.getShaderFogEnd();
        RenderSystem.setShaderFogStart(1.0E6F);
        RenderSystem.setShaderFogEnd(1.0E6F + 1F);
        try {
            ps.pushPose();
            ps.translate(-cam.x, -cam.y, -cam.z);
            VertexConsumer vc = bs.getBuffer(RenderType.lines());
            for (Bloque b : BLOQUES.values()) {
                float edad = reloj - b.desde() + pt;
                float a = edad < 4 ? 1F : Mth.clamp(1F - (edad - 4) / (DURA_BLOQUE - 4), 0F, 1F);
                a = a * a;
                float k = edad < 4 ? 1F : 0.85F;
                LevelRenderer.renderLineBox(ps, vc, b.caja(), CR * k, CG * k, CB, a);
            }
            for (Ente en : ENTES.values()) {
                Entity ent = en.e();
                float a = alfa(en, pt);
                // donde se dibuja la silueta (interpolada): si no, la caja va un tick por delante de la entidad
                AABB caja = ent.getBoundingBox().move(Mth.lerp(pt, ent.xOld, ent.getX()) - ent.getX(),
                        Mth.lerp(pt, ent.yOld, ent.getY()) - ent.getY(), Mth.lerp(pt, ent.zOld, ent.getZ()) - ent.getZ());
                LevelRenderer.renderLineBox(ps, vc, caja, 0.8F, 0.95F, 1F, a);
            }
            Matrix4f m4 = ps.last().pose();
            Matrix3f m3 = ps.last().normal();
            for (Onda o : ONDAS) {
                float r = Mth.lerp(pt, o.rAntes, o.r);
                float a = Mth.clamp(1F - r / RADIO_MAX, 0F, 1F) * 0.9F;
                for (int plano = 0; plano < 3; plano++) circulo(vc, m4, m3, o.x, o.y, o.z, r, plano, a);
            }
            ps.popPose();
            bs.endBatch(RenderType.lines());
            // siluetas: la entidad entera, en un solo color brillante sobre una textura blanca
            if (!ENTES.isEmpty()) {
                EntityRenderDispatcher rd = mc.getEntityRenderDispatcher();
                RenderType silueta = RenderType.entityTranslucentEmissive(BLANCO);
                rd.setRenderShadow(false);
                try {
                    for (Ente en : ENTES.values()) {
                        Entity ent = en.e();
                        if (ent.isRemoved() || ent == mc.player || ent.level() != mc.level) continue;
                        float a = alfa(en, pt) * 0.8F;
                        // solo las capas con el formato de las entidades: las demás (el brillo de lo encantado, la
                        // correa, el texto del nombre, las cajas de F3+B) tienen otro formato y, metidas en este búfer,
                        // dejaban el vértice a medias y tiraban una excepción en cada fotograma: se borraban TODAS las
                        // siluetas (y el que habla parpadeaba, siempre a medio encender)
                        Solido sol = new Solido(bs.getBuffer(silueta), CR, CG, CB, a);
                        MultiBufferSource solido = rt -> rt.format() == DefaultVertexFormat.NEW_ENTITY ? sol : NADA;
                        double x = Mth.lerp(pt, ent.xOld, ent.getX()), y = Mth.lerp(pt, ent.yOld, ent.getY()), z = Mth.lerp(pt, ent.zOld, ent.getZ());
                        float yaw = Mth.lerp(pt, ent.yRotO, ent.getYRot());
                        rd.render(ent, x - cam.x, y - cam.y, z - cam.z, yaw, pt, ps, solido, 0xF000F0);
                    }
                } catch (RuntimeException ex) {
                    if (pruebaFallos++ == 0) Miscelanea.LOGGER.warn("[Eco] fallo al dibujar las siluetas", ex);
                } finally {
                    rd.setRenderShadow(true);
                    bs.endBatch(silueta);
                }
            }
        } finally {
            RenderSystem.setShaderFogStart(nieblaIni);
            RenderSystem.setShaderFogEnd(nieblaFin);
        }
    }

    private static void circulo(VertexConsumer vc, Matrix4f m4, Matrix3f m3, double cx, double cy, double cz, float r, int plano, float a) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float x0, y0, z0, x1, y1, z1;
            switch (plano) {
                case 0 -> {   // horizontal
                    x0 = (float) (cx + Math.cos(a0) * r); y0 = (float) cy; z0 = (float) (cz + Math.sin(a0) * r);
                    x1 = (float) (cx + Math.cos(a1) * r); y1 = (float) cy; z1 = (float) (cz + Math.sin(a1) * r);
                }
                case 1 -> {
                    x0 = (float) (cx + Math.cos(a0) * r); y0 = (float) (cy + Math.sin(a0) * r); z0 = (float) cz;
                    x1 = (float) (cx + Math.cos(a1) * r); y1 = (float) (cy + Math.sin(a1) * r); z1 = (float) cz;
                }
                default -> {
                    x0 = (float) cx; y0 = (float) (cy + Math.sin(a0) * r); z0 = (float) (cz + Math.cos(a0) * r);
                    x1 = (float) cx; y1 = (float) (cy + Math.sin(a1) * r); z1 = (float) (cz + Math.cos(a1) * r);
                }
            }
            float nx = x1 - x0, ny = y1 - y0, nz = z1 - z0;
            float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-6F) l = 1F;
            nx /= l; ny /= l; nz /= l;
            vc.vertex(m4, x0, y0, z0).color(CR, CG, CB, a).normal(m3, nx, ny, nz).endVertex();
            vc.vertex(m4, x1, y1, z1).color(CR, CG, CB, a).normal(m3, nx, ny, nz).endVertex();
        }
    }

    private static java.lang.reflect.Method irisSombras;
    private static Object irisApi;
    private static boolean sinIris;

    /** ¿Está Oculus/Iris dibujando las sombras ahora? Por reflexión: no hace falta tenerlo instalado. */
    private static boolean pasadaSombras() {
        if (sinIris) return false;
        try {
            if (irisSombras == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = c.getMethod("getInstance").invoke(null);
                irisSombras = c.getMethod("isRenderingShadowPass");
            }
            return (Boolean) irisSombras.invoke(irisApi);
        } catch (Throwable t) {
            sinIris = true;
            return false;
        }
    }

    /** Un consumidor que no dibuja nada. */
    private static final class Nada implements VertexConsumer {
        @Override public VertexConsumer vertex(double x, double y, double z) { return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer uv(float u, float v) { return this; }
        @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
        @Override public VertexConsumer uv2(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
        @Override public void endVertex() {}
        @Override public void defaultColor(int r, int g, int b, int a) {}
        @Override public void unsetDefaultColor() {}
    }

    /** Envoltorio que pinta todo de un color fijo (sobre la textura blanca: silueta maciza) con su alfa. */
    private record Solido(VertexConsumer d, float r, float g, float b, float a) implements VertexConsumer {
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            d.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int cr, int cg, int cb, int ca) {
            d.color((int) (255 * r), (int) (255 * g), (int) (255 * b), (int) (ca * a));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            d.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            d.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            d.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            d.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            d.endVertex();
        }

        @Override
        public void defaultColor(int cr, int cg, int cb, int ca) {
            d.defaultColor((int) (255 * r), (int) (255 * g), (int) (255 * b), (int) (ca * a));
        }

        @Override
        public void unsetDefaultColor() {
            d.unsetDefaultColor();
        }
    }

    // ------------------------------------------------------------------ para la prueba del CI (sin sonido no hay ondas)
    public static int pruebaFallos;

    public static void pruebaRuido(double x, double y, double z) {
        Minecraft mc = Minecraft.getInstance();
        if (activo()) oir(mc, x, y, z);
    }

    public static String pruebaEstado() {
        return "ondas " + ONDAS.size() + ", bloques " + BLOQUES.size() + ", entidades " + ENTES.size() + ", fallos al dibujar " + pruebaFallos;
    }

    public static int pruebaEntes() {
        return ENTES.size();
    }

    public static boolean pruebaVacio() {
        return ONDAS.isEmpty() && BLOQUES.isEmpty() && ENTES.isEmpty();
    }

    // ------------------------------------------------------------------ HUD: una viñeta azulada y el aviso de la palmada
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        if (!activo()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Vineta.dibujar(g, w, h, 0x0A2030, 0.6F, 1.08F);
        RenderSystem.enableBlend();
        // el enfriamiento de la palmada: una barrita que se rellena
        int bw = 40, bx = w / 2 - bw / 2, by = h - 70;
        int lleno = Math.round(bw * (1F - enfriamiento / (float) ENFRIAMIENTO));
        g.fill(bx, by, bx + bw, by + 2, 0x66203040);
        g.fill(bx, by, bx + lleno, by + 2, 0xCC8CDCFF);
        RenderSystem.disableBlend();
    }
}
