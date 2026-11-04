package com.miscelanea.client.juegos;

import com.miscelanea.juegos.MesasRed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Relojes de turno de las mesas cercanas (los manda el servidor); los pinta {@link PanelJuego}. */
public final class RelojCliente {
    /** Si no se refresca en este tiempo (ticks), el reloj se quita solo. */
    private static final int CADUCA = 30;
    private static final Map<BlockPos, Reloj> RELOJES = new HashMap<>();

    public static final class Reloj {
        public BlockPos pos;
        @Nullable
        public UUID esperado;
        public String nombre = "";
        public long fin, recibido;
        public int total;
        public boolean dormido, conLimite;
        int ultimoPitido = -1;

        /** Segundos que le quedan (con decimales); negativo si no hay límite. */
        public float segundos(float pt) {
            if (!conLimite) return -1;
            return Math.max(0, (fin - ahora() - pt) / 20F);
        }

        public float fraccion(float pt) {
            return !conLimite || total <= 0 ? 1F : Math.max(0, Math.min(1, (fin - ahora() - pt) / total));
        }

        public boolean mio() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.getUUID().equals(esperado);
        }
    }

    private RelojCliente() {}

    private static long ahora() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void recibir(MesasRed.RelojPacket p) {
        if (p.esperado == null) {
            RELOJES.remove(p.pos);
            return;
        }
        Reloj r = RELOJES.computeIfAbsent(p.pos.immutable(), k -> new Reloj());
        r.pos = p.pos.immutable();
        if (!p.esperado.equals(r.esperado)) r.ultimoPitido = -1;
        r.esperado = p.esperado;
        r.nombre = p.nombre;
        r.dormido = p.dormido;
        r.conLimite = p.restante >= 0;
        r.total = p.total;
        r.recibido = ahora();
        if (r.conLimite) {
            long fin = ahora() + p.restante;
            // sin tirones: solo se corrige si se ha desviado más de un segundo
            if (Math.abs(fin - r.fin) > 20 || p.restante >= p.total - 25) r.fin = fin;
        }
    }

    /** El reloj de la mesa cuyo panel se pinta (la mesa grande puede tener el centro a un par de bloques de su bloque). */
    @Nullable
    public static Reloj de(BlockPos panel) {
        long t = ahora();
        Reloj mejor = null;
        double md = 10;
        for (Iterator<Reloj> it = RELOJES.values().iterator(); it.hasNext(); ) {
            Reloj r = it.next();
            if (t - r.recibido > CADUCA || t < r.recibido) {
                it.remove();
                continue;
            }
            double d = r.pos.distSqr(panel);
            if (d < md) {
                md = d;
                mejor = r;
            }
        }
        return mejor;
    }

    /** Pitidos los últimos 5 segundos de tu turno. */
    static void pitar(Reloj r) {
        if (!r.mio() || r.dormido || !r.conLimite) return;
        int s = (int) Math.ceil(r.segundos(0));
        if (s <= 5 && s >= 1 && s != r.ultimoPitido) {
            r.ultimoPitido = s;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.get(), s <= 2 ? 1.6F : 1.2F, 0.6F));
        }
    }

    public static void limpiar() {
        RELOJES.clear();
    }
}
