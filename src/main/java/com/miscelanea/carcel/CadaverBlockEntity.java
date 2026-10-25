package com.miscelanea.carcel;

import com.miscelanea.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Guarda lo que lleva el cadáver, si es trampa y el progreso de registro. */
public class CadaverBlockEntity extends BlockEntity {
    private ItemStack contenido = ItemStack.EMPTY;
    private boolean trampa;
    private UUID registrando;
    private long ultimoPaso;
    private int pasos;
    /** Símbolo marcado en el cuerpo (-1 = ninguno). Se ve en el cliente al mirarlo fijamente. */
    private int simbolo = -1;

    public CadaverBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.CADAVER_BE.get(), pos, state);
    }

    public void setContenido(ItemStack s) {
        this.contenido = s;
        setChanged();
    }

    public ItemStack sacarContenido() {
        ItemStack c = contenido;
        contenido = ItemStack.EMPTY;
        pasos = 0;
        setChanged();
        return c;
    }

    public int simbolo() {
        return simbolo;
    }

    public void setSimbolo(int s) {
        simbolo = s;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag t = super.getUpdateTag();
        t.putInt("Simbolo", simbolo);
        return t;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    public boolean esTrampa() {
        return trampa;
    }

    public void setTrampa(boolean t) {
        trampa = t;
        setChanged();
    }

    /** Suma un paso de registro; si el jugador cambia o deja de mantener el clic, vuelve a empezar. */
    public int avanzar(Player p, long ahora) {
        if (!p.getUUID().equals(registrando) || ahora - ultimoPaso > 8) pasos = 0;
        registrando = p.getUUID();
        ultimoPaso = ahora;
        return ++pasos;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!contenido.isEmpty()) tag.put("Contenido", contenido.save(new CompoundTag()));
        tag.putBoolean("Trampa", trampa);
        tag.putInt("Simbolo", simbolo);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        contenido = tag.contains("Contenido") ? ItemStack.of(tag.getCompound("Contenido")) : ItemStack.EMPTY;
        trampa = tag.getBoolean("Trampa");
        simbolo = tag.contains("Simbolo") ? tag.getInt("Simbolo") : -1;
    }
}
