package com.miscelanea.tanques;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Los animales que viven en un acuario o terrario (hasta 4), con su tamaño. Se sincroniza con los clientes para dibujarlos. */
public class TanqueBlockEntity extends BlockEntity {
    public static final int MAX = 4;
    public final List<ItemStack> animales = new ArrayList<>();

    public TanqueBlockEntity(BlockPos p, BlockState s) {
        super(ModBlocks.TANQUE_BE.get(), p, s);
    }

    public void cambiado() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag l = new ListTag();
        for (ItemStack s : animales) l.add(s.save(new CompoundTag()));
        t.put("Animales", l);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        animales.clear();
        ListTag l = t.getList("Animales", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size() && i < MAX; i++) animales.add(ItemStack.of(l.getCompound(i)));
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
