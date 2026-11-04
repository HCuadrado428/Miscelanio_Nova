package com.miscelanea.client.dream;

import com.miscelanea.dream.MordiscoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Estado de los mordiscos en el cliente (lo que necesitan las poses, la cámara y la viñeta). */
public final class ClientMordiscos {
    public enum Rol { MORDEDOR, VICTIMA }

    public static final class Estado {
        public final Rol rol;
        public final int otro;
        public final float inicio;
        public float ultimoMordisco = -1000.0F;
        /** Momento en que terminó (para la transición de salida); -1 mientras dura. */
        public float fin = -1.0F;

        Estado(Rol rol, int otro, float inicio) {
            this.rol = rol;
            this.otro = otro;
            this.inicio = inicio;
        }

        public boolean activo() {
            return fin < 0;
        }
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();

    private ClientMordiscos() {}

    public static float ahora(float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0F : mc.level.getGameTime() + pt;
    }

    /** Estado (también los que están terminando, para la salida suave de la pose). */
    @Nullable
    public static Estado de(int id) {
        Estado e = ESTADOS.get(id);
        if (e != null && e.fin >= 0 && ahora(0.0F) - e.fin > 6.0F) {
            ESTADOS.remove(id);
            return null;
        }
        return e;
    }

    @Nullable
    public static Estado activo(int id) {
        Estado e = de(id);
        return e != null && e.activo() ? e : null;
    }

    @Nullable
    public static Estado local() {
        LocalPlayer p = Minecraft.getInstance().player;
        return p == null ? null : activo(p.getId());
    }

    public static void limpiar() {
        ESTADOS.clear();
    }

    public static void recibir(MordiscoPacket p) {
        float t = ahora(0.0F);
        switch (p.tipo) {
            case INICIO -> {
                Estado viejo = ESTADOS.get(p.mordedor);
                if (viejo != null && viejo.activo() && viejo.otro == p.victima) return; // repetido
                ESTADOS.put(p.mordedor, new Estado(Rol.MORDEDOR, p.victima, t));
                ESTADOS.put(p.victima, new Estado(Rol.VICTIMA, p.mordedor, t));
            }
            case FIN -> {
                for (int id : new int[]{p.mordedor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null && e.fin < 0) e.fin = t;
                }
            }
            case MORDISCO -> {
                for (int id : new int[]{p.mordedor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null) e.ultimoMordisco = t;
                }
            }
        }
    }
}
