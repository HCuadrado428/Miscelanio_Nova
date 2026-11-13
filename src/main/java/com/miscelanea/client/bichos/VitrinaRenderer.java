package com.miscelanea.client.bichos;

import com.miscelanea.bichos.BichoEntity;
import com.miscelanea.bichos.BichoItem;
import com.miscelanea.bichos.Especie;
import com.miscelanea.bichos.VitrinaBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.level.Level;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** Dentro de la vitrina: el ejemplar girando despacio (los bichos en 3D), y en la placa de delante su nombre (y su tamaño si es un pez). */
public class VitrinaRenderer implements BlockEntityRenderer<VitrinaBlockEntity> {
    public VitrinaRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(VitrinaBlockEntity v, float partial, PoseStack ps, MultiBufferSource buf, int luz, int overlay) {
        ItemStack s = v.ejemplar();
        if (s.isEmpty() || v.getLevel() == null) return;
        float t = v.getLevel().getGameTime() + partial;
        if (s.getItem() instanceof BichoItem bi && !bi.especie.esPez() && bicho3d(v, bi.especie, t, partial, ps, buf, luz)) {
            placa(v, s, ps, buf, luz);
            return;
        }
        ps.pushPose();
        ps.translate(0.5, 0.62 + Math.sin(t * 0.05) * 0.02, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
        ps.scale(0.6F, 0.6F, 0.6F);
        Minecraft.getInstance().getItemRenderer().renderStatic(s, ItemDisplayContext.FIXED, luz, OverlayTexture.NO_OVERLAY, ps, buf, v.getLevel(), 0);
        ps.popPose();
        placa(v, s, ps, buf, luz);
    }

    /** Ejemplares 3D de la vitrina: uno por especie, solo para dibujar (no están en el mundo). */
    private static final Map<Especie, BichoEntity> EJEMPLARES = new EnumMap<>(Especie.class);

    /** El bicho en 3D girando despacio: los de suelo sobre la base, los voladores en el aire con las alas abiertas. */
    private static boolean bicho3d(VitrinaBlockEntity v, Especie e, float t, float partial, PoseStack ps, MultiBufferSource buf, int luz) {
        Level level = v.getLevel();
        BichoEntity b = EJEMPLARES.get(e);
        if (b == null || b.level() != level) {
            b = BichoEntity.crear(level, e, 0, 0, 0);
            b.expuesto = true;
            EJEMPLARES.put(e, b);
        }
        b.tickCount = (int) level.getGameTime();
        b.setYRot(0); b.yRotO = 0; b.yBodyRot = 0; b.yBodyRotO = 0;
        EntityRenderer<? super BichoEntity> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(b);
        if (r == null) return false;
        float esc = Math.min(0.55F / e.tam, 2.2F);
        ps.pushPose();
        ps.translate(0.5, e.vuela() ? 0.42 + Math.sin(t * 0.05) * 0.015 : 0.19, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(t * 1.2F));
        ps.scale(esc, esc, esc);
        r.render(b, 0, partial, ps, buf, luz);
        ps.popPose();
        return true;
    }

    private static void placa(VitrinaBlockEntity v, ItemStack s, PoseStack ps, MultiBufferSource buf, int luz) {
        // placa con el nombre en la cara frontal de la base
        var dir = v.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Component nombre = s.getItem() instanceof BichoItem || s.getItem() instanceof com.miscelanea.tesoros.FosilItem
                || s.getItem() instanceof com.miscelanea.feria.PeluchePersonaItem
                || s.is(com.miscelanea.registry.ModItems.PELUCHE.get()) ? s.getHoverName() : Component.empty();
        Font f = Minecraft.getInstance().font;
        ps.pushPose();
        ps.translate(0.5, 0.11, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-dir.toYRot()));
        ps.translate(0, 0, 0.502);
        float esc = 0.0065F;
        ps.scale(esc, -esc, esc);
        int w = f.width(nombre);
        float max = 0.9F / esc;
        if (w > max) ps.scale(max / w, max / w, 1);
        f.drawInBatch(nombre, -w / 2F, -4, 0xFF2A1A08, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, luz);
        ps.popPose();
    }
}
