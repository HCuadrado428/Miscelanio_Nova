package com.miscelanea.client.montana;

import com.miscelanea.Miscelanea;
import com.miscelanea.montana.TrenMontana;
import com.miscelanea.montana.Via;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * La montaña rusa en el cliente: el traqueteo (un solo sonido en bucle por tren, que sube con la velocidad), y la cámara
 * de quien va montado, que se inclina en las curvas, mira hacia arriba y hacia abajo en las cuestas y da la vuelta
 * entera en el rizo. Son unas pocas cuentas por fotograma: nada que note un ordenador flojo.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class MontanaCliente {
    private MontanaCliente() {}

    // ------------------------------------------------------------------ sonido
    /** Pone a sonar el traqueteo del tren (o deja el que ya suena). */
    public static Object sonar(TrenMontana t, @Nullable Object antes) {
        if (antes instanceof Traqueteo s && !s.isStopped()) return s;
        Traqueteo s = new Traqueteo(t);
        Minecraft.getInstance().getSoundManager().play(s);
        return s;
    }

    static final class Traqueteo extends AbstractTickableSoundInstance {
        private final TrenMontana tren;

        Traqueteo(TrenMontana t) {
            super(SoundEvents.MINECART_RIDING, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
            tren = t;
            looping = true;
            delay = 0;
            volume = 0.0001F;
            x = t.getX();
            y = t.getY();
            z = t.getZ();
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (tren.isRemoved()) {
                stop();
                return;
            }
            x = tren.getX();
            y = tren.getY();
            z = tren.getZ();
            float v = Math.abs(tren.velocidad());
            if (v < 0.02F) volume = 0F;
            else volume = Mth.clamp(0.15F + v * 0.6F, 0F, 0.9F);
            pitch = Mth.clamp(0.6F + v * 0.6F, 0.6F, 1.6F);
        }
    }

    // ------------------------------------------------------------------ pasajeros (vistos desde fuera)
    /**
     * Los que van montados se dibujan girados igual que su vagón: inclinados en las curvas y las cuestas y boca abajo en
     * lo alto del rizo. Se gira la pose entera alrededor de los pies (que ya están en el asiento); es una matriz por
     * pasajero. Va con la prioridad más baja para que nadie cancele el dibujo después de abrir la pose.
     */
    private static final java.util.Set<net.minecraft.world.entity.LivingEntity> GIRADOS = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void antesPasajero(net.minecraftforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        var le = e.getEntity();
        if (!(le.getVehicle() instanceof TrenMontana t)) return;
        int i = t.getPassengers().indexOf(le);
        if (i < 0) return;
        float pt = e.getPartialTick();
        Via.Sitio s = t.sitioVagon(i / 2, t.velocidad() * (pt - 1));
        if (s == null) return;
        Vec3[] b = TrenMontanaRenderer.base(s);
        Vec3 tg = b[0], up = b[1];
        Vec3 izq = up.cross(tg);
        // hacia dónde mira el cuerpo tal y como lo va a dibujar Minecraft (solo con su giro horizontal)
        float yaw = Mth.rotLerp(pt, le.yBodyRotO, le.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 fa = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 ya = new Vec3(0, 1, 0);
        Vec3 ia = ya.cross(fa);
        org.joml.Matrix3f B = new org.joml.Matrix3f((float) izq.x, (float) izq.y, (float) izq.z, (float) up.x, (float) up.y, (float) up.z, (float) tg.x, (float) tg.y, (float) tg.z);
        org.joml.Matrix3f A = new org.joml.Matrix3f((float) ia.x, (float) ia.y, (float) ia.z, (float) ya.x, (float) ya.y, (float) ya.z, (float) fa.x, (float) fa.y, (float) fa.z);
        org.joml.Matrix3f R = B.mul(A.transpose());
        // casi derecho (en la estación o en una recta): no se toca nada
        if (R.m11() > 0.9995F) return;
        org.joml.Quaternionf q = new org.joml.Quaternionf().setFromNormalized(R);
        e.getPoseStack().pushPose();
        e.getPoseStack().mulPose(q);
        GIRADOS.add(le);
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST, receiveCanceled = true)
    public static void despuesPasajero(net.minecraftforge.client.event.RenderLivingEvent.Post<?, ?> e) {
        if (GIRADOS.remove(e.getEntity())) e.getPoseStack().popPose();
    }

    // ------------------------------------------------------------------ cámara
    @Nullable
    static TrenMontana montado(LocalPlayer p) {
        return p != null && p.getVehicle() instanceof TrenMontana t ? t : null;
    }

    @Nullable
    private static TrenMontana ultimo;

    /** El vagón gira en las curvas y el que va dentro gira con él (la cabeza la sigue moviendo libremente). */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        LocalPlayer p = Minecraft.getInstance().player;
        TrenMontana t = montado(p);
        if (t != ultimo) {
            ultimo = t;
            if (t != null) t.giroTick();     // al subir no se gira: solo cuenta lo que gire a partir de ahora
            return;
        }
        if (t == null) return;
        float d = t.giroTick();
        if (Math.abs(d) > 0.01F && Math.abs(d) < 120) {
            p.setYRot(p.getYRot() + d);
            p.yRotO += d;
        }
    }

    /** Asiento del jugador en el tren (0..vagones*2-1) → vagón. */
    static int vagonDe(TrenMontana t, LocalPlayer p) {
        int i = t.getPassengers().indexOf(p);
        return Math.max(0, i) / 2;
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        TrenMontana t = montado(p);
        if (t == null || !mc.options.getCameraType().isFirstPerson()) return;
        float pt = (float) e.getPartialTick();
        Via.Sitio s = t.sitioVagon(vagonDe(t, p), t.velocidad() * (pt - 1));
        if (s == null) return;
        Vec3[] base = TrenMontanaRenderer.base(s);
        Vec3 tg = base[0], up = base[1], der = base[2];
        // cabeceo de la vía: en el rizo, con el sentido de avance del trozo, para que dé la vuelta entera sin saltos
        Vec3 F = Vec3.atLowerCornerOf(s.pieza().dir().getNormal());
        double hor = s.pieza().clase() == Via.Clase.RIZO ? tg.dot(F) : Math.sqrt(tg.x * tg.x + tg.z * tg.z);
        float cabeceo = (float) (-Math.atan2(tg.y, hor) * Mth.RAD_TO_DEG);
        // alabeo (peralte de las curvas)
        float alabeo = (float) (Math.asin(Mth.clamp(up.dot(der), -1, 1)) * Mth.RAD_TO_DEG);
        e.setPitch(e.getPitch() + cabeceo);
        e.setRoll(e.getRoll() + alabeo);
    }
}
