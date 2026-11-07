package com.miscelanea.client.tortura.castigo;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ItemSupplier;

/** Tomate / huevo en el aire; no se dibuja pegado a la cámara (si no, uno que te cae encima tapa media pantalla). */
public class PodridoRenderer<T extends Entity & ItemSupplier> extends ThrownItemRenderer<T> {
    public PodridoRenderer(EntityRendererProvider.Context c) {
        super(c);
    }

    @Override
    public boolean shouldRender(T e, Frustum f, double x, double y, double z) {
        if (e.distanceToSqr(x, y, z) < 1.4 * 1.4) return false;
        return super.shouldRender(e, f, x, y, z);
    }
}
