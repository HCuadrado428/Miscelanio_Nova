package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Una cámara de vigilancia colocada. Guarda su canal (1 a 99) y hacia dónde mira. Mientras está cargada en el servidor se
 * apunta en {@link CamarasServidor}: es lo que permite saber qué jugador tiene su chunk cargado y puede dibujarla.
 */
public class VigilanciaBlockEntity extends BlockEntity {
    private int canal = 1;
    /** Giro horizontal de quien la colocó (solo se usa en el techo; en la pared mira hacia fuera de la pared). */
    private float giro;

    public VigilanciaBlockEntity(BlockPos p, BlockState s) {
        super(CamarasRegistro.VIGILANCIA_BE.get(), p, s);
    }

    public int canal() { return canal; }

    public void canal(int c) {
        canal = Math.max(1, Math.min(99, c));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public void giro(float g) {
        giro = g;
        setChanged();
    }

    private Direction cara() {
        return getBlockState().getValue(Montaje.FACING);
    }

    /** Dónde están los "ojos": justo delante de la lente. */
    public Vec3 ojo() {
        Direction d = cara();
        return Vec3.atCenterOf(worldPosition).add(d.getStepX() * 0.55, d.getStepY() * 0.55, d.getStepZ() * 0.55);
    }

    public float yaw() {
        Direction d = cara();
        return d == Direction.DOWN ? giro : d.toYRot();
    }

    public float pitch() {
        return cara() == Direction.DOWN ? 50.0F : 10.0F;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel) CamarasServidor.alta(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CamarasServidor.baja(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CamarasServidor.baja(this);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Canal", canal);
        t.putFloat("Giro", giro);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        canal = Math.max(1, Math.min(99, t.contains("Canal") ? t.getInt("Canal") : 1));
        giro = t.getFloat("Giro");
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
