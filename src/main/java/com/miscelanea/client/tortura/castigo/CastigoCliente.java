package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.Cadenas;
import com.miscelanea.client.tortura.qte.Dibujo;
import com.miscelanea.client.tortura.qte.QteCliente;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.tortura.castigo.BolaLanzarPacket;
import com.miscelanea.tortura.castigo.BolaPresoEntity;
import com.miscelanea.tortura.castigo.CepoEntity;
import com.miscelanea.tortura.castigo.JaulaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cliente de los castigos: la cadena de la bola de preso, el balanceo de quien va en la jaula (inclinado con ella y con
 * la cámara ladeada), los mobs doblados en el cepo, la cabeza que esquiva, el HUD del balanceo y las teclas de la bola
 * (no correr ni saltar; clic derecho la lanza).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CastigoCliente {
    private CastigoCliente() {}

    /** Esquivas en curso: id → {lado, tick de inicio}. */
    private static final Map<Integer, float[]> ESQUIVAS = new HashMap<>();
    /** Preso (id) → su bola. */
    private static final Map<Integer, BolaPresoEntity> BOLAS = new HashMap<>();
    private static final Map<Integer, Vector3f> TOBILLOS = new HashMap<>(), ARGOLLAS = new HashMap<>();
    private static final Set<Integer> GIRADOS = new HashSet<>();
    private static int fotograma, fotogramaCaptura;
    private static float tiempo;
    /** Cuándo lanzó la bola el jugador local (para la animación). */
    static int lanzadoT = -100;

    // ------------------------------------------------------------------ consultas
    public static void esquiva(int id, int lado) {
        ESQUIVAS.put(id, new float[]{lado, tiempo});
    }

    /** Inclinación de la cabeza al esquivar (-1..1; + = a su derecha). */
    public static float inclinacion(int id, float pt) {
        float[] e = ESQUIVAS.get(id);
        if (e == null) return 0;
        float t = tiempo + pt - e[1];
        float k = t < 3 ? t / 3 : t < 12 ? 1 : Math.max(0, 1 - (t - 12) / 7);
        k = k * k * (3 - 2 * k);
        return e[0] * k;
    }

    @Nullable
    public static BolaPresoEntity bolaDe(int id) {
        BolaPresoEntity b = BOLAS.get(id);
        return b != null && !b.isRemoved() ? b : null;
    }

    public static boolean llevaBolaEnBrazos(int id) {
        BolaPresoEntity b = bolaDe(id);
        return b != null && b.estado() == BolaPresoEntity.EN_BRAZOS;
    }

    public static boolean enCepo(Entity e) {
        return e.getVehicle() instanceof CepoEntity && CepoEntity.humanoide(e);
    }

    public static boolean enJaula(Entity e) {
        return e.getVehicle() instanceof JaulaEntity;
    }

    static void capturarTobillo(int id, Vector3f v) {
        nuevoFotograma();
        TOBILLOS.put(id, v);
    }

    static void capturarArgolla(int id, Vector3f v) {
        nuevoFotograma();
        ARGOLLAS.put(id, v);
    }

    private static void nuevoFotograma() {
        if (fotogramaCaptura != fotograma) {
            TOBILLOS.clear();
            ARGOLLAS.clear();
            fotogramaCaptura = fotograma;
        }
    }

    /** Giro de la jaula con esos ángulos (de "colgar recto" a la dirección de la cadena). */
    public static Quaternionf giroJaula(float ax, float az) {
        Vec3 u = JaulaEntity.direccion(ax, az);
        return new Quaternionf().rotationTo(0F, -1F, 0F, (float) u.x, (float) u.y, (float) u.z);
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            BOLAS.clear();
            ESQUIVAS.clear();
            return;
        }
        if (mc.isPaused()) return;
        tiempo++;
        ESQUIVAS.values().removeIf(v -> tiempo - v[1] > 25);
        BOLAS.clear();
        for (Entity en : mc.level.entitiesForRendering())
            if (en instanceof BolaPresoEntity b && b.presoIdRed() >= 0) BOLAS.put(b.presoIdRed(), b);
        LocalPlayer p = mc.player;
        if (p != null && bolaDe(p.getId()) != null && p.isSprinting()) p.setSprinting(false);
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        BOLAS.clear();
        ESQUIVAS.clear();
        TOBILLOS.clear();
        ARGOLLAS.clear();
        GIRADOS.clear();
    }

    // ------------------------------------------------------------------ teclas
    @SubscribeEvent
    public static void movimiento(MovementInputUpdateEvent e) {
        Player p = e.getEntity();
        if (bolaDe(p.getId()) == null) return;
        // ni saltar ni correr (correr necesita el impulso hacia delante completo)
        e.getInput().jumping = false;
        e.getInput().forwardImpulse = Math.min(e.getInput().forwardImpulse, 0.79F);
    }

    @SubscribeEvent
    public static void saltar(LivingEvent.LivingJumpEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide && bolaDe(v.getId()) != null) {
            Vec3 d = v.getDeltaMovement();
            v.setDeltaMovement(d.x, Math.min(d.y, 0.0), d.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !e.isUseItem() || e.getHand() != InteractionHand.MAIN_HAND || QteCliente.activo()) return;
        if (!llevaBolaEnBrazos(mc.player.getId()) || enJaula(mc.player) || mc.player.isPassenger()) return;
        ModNetwork.CHANNEL.sendToServer(new BolaLanzarPacket());
        lanzadoT = mc.player.tickCount;
        e.setCanceled(true);
        e.setSwingHand(true);
    }

    // ------------------------------------------------------------------ dibujar: cuerpo inclinado (jaula) o doblado (mobs en el cepo)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void antes(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity v = e.getEntity();
        Entity veh = v.getVehicle();
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        if (veh instanceof JaulaEntity j && j.estado() == JaulaEntity.COLGANDO) {
            ps.pushPose();
            GIRADOS.add(v.getId());
            ps.mulPose(giroJaula(Mth.lerp(pt, j.axO, j.axC), Mth.lerp(pt, j.azO, j.azC)));
            return;
        }
        if (veh instanceof CepoEntity c && !(v instanceof Player) && CepoEntity.humanoide(v) && e.getRenderer().getModel() instanceof HumanoidModel<?>) {
            ps.pushPose();
            GIRADOS.add(v.getId());
            float yaw = c.getYRot();
            ps.mulPose(Axis.YP.rotationDegrees(-yaw));
            ps.translate(0, CepoEntity.BAJADA, -CepoEntity.ADELANTE);
            ps.translate(0, 0.7, 0);
            ps.mulPose(Axis.XP.rotationDegrees(70F));
            ps.translate(0, -0.7, 0);
            ps.mulPose(Axis.YP.rotationDegrees(yaw));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void despues(RenderLivingEvent.Post<?, ?> e) {
        if (GIRADOS.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !mc.options.getCameraType().isFirstPerson() || !(p.getVehicle() instanceof JaulaEntity j) || j.estado() != JaulaEntity.COLGANDO) return;
        float pt = (float) e.getPartialTick();
        float lat = lateral(j, p, pt);
        e.setRoll(e.getRoll() + (float) Math.toDegrees(lat) * 0.7F);
    }

    /** Ángulo de la jaula hacia la izquierda del jugador (rad). */
    static float lateral(JaulaEntity j, Player p, float pt) {
        float ax = Mth.lerp(pt, j.axO, j.axC), az = Mth.lerp(pt, j.azO, j.azC);
        double yaw = Math.toRadians(p.getViewYRot(pt));
        return (float) (ax * Math.cos(yaw) + az * Math.sin(yaw));
    }

    // ------------------------------------------------------------------ cadena de la bola
    @SubscribeEvent
    public static void dibujarNivel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            fotograma++;
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || BOLAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f rot = new Matrix4f(e.getPoseStack().last().pose());
        boolean frescos = fotogramaCaptura == fotograma;
        Vector3f abajo = rot.transformDirection(new Vector3f(0, -1, 0));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean algo = false;
        for (Map.Entry<Integer, BolaPresoEntity> en : BOLAS.entrySet()) {
            BolaPresoEntity b = en.getValue();
            Entity pe = level.getEntity(en.getKey());
            if (!(pe instanceof LivingEntity preso) || b.isRemoved()) continue;
            Vec3 a = BolaPresoEntity.tobillo(preso, pt);
            Vec3 bc = b.getPosition(pt).add(0, BolaPresoEntity.RADIO * 1.9, 0);
            if (b.estado() == BolaPresoEntity.EN_BRAZOS) bc = BolaPresoEntity.enBrazos(preso, pt).add(0, BolaPresoEntity.RADIO * 1.9, 0);
            Vector3f va = frescos ? TOBILLOS.get(preso.getId()) : null;
            Vector3f vb = frescos ? ARGOLLAS.get(b.getId()) : null;
            if (va == null) va = vista(rot, a, cam);
            if (vb == null) vb = vista(rot, bc, cam);
            double dist = a.distanceTo(bc);
            float cuelga = (float) Mth.clamp((BolaPresoEntity.CADENA - dist) * 0.4 + 0.04, 0.02, 0.6);
            int luz = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(bc).scale(0.5)));
            Cadenas.dibujar(new PoseStack(), buffers.getBuffer(Cadenas.TIPO), va, vb, abajo, cuelga, luz, 0.085F);
            algo = true;
        }
        if (algo) buffers.endBatch(Cadenas.TIPO);
    }

    private static Vector3f vista(Matrix4f rot, Vec3 mundo, Vec3 cam) {
        Vector4f v = rot.transform(new Vector4f((float) (mundo.x - cam.x), (float) (mundo.y - cam.y), (float) (mundo.z - cam.z), 1F));
        return new Vector3f(v.x, v.y, v.z);
    }

    // ------------------------------------------------------------------ HUD del balanceo
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui || !(p.getVehicle() instanceof JaulaEntity j)) return;
        if (j.estado() != JaulaEntity.COLGANDO || j.puerta() == JaulaEntity.ABIERTA || j.enSuelo()) return;
        float lat = lateral(j, p, pt), latO = lateral(j, p, pt - 1F);
        float vel = lat - latO;
        float cx = w / 2F, y0 = h - 108;
        int x0 = (int) cx - 104, x1 = (int) cx + 104;
        Dibujo.panel(g, x0, (int) y0, x1, (int) y0 + 62, 0xFFB08850);
        // péndulo: arco de -60º a 60º, la aguja es la cadena y la jaula
        float px = cx, py = y0 + 8;
        Dibujo.arco(g, px, py, 40, 42, 120, 240, 0x50FFFFFF);
        float umbral = (float) Math.toDegrees(JaulaEntity.UMBRAL);
        Dibujo.arco(g, px, py, 40, 43, 180 - 70, 180 - umbral, 0x90FF5040);
        Dibujo.arco(g, px, py, 40, 43, 180 + umbral, 180 + 70, 0x90FF5040);
        float ang = (float) Math.toDegrees(lat);
        double r = Math.toRadians(180 + ang);   // a la izquierda de la pantalla = a su izquierda
        float ex = px + (float) Math.sin(r) * 38, ey = py - (float) Math.cos(r) * 38;
        Dibujo.linea(g, px, py, ex, ey, 1.6F, 0xFFB8B4AC);
        Dibujo.disco(g, px, py, 2.2F, 0xFF8A847C);
        g.fill((int) ex - 4, (int) ey - 3, (int) ex + 4, (int) ey + 6, 0xFF2A2622);
        for (int i = -3; i <= 3; i += 2) g.fill((int) ex + i, (int) ey - 3, (int) ex + i + 1, (int) ey + 6, 0xFF9A948A);
        // teclas: la que empuja a favor del vaivén
        boolean izq = vel > 0.002F, der = vel < -0.002F;
        String a = mc.options.keyLeft.getTranslatedKeyMessage().getString(), d = mc.options.keyRight.getTranslatedKeyMessage().getString();
        a = a.length() > 3 ? a.substring(0, 3) : a.toUpperCase();
        d = d.length() > 3 ? d.substring(0, 3) : d.toUpperCase();
        boolean pulsaA = mc.options.keyLeft.isDown(), pulsaD = mc.options.keyRight.isDown();
        Dibujo.tecla(g, mc.font, x0 + 10, (int) y0 + 18, 22, 20, a, pulsaA ? (izq ? 3 : 4) : izq ? 1 : 0, 0.8F);
        Dibujo.tecla(g, mc.font, x1 - 32, (int) y0 + 18, 22, 20, d, pulsaD ? (der ? 3 : 4) : der ? 1 : 0, 0.8F);
        // fuerza del balanceo y la cadena
        float amp = Math.min(1F, (float) Math.sqrt(j.axC * j.axC + j.azC * j.azC) / 1.0F);
        int bx = (int) cx - 60;
        Dibujo.barra(g, bx, (int) y0 + 44, 120, 4, amp, 0xFFFFD070, 0xFFB07020);
        float cad = 1F - j.fatiga();
        int cc = Dibujo.mezcla(0xFFE04030, 0xFFB8B4AC, cad);
        Dibujo.barra(g, bx, (int) y0 + 53, 120, 4, cad, cc, Dibujo.mezcla(cc, 0xFF000000, 0.35F));
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.jaula.fuerza"), bx - 20, (int) y0 + 43, 0.6F, 0xFFE8D8B0, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.jaula.cadena"), bx - 20, (int) y0 + 52, 0.6F, 0xFFE8D8B0, true);
        Dibujo.texto(g, mc.font, Component.translatable("hud.miscelanea.jaula.titulo").withStyle(ChatFormatting.BOLD), cx, y0 - 11, 0.9F, 0xFFF2E6C8, true);
    }
}
