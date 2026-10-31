package com.miscelanea.bichos;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Lo que hay expuesto en la vitrina y quién lo donó. */
public class VitrinaBlockEntity extends BlockEntity {
    private ItemStack ejemplar = ItemStack.EMPTY;
    private String donante = "";

    public VitrinaBlockEntity(BlockPos pos, BlockState st) {
        super(ModBlocks.VITRINA_BE.get(), pos, st);
    }

    public ItemStack ejemplar() {
        return ejemplar;
    }

    public String donante() {
        return donante;
    }

    public void poner(ItemStack s, String quien) {
        ejemplar = s;
        donante = quien;
        cambio();
    }

    public ItemStack quitar() {
        ItemStack s = ejemplar;
        ejemplar = ItemStack.EMPTY;
        donante = "";
        cambio();
        return s;
    }

    private void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (!ejemplar.isEmpty()) t.put("Ejemplar", ejemplar.save(new CompoundTag()));
        t.putString("Donante", donante);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        ejemplar = t.contains("Ejemplar") ? ItemStack.of(t.getCompound("Ejemplar")) : ItemStack.EMPTY;
        donante = t.getString("Donante");
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
