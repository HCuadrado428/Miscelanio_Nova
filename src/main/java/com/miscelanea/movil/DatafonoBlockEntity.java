package com.miscelanea.movil;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.UUID;

/** Datáfono: de quién es (cobra el dueño), cuánto cobra y por qué. Se manda al cliente para enseñarlo antes de pagar. */
public class DatafonoBlockEntity extends BlockEntity {
    @Nullable
    private UUID dueno;
    private String nombreDueno = "";
    private long precio;
    private String concepto = "";

    public DatafonoBlockEntity(BlockPos pos, BlockState st) {
        super(MovilRegistro.DATAFONO_BE.get(), pos, st);
    }

    @Nullable
    public UUID dueno() {
        return dueno;
    }

    public String nombreDueno() {
        return nombreDueno;
    }

    public long precio() {
        return precio;
    }

    public String concepto() {
        return concepto;
    }

    public boolean esDueno(Player p) {
        return dueno != null && dueno.equals(p.getUUID());
    }

    void ponerDueno(ServerPlayer p) {
        dueno = p.getUUID();
        nombreDueno = MovilServidor.nombre(p);
        avisar();
    }

    void configurar(long precio, String concepto) {
        this.precio = precio;
        this.concepto = concepto == null ? "" : concepto;
        avisar();
    }

    private void avisar() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag t) {
        super.saveAdditional(t);
        if (dueno != null) t.putUUID("Dueno", dueno);
        t.putString("NombreDueno", nombreDueno);
        t.putLong("Precio", precio);
        t.putString("Concepto", concepto);
    }

    @Override
    public void load(CompoundTag t) {
        super.load(t);
        dueno = t.hasUUID("Dueno") ? t.getUUID("Dueno") : null;
        nombreDueno = t.getString("NombreDueno");
        precio = t.getLong("Precio");
        concepto = t.getString("Concepto");
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
