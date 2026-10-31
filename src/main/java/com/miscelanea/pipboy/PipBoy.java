package com.miscelanea.pipboy;

import com.miscelanea.Miscelanea;
import com.miscelanea.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Pip-Boy de muñeca (Fallout). En el servidor se guarda si lo llevas puesto, tus notas y tus marcadores del mapa.
 * Se sincroniza con tu cliente y con los jugadores que te ven (para dibujarlo en tu brazo y la pose de mirarlo).
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID)
public final class PipBoy {
    public static final String LLEVA = "MiscelaneaPipBoy", NOTAS = "MiscelaneaPipNotas", MARCAS = "MiscelaneaPipMarcas";
    public static final int MAX_NOTAS = 6, MAX_MARCAS = 16;

    private PipBoy() {}

    public static boolean lleva(Player p) {
        return p.getPersistentData().getBoolean(LLEVA);
    }

    public static void poner(ServerPlayer p, boolean v) {
        p.getPersistentData().putBoolean(LLEVA, v);
        enviarEstado(p, false);
    }

    /** Estado (lo lleva / lo está mirando) a él y a quien le ve. */
    public static void enviarEstado(ServerPlayer p, boolean mirando) {
        byte f = (byte) ((lleva(p) ? 1 : 0) | (mirando ? 2 : 0));
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new PipBoyEstadoPacket(p.getId(), f));
    }

    public static void enviarDatos(ServerPlayer p) {
        CompoundTag d = new CompoundTag();
        d.put(NOTAS, p.getPersistentData().getList(NOTAS, Tag.TAG_STRING).copy());
        d.put(MARCAS, p.getPersistentData().getList(MARCAS, Tag.TAG_COMPOUND).copy());
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new PipBoyDatosPacket(d));
    }

    public static void notas(ServerPlayer p, java.util.List<String> notas) {
        ListTag l = new ListTag();
        for (int i = 0; i < Math.min(MAX_NOTAS, notas.size()); i++) {
            String n = notas.get(i);
            l.add(StringTag.valueOf(n.length() > 64 ? n.substring(0, 64) : n));
        }
        p.getPersistentData().put(NOTAS, l);
    }

    public static void marca(ServerPlayer p, int x, int z, String nombre) {
        ListTag l = p.getPersistentData().getList(MARCAS, Tag.TAG_COMPOUND);
        if (l.size() >= MAX_MARCAS) return;
        CompoundTag m = new CompoundTag();
        m.putInt("x", x);
        m.putInt("z", z);
        m.putString("n", nombre.length() > 24 ? nombre.substring(0, 24) : nombre);
        m.putString("d", p.level().dimension().location().toString());
        l.add(m);
        p.getPersistentData().put(MARCAS, l);
        enviarDatos(p);
    }

    public static void borrarMarca(ServerPlayer p, int i) {
        ListTag l = p.getPersistentData().getList(MARCAS, Tag.TAG_COMPOUND);
        if (i >= 0 && i < l.size()) l.remove(i);
        p.getPersistentData().put(MARCAS, l);
        enviarDatos(p);
    }

    @SubscribeEvent
    public static void entrar(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            enviarEstado(p, false);
            enviarDatos(p);
        }
    }

    @SubscribeEvent
    public static void reaparecer(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            enviarEstado(p, false);
            enviarDatos(p);
        }
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) enviarEstado(p, false);
    }

    /** Al morir no se pierde: el Pip-Boy, las notas y los marcadores pasan al nuevo jugador. */
    @SubscribeEvent
    public static void clonar(PlayerEvent.Clone e) {
        CompoundTag viejo = e.getOriginal().getPersistentData(), nuevo = e.getEntity().getPersistentData();
        for (String k : new String[]{LLEVA, NOTAS, MARCAS}) if (viejo.contains(k)) nuevo.put(k, viejo.get(k).copy());
    }

    @SubscribeEvent
    public static void verJugador(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer otro && e.getEntity() instanceof ServerPlayer yo && lleva(otro)) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> yo), new PipBoyEstadoPacket(otro.getId(), (byte) 1));
        }
    }
}
