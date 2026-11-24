package com.miscelanea.client.camaras;

import com.miscelanea.camaras.Montaje;
import com.miscelanea.camaras.TeleBlock;
import com.miscelanea.camaras.TeleBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * La pantalla de la tele pequeña (ver {@link Pantalla}). Al dibujarse avisa de que alguien mira ese canal, que es lo que hace
 * que el servidor pida a alguien que transmita.
 */
public class TeleRenderer implements BlockEntityRenderer<TeleBlockEntity> {
    public TeleRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Override
    public void render(TeleBlockEntity be, float pt, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        Pantalla.dibujar(ps, buf, be.getBlockState().getValue(Montaje.FACING), be.getBlockPos(), be.canal(),
                TeleBlock.PX1, TeleBlock.PX0, TeleBlock.PY0, TeleBlock.PY1, TeleBlock.PZ);
    }
}
