package com.miscelanea.tortura;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La caja no guarda nada: quién la ha registrado y dónde está la llave van en los datos de cada víctima. La entidad de
 * bloque solo sirve para encontrar las cajas cercanas rápido (recorriendo las entidades de bloque de los chunks).
 */
public class CajaJigsawBlockEntity extends BlockEntity {
    public CajaJigsawBlockEntity(BlockPos p, BlockState s) {
        super(TorturaRegistro.CAJA_JIGSAW_BE.get(), p, s);
    }
}
