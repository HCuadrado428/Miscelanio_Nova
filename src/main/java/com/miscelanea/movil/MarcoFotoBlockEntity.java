package com.miscelanea.movil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * El marco de la pared: se queda con una copia de la foto, igual que cuando se manda por el chat, para que la vea
 * cualquiera que pase y no solo su dueño. Guarda el número de esa copia y de qué foto de la galería salió, para poder
 * ir pasando de una a otra.
 */
public class MarcoFotoBlockEntity extends BlockEntity {

    /** La copia que se enseña (id de adjunto), o -1 si el marco está vacío. */
    private int foto = -1;
    /** En qué foto de la galería del dueño se quedó, para que el siguiente clic pase a la de al lado. */
    private int indice = -1;

    public MarcoFotoBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.MARCO_FOTO_BE.get(), p, s);
    }

    public int foto() {
        return foto;
    }

    public int indice() {
        return indice;
    }

    public void poner(int copia, int indiceGaleria) {
        foto = copia;
        indice = indiceGaleria;
        setChanged();
        if (level != null && !level.isClientSide)
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        t.putInt("foto", foto);
        t.putInt("indice", indice);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        foto = t.contains("foto") ? t.getInt("foto") : -1;
        indice = t.contains("indice") ? t.getInt("indice") : -1;
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
