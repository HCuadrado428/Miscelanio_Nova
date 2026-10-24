package com.miscelanea.client.porra;

import com.miscelanea.network.AgarrePacket;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Estado de los agarres en el cliente (lo que necesitan las animaciones y la cámara). */
public final class ClientAgarres {
    public enum Rol { AGRESOR, VICTIMA }

    public static final class Estado {
        public final Rol rol;
        public final int otro;
        public final float inicio;
        public float ultimaDescarga = -1000.0F;
        public float ultimoForcejeo = -1000.0F;
        public float ejecucion = -1.0F;
        public float resistencia = 100.0F;
        /** Momento en que terminó (para la transición de salida); -1 mientras dura. */
        public float fin = -1.0F;

        public boolean activo() {
            return fin < 0;
        }

        Estado(Rol rol, int otro, float inicio) {
            this.rol = rol;
            this.otro = otro;
            this.inicio = inicio;
        }
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();

    private ClientAgarres() {}

    /** Tiempo del cliente en ticks (con fracción). */
    public static float ahora(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0.0F : mc.level.getGameTime() + partialTick;
    }

    /** Estado (incluye los que están terminando, para la animación de salida). */
    @Nullable
    public static Estado de(int entityId) {
        Estado e = ESTADOS.get(entityId);
        if (e != null && e.fin >= 0 && ahora(0.0F) - e.fin > 6.0F) {
            ESTADOS.remove(entityId);
            return null;
        }
        return e;
    }

    /** Solo si el agarre sigue en marcha. */
    @Nullable
    public static Estado activo(int entityId) {
        Estado e = de(entityId);
        return e != null && e.activo() ? e : null;
    }

    public static void limpiar() {
        ESTADOS.clear();
    }

    public static void recibir(AgarrePacket p) {
        float t = ahora(0.0F);
        switch (p.tipo) {
            case INICIO -> {
                ESTADOS.put(p.agresor, new Estado(Rol.AGRESOR, p.victima, t));
                ESTADOS.put(p.victima, new Estado(Rol.VICTIMA, p.agresor, t));
            }
            case FIN -> {
                for (int id : new int[]{p.agresor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null && e.fin < 0) e.fin = t;
                }
            }
            case DESCARGA -> {
                for (int id : new int[]{p.agresor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null) {
                        e.ultimaDescarga = t;
                        e.resistencia = p.valor;
                    }
                }
            }
            case FORCEJEO -> {
                for (int id : new int[]{p.agresor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null) {
                        e.ultimoForcejeo = t;
                        e.resistencia = p.valor;
                    }
                }
            }
            case EJECUCION -> {
                for (int id : new int[]{p.agresor, p.victima}) {
                    Estado e = ESTADOS.get(id);
                    if (e != null) e.ejecucion = t;
                }
            }
        }
    }
}
