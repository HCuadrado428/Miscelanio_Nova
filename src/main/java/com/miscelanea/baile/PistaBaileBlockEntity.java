package com.miscelanea.baile;

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

import java.util.UUID;

/** Quién baila en la pista y qué canción suena (el servidor avisa a los de alrededor y cada cliente pone la música en la pista). */
public class PistaBaileBlockEntity extends BlockEntity {
    public static final String[] CANCIONES = {"ri_verbena", "ri_neon", "ri_tormenta"};
    public UUID bailarin;
    public int cancion = -1;
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
        cancion = -1;
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new BailePacket(BailePacket.ABRIR, worldPosition, sp.getId(), 0, 0));
    }

    void empezar(ServerPlayer sp, int c, int d) {
        if (!sp.getUUID().equals(bailarin) || c < 0 || c >= CANCIONES.length) return;
        parar();
        cancion = c;
        dificultad = Math.max(0, Math.min(2, d));
        inicio = level.getGameTime();
        // la música la pone cada cliente en la pista (sonido con posición que se corta solo si la pista desaparece)
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.EMPIEZA, worldPosition, sp.getId(), c, 0));
    }

    /**
     * Máximo de puntos que se pueden haber hecho en esta canción y dificultad con el tiempo que ha pasado desde que empezó
     * (todas perfectas con el combo subiendo: 300 + 2·combo cada flecha). -1 si no hay canción en marcha.
     */
    int maxPuntos() {
        if (cancion < 0 || level == null) return -1;
        int[] base = com.miscelanea.client.arcade.RitmoCanciones.NOTAS[cancion][dificultad];
        // tiempo de la canción en pasos de 1/60 s (3 por tic), con 2 s de margen por el retraso de la red
        long t = (level.getGameTime() - inicio) * 3 + 120;
        long n = 0;
        for (int x : base) {
            if ((x >> 3) > t) break;
            n += (x & 4) != 0 ? 2 : 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, 300 * n + n * (n - 1));
    }

    void parar() {
        // el cliente ve en los datos de la pista que ya no hay canción y para la música de esta pista (solo de esta)
        cancion = -1;
    }

    void salir() {
        ServerPlayer sp = bailarin == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(bailarin);
        parar();
        if (sp != null) ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.PARA, worldPosition, sp.getId(), 0, 0));
        bailarin = null;
        cambio();
    }

    public static void tickServidor(Level l, BlockPos pos, BlockState s, PistaBaileBlockEntity be) {
        if (be.bailarin == null || l.getGameTime() % 10 != 0) return;
        ServerPlayer sp = l.getServer().getPlayerList().getPlayer(be.bailarin);
        if (sp == null || sp.level() != l || sp.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > 9) be.salir();
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        if (bailarin != null) t.putUUID("b", bailarin);
        t.putInt("c", cancion);
        return t;
    }

    @Override
    public void handleUpdateTag(CompoundTag t) {
        bailarin = t.hasUUID("b") ? t.getUUID("b") : null;
        cancion = t.getInt("c");
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
