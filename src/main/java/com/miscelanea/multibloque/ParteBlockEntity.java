package com.miscelanea.multibloque;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Una parte de un bloque de varios huecos: solo recuerda dónde está su maestro. */
public class ParteBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos maestro;

    public ParteBlockEntity(BlockPos p, BlockState s) {
        super(MultiRegistro.PARTE_BE.get(), p, s);
    }

    @Nullable
    public BlockPos maestro() {
        return maestro;
    }

    void maestro(BlockPos m) {
        maestro = m.immutable();
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (maestro != null) t.put("Maestro", NbtUtils.writeBlockPos(maestro));
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        maestro = t.contains("Maestro") ? NbtUtils.readBlockPos(t.getCompound("Maestro")) : null;
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
