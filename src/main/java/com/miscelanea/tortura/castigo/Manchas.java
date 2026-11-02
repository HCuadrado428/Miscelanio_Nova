package com.miscelanea.tortura.castigo;

import com.miscelanea.network.ModNetwork;
import com.miscelanea.tortura.TorturaRegistro;
import com.miscelanea.tortura.heridas.Parte;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Manchas de tomate y huevo sobre la piel (servidor). Se guardan en los datos persistentes del jugador
 * ("MiscelaneaManchas"), se secan y desaparecen solas en unos 2 minutos y el agua o la lluvia las lavan al momento.
 * Se mandan a él y a quien le ve; el cliente las pinta en una capa sobre la skin (como las heridas, pero aparte).
 */
public final class Manchas {
    private Manchas() {}

    public static final String CLAVE = "MiscelaneaManchas";
    public static final int MAX = 24, DURACION = 2 * 60 * 20;

    public static List<Mancha> leer(Player p) {
        List<Mancha> l = new ArrayList<>();
        if (!p.getPersistentData().contains(CLAVE)) return l;
        ListTag t = p.getPersistentData().getList(CLAVE, Tag.TAG_COMPOUND);
        for (int i = 0; i < t.size(); i++) l.add(Mancha.cargar(t.getCompound(i)));
        return l;
    }

    static void guardar(Player p, List<Mancha> l) {
        if (l.isEmpty()) {
            p.getPersistentData().remove(CLAVE);
            return;
        }
        ListTag t = new ListTag();
        for (Mancha m : l) t.add(m.guardar());
        p.getPersistentData().put(CLAVE, t);
    }

    public static boolean tiene(Player p) {
        return p.getPersistentData().contains(CLAVE);
    }

    /** Mancha donde ha dado el proyectil (punto de impacto en el mundo y de dónde venía). */
    public static void agregar(ServerPlayer p, Vec3 punto, Vec3 desde, boolean huevo) {
        Golpes.Golpe g = Golpes.desdePunto(p, punto, desde);
        agregarEn(p, g.parte(), g.cara(), g.fx(), g.fy(), huevo);
    }

    public static void agregarEn(ServerPlayer p, Parte parte, int cara, int fx, int fy, boolean huevo) {
        if (p instanceof FakePlayer) return;
        List<Mancha> l = leer(p);
        if (l.size() >= MAX) l.remove(0);   // la más vieja se va
        int dur = DURACION + p.getRandom().nextInt(400) - 200;
        l.add(new Mancha(parte, cara, fx, fy, huevo ? Mancha.HUEVO : Mancha.TOMATE, p.getRandom().nextInt(), dur));
        guardar(p, l);
        enviar(p, l);
    }

    /** Lo deja limpio. conEfecto: chapoteo y burbujas (al lavarse en el agua o con la lluvia). */
    public static void lavar(ServerPlayer p, boolean conEfecto) {
        if (!tiene(p)) return;
        guardar(p, new ArrayList<>());
        enviar(p, new ArrayList<>());
        if (conEfecto && p.level() instanceof ServerLevel sl) {
            sl.playSound(null, p.getX(), p.getY() + 1, p.getZ(), TorturaRegistro.LAVAR.get(), SoundSource.PLAYERS, 0.6F, 1F);
            sl.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 1, p.getZ(), 30, 0.3, 0.6, 0.3, 0.1);
        }
    }

    public static void enviar(ServerPlayer p, List<Mancha> l) {
        if (p instanceof FakePlayer || p.connection == null) return;
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), new ManchasPacket(p.getId(), l));
    }

    public static void enviarA(ServerPlayer p, ServerPlayer quien) {
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> quien), new ManchasPacket(p.getId(), leer(p)));
    }

    /** Cada 10 ticks: secarse y lavarse. */
    public static void tick(ServerPlayer p) {
        if (!tiene(p)) return;
        if (p.isInWaterRainOrBubble()) {
            lavar(p, true);
            return;
        }
        List<Mancha> l = leer(p);
        boolean quitadas = l.removeIf(m -> (m.restante -= 10) <= 0);
        guardar(p, l);
        // el cliente las va secando a ojo; se le avisa cuando desaparece alguna
        if (quitadas) enviar(p, l);
    }
}
