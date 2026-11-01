package com.miscelanea.tirolina;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/** Poste de tirolina: a qué poste va su cable (destino) o de cuál viene (origen). El cable lo dibuja el poste de origen. */
public class PosteTirolinaBlockEntity extends BlockEntity {
    @Nullable
    public BlockPos destino, origen;

    public PosteTirolinaBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.POSTE_TIROLINA_BE.get(), p, s);
    }

    @Nullable
    public BlockPos otro() {
        return destino != null ? destino : origen;
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (destino != null) t.put("destino", NbtUtils.writeBlockPos(destino));
        if (origen != null) t.put("origen", NbtUtils.writeBlockPos(origen));
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        destino = t.contains("destino") ? NbtUtils.readBlockPos(t.getCompound("destino")) : null;
        origen = t.contains("origen") ? NbtUtils.readBlockPos(t.getCompound("origen")) : null;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        if (destino == null) return new AABB(worldPosition);
        return new AABB(worldPosition, destino).inflate(1, 4, 1);
    }
}
