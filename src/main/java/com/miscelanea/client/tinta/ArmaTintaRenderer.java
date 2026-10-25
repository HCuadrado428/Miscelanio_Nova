package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.ArmaTinta;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Armas de tinta: modelo GeckoLib + capa con las partes de tinta teñidas del color del arma (textures/item/&lt;nombre&gt;_tinta.png). */
public class ArmaTintaRenderer<T extends Item & GeoItem> extends GeoItemRenderer<T> {
    public ArmaTintaRenderer(String nombre) {
        super(new DefaultedItemGeoModel<>(new ResourceLocation(Miscelanea.MOD_ID, nombre)));
        ResourceLocation mascara = new ResourceLocation(Miscelanea.MOD_ID, "textures/item/" + nombre + "_tinta.png");
        addRenderLayer(new GeoRenderLayer<T>(this) {
            @Override
            public void render(PoseStack ps, T anim, BakedGeoModel modelo, RenderType rt, MultiBufferSource buf, VertexConsumer vc,
                               float partial, int luz, int overlay) {
                ItemStack s = ((ArmaTintaRenderer<T>) getRenderer()).getCurrentItemStack();
                int c = s == null ? ArmaTinta.COLOR_DEFECTO : ArmaTinta.color(s);
                RenderType tipo = RenderType.entityTranslucent(mascara);
                getRenderer().reRender(modelo, ps, buf, anim, tipo, buf.getBuffer(tipo), partial, luz, overlay,
                        ((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F, 1.0F);
            }
        });
    }
}
