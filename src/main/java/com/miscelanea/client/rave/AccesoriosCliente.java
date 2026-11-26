package com.miscelanea.client.rave;

import com.miscelanea.Miscelanea;
import com.miscelanea.rave.Neon;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/** Lo que lleva puesto cada jugador (pulsera y collar de neón) y la capa que lo dibuja brillando en la muñeca y el cuello. */
public final class AccesoriosCliente {
    private AccesoriosCliente() {}

    /** Por id de entidad: {color de la pulsera, color del collar} (-1 = nada). */
    private static final Map<Integer, int[]> PUESTOS = new HashMap<>();
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/block/rave/neon_tubo.png");

    public static void recibir(int id, int pulsera, int collar) {
        if (pulsera < 0 && collar < 0) PUESTOS.remove(id);
        else PUESTOS.put(id, new int[]{pulsera, collar});
    }

    /** Para las pruebas. */
    public static int[] de(int id) {
        return PUESTOS.get(id);
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static final class Eventos {
        @SubscribeEvent
        public static void salir(ClientPlayerNetworkEvent.LoggingOut e) {
            PUESTOS.clear();
        }

        /**
         * Agachado + clic derecho al aire con la mano vacía: quitarse la pulsera (o el collar, si no lleva pulsera). Ese clic
         * no llega al servidor por sí solo, así que se le avisa. Solo al aire: agachado + clic sobre un bloque ya hace otras
         * cosas (cambiar el patrón de una luz, quitar los LED de un andamio...).
         */
        @SubscribeEvent
        public static void clicAlAire(net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickEmpty e) {
            var j = e.getEntity();
            if (e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND || !j.isShiftKeyDown() || !j.getMainHandItem().isEmpty()) return;
            if (PUESTOS.get(j.getId()) == null) return;
            com.miscelanea.rave.RaveRed.quitarAccesorio();
        }
    }

    @Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registro {
        @SubscribeEvent
        public static void capas(EntityRenderersEvent.AddLayers e) {
            for (String piel : e.getSkins())
                if (e.getSkin(piel) instanceof PlayerRenderer r) r.addLayer(new Capa(r));
        }
    }

    static final class Capa extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        Capa(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> p) {
            super(p);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int luz, AbstractClientPlayer j, float a, float b, float pt, float c, float d, float e) {
            int[] q = PUESTOS.get(j.getId());
            if (q == null || j.isInvisible()) return;
            VertexConsumer vc = buf.getBuffer(RenderType.eyes(TEX));
            if (q[0] >= 0) {
                ps.pushPose();
                getParentModel().rightArm.translateAndRotate(ps);
                // el brazo va de x -3 a 1 (en dieciseisavos) y la muñeca está hacia y = 7.5
                anillo(ps, vc, -3.4F, 6.8F, -2.4F, 1.4F, 8.2F, 2.4F, Neon.rgb(DyeColor.byId(q[0])), true);
                ps.popPose();
            }
            if (q[1] >= 0) {
                ps.pushPose();
                getParentModel().body.translateAndRotate(ps);
                anillo(ps, vc, -3.2F, -0.1F, -2.45F, 3.2F, 0.9F, 2.45F, Neon.rgb(DyeColor.byId(q[1])), false);
                ps.popPose();
            }
        }

        /** Las cuatro caras de lado de una caja (y, si se pide, las tapas), en dieciseisavos de bloque, por los dos lados. */
        private static void anillo(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, boolean tapas) {
            float r = ((rgb >> 16) & 255) / 255F, g = ((rgb >> 8) & 255) / 255F, b = (rgb & 255) / 255F;
            float[][][] caras = {
                    {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}}, {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
                    {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}}, {{x1, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x1, y1, z0}},
                    {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}}, {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}}};
            var m = ps.last().pose();
            var n = ps.last().normal();
            for (int i = 0; i < (tapas ? 6 : 4); i++)
                for (int lado = 0; lado < 2; lado++)
                    for (int k = 0; k < 4; k++) {
                        float[] v = caras[i][lado == 0 ? k : 3 - k];
                        vc.vertex(m, v[0] / 16F, v[1] / 16F, v[2] / 16F).color(r, g, b, 1F).uv(0.45F, k < 2 ? 0 : 1)
                                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(n, 0, 1, 0).endVertex();
                    }
        }
    }
}
