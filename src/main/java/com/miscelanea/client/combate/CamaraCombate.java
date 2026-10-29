package com.miscelanea.client.combate;

import com.miscelanea.Miscelanea;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.function.Function;

/**
 * Cámara libre de combate en tercera persona (como en Kingdom Hearts / God of War):
 * - El ratón gira la cámara; el jugador se gira hacia donde anda (o hacia donde ataca).
 * - Distancia, altura y desplazamiento lateral dependen del juego del arma.
 * - Se puede fijar un objetivo: la cámara lo sigue.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CamaraCombate {
    /** Colocación de la cámara de cada juego. */
    public record Estilo(double distancia, double altura, double lateral, float giroMax, float pitchMin, float pitchMax) {}

    public static final Estilo KINGDOM_HEARTS = new Estilo(5.2, 1.55, 0.0, 30F, -40F, 70F);
    public static final Estilo GOD_OF_WAR = new Estilo(2.4, 1.62, 0.7, 25F, -50F, 60F);

    /** Qué estilo de cámara toca con lo que lleva el jugador (lo rellenan las armas). */
    public static Function<LocalPlayer, Estilo> ESTILO = p -> null;

    private static Estilo estilo;
    private static float yaw, pitch;
    private static CameraType antes;
    private static LivingEntity fijado;
    private static long ultimoFrame;
    /** Mientras un ataque manda en el giro del jugador, el movimiento no lo gira. */
    private static int bloqueoGiro;
    /** Mientras > 0 el jugador no se mueve por el teclado (está atacando). */
    private static int bloqueoMovimiento;

    private CamaraCombate() {}

    public static boolean activa() {
        return estilo != null;
    }

    public static float yaw() {
        return yaw;
    }

    public static LivingEntity fijado() {
        return fijado;
    }

    public static void fijar(LivingEntity e) {
        fijado = e;
    }

    public static void bloquear(int giroTicks, int movimientoTicks) {
        bloqueoGiro = Math.max(bloqueoGiro, giroTicks);
        bloqueoMovimiento = Math.max(bloqueoMovimiento, movimientoTicks);
    }

    /** El ratón: gira la cámara. Devuelve false si la cámara de combate no está activa. */
    public static boolean girar(double dy, double dx) {
        if (estilo == null) return false;
        yaw += (float) dy * 0.15F;
        pitch = Mth.clamp(pitch + (float) dx * 0.15F, estilo.pitchMin(), estilo.pitchMax());
        return true;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        Estilo nuevo = p == null || p.isSpectator() ? null : ESTILO.apply(p);
        if (nuevo != null && estilo == null) {
            yaw = p.getYRot();
            pitch = 15F;
            antes = mc.options.getCameraType();
        } else if (nuevo == null && estilo != null) {
            if (p != null) {
                p.setYRot(yaw);
                p.setXRot(Mth.clamp(pitch, -90, 90));
            }
            if (antes != null) mc.options.setCameraType(antes);
            antes = null;
            fijado = null;
        }
        estilo = nuevo;
        if (estilo == null) return;
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        if (fijado != null && (!fijado.isAlive() || fijado.level() != p.level() || fijado.distanceToSqr(p) > 28 * 28)) fijado = null;
        if (bloqueoGiro > 0) bloqueoGiro--;
        if (bloqueoMovimiento > 0) bloqueoMovimiento--;
    }

    /** Movimiento relativo a la cámara: el jugador se gira hacia donde anda. */
    @SubscribeEvent
    public static void mover(MovementInputUpdateEvent e) {
        if (estilo == null || !(e.getEntity() instanceof LocalPlayer p)) return;
        var in = e.getInput();
        if (bloqueoMovimiento > 0) {
            in.forwardImpulse = 0;
            in.leftImpulse = 0;
            return;
        }
        float f = in.forwardImpulse, l = in.leftImpulse;
        float r = yaw * Mth.DEG_TO_RAD;
        double mx = -Mth.sin(r) * f + Mth.cos(r) * l;
        double mz = Mth.cos(r) * f + Mth.sin(r) * l;
        double largo = Math.sqrt(mx * mx + mz * mz);
        if (largo > 0.01) {
            float deseado = (float) (Mth.atan2(-mx, mz) * Mth.RAD_TO_DEG);
            if (bloqueoGiro <= 0) {
                float d = Mth.wrapDegrees(deseado - p.getYRot());
                p.setYRot(p.getYRot() + Mth.clamp(d, -estilo.giroMax(), estilo.giroMax()));
                // si aún mira muy hacia otro lado, que no corra de lado: avanza solo lo que mira
                float resto = Math.abs(Mth.wrapDegrees(deseado - p.getYRot()));
                in.forwardImpulse = (float) Math.min(1, largo) * Mth.clamp(1 - resto / 120F, 0.15F, 1);
                in.leftImpulse = 0;
            } else {
                // atacando mirando a un sitio: se mueve relativo a donde mira
                float ry = p.getYRot() * Mth.DEG_TO_RAD;
                in.forwardImpulse = (float) (-mx * Mth.sin(ry) + mz * Mth.cos(ry));
                in.leftImpulse = (float) (mx * Mth.cos(ry) + mz * Mth.sin(ry));
            }
        }
        p.setXRot(Mth.clamp(pitch * 0.6F, -30, 45));
    }

    /** Llamado desde el mixin de Camera.setup: posición y giro de la cámara, o null para dejar la de vanilla. */
    public static double[] colocar(BlockGetter level, Entity ent, float partial) {
        if (estilo == null || ent != Minecraft.getInstance().player) return null;
        long ahora = System.nanoTime();
        float dt = ultimoFrame == 0 ? 0.016F : Math.min(0.1F, (ahora - ultimoFrame) / 1e9F);
        ultimoFrame = ahora;
        Vec3 pivote = new Vec3(Mth.lerp(partial, ent.xo, ent.getX()), Mth.lerp(partial, ent.yo, ent.getY()) + estilo.altura(),
                Mth.lerp(partial, ent.zo, ent.getZ()));
        if (fijado != null) {
            Vec3 obj = fijado.getPosition(partial).add(0, fijado.getBbHeight() * 0.5, 0);
            Vec3 d = obj.subtract(pivote);
            float deseadoYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
            float deseadoPitch = Mth.clamp((float) (-Mth.atan2(d.y, d.horizontalDistance()) * Mth.RAD_TO_DEG) + 14F, -20F, 50F);
            float k = Math.min(1, dt * 7F);
            yaw += Mth.wrapDegrees(deseadoYaw - yaw) * k;
            pitch += (deseadoPitch - pitch) * k;
        }
        Vec3 delante = Vec3.directionFromRotation(pitch, yaw);
        float r = yaw * Mth.DEG_TO_RAD;
        Vec3 derecha = new Vec3(-Mth.cos(r), 0, -Mth.sin(r));
        Vec3 lado = pivote.add(derecha.scale(estilo.lateral()));
        lado = choque(level, ent, pivote, lado);
        Vec3 pos = choque(level, ent, lado, lado.subtract(delante.scale(estilo.distancia())));
        return new double[]{pos.x, pos.y, pos.z, yaw, pitch};
    }

    private static Vec3 choque(BlockGetter level, Entity ent, Vec3 desde, Vec3 hasta) {
        Vec3 dir = hasta.subtract(desde);
        double largo = dir.length();
        if (largo < 1e-4) return hasta;
        Vec3 u = dir.scale(1 / largo);
        double mejor = largo;
        // cuatro rayos un poco separados (como vanilla) para no meter la cámara en la pared
        for (int i = 0; i < 4; i++) {
            Vec3 off = new Vec3(((i & 1) * 2 - 1) * 0.1, ((i >> 1) * 2 - 1) * 0.1, ((i & 1) * 2 - 1) * 0.1);
            var hit = level.clip(new ClipContext(desde.add(off), hasta.add(off).add(u.scale(0.2)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, ent));
            if (hit.getType() != HitResult.Type.MISS) mejor = Math.min(mejor, hit.getLocation().distanceTo(desde.add(off)) - 0.2);
        }
        return desde.add(u.scale(Math.max(0, mejor)));
    }
}
