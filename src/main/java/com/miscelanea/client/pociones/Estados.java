package com.miscelanea.client.pociones;

import com.miscelanea.Miscelanea;
import com.miscelanea.pociones.EstadoPacket;
import com.miscelanea.pociones.PocionesRegistro;
import com.miscelanea.pociones.Tamanos;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Lo que el servidor cuenta de los demás (vanilla no sincroniza los efectos ajenos): sombra, niebla, tamaño y a quién
 * imita cada uno. Cada entrada caduca si el servidor deja de reenviarla (manda cada segundo). Para el propio jugador
 * se miran directamente sus efectos (llegan al instante).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class Estados {
    private Estados() {}

    /** Ticks sin noticias tras los que se olvida una entrada. */
    private static final int CADUCA = 70;
    private static final Map<Integer, Estado> MAPA = new HashMap<>();
    private static int reloj;

    public static final class Estado {
        public int banderas, pasos, desde;
        public UUID espejo;
        public String nombre = "";
    }

    /** Desde el arranque del cliente: el tamaño de los demás lo sabe este mapa. */
    public static void iniciar() {
        Tamanos.PASOS_REMOTOS = v -> {
            Estado s = remoto(v);
            return s == null ? 0 : s.pasos;
        };
    }

    public static void recibir(EstadoPacket p) {
        if (p.banderas == 0 && p.pasos == 0) {
            MAPA.remove(p.id);
            return;
        }
        Estado s = MAPA.computeIfAbsent(p.id, k -> new Estado());
        s.banderas = p.banderas;
        s.pasos = p.pasos;
        s.espejo = p.espejo;
        s.nombre = p.nombre;
        s.desde = reloj;
    }

    private static Estado remoto(Entity e) {
        Estado s = MAPA.get(e.getId());
        return s == null || reloj - s.desde > CADUCA ? null : s;
    }

    private static boolean esYo(Entity e) {
        return e == Minecraft.getInstance().player;
    }

    public static boolean sombra(Entity e) {
        if (esYo(e)) return ((LivingEntity) e).hasEffect(PocionesRegistro.SOMBRA.get());
        Estado s = remoto(e);
        return s != null && (s.banderas & EstadoPacket.SOMBRA) != 0;
    }

    public static boolean niebla(Entity e) {
        if (esYo(e)) return ((LivingEntity) e).hasEffect(PocionesRegistro.NIEBLA_VIVIENTE.get());
        Estado s = remoto(e);
        return s != null && (s.banderas & EstadoPacket.NIEBLA) != 0;
    }

    /** A quién imita (espejismo), o null. El propio jugador nunca se ve cambiado. */
    public static Estado espejismo(Entity e) {
        if (esYo(e)) return null;
        Estado s = remoto(e);
        return s != null && (s.banderas & EstadoPacket.ESPEJISMO) != 0 && s.espejo != null ? s : null;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        reloj++;
        if (reloj % 40 != 0) return;
        for (Iterator<Estado> it = MAPA.values().iterator(); it.hasNext(); ) if (reloj - it.next().desde > CADUCA) it.remove();
    }

    @SubscribeEvent
    public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
        MAPA.clear();
    }
}
