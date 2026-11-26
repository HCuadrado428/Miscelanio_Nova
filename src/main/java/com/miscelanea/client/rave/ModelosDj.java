package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.rave.AltavozBlockEntity;
import com.miscelanea.rave.MesaDjBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Modelos de GeckoLib de la cabina de DJ (los platos giran mientras suena) y de la torre de altavoces (los conos laten con el ritmo). */
final class ModelosDj {
    private ModelosDj() {}

    private static final ResourceLocation ANIM = new ResourceLocation(Miscelanea.MOD_ID, "animations/block/rave.animation.json"),
            TEX_MESA = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/mesa_dj.png"),
            TEX_ALTAVOZ = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/altavoz_concierto.png");

    static GeoBlockRenderer<MesaDjBlockEntity> mesa() {
        GeoBlockRenderer<MesaDjBlockEntity> r = new GeoBlockRenderer<>(new GeoModel<>() {
            private final ResourceLocation geo = new ResourceLocation(Miscelanea.MOD_ID, "geo/block/mesa_dj.geo.json");

            @Override
            public ResourceLocation getModelResource(MesaDjBlockEntity e) {
                return geo;
            }

            @Override
            public ResourceLocation getTextureResource(MesaDjBlockEntity e) {
                return TEX_MESA;
            }

            @Override
            public ResourceLocation getAnimationResource(MesaDjBlockEntity e) {
                return ANIM;
            }

            @Override
            public void setCustomAnimations(MesaDjBlockEntity e, long id, AnimationState<MesaDjBlockEntity> st) {
                if (e.getLevel() == null) return;
                float t = e.sonando() ? (e.getLevel().getGameTime() - e.inicio() + st.getPartialTick()) * 0.12F : 0;
                getBone("plato_izq").ifPresent(b -> b.setRotY(t));
                getBone("plato_der").ifPresent(b -> b.setRotY(-t * 1.07F));
            }
        });
        r.addRenderLayer(new CapaBrillo<>(r, new net.minecraft.resources.ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "textures/block/rave/mesa_dj_glowmask.png")));
        return r;
    }

    static GeoBlockRenderer<AltavozBlockEntity> altavoz() {
        GeoBlockRenderer<AltavozBlockEntity> r = new GeoBlockRenderer<>(new GeoModel<>() {
            private final ResourceLocation geo = new ResourceLocation(Miscelanea.MOD_ID, "geo/block/altavoz_concierto.geo.json");

            @Override
            public ResourceLocation getModelResource(AltavozBlockEntity e) {
                return geo;
            }

            @Override
            public ResourceLocation getTextureResource(AltavozBlockEntity e) {
                return TEX_ALTAVOZ;
            }

            @Override
            public ResourceLocation getAnimationResource(AltavozBlockEntity e) {
                return ANIM;
            }

            @Override
            public void setCustomAnimations(AltavozBlockEntity e, long id, AnimationState<AltavozBlockEntity> st) {
                float latido = MusicaDj.latido(e.getBlockPos(), st.getPartialTick());
                // el subgrave late con el bombo; las dos cajas de arriba vibran menos
                float s = 1 + 0.12F * latido;
                getBone("cono_sub").ifPresent(b -> {
                    b.setScaleX(s);
                    b.setScaleY(s);
                    b.setPosZ(-0.5F * latido);
                });
                getBone("cono_a").ifPresent(b -> b.setPosZ(-0.25F * latido));
                getBone("cono_b").ifPresent(b -> b.setPosZ(-0.18F * latido));
            }
        });
        r.addRenderLayer(new CapaBrillo<>(r, new net.minecraft.resources.ResourceLocation(com.miscelanea.Miscelanea.MOD_ID, "textures/block/rave/altavoz_concierto_glowmask.png")));
        return r;
    }
}
