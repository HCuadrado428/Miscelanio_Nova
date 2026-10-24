package com.miscelanea.client.carcel;

import com.miscelanea.Miscelanea;
import com.miscelanea.carcel.enemigos.EnemigoCarcel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Render de los enemigos de la cárcel: assets/miscelanea/{geo,animations,textures}/entity/&lt;nombre&gt;.* (ojos y lentes brillan). */
public class EnemigoRenderer<T extends EnemigoCarcel> extends GeoEntityRenderer<T> {
    public EnemigoRenderer(EntityRendererProvider.Context ctx, String nombre, float sombra) {
        super(ctx, new DefaultedEntityGeoModel<T>(new ResourceLocation(Miscelanea.MOD_ID, nombre)) {
            // la cabeza (hueso "cabeza") mira a donde mira el mob, sumándose a la animación
            @Override
            public void setCustomAnimations(T animatable, long id, software.bernie.geckolib.core.animation.AnimationState<T> state) {
                var datos = state.getData(software.bernie.geckolib.constant.DataTickets.ENTITY_MODEL_DATA);
                getBone("cabeza").ifPresent(b -> {
                    if (datos == null) return;
                    b.setRotX(b.getRotX() + datos.headPitch() * net.minecraft.util.Mth.DEG_TO_RAD);
                    b.setRotY(b.getRotY() + datos.netHeadYaw() * net.minecraft.util.Mth.DEG_TO_RAD);
                });
            }
        });
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = sombra;
    }
}
