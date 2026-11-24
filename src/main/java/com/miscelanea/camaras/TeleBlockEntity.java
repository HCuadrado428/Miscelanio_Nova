package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Una televisión: solo recuerda qué canal enseña (0 = automático, rota entre todas las cámaras). El dibujado es del cliente. */
public class TeleBlockEntity extends BlockEntity {
    private int canal = 1;

    public TeleBlockEntity(BlockPos p, BlockState s) {
        super(CamarasRegistro.TELE_BE.get(), p, s);
    }

    protected TeleBlockEntity(net.minecraft.world.level.block.entity.BlockEntityType<?> tipo, BlockPos p, BlockState s) {
        super(tipo, p, s);
    }

    public int canal() { return canal; }

    /** La pantalla ocupa dos bloques: que no deje de dibujarse cuando el suyo queda fuera de la vista. */
    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(1.5);
    }

    public void canal(int c) {
        canal = Math.max(0, Math.min(99, c));
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("Canal", canal);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        canal = Math.max(0, Math.min(99, t.contains("Canal") ? t.getInt("Canal") : 1));
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
