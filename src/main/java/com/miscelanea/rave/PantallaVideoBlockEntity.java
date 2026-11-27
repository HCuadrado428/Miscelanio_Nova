package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/** Un trozo de la pantalla de vídeo; el de abajo a la izquierda guarda qué vídeo del móvil enseña toda la pantalla y desde cuándo. */
public class PantallaVideoBlockEntity extends BlockEntity {
    @Nullable
    private UUID dueno;
    private int clip = -1;
    private long inicio;

    public PantallaVideoBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.PANTALLA_BE.get(), p, s);
    }

    @Nullable public UUID dueno() { return dueno; }
    public int clip() { return clip; }
    public long inicio() { return inicio; }

    public void poner(@Nullable UUID quien, int id) {
        dueno = quien;
        clip = quien == null ? -1 : id;
        inicio = level == null ? 0 : level.getGameTime();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** ¿Enseña un vídeo? */
    public boolean conVideo() { return dueno != null; }

    /** Se queda con el vídeo de otro panel (desde el mismo momento) y se lo quita a ese. */
    void quitarVideoA(PantallaVideoBlockEntity otro) {
        dueno = otro.dueno;
        clip = otro.clip;
        inicio = otro.inicio;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        if (otro != this) otro.poner(null, -1);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (dueno != null) t.putUUID("Dueno", dueno);
        t.putInt("Clip", clip);
        t.putLong("Inicio", inicio);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        dueno = t.hasUUID("Dueno") ? t.getUUID("Dueno") : null;
        clip = t.contains("Clip") ? t.getInt("Clip") : -1;
        inicio = t.getLong("Inicio");
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
