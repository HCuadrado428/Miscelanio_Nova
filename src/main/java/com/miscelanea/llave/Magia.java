package com.miscelanea.llave;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** PM (puntos de magia) para la Llave Espada. Se recuperan solos y al golpear. */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class Magia {
    public static final float MAX = 100F;
    private static final String CLAVE = "MiscelaneaPM";
    /** Lo que sabe el cliente de sus propios PM. */
    public static float cliente = MAX;

    private Magia() {}

    public static float pm(Player p) {
        var d = p.getPersistentData();
        return d.contains(CLAVE) ? d.getFloat(CLAVE) : MAX;
    }

    public static void poner(ServerPlayer p, float v) {
        float antes = pm(p);
        v = Math.max(0, Math.min(MAX, v));
        p.getPersistentData().putFloat(CLAVE, v);
        if ((int) antes != (int) v) enviar(p);
    }

    private static void enviar(ServerPlayer p) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PmPacket(pm(p)));
    }

    public static boolean gastar(ServerPlayer p, float coste) {
        if (p.getAbilities().instabuild) return true;
        if (pm(p) < coste) return false;
        poner(p, pm(p) - coste);
        return true;
    }

    public static void ganar(ServerPlayer p, float x) {
        poner(p, pm(p) + x);
    }

    @SubscribeEvent
    public static void tick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (pm(p) < MAX) poner(p, pm(p) + 0.12F);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviar(p);
    }
}
