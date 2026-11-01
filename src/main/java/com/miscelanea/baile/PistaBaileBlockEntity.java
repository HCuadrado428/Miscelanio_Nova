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
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/** Quién baila en la pista y qué canción suena (el servidor pone la música en el sitio para todos y avisa a los de alrededor). */
public class PistaBaileBlockEntity extends BlockEntity {
    public static final String[] CANCIONES = {"ri_verbena", "ri_neon", "ri_tormenta"};
    public UUID bailarin;
    public int cancion = -1;
    public long inicio;
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

    void empezar(ServerPlayer sp, int c) {
        if (!sp.getUUID().equals(bailarin) || c < 0 || c >= CANCIONES.length) return;
        parar();
        cancion = c;
        inicio = level.getGameTime();
        SoundEvent ev = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("miscelanea", CANCIONES[c]));
        if (ev != null) level.playSound(null, worldPosition, ev, SoundSource.RECORDS, 2.0F, 1F);
        cambio();
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(worldPosition)),
                new BailePacket(BailePacket.EMPIEZA, worldPosition, sp.getId(), c, 0));
    }

    void parar() {
        if (cancion >= 0 && level instanceof ServerLevel sl) {
            var p = new ClientboundStopSoundPacket(new ResourceLocation("miscelanea", CANCIONES[cancion]), SoundSource.RECORDS);
            for (ServerPlayer o : sl.players()) if (o.distanceToSqr(Vec3.atCenterOf(worldPosition)) < 64 * 64) o.connection.send(p);
        }
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
