package com.miscelanea.laboratorio;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Los 27 huecos del frigorífico de muestras. */
public class FrigorificoBlockEntity extends RandomizableContainerBlockEntity {
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);

    public FrigorificoBlockEntity(BlockPos p, BlockState s) {
        super(LabRegistro.FRIGORIFICO_BE.get(), p, s);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> l) {
        items = l;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.miscelanea.frigorifico_muestras");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inv) {
        return ChestMenu.threeRows(id, inv, this);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (!trySaveLootTable(t)) ContainerHelper.saveAllItems(t, items);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        items = NonNullList.withSize(27, ItemStack.EMPTY);
        if (!tryLoadLootTable(t)) ContainerHelper.loadAllItems(t, items);
    }
}
