package com.miscelanea.baile;

import com.miscelanea.ConfigServidor;
import com.miscelanea.network.ModNetwork;
import com.miscelanea.registry.ModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Quién baila en la pista y qué canción suena (el servidor avisa a los de alrededor y cada cliente pone la música en la
 * pista). Mientras una pista suena, ninguna otra a menos de {@link ConfigServidor#baileRadioPistas()} bloques puede
 * empezar una canción (para que no se mezclen las músicas); eso lo decide solo el servidor.
 */
public class PistaBaileBlockEntity extends BlockEntity {
    /** Servidor: pistas con una canción en marcha (para no dejar empezar otra cerca). */
    private static final Set<PistaBaileBlockEntity> SONANDO = Collections.newSetFromMap(new WeakHashMap<>());

    public UUID bailarin;
    /** Id de la canción que suena (ver {@link CancionesBaile}) o null. */
    public String cancion;
    /** Tic del mundo en el que empezó la canción (también lo sabe el cliente, para los que llegan a mitad). */
    public long inicio;
    /** Servidor: dificultad elegida para la canción en marcha (para saber cuántos puntos se pueden sacar). */
    int dificultad = 1;
    // cliente: luces de las flechas
    public final long[] luz = new long[4];

    public PistaBaileBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.PISTA_BAILE_BE.get(), p, s);
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    boolean encima(ServerPlayer sp) {
        return sp.position().distanceToSqr(Vec3.atBottomCenterOf(worldPosition)) < 2.5;
    }

    void usar(ServerPlayer sp) {
        if (bailarin != null && !bailarin.equals(sp.getUUID())) {
            sp.displayClientMessage(Component.translatable("baile.miscelanea.ocupada").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!encima(sp)) {
            sp.displayClientMessage(Component.translatable("baile.miscelanea.subete").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        bailarin = sp.getUUID();
        parar();
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 0, 0));
        PistaBaileBlockEntity otra = otraSonando();
        if (otra != null) avisarOtra(sp, otra);
    }

    /** Otra pista con canción a menos del radio de la configuración (o null). */
    PistaBaileBlockEntity otraSonando() {
        int r = ConfigServidor.baileRadioPistas();
        if (r <= 0 || level == null) return null;
        PistaBaileBlockEntity mejor = null;
        double md = (double) r * r;
        synchronized (SONANDO) {
            SONANDO.removeIf(o -> o.isRemoved() || o.cancion == null || o.level == null);
            for (PistaBaileBlockEntity o : SONANDO) {
                if (o == this || o.level != level) continue;
                double d = o.worldPosition.distSqr(worldPosition);
                if (d <= md) {
                    md = d;
                    mejor = o;
                }
            }
        }
        return mejor;
    }

    private void avisarOtra(ServerPlayer sp, PistaBaileBlockEntity otra) {
        int d = (int) Math.round(Math.sqrt(otra.worldPosition.distSqr(worldPosition)));
        sp.displayClientMessage(Component.translatable("baile.miscelanea.otra_sonando", d).withStyle(ChatFormatting.GOLD), true);
    }

    void empezar(ServerPlayer sp, String id, int d) {
        CancionesBaile.Cancion c = CancionesBaile.de(id);
        if (!sp.getUUID().equals(bailarin) || c == null) return;
        // si ya suena otra pista cerca, no se puede empezar (se mezclarían las dos músicas)
        PistaBaileBlockEntity otra = otraSonando();
        if (otra != null) {
            avisarOtra(sp, otra);
            return;
        }
        parar();
        cancion = c.id;
        dificultad = Math.max(0, Math.min(2, d));
        inicio = level.getGameTime();
        synchronized (SONANDO) {
            SONANDO.add(this);
        }
        // la música la pone cada cliente en la pista (sonido con posición que se corta solo si la pista desaparece)
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.EMPIEZA, worldPosition, sp.getId(), 0, dificultad, c.id));
    }

    /**
     * Máximo de puntos que se pueden haber hecho en esta canción y dificultad con el tiempo que ha pasado desde que empezó
     * (todas perfectas con el combo subiendo). -1 si no hay canción en marcha.
     */
    int maxPuntos() {
        CancionesBaile.Cancion c = CancionesBaile.de(cancion);
        if (c == null || level == null) return -1;
        // tiempo de la canción en pasos de 1/60 s (3 por tic), con 2 s de margen por el retraso de la red
        long t = (level.getGameTime() - inicio) * 3 + 120;
        return c.maxPuntos(dificultad, t);
    }

    void parar() {
        // el cliente ve en los datos de la pista que ya no hay canción y para la música de esta pista (solo de esta)
        cancion = null;
        synchronized (SONANDO) {
            SONANDO.remove(this);
        }
    }

    void salir() {
        ServerPlayer sp = bailarin == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(bailarin);
        parar();
        if (sp != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.PARA, worldPosition, sp.getId(), 0, 0));
        bailarin = null;
        cambio();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        synchronized (SONANDO) {
            SONANDO.remove(this);
        }
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, PistaBaileBlockEntity be) {
        if (be.bailarin == null || l.getGameTime() % 10 != 0) return;
        ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.bailarin);
        if (sp == null || sp.level() != l || sp.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > 9) {
            be.salir();
            return;
        }
        // la canción se acaba sola aunque el cliente no avise (así la pista deja de bloquear a las de alrededor)
        CancionesBaile.Cancion c = CancionesBaile.de(be.cancion);
        if (be.cancion != null && (c == null || l.getGameTime() - be.inicio > (long) (c.duracion * 20) + 200)) {
            be.parar();
            be.cambio();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        if (bailarin != null) t.putUUID("b", bailarin);
        if (cancion != null) t.putString("c", cancion);
        t.putLong("i", inicio);
        return t;
    }

    @Override
    public void handleUpdateTag(CompoundTag t) {
        bailarin = t.hasUUID("b") ? t.getUUID("b") : null;
        cancion = t.contains("c") ? t.getString("c") : null;
        inicio = t.getLong("i");
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(net.minecraft.network.Connection net, ClientboundBlockEntityDataPacket pkt) {
        if (pkt.getTag() != null) handleUpdateTag(pkt.getTag());
    }
}
