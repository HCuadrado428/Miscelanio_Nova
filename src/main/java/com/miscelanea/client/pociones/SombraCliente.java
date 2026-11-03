package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.PocionesRegistro;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sombra (la sombra de Hollow Knight) en el cliente: en vez del modelo normal se dibuja el mismo modelo tumbado
 * (pose de reptar) y aplastado a casi nada de alto, negro y algo translúcido: una silueta plana pegada al suelo, con
 * humo oscuro saliendo. Sin manos en primera persona y con todos los sonidos amortiguados para quien la lleva.
 * (Contra las paredes no se "sube": la silueta siempre va por el suelo.)
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class SombraCliente {
    private SombraCliente() {}

    private static final float ALTO = 0.07F;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void dibujar(RenderLivingEvent.Pre<?, ?> e) {
        LivingEntity v = e.getEntity();
        if (!Estados.sombra(v)) return;
        e.setCanceled(true);
        LivingEntityRenderer r = e.getRenderer();
        EntityModel m = r.getModel();
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        try {
            float yCuerpo = Mth.rotLerp(pt, v.yBodyRotO, v.yBodyRot);
            float yCabeza = Mth.rotLerp(pt, v.yHeadRotO, v.yHeadRot);
            float giroCabeza = Mth.wrapDegrees(yCabeza - yCuerpo);
            float xRot = Mth.lerp(pt, v.xRotO, v.getXRot());
            float edad = v.tickCount + pt;
            float paso = 0, pasoFuerza = 0;
            if (!v.isPassenger() && v.isAlive()) {
                pasoFuerza = Math.min(1F, v.walkAnimation.speed(pt));
                paso = v.walkAnimation.position(pt);
            }
            // aplastada contra el suelo (un pelo por encima para que no parpadee con el bloque)
            ps.translate(0, 0.03, 0);
            ps.scale(1F, ALTO, 1F);
            // como la pose de reptar de LivingEntityRenderer.setupRotations: tumbada, con la cabeza hacia delante
            ps.mulPose(Axis.YP.rotationDegrees(180F - yCuerpo));
            ps.mulPose(Axis.XP.rotationDegrees(-90F));
            ps.translate(0, -1.0F, 0.3F);
            ps.scale(-1F, -1F, 1F);
            ps.translate(0, -1.501F, 0);
            m.prepareMobModel(v, paso, pasoFuerza, pt);
            m.setupAnim(v, paso, pasoFuerza, edad, giroCabeza, xRot);
            ResourceLocation tex = r.getTextureLocation(v);
            VertexConsumer vc = e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(tex));
            m.renderToBuffer(ps, vc, e.getPackedLight(), OverlayTexture.NO_OVERLAY, 0.02F, 0.02F, 0.035F, 0.82F);
        } catch (RuntimeException ex) {
            // algún modelo raro de otro mod: ese tick no se dibuja
        } finally {
            ps.popPose();
        }
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

    /** Humo oscuro saliendo de las sombras que hay a la vista. */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel l = mc.level;
        if (l == null || mc.player == null || mc.isPaused()) return;
        RandomSource r = l.getRandom();
        for (Player p : l.players()) {
            if (!Estados.sombra(p) || p.distanceToSqr(mc.player) > 64 * 64) continue;
            for (int i = 0; i < 2; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 0.6;
                l.addParticle(r.nextInt(3) == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                        p.getX() + Math.cos(a) * d, p.getY() + 0.05, p.getZ() + Math.sin(a) * d, 0, 0.02 + r.nextDouble() * 0.03, 0);
            }
        }
    }
}
