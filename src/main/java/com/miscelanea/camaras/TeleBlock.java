package com.miscelanea.camaras;

import net.minecraft.world.level.block.entity.BlockEntity;

/** La televisión (ver {@link MontadoBlock}); el canal 0 es el automático, que rota entre todas las cámaras. */
public class TeleBlock extends MontadoBlock {
    /** Pantalla (en píxeles del modelo): la imagen de 16:9 cabe dentro del marco. */
    public static final double PX0 = 1, PX1 = 15, PY0 = 4.0625, PY1 = 11.9375, PZ = 11.98;

    public TeleBlock(Properties p) {
        super(p, TeleBlockEntity::new, () -> CamarasRegistro.TELE_BE.get());
    }

    @Override
    protected double[] caja() { return new double[]{0, 3.5, 12, 16, 12.5, 16}; }

    @Override
    protected int canalMin() { return 0; }

    @Override
    protected int canalActual(BlockEntity be) { return be instanceof TeleBlockEntity t ? t.canal() : 1; }

    @Override
    protected void ponerCanal(BlockEntity be, int canal) {
        if (be instanceof TeleBlockEntity t) t.canal(canal);
    }
}
