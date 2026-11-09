package com.miscelanea.purgatorio;

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
 * Los habitantes del Purgatorio ({@link Purgado}): cuántos hay, dónde salen y qué oyen. Lo que oyen se mira cada 5 tics
 * por jugador: si habla por el chat de voz (Simple Voice Chat, si está), se le oye a 22 bloques; corriendo, a 12;
 * andando, a 4,5; agachado o quieto, nada. Cuántos hay lo dice la configuración ({@link PurgatorioConfig}: unos pocos
 * cerca de cada jugador y un tope para todo el Purgatorio), y salen fuera de la vista (a dos o tres chunks, dentro de
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

    /** Se olvida de un jugador (al salir del Purgatorio o del servidor): que no se queden apuntados para siempre. */
    public static void olvidar(UUID u) {
        HABLA.remove(u);
        ANTES.remove(u);
    }

    static boolean hablando(UUID u) {
        Long t = HABLA.get(u);
        return t != null && System.currentTimeMillis() - t < 700;
    }

    /** Cada tic del jugador en el Purgatorio. */
    static void tick(ServerPlayer p) {
        if (p.isSpectator()) return;
        if (p.tickCount % 5 == 0) escuchar(p);
        if (p.tickCount % 200 == 37) poblar(p);
    }

    static void escuchar(ServerPlayer p) {
        Vec3 ahora = p.position(), antes = ANTES.put(p.getUUID(), ahora);
        double andado = antes == null ? 0 : Math.sqrt(Math.pow(ahora.x - antes.x, 2) + Math.pow(ahora.z - antes.z, 2));
        if (andado > 8) andado = 0;            // se ha teletransportado (o le han recolocado): eso no hace ruido
        double r;
        boolean fuerte;
        if (hablando(p.getUUID())) {
            r = 22;
            fuerte = true;
        } else if (p.isSprinting() && andado > 0.5) {
            r = 12;
            fuerte = true;
        } else if (!p.isShiftKeyDown() && andado > 0.25) {
            r = 4.5;
            fuerte = false;
        } else return;
        for (Purgado g : p.serverLevel().getEntitiesOfClass(Purgado.class, p.getBoundingBox().inflate(r)))
            if (g.distanceToSqr(p) < r * r) g.oye(ahora, fuerte);
    }

    /** Si hay pocos cerca, saca uno en el cruce de un chunk a dos o tres chunks (ya cargado y despejado). */
    static void poblar(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        if (!PurgatorioConfig.activado()) return;
        int max = PurgatorioConfig.habitantesPorJugador();
        if (max <= 0 || l.getEntitiesOfClass(Purgado.class, p.getBoundingBox().inflate(64)).size() >= max) return;
        if (cuantos(l) >= PurgatorioConfig.maxHabitantes()) return;
        var r = p.getRandom();
        int a = (r.nextBoolean() ? 1 : -1) * (2 + r.nextInt(2)), b = r.nextInt(7) - 3;
        int cx = (p.getBlockX() >> 4) + (r.nextBoolean() ? a : b), cz = (p.getBlockZ() >> 4) + (r.nextBoolean() ? b : a);
        if (Math.max(Math.abs(cx - (p.getBlockX() >> 4)), Math.abs(cz - (p.getBlockZ() >> 4))) < 2) return;
        // en el piso del jugador (el cruce de un chunk normal está libre)
        int n = PurgatorioPlano.nivel(p.getY());
        if (!PurgatorioGenerador.entero(n, cx, cz)) return;
        BlockPos sitio = new BlockPos(cx * 16 + 8, PurgatorioPlano.base(n), cz * 16 + 8);
        aparecer(l, sitio, null);
    }

    /** Cuántos habitantes hay ahora en todo el Purgatorio (solo se mira cada 10 s por jugador, y si hay pocos cerca). */
    public static int cuantos(ServerLevel l) {
        return l.getEntities(ModEntities.PURGADO.get(), g -> g.isAlive()).size();
    }

    /** Pone uno en ese sitio (si está cargado y libre). Variante: la de la zona si es null. */
    public static Purgado aparecer(ServerLevel l, BlockPos sitio, Purgado.Variante v) {
        if (!l.hasChunk(sitio.getX() >> 4, sitio.getZ() >> 4) || !l.getBlockState(sitio).isAir() || !l.getBlockState(sitio.above()).isAir()) return null;
        Purgado g = ModEntities.PURGADO.get().create(l);
        if (g == null) return null;
        if (v == null) {
            var z = PurgatorioGenerador.zonaEn(sitio.getX(), sitio.getY(), sitio.getZ());
            int k = l.random.nextInt(10);
            v = z == PurgatorioGenerador.Zona.SIN_TEXTURA ? (k < 7 ? Purgado.Variante.SIN_TEXTURA : Purgado.Variante.RELLENO)
                    : k < 5 ? Purgado.Variante.HEROBRINE : k < 8 ? Purgado.Variante.RELLENO : Purgado.Variante.SIN_TEXTURA;
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
            e.put(ModEntities.PURGADO.get(), Purgado.atributos().build());
        }
    }
}
