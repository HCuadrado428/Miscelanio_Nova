package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** El estroboscopio solo necesita estar a la vista del cliente: el destello en pantalla busca los cargados en {@link #CLIENTE}. */
public class EstroboBlockEntity extends BlockEntity {
    /** Estroboscopios cargados en el cliente (para el destello en pantalla). */
    public static final Set<EstroboBlockEntity> CLIENTE = Collections.newSetFromMap(new WeakHashMap<>());

    public EstroboBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.ESTROBO_BE.get(), p, s);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) CLIENTE.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENTE.remove(this);
    }
}
