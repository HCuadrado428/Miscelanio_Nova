package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PocionesRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Sombra (la sombra de Hollow Knight) en el cliente. En vez del modelo (ni armadura, ni objetos, ni nombre) se dibuja
 * una silueta de humo negro tumbada en el suelo, con capas planas de una sola textura (sombra.png, 32 texels por
 * bloque, ver gen_sombra.py) y sin aplastar el modelo (así no hay caras solapadas que parpadeen):
 * <ol>
 *   <li>un aura oscura redonda que late, pegada al suelo (sustituye a la sombra redonda de vanilla);</li>
 *   <li>la silueta, con 5 poses (quieta y 4 de reptar, según la animación de andar) y 3 "hervidos" del borde que se
 *       alternan solos: el contorno se deshilacha y se mueve como humo;</li>
 *   <li>una segunda silueta algo más alta, más pequeña y translúcida, con otro hervido: da volumen y un borde suave;</li>
 *   <li>los ojos, dos puntos blancos que brillan en la oscuridad y parpadean de vez en cuando.</li>
 * </ol>
 * Suelta volutas de humo negro (partícula propia) que suben y se deshacen, más al moverse; al convertirse, el cuerpo se
 * deshace en una nube que cae y, al volver, la nube sube. Quien es sombra no tiene manos en primera persona, oye todo
 * amortiguado y ve la pantalla oscurecida con bordes de humo que respiran (con un fogonazo de oscuridad al empezar).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class SombraCliente {
    private SombraCliente() {}

    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/sombra.png");
    private static final ResourceLocation[] VINETA = {
            new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/sombra_vineta_0.png"),
            new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/sombra_vineta_1.png")};
    private static final float TW = 256F, TH = 320F, MARCO_W = 48F, MARCO_H = 72F, TEXELS = 32F;
    /** Alturas de cada capa sobre el suelo (separadas para que no se peleen en el búfer de profundidad). */
    private static final float Y_AURA = 0.012F, Y_CUERPO = 0.03F, Y_VOLUMEN = 0.055F, Y_OJOS = 0.07F;
    private static final int LUZ_MAX = 0xF000F0;

    /** Ticks desde que cada entidad se ve como sombra (para el fundido de entrada). */
    private static final Map<Integer, Integer> EDAD = new HashMap<>();
    /** Quién era sombra el tick anterior (de los jugadores cercanos): para la nube al entrar o salir. */
    private static final Set<Integer> ANTES = new HashSet<>();
    private static final Set<Integer> VISTOS = new HashSet<>();
    /** Pantalla de quien es sombra: intensidad (0..1) suavizada y fogonazo al empezar. */
    private static float k, kAntes, golpe, golpeAntes;

    // ------------------------------------------------------------------ dibujo en el mundo
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void dibujar(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity v = e.getEntity();
        if (!Estados.sombra(v)) return;
        e.setCanceled(true);
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        float yaw = Mth.rotLerp(pt, v.yBodyRotO, v.yBodyRot) * Mth.DEG_TO_RAD;
        // delante (hacia donde mira el cuerpo) y derecha, en horizontal
        float fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        float t = v.tickCount + pt;
        Integer edadE = EDAD.get(v.getId());
        float fundido = edadE == null ? 1F : Mth.clamp((edadE + pt) / 12F, 0F, 1F);
        if (fundido <= 0.01F) return;
        float vel = v.isAlive() && !v.isPassenger() ? Math.min(1F, v.walkAnimation.speed(pt)) : 0F;
        int pose = vel < 0.12F ? 0 : 1 + (Mth.floor(v.walkAnimation.position(pt) * 0.8F) & 3);
        int hervido = Mth.floor((t + v.getId() * 7) / 3F) % 3;
        int luz = e.getPackedLight();
        VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        // 1. aura: 96 texels (3 bloques), late despacio
        float pulso = 1F + 0.05F * Mth.sin(t * 0.08F);
        float ra = 1.5F * pulso;
        capa(vc, m, n, 0, 0, fx, fz, rx, rz, ra, ra, Y_AURA, 0, 224, 96, 96, 0.95F * fundido, luz);
        // 2. silueta
        float hw = MARCO_W / 2 / TEXELS, hl = MARCO_H / 2 / TEXELS;
        capa(vc, m, n, 0, 0, fx, fz, rx, rz, hw, hl, Y_CUERPO, pose * MARCO_W, hervido * MARCO_H, MARCO_W, MARCO_H, fundido, luz);
        // 3. volumen: otra silueta encima, un 6 % menor, con otro hervido y meciéndose un pelo
        float mece = 0.025F * Mth.sin(t * 0.21F + v.getId());
        capa(vc, m, n, rx * mece, rz * mece, fx, fz, rx, rz, hw * 0.94F, hl * 0.94F, Y_VOLUMEN, pose * MARCO_W, ((hervido + 1) % 3) * MARCO_H,
                MARCO_W, MARCO_H, 0.45F * fundido, luz);
        // 4. ojos: en la cabeza (texel 24,12 del marco, 36 - 12 = 24 texels por delante del centro); parpadean
        float ciclo = (t + v.getId() * 31) % 90F;
        if (ciclo > 4F) {
            float delante = (MARCO_H / 2 - 12F) / TEXELS * 0.97F;
            capa(vc, m, n, fx * delante, fz * delante, fx, fz, rx, rz, 8F / TEXELS, 4F / TEXELS, Y_OJOS, 96, 224, 16, 8,
                    Math.min(1F, fundido * 1.3F), LUZ_MAX);
        }
    }

    /** Un cuadrado horizontal centrado en (cx, cz) con la parte de arriba de la textura hacia delante. */
    private static void capa(VertexConsumer vc, Matrix4f m, Matrix3f n, float cx, float cz, float fx, float fz, float rx, float rz,
                             float medioAncho, float medioLargo, float y, float u, float v, float uw, float vh, float a, int luz) {
        float u0 = u / TW, u1 = (u + uw) / TW, v0 = v / TH, v1 = (v + vh) / TH;
        float ax = rx * medioAncho, az = rz * medioAncho, bx = fx * medioLargo, bz = fz * medioLargo;
        vertice(vc, m, n, cx - ax + bx, y, cz - az + bz, u0, v0, a, luz);
        vertice(vc, m, n, cx - ax - bx, y, cz - az - bz, u0, v1, a, luz);
        vertice(vc, m, n, cx + ax - bx, y, cz + az - bz, u1, v1, a, luz);
        vertice(vc, m, n, cx + ax + bx, y, cz + az + bz, u1, v0, a, luz);
    }

    private static void vertice(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float a, int luz) {
        vc.vertex(m, x, y, z).color(1F, 1F, 1F, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(luz).normal(n, 0F, 1F, 0F).endVertex();
    }

    /** Sin manos: una sombra no tiene. */
    @SubscribeEvent
    public static void manos(RenderHandEvent e) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && p.hasEffect(PocionesRegistro.SOMBRA.get())) e.setCanceled(true);
    }

    /** Todo suena amortiguado (más bajo y más grave) para quien es sombra. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void amortiguar(PlaySoundEvent e) {
        LocalPlayer p = Minecraft.getInstance().player;
        SoundInstance s = e.getSound();
        if (p == null || s == null || s instanceof TickableSoundInstance || !p.hasEffect(PocionesRegistro.SOMBRA.get())) return;
        if (s.getSource() == SoundSource.MUSIC || s.getSource() == SoundSource.RECORDS || s instanceof Sordina) return;
        e.setSound(new Sordina(s));
    }

    /** Envoltorio de un sonido que lo deja al 35 % y más grave (el sonido aún no está resuelto en el evento). */
    private record Sordina(SoundInstance d) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return d.getLocation();
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager m) {
            return d.resolve(m);
        }

        @Override
        public Sound getSound() {
            return d.getSound();
        }

        @Override
        public SoundSource getSource() {
            return d.getSource();
        }

        @Override
        public boolean isLooping() {
            return d.isLooping();
        }

        @Override
        public boolean isRelative() {
            return d.isRelative();
        }

        @Override
        public int getDelay() {
            return d.getDelay();
        }

        @Override
        public float getVolume() {
            return d.getVolume() * 0.35F;
        }

        @Override
        public float getPitch() {
            return d.getPitch() * 0.8F;
        }

        @Override
        public double getX() {
            return d.getX();
        }

        @Override
        public double getY() {
            return d.getY();
        }

        @Override
        public double getZ() {
            return d.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return d.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return d.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return d.canPlaySound();
        }
    }

    // ------------------------------------------------------------------ humo, nubes de cambio y pantalla
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        kAntes = k;
        golpeAntes = golpe;
        if (l == null || mc.player == null) {
            k = golpe = 0;
            return;
        }
        if (mc.isPaused()) return;
        boolean yo = mc.player.hasEffect(PocionesRegistro.SOMBRA.get());
        k += ((yo ? 1F : 0F) - k) * (yo ? 0.12F : 0.08F);
        if (k < 0.003F) k = 0;
        golpe = Math.max(0F, golpe - 0.06F);
        RandomSource r = l.getRandom();
        VISTOS.clear();
        for (Player p : l.players()) {
            if (p.distanceToSqr(mc.player) > 48 * 48) continue;
            int id = p.getId();
            boolean es = Estados.sombra(p);
            boolean era = ANTES.contains(id);
            boolean conocido = EDAD.containsKey(id) || ANTES.contains(id) || VISTO_NORMAL.contains(id);
            if (es) {
                VISTOS.add(id);
                EDAD.merge(id, 1, Integer::sum);
                if (!era && VISTO_NORMAL.contains(id)) {
                    nube(l, r, p, true);
                    if (p == mc.player) golpe = 1F;
                } else if (!era && !conocido) EDAD.put(id, 100);   // ya lo era al aparecer: sin fundido
                humo(l, r, p);
            } else {
                if (era) nube(l, r, p, false);
                EDAD.remove(id);
            }
            if (!es) VISTO_NORMAL.add(id);
            else VISTO_NORMAL.remove(id);
        }
        ANTES.clear();
        ANTES.addAll(VISTOS);
        if (l.getGameTime() % 200 == 0) {
            EDAD.keySet().removeIf(id -> l.getEntity(id) == null);
            VISTO_NORMAL.removeIf(id -> l.getEntity(id) == null);
        }
    }

    /** Jugadores vistos con su forma normal (si luego pasan a sombra, se ve la transformación). */
    private static final Set<Integer> VISTO_NORMAL = new HashSet<>();

    /** Volutas que suben de la silueta y, al moverse, una estela por detrás. */
    private static void humo(ClientLevel l, RandomSource r, Player p) {
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw), rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        if (r.nextFloat() < 0.3F) {
            double a = (r.nextDouble() * 2 - 1) * 0.85, b = (r.nextDouble() * 2 - 1) * 0.3;
            l.addParticle(PocionesRegistro.HUMO_SOMBRA.get(), p.getX() + fx * a + rx * b, p.getY() + 0.06, p.getZ() + fz * a + rz * b,
                    0, 0.012 + r.nextDouble() * 0.02, 0);
        }
        double vel = Math.sqrt(p.getDeltaMovement().x * p.getDeltaMovement().x + p.getDeltaMovement().z * p.getDeltaMovement().z);
        if (vel > 0.02 && r.nextFloat() < Math.min(0.5, vel * 5)) {
            double b = (r.nextDouble() * 2 - 1) * 0.25;
            l.addParticle(PocionesRegistro.HUMO_SOMBRA.get(), p.getX() - fx * 1.0 + rx * b, p.getY() + 0.05, p.getZ() - fz * 1.0 + rz * b,
                    -p.getDeltaMovement().x * 0.2, 0.008, -p.getDeltaMovement().z * 0.2);
        }
    }

    /** Al hacerse sombra, el cuerpo se deshace en humo que cae al suelo; al volver, el humo sube y forma el cuerpo. */
    private static void nube(ClientLevel l, RandomSource r, Player p, boolean entra) {
        for (int i = 0; i < 46; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = 0.15 + r.nextDouble() * 0.4, h = r.nextDouble() * 1.8;
            double x = p.getX() + Math.cos(ang) * rad, z = p.getZ() + Math.sin(ang) * rad;
            if (entra) l.addParticle(PocionesRegistro.HUMO_SOMBRA.get(), x, p.getY() + h, z, Math.cos(ang) * 0.03, -0.03 - h * 0.02, Math.sin(ang) * 0.03);
            else l.addParticle(PocionesRegistro.HUMO_SOMBRA.get(), x, p.getY() + h * 0.3, z, Math.cos(ang) * 0.01, 0.05 + r.nextDouble() * 0.05, Math.sin(ang) * 0.01);
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        EDAD.clear();
        ANTES.clear();
        VISTO_NORMAL.clear();
        k = kAntes = golpe = golpeAntes = 0;
    }

    /** Pantalla de quien es sombra: oscura y violácea, con bordes de humo que respiran (dos capas que se cruzan). */
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float kk = Mth.lerp(pt, kAntes, k);
        if (kk < 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;
        float gg = Mth.lerp(pt, golpeAntes, golpe);
        float t = mc.player.tickCount + pt;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        int tinte = Math.round(Mth.clamp(0.16F * kk + 0.55F * gg * gg, 0F, 0.85F) * 255);
        g.fill(0, 0, w, h, (tinte << 24) | 0x0A0614);
        for (int i = 0; i < 2; i++) {
            float onda = Mth.sin(t * 0.045F + i * 2.1F);
            float esc = 1.03F + 0.035F * Mth.sin(t * 0.031F + i * 1.7F) - 0.12F * gg;
            float a = kk * (0.55F + 0.25F * (i == 0 ? onda : -onda));
            int ew = Math.round(w * esc), eh = Math.round(h * esc);
            RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(a, 0F, 1F));
            g.blit(VINETA[i], (w - ew) / 2, (h - eh) / 2, ew, eh, 0F, 0F, 256, 256, 256, 256);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }
}
