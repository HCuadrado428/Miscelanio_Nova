package com.miscelanea.client.purgatorio;

import com.miscelanea.Miscelanea;
import com.miscelanea.purgatorio.Purgado;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Los habitantes del Purgatorio: el cuerpo de un jugador con una piel a 128 px (el doble de detalle de lo normal) y los
 * ojos que brillan en la oscuridad (capa de ojos, como la del enderman). Cuando van detrás de un ruido llevan los brazos
 * por delante, a tientas.
 */
public class PurgadoRenderer extends MobRenderer<Purgado, PurgadoRenderer.Modelo> {
    private static final ResourceLocation[] PIELES = {
            new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/purgado_herobrine.png"),
            new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/purgado_sin_textura.png"),
            new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/purgado_relleno.png")};
    private static final RenderType OJOS = RenderType.eyes(new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/purgado_ojos.png"));

    public PurgadoRenderer(EntityRendererProvider.Context c) {
        super(c, new Modelo(c.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new EyesLayer<>(this) {
            @Override
            public RenderType renderType() {
                return OJOS;
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(Purgado e) {
        return PIELES[e.variante().ordinal()];
    }

    public static class Modelo extends HumanoidModel<Purgado> {
        Modelo(ModelPart p) {
            super(p);
        }

        @Override
        public void setupAnim(Purgado e, float swing, float amount, float age, float headYaw, float headPitch) {
            super.setupAnim(e, swing, amount, age, headYaw, headPitch);
            if (e.cazando()) AnimationUtils.animateZombieArms(leftArm, rightArm, true, attackTime, age);
        }
    }
}
