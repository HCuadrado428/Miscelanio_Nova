package com.miscelanea.client.montana;

import com.miscelanea.Miscelanea;
import com.miscelanea.montana.TrenMontana;
import com.miscelanea.montana.Via;
import com.miscelanea.registry.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * La montaña rusa en el cliente: el traqueteo (un solo sonido en bucle por tren, que sube con la velocidad), la cámara
 * de quien va montado (gira con el vagón en las curvas, mira hacia arriba y hacia abajo en las cuestas y da la vuelta
 * entera en el rizo), la cuenta atrás de la estación en la barra de acción y la foto de la bajada, que se guarda sola en
 * la galería del móvil. Son unas pocas cuentas por fotograma: nada que note un ordenador flojo.
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
            super(ModSounds.MONTANA_TRAQUETEO.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
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
            float objetivo = v < 0.02F ? 0F : Mth.clamp(0.2F + v * 0.7F, 0F, 1F);
            // sin saltos de volumen al arrancar y al parar
            volume += (objetivo - volume) * 0.3F;
            pitch = Mth.clamp(0.7F + v * 0.55F, 0.7F, 1.5F);
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
        float pt = e.getPartialTick();
        Via.Sitio s = t.sitioDibujo(t.asiento(le) / 2, pt);
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
    private static int cuentaAntes = -1;
    @Nullable
    private static Via.Clase claseAntes;

    /** El vagón gira en las curvas y el que va dentro gira con él (la cabeza la sigue moviendo libremente). */
    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        TrenMontana t = montado(p);
        if (t != ultimo) {
            ultimo = t;
            cuentaAntes = -1;
            claseAntes = null;
            if (t != null) {
                t.reiniciarGiro();
                t.giroVagon(vagonDe(t, p));
            }
            return;
        }
        if (t == null) return;
        int k = vagonDe(t, p);
        float d = t.giroVagon(k);
        if (Math.abs(d) > 0.01F && Math.abs(d) < 120) {
            p.setYRot(p.getYRot() + d);
            p.yRotO += d;
        }
        // cuenta atrás de la estación (en la barra de acción, no en el mundo)
        int c = t.cuenta();
        if (c != cuentaAntes) {
            if (c > 0) mc.gui.setOverlayMessage(Component.translatable("montana.miscelanea.sale", c), false);
            else if (cuentaAntes > 0) mc.gui.setOverlayMessage(Component.translatable("montana.miscelanea.sale_ya"), false);
            cuentaAntes = c;
        }
        // la foto de la bajada: un poco después de empezar a caer
        Via.Sitio s = t.sitioVagon(k);
        Via.Clase cl = s == null ? null : s.pieza().clase();
        if (cl == Via.Clase.BAJADA && claseAntes != Via.Clase.BAJADA && claseAntes != null && t.velocidad() > 0.15F
                && Util.getMillis() - ultimaFoto > 25_000 && fotoFase == 0 && FotoMontana.puede(p)) fotoCuenta = 4;
        claseAntes = cl;
        if (fotoCuenta > 0 && --fotoCuenta == 0 && mc.screen == null) empezarFoto(mc);
    }

    /** Asiento del jugador en el tren → vagón. */
    static int vagonDe(TrenMontana t, LocalPlayer p) {
        return t.asiento(p) / 2;
    }

    @SubscribeEvent
    public static void camara(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        TrenMontana t = montado(p);
        if (t == null || !mc.options.getCameraType().isFirstPerson()) return;
        Via.Sitio s = t.sitioDibujo(vagonDe(t, p), (float) e.getPartialTick());
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

    // ------------------------------------------------------------------ foto de la bajada
    private static long ultimaFoto = -100_000, flash;
    private static int fotoCuenta, fotoFase, fotoFotogramas;
    @Nullable
    private static CameraType camAntes;

    /** Se pone la cámara de frente (como la de las atracciones de verdad) y se pide la imagen al capturador del móvil. */
    private static void empezarFoto(Minecraft mc) {
        camAntes = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        FotoMontana.pedir();
        fotoFase = 1;
        fotoFotogramas = 0;
        ultimaFoto = Util.getMillis();
    }

    @SubscribeEvent
    public static void fotograma(TickEvent.RenderTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            // por si algún dibujo se canceló entre el Pre y el Post: que no se quede nada colgado
            GIRADOS.clear();
            if (fotoFase == 1) FotoMontana.pedir();
            return;
        }
        if (fotoFase != 1 || ++fotoFotogramas < 2) return;
        Minecraft mc = Minecraft.getInstance();
        fotoFase = 0;
        FotoMontana.sacar();
        if (camAntes != null) mc.options.setCameraType(camAntes);
        camAntes = null;
        flash = Util.getMillis();
    }

    /** El fogonazo de la cámara de la atracción (tapa el cambio de cámara de un instante). */
    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        long dt = Util.getMillis() - flash;
        if (fotoFase == 1) dt = 0;
        if (dt > 450) return;
        int a = (int) (235 * (1 - dt / 450F));
        var g = e.getGuiGraphics();
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFFFFF);
    }

    /** Para las pruebas: hace la foto de la bajada ya (si va montado). */
    public static void pruebaFoto() {
        Minecraft mc = Minecraft.getInstance();
        if (montado(mc.player) != null && fotoFase == 0) empezarFoto(mc);
    }

    /** Para las pruebas: cuántas fotos de la montaña se han mandado a la galería. */
    public static int fotosHechas() {
        return FotoMontana.hechas;
    }
}
