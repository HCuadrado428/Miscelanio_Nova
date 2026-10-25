package com.miscelanea.client.programa;

import com.miscelanea.Miscelanea;
import com.miscelanea.programa.AccionPacket;
import com.miscelanea.programa.Acciones;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Acciones animadas de cada jugador visible (las manda el servidor). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ClientAcciones {
    public static final class Estado {
        public Acciones.Accion accion;
        public float inicio, fin, terminado = -1;
    }

    private static final Map<Integer, Estado> ESTADOS = new HashMap<>();
    private static long ticks;

    private ClientAcciones() {}

    public static float ahora(float delta) {
        return ticks + delta;
    }

    public static void recibir(AccionPacket p) {
        Estado e = ESTADOS.get(p.id);
        if (p.accion == 0) {
            if (e != null && e.terminado < 0) e.terminado = ticks;
            return;
        }
        Acciones.Accion a = Acciones.Accion.values()[p.accion];
        if (e == null || e.accion != a || e.terminado >= 0) {
            e = new Estado();
            e.accion = a;
            e.inicio = ticks;
            ESTADOS.put(p.id, e);
        }
        e.fin = ticks + p.ticks;
    }

    /** Estado de la acción (incluye los ticks de salida suave tras terminar). */
    public static Estado de(int id) {
        return ESTADOS.get(id);
    }

    public static boolean caido(int id) {
        Estado e = ESTADOS.get(id);
        return e != null && e.accion == Acciones.Accion.CAIDO && e.terminado < 0;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent ev) {
        if (ev.phase != TickEvent.Phase.END) return;
        ticks++;
        ESTADOS.values().removeIf(e -> {
            if (e.terminado < 0 && ticks >= e.fin) e.terminado = ticks;
            return e.terminado >= 0 && ticks - e.terminado > 6;
        });
        var mc = Minecraft.getInstance();
        if (mc.player != null && caido(mc.player.getId())) mc.player.setPose(Pose.SWIMMING);
    }
}
