package com.miscelanea.rave;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** Máquina de humo y fuente de chispas: solo echan partículas en el cliente (ver EfectosRave del cliente). */
public class EfectoBlockEntity extends BlockEntity {
    public EfectoBlockEntity(BlockPos p, BlockState s) {
        super(RaveRegistro.EFECTO_BE.get(), p, s);
    }

    public static void tickCliente(Level l, BlockPos p, BlockState s, EfectoBlockEntity be) {
        if (!s.hasProperty(LucesRave.ENCENDIDA) || !s.getValue(LucesRave.ENCENDIDA)) return;
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.rave.EfectosRave.tick(l, p, s));
    }
}
