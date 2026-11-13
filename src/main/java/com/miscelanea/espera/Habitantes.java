package com.miscelanea.espera;

import com.miscelanea.Miscelanea;
import com.miscelanea.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Los habitantes de La Espera ({@link Paciente}): cuántos hay, dónde salen y a quién van. Se mira cada 5 tics por
 * jugador y solo cuenta la distancia: moverse, correr o hablar no te delata.
 * andando, a 4,5; agachado o quieto, nada. Cuántos hay lo dice la configuración ({@link EsperaConfig}: unos pocos
 * cerca de cada jugador y un tope para todo el Espera), y salen fuera de la vista (a dos o tres chunks, dentro de
 * la niebla). No se guardan con el mundo: si no hay nadie cerca, desaparecen.
 */
public final class Habitantes {
    private Habitantes() {}

    /** Última vez (ms) que cada jugador habló por el chat de voz: lo apunta el plugin del voicechat, desde su hilo. */
    public static final Map<UUID, Long> HABLA = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> ANTES = new ConcurrentHashMap<>();

    public static void habla(UUID u) {
        HABLA.put(u, System.currentTimeMillis());
    }

    /** Se olvida de un jugador (al salir del Espera o del servidor): que no se queden apuntados para siempre. */
    public static void olvidar(UUID u) {
        HABLA.remove(u);
        ANTES.remove(u);
    }

    static boolean hablando(UUID u) {
        Long t = HABLA.get(u);
        return t != null && System.currentTimeMillis() - t < 700;
    }

    /** Cada tic del jugador en el Espera. */
    static void tick(ServerPlayer p) {
        if (p.isSpectator()) return;
        if (p.tickCount % 5 == 0) detectar(p);
        if (p.tickCount % 200 == 37) poblar(p);
    }

    /**
     * A quién van los Pacientes. No oyen: no les importa si corres, si hablas o si vas agachado, solo lo cerca que
     * estés. Así se puede jugar normal sin que moverse te castigue.
     */
    static void detectar(ServerPlayer p) {
        // con el "No molestar" de la llave del hotel colgado, no te hacen caso
        if (com.miscelanea.espera.eventos.EsperaExtras.noMolestar(p)) return;
        double r = EsperaConfig.alcanceHabitantes();
        if (r <= 0) return;
        Vec3 donde = p.position();
        for (Paciente g : p.serverLevel().getEntitiesOfClass(Paciente.class, p.getBoundingBox().inflate(r)))
            if (g.distanceToSqr(p) < r * r) g.oye(donde, g.distanceToSqr(p) < r * r / 4);
    }

    /** Si hay pocos cerca, saca uno en el cruce de un chunk a dos o tres chunks (ya cargado y despejado). */
    static void poblar(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (!EsperaConfig.activado()) return;
        int max = EsperaConfig.habitantesPorJugador();
        if (max <= 0 || l.getEntitiesOfClass(Paciente.class, p.getBoundingBox().inflate(64)).size() >= max) return;
        if (cuantos(l) >= EsperaConfig.maxHabitantes()) return;
        var r = p.getRandom();
        int a = (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(2)), b = r.nextInt(7) - 3;
        int cx = (p.getBlockX() >> 4) + (r.nextBoolean() ? a : b), cz = (p.getBlockZ() >> 4) + (r.nextBoolean() ? b : a);
        if (Math.max(Math.abs(cx - (p.getBlockX() >> 4)), Math.abs(cz - (p.getBlockZ() >> 4))) < 2) return;
        // en el piso del jugador (el cruce de un chunk normal está libre)
        int n = EsperaPlano.nivel(p.getY());
        if (!EsperaGenerador.entero(n, cx, cz)) return;
        BlockPos sitio = new BlockPos(cx * 16 + 8, EsperaPlano.base(n), cz * 16 + 8);
        aparecer(l, sitio, null);
    }

    /** Cuántos habitantes hay ahora en todo el Espera (solo se mira cada 10 s por jugador, y si hay pocos cerca). */
    public static int cuantos(ServerLevel l) {
        return l.getEntities(ModEntities.PACIENTE.get(), g -> g.isAlive()).size();
    }

    /** Pone uno en ese sitio (si está cargado y libre). Variante: la de la zona si es null. */
    public static Paciente aparecer(ServerLevel l, BlockPos sitio, Paciente.Variante v) {
        if (!l.hasChunk(sitio.getX() >> 4, sitio.getZ() >> 4) || !l.getBlockState(sitio).isAir() || !l.getBlockState(sitio.above()).isAir()) return null;
        Paciente g = ModEntities.PACIENTE.get().create(l);
        if (g == null) return null;
        if (v == null) {
            var z = EsperaGenerador.zonaEn(sitio.getX(), sitio.getY(), sitio.getZ());
            int k = l.random.nextInt(10);
            v = z == EsperaGenerador.Zona.SIN_TEXTURA ? (k < 7 ? Paciente.Variante.SIN_TEXTURA : Paciente.Variante.RELLENO)
                    : k < 5 ? Paciente.Variante.HEROBRINE : k < 8 ? Paciente.Variante.RELLENO : Paciente.Variante.SIN_TEXTURA;
        }
        g.setVariante(v);
        g.moveTo(sitio.getX() + 0.5, sitio.getY(), sitio.getZ() + 0.5, l.random.nextFloat() * 360, 0);
        l.addFreshEntity(g);
        return g;
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Registro {
        @SubscribeEvent
        public static void atributos(EntityAttributeCreationEvent e) {
            e.put(ModEntities.PACIENTE.get(), Paciente.atributos().build());
        }
    }
}
