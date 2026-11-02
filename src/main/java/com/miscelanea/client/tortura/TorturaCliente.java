package com.miscelanea.client.tortura;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.EstadoCautivoPacket;
import com.miscelanea.tortura.GanzuaItem;
import com.miscelanea.tortura.LlaveTrampaItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Lo que el cliente sabe de los cautivos (trampa, grilletes, cadena, lazo), la cadena-correa y la cuerda del lazo en el mundo. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class TorturaCliente {
    private TorturaCliente() {}

    public static final class Estado {
        public int trampa;
        public boolean sinCajas, grilletes;
        public int cadena = -1;
        /** Lazo: estado (0..3, ver Lazo), quién lo lleva (id o -1) y ticks que le quedan al estado. */
        public int lazo, lazoCaptor = -1, lazoT;
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();
    /** Punto de las muñecas (en espacio de vista) capturado al dibujar a la entidad en este fotograma. */
    private static final Map<Integer, Vector3f> MUNECAS = new HashMap<>();
    private static final Map<Integer, Vector3f> MANOS = new HashMap<>();
    /** Punto del torso (donde se cierra el lazo), también en espacio de vista. */
    private static final Map<Integer, Vector3f> TORSOS = new HashMap<>();
    private static int fotograma, fotogramaCaptura;

    public static void recibir(EstadoCautivoPacket p) {
        if (p.trampa <= 0 && !p.grilletes && p.lazo == 0) {
            ESTADOS.remove(p.entidad);
            return;
        }
        Estado e = ESTADOS.computeIfAbsent(p.entidad, k -> new Estado());
        e.trampa = p.trampa;
        e.sinCajas = p.sinCajas;
        e.grilletes = p.grilletes;
        e.cadena = p.grilletes ? p.cadena : -1;
        e.lazo = p.lazo;
        e.lazoCaptor = p.lazo != 0 ? p.lazoCaptor : -1;
        e.lazoT = p.lazoTicks;
    }

    @Nullable
    public static Estado estado(int id) {
        return ESTADOS.get(id);
    }

    public static boolean atado(int id) {
        Estado e = ESTADOS.get(id);
        return e != null && e.grilletes;
    }

    public static boolean esQuienTira(int id) {
        for (Estado e : ESTADOS.values()) if (e.cadena == id || (e.lazo == 1 && e.lazoCaptor == id)) return true;
        return false;
    }

    /** Estado del lazo de esa entidad (0 = nada). */
    public static int lazo(int id) {
        Estado e = ESTADOS.get(id);
        return e == null ? 0 : e.lazo;
    }

    /** ¿Tiene a alguien enlazado (tira y afloja)? */
    public static boolean esCaptorTirando(int id) {
        for (Estado e : ESTADOS.values()) if (e.lazo == 1 && e.lazoCaptor == id) return true;
        return false;
    }

    // ------------------------------------------------------------------ captura de puntos desde la capa
    static void capturarMunecas(int id, Vector3f vista) {
        nuevoFotograma();
        MUNECAS.put(id, vista);
    }

    static void capturarMano(int id, Vector3f vista) {
        nuevoFotograma();
        MANOS.put(id, vista);
    }

    static void capturarTorso(int id, Vector3f vista) {
        nuevoFotograma();
        TORSOS.put(id, vista);
    }

    private static void nuevoFotograma() {
        if (fotogramaCaptura != fotograma) {
            MUNECAS.clear();
            MANOS.clear();
            TORSOS.clear();
            fotogramaCaptura = fotograma;
        }
    }

    // ------------------------------------------------------------------ eventos
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ESTADOS.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (Estado s : ESTADOS.values()) {
            if (s.trampa > 0) s.trampa--;
            if (s.lazoT > 0) s.lazoT--;
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        ESTADOS.clear();
        MUNECAS.clear();
        MANOS.clear();
        TORSOS.clear();
    }

    /**
     * Con las manos atadas no se puede pegar ni usar nada (salvo la ganzúa o la llave con grilletes; con el lazo, nada):
     * ni siquiera se mueve el brazo.
     */
    @SubscribeEvent
    public static void interaccion(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (lazo(mc.player.getId()) != 0) {
            e.setCanceled(true);
            e.setSwingHand(false);
            return;
        }
        if (!atado(mc.player.getId())) return;
        if (e.isUseItem()) {
            ItemStack s = mc.player.getItemInHand(e.getHand());
            if (s.getItem() instanceof GanzuaItem || s.getItem() instanceof LlaveTrampaItem) return;
        }
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    // ------------------------------------------------------------------ cadena-correa
    static float yawCuerpo(Entity e, float pt) {
        return e instanceof LivingEntity le ? Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) : e.getViewYRot(pt);
    }

    /** Muñecas aproximadas (delante de la cintura). */
    private static Vec3 munecasMundo(Entity v, float pt) {
        float y = yawCuerpo(v, pt) * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        return v.getPosition(pt).add(f.scale(0.34 * v.getBbWidth() / 0.6)).add(0, v.getBbHeight() * 0.43, 0);
    }

    /** Mano derecha aproximada de quien tira. */
    static Vec3 manoMundo(Entity h, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (h == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 mira = h.getViewVector(pt);
            Vec3 der = new Vec3(-mira.z, 0, mira.x);
            der = der.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : der.normalize();
            return h.getEyePosition(pt).add(mira.scale(0.55)).add(der.scale(0.42)).add(0, -0.42, 0);
        }
        float y = yawCuerpo(h, pt) * Mth.DEG_TO_RAD;
        Vec3 f = new Vec3(-Mth.sin(y), 0, Mth.cos(y));
        Vec3 der = new Vec3(-Mth.cos(y), 0, -Mth.sin(y));
        return h.getPosition(pt).add(f.scale(0.25)).add(der.scale(0.36)).add(0, h.getBbHeight() * 0.5, 0);
    }

    @SubscribeEvent
    public static void dibujarNivel(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            fotograma++;
            return;
        }
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || ESTADOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        Matrix4f rot = new Matrix4f(e.getPoseStack().last().pose());
        boolean frescos = fotogramaCaptura == fotograma;
        Vector3f abajo = rot.transformDirection(new Vector3f(0, -1, 0));
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        boolean algo = false, cuerda = false;
        for (Map.Entry<Integer, Estado> en : ESTADOS.entrySet()) {
            Estado s = en.getValue();
            if (s.lazo == 1 && s.lazoCaptor >= 0) {
                cuerda |= cuerdaLazo(level, en.getKey(), s, pt, cam, rot, frescos, abajo, buffers);
                continue;
            }
            if (s.cadena < 0) continue;
            Entity v = level.getEntity(en.getKey()), h = level.getEntity(s.cadena);
            if (v == null || h == null) continue;
            Vec3 a = munecasMundo(v, pt), b = manoMundo(h, pt);
            Vector3f va = frescos ? MUNECAS.get(v.getId()) : null;
            Vector3f vb = frescos ? MANOS.get(h.getId()) : null;
            if (va == null) va = vista(rot, a, cam);
            if (vb == null) vb = vista(rot, b, cam);
            double dist = a.distanceTo(b);
            float cuelga = (float) Mth.clamp(0.55 - dist * 0.08, 0.04, 0.5);
            int luz = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(b).scale(0.5)));
            Cadenas.dibujar(new PoseStack(), buffers.getBuffer(Cadenas.TIPO), va, vb, abajo, cuelga, luz, 0.075F);
            algo = true;
        }
        if (algo) buffers.endBatch(Cadenas.TIPO);
        if (cuerda) buffers.endBatch(Cuerdas.TIPO);
    }

    /** Tira y afloja: cuerda tensa de la mano del captor al lazo cerrado en el torso de la víctima. */
    private static boolean cuerdaLazo(ClientLevel level, int idVictima, Estado s, float pt, Vec3 cam, Matrix4f rot, boolean frescos,
                                      Vector3f abajo, MultiBufferSource.BufferSource buffers) {
        Entity v = level.getEntity(idVictima), h = level.getEntity(s.lazoCaptor);
        if (v == null || h == null) return false;
        Vec3 a = v.getPosition(pt).add(0, v.getBbHeight() * 0.62, 0), b = manoMundo(h, pt);
        Vector3f va = frescos ? TORSOS.get(v.getId()) : null;
        Vector3f vb = frescos ? MANOS.get(h.getId()) : null;
        if (va == null) va = vista(rot, a, cam);
        if (vb == null) vb = vista(rot, b, cam);
        double dist = a.distanceTo(b);
        // casi tensa; vibra un poco con el forcejeo
        float t = h.tickCount + pt;
        float cuelga = (float) Mth.clamp(0.05 + dist * 0.012, 0.03, 0.3) + Mth.sin(t * 1.7F) * 0.015F;
        int luz = LevelRenderer.getLightColor(level, BlockPos.containing(a.add(b).scale(0.5)));
        Cuerdas.dibujar(new PoseStack(), buffers.getBuffer(Cuerdas.TIPO), vb, va, abajo, cuelga, luz, 0.05F, -t * 0.08F);
        return true;
    }

    private static Vector3f vista(Matrix4f rot, Vec3 mundo, Vec3 cam) {
        Vector4f v = rot.transform(new Vector4f((float) (mundo.x - cam.x), (float) (mundo.y - cam.y), (float) (mundo.z - cam.z), 1F));
        return new Vector3f(v.x, v.y, v.z);
    }
}
