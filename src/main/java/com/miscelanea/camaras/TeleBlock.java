package com.miscelanea.camaras;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

/** La televisión (ver {@link MontadoBlock}); el canal 0 es el automático, que rota entre todas las cámaras. */
public class TeleBlock extends MontadoBlock {
    /** Pantalla (en píxeles del modelo): la imagen de 16:9 cabe dentro del marco. */
    public static final double PX0 = 1, PX1 = 15, PY0 = 4.0625, PY1 = 11.9375, PZ = 11.98;

    public TeleBlock(Properties p) {
        super(p, TeleBlockEntity::new, () -> CamarasRegistro.TELE_BE.get());
    }

    /** Clic derecho: la imagen en pantalla grande; agachado: canal siguiente (0 es el automático). */
    @Override
    public InteractionResult use(BlockState s, Level l, BlockPos p, Player j, InteractionHand m, BlockHitResult h) {
        if (!j.isShiftKeyDown()) {
            if (l.isClientSide) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.miscelanea.client.camaras.VistaCamaraScreen.abrir(p));
            return InteractionResult.SUCCESS;
        }
        return super.use(s, l, p, j, m, h);
    }

    @Override
    protected boolean agachadoRetrocede() { return false; }

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
