package com.miscelanea.client.espera;

import com.miscelanea.Miscelanea;
import com.miscelanea.espera.Paciente;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Los Pacientes de La Espera. Cada variante es una criatura distinta, con su propio cuerpo:
 *
 * <ul>
 *   <li><b>alto</b> (Herobrine): flaco y encorvado, dos bloques y medio, brazos hasta los tobillos.</li>
 *   <li><b>roto</b> (sin cargar): el modelo no cargó bien, con la cabeza suelta, el tronco partido y un muñón.</li>
 *   <li><b>gordo</b> (relleno): bajo y abultado, un saco cosido con la cabeza encima.</li>
 * </ul>
 *
 * <p>Los tres comparten esqueleto, así que las mismas animaciones les valen a todos. Los ojos se ven aunque esté
 * oscuro, como los del enderman.
 */
public class PacienteRenderer extends GeoEntityRenderer<Paciente> {

    /** El nombre de cada cuerpo, en el orden de {@link Paciente.Variante}. */
    private static final String[] CUERPOS = {"alto", "roto", "gordo"};

    private static final ResourceLocation ANIMS =
            new ResourceLocation(Miscelanea.MOD_ID, "animations/entity/paciente.animation.json");
    private static final ResourceLocation[] MODELOS = new ResourceLocation[CUERPOS.length];
    private static final ResourceLocation[] PIELES = new ResourceLocation[CUERPOS.length];
    private static final RenderType[] OJOS = new RenderType[CUERPOS.length];

    static {
        for (int i = 0; i < CUERPOS.length; i++) {
            MODELOS[i] = new ResourceLocation(Miscelanea.MOD_ID, "geo/entity/paciente_" + CUERPOS[i] + ".geo.json");
            PIELES[i] = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/paciente_" + CUERPOS[i] + ".png");
            OJOS[i] = RenderType.eyes(
                    new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/paciente_" + CUERPOS[i] + "_ojos.png"));
        }
    }

    private static int cual(Paciente e) {
        return e.variante().ordinal() % CUERPOS.length;
    }

    public PacienteRenderer(EntityRendererProvider.Context c) {
        super(c, new Modelo());
        this.shadowRadius = 0.5F;
        addRenderLayer(new Ojos(this));
    }

    public static class Modelo extends GeoModel<Paciente> {
        @Override
        public ResourceLocation getModelResource(Paciente e) {
            return MODELOS[cual(e)];
        }

        @Override
        public ResourceLocation getTextureResource(Paciente e) {
            return PIELES[cual(e)];
        }

        @Override
        public ResourceLocation getAnimationResource(Paciente e) {
            return ANIMS;
        }
    }

    /** Los ojos, que se ven aunque esté oscuro. */
    static class Ojos extends GeoRenderLayer<Paciente> {
        Ojos(GeoEntityRenderer<Paciente> r) {
            super(r);
        }

        @Override
        public void render(PoseStack ps, Paciente e, BakedGeoModel modelo, RenderType rt, MultiBufferSource buf,
                           VertexConsumer vc, float parcial, int luz, int overlay) {
            RenderType ojos = OJOS[cual(e)];
            getRenderer().reRender(modelo, ps, buf, e, ojos, buf.getBuffer(ojos), parcial,
                    15728640, overlay, 1F, 1F, 1F, 1F);
        }
    }
}
