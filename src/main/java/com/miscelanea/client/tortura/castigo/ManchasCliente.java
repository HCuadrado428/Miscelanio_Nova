package com.miscelanea.client.tortura.castigo;

import com.miscelanea.Miscelanea;
import com.miscelanea.tortura.castigo.Mancha;
import com.miscelanea.tortura.castigo.ManchasPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Manchas de tomate / huevo de cada jugador visible (las va secando a ojo entre sincronizaciones). */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class ManchasCliente {
    private ManchasCliente() {}

    private static final Map<Integer, List<Mancha>> DATOS = new HashMap<>();

    public static void recibir(ManchasPacket p) {
        if (p.lista.isEmpty()) {
            DATOS.remove(p.entidad);
            ManchasTextura.soltar(p.entidad);
        } else DATOS.put(p.entidad, p.lista);
    }

    public static List<Mancha> de(int id) {
        return DATOS.get(id);
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (!DATOS.isEmpty()) {
                DATOS.clear();
                ManchasTextura.soltarTodo();
            }
            return;
        }
        if (mc.isPaused()) return;
        for (List<Mancha> l : DATOS.values()) for (Mancha m : l) m.restante = Math.max(1, m.restante - 1);
        if (mc.level.getGameTime() % 100 == 0) {
            DATOS.keySet().removeIf(id -> {
                Entity en = mc.level.getEntity(id);
                return !(en instanceof Player) || en.isRemoved();
            });
            for (int id : ManchasTextura.ids()) if (!DATOS.containsKey(id)) ManchasTextura.soltar(id);
        }
    }

    @SubscribeEvent
    public static void descargar(LevelEvent.Unload e) {
        if (e.getLevel().isClientSide()) {
            DATOS.clear();
            ManchasTextura.soltarTodo();
        }
    }
}
