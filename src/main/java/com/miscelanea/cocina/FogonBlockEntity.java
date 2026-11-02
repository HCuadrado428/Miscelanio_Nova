package com.miscelanea.cocina;

import com.miscelanea.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Lo que hay en la sartén, si se está cocinando (y cuánto falta) y el plato hecho esperando a que lo saquen. */
public class FogonBlockEntity extends BlockEntity {
    public static final int MAX = 4, COCCION = 200;
    public final List<ItemStack> dentro = new ArrayList<>();
    public ItemStack hecho = ItemStack.EMPTY;
    public String receta = "";
    public int color;
    public long inicio = -1;

    public FogonBlockEntity(BlockPos p, BlockState s) {
        super(com.miscelanea.registry.ModBlocks.FOGON_BE.get(), p, s);
    }

    public boolean cocinando() {
        return inicio >= 0;
    }

    void usar(Player p, ItemStack st) {
        if (!hecho.isEmpty()) {
            if (!p.getInventory().add(hecho.copy())) p.drop(hecho.copy(), false);
            hecho = ItemStack.EMPTY;
            receta = "";
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6F, 1F);
            cambio();
            return;
        }
        if (cocinando()) {
            p.displayClientMessage(Component.translatable("miscelanea.fogon.cocinando", (COCCION - (level.getGameTime() - inicio)) / 20), true);
            return;
        }
        if (st.isEmpty() || p.isSecondaryUseActive()) {
            if (dentro.isEmpty()) {
                p.displayClientMessage(Component.translatable("miscelanea.fogon.ayuda"), true);
                return;
            }
            for (ItemStack d : dentro) if (!p.getInventory().add(d.copy())) p.drop(d.copy(), false);
            dentro.clear();
            cambio();
            return;
        }
        if (!Recetas.sirve(st) || dentro.size() >= MAX) {
            p.displayClientMessage(Component.translatable("miscelanea.fogon.no_sirve"), true);
            return;
        }
        ItemStack uno = st.copyWithCount(1);
        dentro.add(uno);
        if (!p.getAbilities().instabuild) {
            st.shrink(1);
        }
        level.playSound(null, worldPosition, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.2F);
        Recetas.Receta r = Recetas.buscar(dentro);
        if (r != null) {
            receta = r.id();
            color = r.color();
            inicio = level.getGameTime();
            level.setBlock(worldPosition, getBlockState().setValue(FogonBlock.LIT, true), 3);
            level.playSound(null, worldPosition, ModSounds.CO_CHISPORROTEO.get(), SoundSource.BLOCKS, 0.8F, 1F);
            p.displayClientMessage(Component.translatable("miscelanea.fogon.empieza", Component.translatable("miscelanea.plato." + r.id())), true);
        }
        cambio();
    }

    public static void tick(Level l, BlockPos pos, BlockState s, FogonBlockEntity be) {
        if (!be.cocinando()) return;
        long t = l.getGameTime() - be.inicio;
        if (t % 60 == 0 && t > 0) l.playSound(null, pos, ModSounds.CO_CHISPORROTEO.get(), SoundSource.BLOCKS, 0.6F, 0.9F + l.random.nextFloat() * 0.2F);
        if (t >= COCCION) {
            Recetas.Receta r = Recetas.buscar(be.dentro);
            be.hecho = r != null ? r.plato().get() : ItemStack.EMPTY;
            if (be.dentro.stream().anyMatch(x -> x.is(Items.MILK_BUCKET)))
                Containers.dropItemStack(l, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, new ItemStack(Items.BUCKET));
            be.dentro.clear();
            be.inicio = -1;
            l.setBlock(pos, s.setValue(FogonBlock.LIT, false), 3);
            l.playSound(null, pos, ModSounds.FE_PREMIO.get(), SoundSource.BLOCKS, 0.5F, 1.3F);
            be.cambio();
        }
    }

    void vaciar() {
        for (ItemStack d : dentro) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, d);
        if (!hecho.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, hecho);
        dentro.clear();
        hecho = ItemStack.EMPTY;
    }

    void cambio() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        ListTag l = new ListTag();
        for (ItemStack d : dentro) l.add(d.save(new CompoundTag()));
        t.put("dentro", l);
        if (!hecho.isEmpty()) t.put("hecho", hecho.save(new CompoundTag()));
        t.putString("rec", receta);
        t.putInt("color", color);
        t.putLong("ini", inicio);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        dentro.clear();
        for (Tag e : t.getList("dentro", Tag.TAG_COMPOUND)) dentro.add(ItemStack.of((CompoundTag) e));
        hecho = t.contains("hecho") ? ItemStack.of(t.getCompound("hecho")) : ItemStack.EMPTY;
        receta = t.getString("rec");
        color = t.getInt("color");
        inicio = t.contains("ini") ? t.getLong("ini") : -1;
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
