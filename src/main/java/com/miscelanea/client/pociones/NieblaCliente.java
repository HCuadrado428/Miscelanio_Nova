package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PocionesRegistro;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Niebla viviente (Silent Hill) en el cliente: una niebla espesa y cenicienta que te sigue (empieza a 2 bloques y
 * lo tapa todo a 6), ceniza cayendo, una sirena antiaérea lejana en bucle y grano en la imagen. A los demás
 * jugadores, cuando están a menos de 12 bloques de alguien con niebla, la niebla también se les cierra (sin sirena).
 * Que no veas a los mobs es cosa de la niebla; que ellos no te vean, del servidor.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class NieblaCliente {
    private NieblaCliente() {}

    private static final ResourceLocation GRANO = new ResourceLocation(Miscelanea.MOD_ID, "textures/pociones/grano.png");
    private static final float CERCA = 2F, LEJOS = 6F, CONTAGIO = 12F;
    /** 0..1: cuánto se cierra la niebla (1 = la llevas tú); se suaviza entre ticks. */
    private static float k, kAntes;
    private static Sirena sirena;

    private static final class Sirena extends AbstractTickableSoundInstance {
        Sirena() {
            super(PocionesRegistro.SIRENA.get(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = 0.55F;
        }

        @Override
        public void tick() {
            LocalPlayer p = Minecraft.getInstance().player;
            if (p == null || !p.hasEffect(PocionesRegistro.NIEBLA_VIVIENTE.get())) stop();
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        ClientLevel l = mc.level;
        kAntes = k;
        if (p == null || l == null) {
            k = 0;
            return;
        }
        boolean propia = p.hasEffect(PocionesRegistro.NIEBLA_VIVIENTE.get());
        float objetivo = 0;
        if (propia) objetivo = 1;
        else {
            for (Player o : l.players()) {
                if (o == p || !Estados.niebla(o)) continue;
                float d = o.distanceTo(p);
                if (d < CONTAGIO) objetivo = Math.max(objetivo, 1F - d / CONTAGIO);
            }
        }
        if (mc.isPaused()) objetivo = k;
        k += (objetivo - k) * 0.08F;
        if (k < 0.002F) k = 0;
        // sirena
        if (propia && (sirena == null || !mc.getSoundManager().isActive(sirena))) {
            sirena = new Sirena();
            mc.getSoundManager().play(sirena);
        }
        // ceniza cayendo alrededor (más cuanto más cerrada)
        if (k > 0.05F && !mc.isPaused()) {
            RandomSource r = l.getRandom();
            int n = Math.round(k * 6);
            for (int i = 0; i < n; i++) {
                double x = p.getX() + r.nextGaussian() * 5, y = p.getEyeY() + 1 + r.nextDouble() * 4, z = p.getZ() + r.nextGaussian() * 5;
                l.addParticle(r.nextInt(4) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, x, y, z, 0, -0.02, 0);
            }
        }
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        k = kAntes = 0;
        sirena = null;
    }

    static float fuerza(float pt) {
        return Mth.lerp(pt, kAntes, k);
    }

    // ------------------------------------------------------------------ la niebla
    @SubscribeEvent
    public static void niebla(ViewportEvent.RenderFog e) {
        float f = fuerza((float) e.getPartialTick());
        if (f < 0.004F || e.getType() != FogType.NONE) return;
        float lejos = e.getFarPlaneDistance(), cerca = e.getNearPlaneDistance();
        boolean cielo = e.getMode() == FogRenderer.FogMode.FOG_SKY;
        // de la distancia normal a 6 bloques (2 para el cielo), con curva: la mitad del camino ya tapa casi todo
        float ff = f * f * (3 - 2 * f);
        float nl = Mth.lerp(ff, lejos, cielo ? LEJOS * 0.4F : LEJOS);
        float nc = Mth.lerp(ff, Math.min(cerca, nl), cielo ? 0F : CERCA);
        e.setFarPlaneDistance(Math.min(lejos, nl));
        e.setNearPlaneDistance(Math.min(nc, nl * 0.9F));
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void colorNiebla(ViewportEvent.ComputeFogColor e) {
        float f = fuerza((float) e.getPartialTick());
        if (f < 0.004F || e.getCamera().getFluidInCamera() != FogType.NONE) return;
        // gris ceniza, un pelo cálido
        float kc = f * 0.9F;
        e.setRed(Mth.lerp(kc, e.getRed(), 0.62F));
        e.setGreen(Mth.lerp(kc, e.getGreen(), 0.6F));
        e.setBlue(Mth.lerp(kc, e.getBlue(), 0.57F));
    }

    // ------------------------------------------------------------------ grano
    public static void hud(ForgeGui gui, GuiGraphics g, float pt, int w, int h) {
        float f = fuerza(pt);
        if (f < 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        RandomSource r = RandomSource.create(mc.level == null ? 0 : mc.level.getGameTime() * 7919L + (long) (pt * 1000));
        int T = 128;
        int ox = r.nextInt(T), oy = r.nextInt(T);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, 0.22F * f);
        for (int y = -T; y < h + T; y += T)
            for (int x = -T; x < w + T; x += T)
                g.blit(GRANO, x + ox - T, y + oy - T, 0, 0, T, T, T, T);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        // un velo gris muy leve que oscurece el centro menos que los bordes
        int a = Math.round(f * 40);
        g.fillGradient(0, 0, w, h / 3, (a << 24) | 0x404040, 0x00404040);
        g.fillGradient(0, h - h / 3, w, h, 0x00404040, (a << 24) | 0x404040);
        RenderSystem.disableBlend();
    }
}
