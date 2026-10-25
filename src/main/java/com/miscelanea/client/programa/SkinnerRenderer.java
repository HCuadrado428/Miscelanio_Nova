package com.miscelanea.client.programa;

import com.miscelanea.carcel.enemigos.Skinner;
import com.miscelanea.client.carcel.EnemigoRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

/** El Skinner Man solo lo ve quien está en psicosis. */
public class SkinnerRenderer extends EnemigoRenderer<Skinner> {
    public SkinnerRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, "skinner", 0.0F);
    }

    @Override
    public boolean shouldRender(Skinner e, Frustum f, double x, double y, double z) {
        var p = Minecraft.getInstance().player;
        if (p == null || e.victima().isEmpty() || !e.victima().get().equals(p.getUUID())) return false;
        return super.shouldRender(e, f, x, y, z);
    }
}
