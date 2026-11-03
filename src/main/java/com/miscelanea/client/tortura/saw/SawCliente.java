package com.miscelanea.client.tortura.saw;

import com.miscelanea.Miscelanea;
import com.miscelanea.client.tortura.Cuerdas;
import com.miscelanea.tortura.saw.CajaAgua;
import com.miscelanea.tortura.saw.SawEstadoPacket;
import com.miscelanea.tortura.saw.SawManosPacket;
import com.miscelanea.tortura.saw.SawRed;
import com.miscelanea.tortura.saw.TanqueAguaBlock;
import com.miscelanea.tortura.saw.SawRegistro;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cliente de la caja de agua: estado de cada entidad que la lleva (con el agua subiendo suave entre paquetes), la tecla
 * "Meter las manos" (y el clic derecho al aire con la mano vacía), la manguera hasta el tanque y, para quien la lleva,
 * lo que ve y oye: el agua que sube por delante de los ojos, la vista azul (roja con la sangre) y la niebla cuando la cubre,
 * y el sonido ahogado.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class SawCliente {
    private SawCliente() {}

    public static final KeyMapping MANOS = new KeyMapping("key.miscelanea.saw_manos", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J, "key.categories.miscelanea");
    static final ResourceLocation MANGUERA = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/saw/manguera.png");
    static final ResourceLocation CRISTAL = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/saw/caja_agua_cristal.png");
    private static final ResourceLocation BAJO_AGUA = new ResourceLocation("textures/misc/underwater.png");
    private static final RenderType TIPO_MANGUERA = RenderType.entityCutoutNoCull(MANGUERA);

    /** Caja de agua de una entidad (lo que sabe el cliente). */
    public static final class Caja {
        public boolean activa;
        public float nivel, nivelAntes, objetivo, sangre;
        public long tanque = CajaAgua.SIN_TANQUE;
        public int empujes, abierta, perdida;

        public float nivel(float pt) {
            return Mth.lerp(pt, nivelAntes, nivel);
        }
    }

    private static final Map<Integer, Caja> CAJAS = new HashMap<>();
    private static int reloj;

    @Nullable
    public static Caja caja(int id) {
        return CAJAS.get(id);
    }

    public static void recibir(SawEstadoPacket p) {
        if (!p.caja) {
            CAJAS.remove(p.entidad);
            return;
        }
        Caja c = CAJAS.computeIfAbsent(p.entidad, k -> {
            Caja n = new Caja();
            n.nivel = n.nivelAntes = p.nivel;
            return n;
        });
        c.activa = p.activa;
        c.objetivo = p.nivel;
        c.sangre = p.sangre;
        c.tanque = p.tanque;
        c.empujes = p.empujes;
        if (p.abierta == 0 || c.abierta == 0) c.abierta = p.abierta;
        c.perdida = 0;
    }

    // ------------------------------------------------------------------ tick
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            CAJAS.clear();
            return;
        }
        if (mc.isPaused()) return;
        reloj++;
        for (Iterator<Map.Entry<Integer, Caja>> it = CAJAS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Caja> en = it.next();
            Caja c = en.getValue();
            if (mc.level.getEntity(en.getKey()) == null) {
                if (++c.perdida > 60) it.remove();
                continue;
            }
            c.nivelAntes = c.nivel;
            if (c.activa && c.objetivo < 1F) c.objetivo = Math.min(1F, c.objetivo + 1F / CajaAgua.LLENADO);
            c.nivel += (c.objetivo - c.nivel) * 0.25F;
            if (c.abierta > 0) c.abierta++;
        }
        LocalPlayer p = mc.player;
        while (MANOS.consumeClick()) if (caja(p.getId()) != null) manos();
    }

    private static void manos() {
        SawRed.CANAL.sendToServer(new SawManosPacket());
    }

    @SubscribeEvent
    public static void clicAire(PlayerInteractEvent.RightClickEmpty e) {
        if (e.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND && e.getItemStack().isEmpty() && caja(e.getEntity().getId()) != null) manos();
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        CAJAS.clear();
    }

    // ------------------------------------------------------------------ lo que ve quien la lleva
    /** Nivel del agua de la caja del jugador local (o -1). */
    private static float propio(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return -1F;
        Caja c = caja(mc.player.getId());
        return c == null ? -1F : c.nivel(pt);
    }

    private static boolean sumergido() {
        return propio(1F) >= CajaAgua.CARA;
    }

    /** Color del agua (azul turbio → rojo con la sangre). */
    static float[] color(float sangre) {
        float s = Mth.clamp(sangre, 0F, 1F);
        return new float[]{Mth.lerp(s, 0.26F, 0.48F), Mth.lerp(s, 0.42F, 0.05F), Mth.lerp(s, 0.52F, 0.04F)};
    }

    /** HUD (por debajo de todo): el cristal sucio delante de la cara, el agua que sube y, debajo del agua, todo azul (o rojo). */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        Caja c = caja(mc.player.getId());
        if (c == null) return;
        float n = c.nivel(pt);
        float[] col = color(c.sangre);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        float t = reloj + pt;
        if (n >= CajaAgua.CARA) {
            g.setColor(col[0], col[1], col[2], 0.55F);
            g.blit(BAJO_AGUA, 0, 0, -90, t * 0.4F, t * 0.25F, w, h, w * 2, h * 2);
            g.setColor(1F, 1F, 1F, 1F);
            g.fill(0, 0, w, h, argb(0.38F, col));
        } else if (n > 0.02F) {
            // la superficie sube por delante de los ojos: a la altura de los ojos llega al centro de la pantalla
            float k = Mth.clamp(n / CajaAgua.CARA, 0F, 1F);
            int arriba = (int) (h * (1F - 0.55F * (float) Math.pow(k, 1.4)));
            int pasos = 24;
            for (int i = 0; i < pasos; i++) {
                int x0 = w * i / pasos, x1 = w * (i + 1) / pasos;
                int y = arriba + (int) (Mth.sin(t * 0.15F + i * 0.6F) * 2.5F);
                g.fill(x0, y, x1, h, argb(0.32F + 0.1F * k, col));
                g.fill(x0, y, x1, y + 1, argb(0.55F, new float[]{0.8F, 0.85F, 0.85F}));
            }
        }
        // el cristal: sucio, siempre delante
        g.setColor(1F, 1F, 1F, 0.35F);
        g.blit(CRISTAL, 0, 0, -90, 0F, 0F, w, h, w, h);
        g.setColor(1F, 1F, 1F, 1F);
        if (c.activa && c.abierta == 0) {
            Component txt = Component.translatable("msg.miscelanea.saw.caja.ayuda_tecla", MANOS.getTranslatedKeyMessage(), c.empujes, CajaAgua.EMPUJES);
            g.drawCenteredString(mc.font, txt, w / 2, h - 62, 0xFFE0C0C0);
        }
        RenderSystem.depthMask(true);
    }

    private static int argb(float a, float[] c) {
        return ((int) (Mth.clamp(a, 0, 1) * 255) << 24) | ((int) (c[0] * 255) << 16) | ((int) (c[1] * 255) << 8) | (int) (c[2] * 255);
    }

    @SubscribeEvent
    public static void niebla(ViewportEvent.RenderFog e) {
        if (!sumergido() || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) return;
        e.setNearPlaneDistance(-2F);
        e.setFarPlaneDistance(7F);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (!sumergido() || mc.player == null || !mc.options.getCameraType().isFirstPerson()) return;
        Caja c = caja(mc.player.getId());
        float[] col = color(c == null ? 0F : c.sangre);
        e.setRed(col[0] * 0.6F);
        e.setGreen(col[1] * 0.6F);
        e.setBlue(col[2] * 0.6F);
    }

    /** Con la cabeza bajo el agua, todo suena ahogado (más bajo y más grave), menos el HUD y los minijuegos. */
    @SubscribeEvent
    public static void sonido(PlaySoundEvent e) {
        SoundInstance s = e.getSound();
        if (s == null || s instanceof TickableSoundInstance || s.isRelative() || s instanceof Ahogado || !sumergido()) return;
        e.setSound(new Ahogado(s));
    }

    /** Envoltorio de un sonido: el mismo, más bajo y más grave. */
    static final class Ahogado implements SoundInstance {
        private final SoundInstance s;

        Ahogado(SoundInstance s) {
            this.s = s;
        }

        @Override
        public ResourceLocation getLocation() {
            return s.getLocation();
        }

        @Nullable
        @Override
        public WeighedSoundEvents resolve(SoundManager m) {
            return s.resolve(m);
        }

        @Override
        public Sound getSound() {
            return s.getSound();
        }

        @Override
        public SoundSource getSource() {
            return s.getSource();
        }

        @Override
        public boolean isLooping() {
            return s.isLooping();
        }

        @Override
        public boolean isRelative() {
            return s.isRelative();
        }

        @Override
        public int getDelay() {
            return s.getDelay();
        }

        @Override
        public float getVolume() {
            return s.getVolume() * 0.35F;
        }

        @Override
        public float getPitch() {
            return s.getPitch() * 0.78F;
        }

        @Override
        public double getX() {
            return s.getX();
        }

        @Override
        public double getY() {
            return s.getY();
        }

        @Override
        public double getZ() {
            return s.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return s.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return s.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return s.canPlaySound();
        }
    }

    // ------------------------------------------------------------------ la manguera
    @SubscribeEvent
    public static void manguera(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || CAJAS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource bs = mc.renderBuffers().bufferSource();
        VertexConsumer vc = null;
        for (Map.Entry<Integer, Caja> en : CAJAS.entrySet()) {
            Caja c = en.getValue();
            if (c.tanque == CajaAgua.SIN_TANQUE || c.abierta > 0) continue;
            Entity ent = mc.level.getEntity(en.getKey());
            if (!(ent instanceof LivingEntity v) || v.isInvisible()) continue;
            BlockPos tp = BlockPos.of(c.tanque);
            BlockState ts = mc.level.getBlockState(tp);
            if (!ts.is(SawRegistro.TANQUE.get())) continue;
            // primera persona: la manguera sale de detrás de la cabeza (no se ve el principio)
            Vec3 ojo = v.getEyePosition(pt);
            float yaw = Mth.lerp(pt, v.yHeadRotO, v.yHeadRot) * Mth.DEG_TO_RAD;
            Vec3 a = ojo.add(Mth.sin(yaw) * 0.25, 0.78 * v.getBbHeight() / 1.8, -Mth.cos(yaw) * 0.25);
            Vec3 b = TanqueAguaBlock.toma(ts, tp);
            if (a.distanceToSqr(cam) > 64 * 64) continue;
            if (vc == null) vc = bs.getBuffer(TIPO_MANGUERA);
            Vec3 medio = a.add(b).scale(0.5);
            int luz = LevelRenderer.getLightColor(mc.level, BlockPos.containing(medio));
            float largo = (float) a.distanceTo(b);
            Cuerdas.dibujar(ps, vc, new Vector3f((float) (a.x - cam.x), (float) (a.y - cam.y), (float) (a.z - cam.z)),
                    new Vector3f((float) (b.x - cam.x), (float) (b.y - cam.y), (float) (b.z - cam.z)), new Vector3f(0, -1, 0),
                    Math.min(1.2F, 0.18F * largo), luz, 0.1F, 0F);
        }
        if (vc != null) bs.endBatch(TIPO_MANGUERA);
    }
}
