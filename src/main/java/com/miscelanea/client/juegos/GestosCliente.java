package com.miscelanea.client.juegos;

import com.miscelanea.Miscelanea;
import com.miscelanea.juegos.Gestos;
import com.miscelanea.juegos.MesasRed;
import com.miscelanea.mesas.AirHockeyBlockEntity;
import com.miscelanea.mesas.AirHockeyPartida;
import com.miscelanea.mesas.FutbolinBlockEntity;
import com.miscelanea.mesas.FutbolinPartida;
import com.miscelanea.mesas.MesaGeo;
import com.miscelanea.mesas.Visibles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Gestos de los jugadores en las mesas de juego (los manda el servidor) y posturas continuas (manos en las barras del
 * futbolín, mano en el mazo del air hockey). Los pinta la capa {@link AnimGesto} de playerAnimator.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GestosCliente {
    /** Por debajo de las cinemáticas (tortura 1250-1312, muertes 1410, baile 1250) y por encima del agarre (1000). */
    public static final int PRIORIDAD = 1105;

    public record Activo(int tipo, Vec3 objetivo, long inicio) {}

    /** Postura continua: dónde van las manos (null = esa mano libre) y cuánto se está chutando (0..1). */
    public record Postura(@Nullable Vec3 manoD, @Nullable Vec3 manoI, float chut, float inclina) {}

    private static final Map<Integer, Activo> ACTIVOS = new HashMap<>();

    private GestosCliente() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent e) {
        dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                new ResourceLocation(Miscelanea.MOD_ID, "gestos_mesa"), PRIORIDAD, AnimGesto::new);
    }

    public static void recibir(MesasRed.GestoPacket p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        // la jugada que gana llega justo después de la celebración: no la tapa
        Activo antes = de(p.entidad);
        if (antes != null && antes.tipo >= Gestos.PUNO && p.tipo < Gestos.PUNO && mc.level.getGameTime() - antes.inicio < 3) return;
        ACTIVOS.put(p.entidad, new Activo(p.tipo, new Vec3(p.x, p.y, p.z), mc.level.getGameTime()));
    }

    /** Duración de cada gesto en ticks. */
    public static int duracion(int tipo) {
        return switch (tipo) {
            case Gestos.ALCANZAR -> 8;
            case Gestos.DADOS -> 22;
            case Gestos.CARTA -> 10;
            case Gestos.TACO -> 18;
            case Gestos.MANIVELA, Gestos.PALA -> 8;
            case Gestos.PUNO -> 32;
            case Gestos.APLAUSO -> 32;
            case Gestos.BAILE -> 40;
            case Gestos.FACEPALM -> 36;
            case Gestos.ENCOGERSE -> 26;
            default -> 10;
        };
    }

    @Nullable
    public static Activo de(int entidad) {
        Activo a = ACTIVOS.get(entidad);
        if (a == null) return null;
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level == null ? Long.MAX_VALUE : mc.level.getGameTime();
        if (t < a.inicio || t - a.inicio > duracion(a.tipo) + 1) {
            ACTIVOS.remove(entidad);
            return null;
        }
        return a;
    }

    public static float tiempo(Activo a, float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime() - a.inicio + pt;
    }

    /** Postura continua del jugador en una mesa de juego física (o null si no está jugando en ninguna). */
    @Nullable
    public static Postura postura(AbstractClientPlayer p, float pt) {
        UUID u = p.getUUID();
        for (FutbolinBlockEntity be : Visibles.FUTBOLIN) {
            if (be.isRemoved() || be.getLevel() != p.level()) continue;
            int e = be.lado(u);
            if (e < 0 || be.p.fase == FutbolinPartida.ESPERA) continue;
            return futbolin(be, e, p, pt);
        }
        for (AirHockeyBlockEntity be : Visibles.AIRHOCKEY) {
            if (be.isRemoved() || be.getLevel() != p.level()) continue;
            int e = be.lado(u);
            if (e < 0 || be.p.fase == AirHockeyPartida.ESPERA) continue;
            Direction f = be.facing();
            float mu = be.p.mu[e], mv = be.p.mv[e];
            if (System.currentTimeMillis() - be.ultimoFisica < 200 && be.antes.length >= 6) {
                mu = be.antes[2 + e * 2] + (mu - be.antes[2 + e * 2]) * pt;
                mv = be.antes[3 + e * 2] + (mv - be.antes[3 + e * 2]) * pt;
            }
            Vec3 m = Vec3.atLowerCornerOf(be.getBlockPos()).add(MesaGeo.local(f, mu, mv, 13F / 16 + 0.08));
            double lejos = Math.min(1.0, Math.max(0, m.distanceTo(p.getPosition(pt).add(0, 1.2, 0)) - 0.5));
            return new Postura(m, null, 0, (float) lejos);
        }
        return null;
    }

    private static Postura futbolin(FutbolinBlockEntity be, int e, AbstractClientPlayer p, float pt) {
        Direction f = be.facing();
        float s = be.p.s[e];
        if (System.currentTimeMillis() - be.ultimoFisica < 200) s = (e == 0 ? be.antesS0 : be.antesS1) + (s - (e == 0 ? be.antesS0 : be.antesS1)) * pt;
        Vec3 base = Vec3.atLowerCornerOf(be.getBlockPos());
        Vec3 pos = p.getPosition(pt);
        // las dos barras de su equipo más cercanas: la de su derecha para la mano derecha y la otra para la izquierda
        Vec3 a = null, b = null;
        double da = 1e9, db = 1e9;
        for (int k = 0; k < 8; k++) {
            if (FutbolinPartida.EQUIPO[k] != e) continue;
            float desp = s * FutbolinPartida.limite(k);
            Vec3 m = base.add(MesaGeo.local(f, FutbolinPartida.barraU(k), desp + (e == 0 ? -0.45 : 2.45), 14F / 16));
            double d = m.distanceToSqr(pos);
            if (d < da) {
                b = a;
                db = da;
                a = m;
                da = d;
            } else if (d < db) {
                b = m;
                db = d;
            }
        }
        if (a == null) return null;
        if (b == null) b = a;
        // ¿cuál queda a su derecha?
        float yaw = (float) Math.toRadians(p.yBodyRot);
        Vec3 der = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 manoD = a.subtract(pos).dot(der) >= b.subtract(pos).dot(der) ? a : b;
        Vec3 manoI = manoD == a ? b : a;
        float chut = be.p.chut[e] > 0 ? Math.min(1, (be.p.chut[e] - pt) / 6F) : 0;
        return new Postura(manoD, manoI, chut, 0.3F);
    }
}
