package com.miscelanea.arcade;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Entidad del cartel de récords: sirve para dibujarlo (la esquina de cada grupo dibuja el cartel entero). En el tablón de
 * récords guarda además la categoría del ranking que enseña (la de la esquina es la que vale).
 */
public class CartelRecordsBlockEntity extends BlockEntity {
    public static final String POR_DEFECTO = "global/podios";
    public String categoria = POR_DEFECTO;

    public CartelRecordsBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.CARTEL_RECORDS_BE.get(), p, s);
    }

    public void ponerCategoria(String c) {
        categoria = c == null || c.isEmpty() ? POR_DEFECTO : c;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putString("categoria", categoria);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        if (t.contains("categoria")) categoria = t.getString("categoria");
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = new CompoundTag();
        t.putString("categoria", categoria);
        return t;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(12, 8, 12);
    }
}
