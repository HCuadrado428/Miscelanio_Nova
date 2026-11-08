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
