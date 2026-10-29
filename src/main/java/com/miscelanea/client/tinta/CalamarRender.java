package com.miscelanea.client.tinta;

import com.miscelanea.Miscelanea;
import com.miscelanea.tinta.ArmaTinta;
import com.miscelanea.tinta.Calamar;
import com.miscelanea.tinta.Deposito;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forma de calamar: en vez del jugador se dibuja un calamar de Splatoon (teñido de su color) que va dando saltitos.
 * Nadando en su tinta no se dibuja nada (solo las salpicaduras). También el medidor de tinta junto a la mira.
 */
@Mod.EventBusSubscriber(modid = Miscelanea.MOD_ID, value = Dist.CLIENT)
public final class CalamarRender {
    public static final ModelLayerLocation CAPA = new ModelLayerLocation(new ResourceLocation(Miscelanea.MOD_ID, "calamar"), "main");
    private static final ResourceLocation TEX = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/calamar.png");
    private static final ResourceLocation OJOS = new ResourceLocation(Miscelanea.MOD_ID, "textures/entity/calamar_ojos.png");
    private static ModelPart modelo;
    private static CameraType camaraAntes;

    private CalamarRender() {}

    public static LayerDefinition crear() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition raiz = mesh.getRoot();
        PartDefinition c = raiz.addOrReplaceChild("cuerpo", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, 12, -3, 7, 7, 6)   // cuerpo
                .texOffs(0, 13).addBox(-3, 13, -3.5F, 6, 5, 7)   // cuerpo2
                .texOffs(0, 25).addBox(-4, 13, -2.5F, 8, 5, 5), PartPose.ZERO);   // cuerpo3
        c.addOrReplaceChild("manto", CubeListBuilder.create()
                .texOffs(26, 0).addBox(-4.5F, -4.5F, -1.75F, 9, 9, 3.5F)   // manto
                .texOffs(26, 13).addBox(-3, -3, -2.5F, 6, 6, 5), PartPose.offsetAndRotation(0, 9, 0, 0, 0, (float) Math.PI / 4));   // manto2
        float[][] tent = {{-2.6F, -1.6F, 3}, {-0.9F, -1.8F, 3}, {0.9F, -1.8F, 3}, {2.6F, -1.6F, 3}, {-1.8F, 1.4F, 3}, {1.8F, 1.4F, 3}};
        for (int k = 0; k < tent.length; k++) {
            PartDefinition t = c.addOrReplaceChild("tentaculo" + k, CubeListBuilder.create().texOffs(26, 25).addBox(-0.6F, 0, -0.6F, 1.2F, 3, 1.2F), PartPose.offset(tent[k][0], 19, tent[k][1]));
            t.addOrReplaceChild("punta", CubeListBuilder.create().texOffs(31, 25).addBox(-0.5F, 0, -0.5F, 1, 2, 1), PartPose.offset(0, tent[k][2], 0));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static ModelPart modelo() {
        if (modelo == null) modelo = Minecraft.getInstance().getEntityModels().bakeLayer(CAPA);
        return modelo;
    }

    @SubscribeEvent
    public static void jugador(RenderPlayerEvent.Pre e) {
        AbstractClientPlayer p = (AbstractClientPlayer) e.getEntity();
        if (!Calamar.es(p)) return;
        e.setCanceled(true);
        boolean sumergido = Calamar.enSuTinta(p);
        // Sumergido en tu tinta: los demás no te ven; tú ves una silueta translúcida medio hundida
        boolean yo = p == Minecraft.getInstance().player;
        if (sumergido && !yo) return;
        float partial = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        int c = ArmaTinta.colorJugador(p);
        float t = p.tickCount + partial;
        boolean anda = p.getDeltaMovement().horizontalDistanceSqr() > 0.0005;
        ModelPart m = modelo();
        ModelPart cuerpo = m.getChild("cuerpo");
        ps.pushPose();
        float yaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        float salto = 0;
        if (sumergido) {
            ps.translate(0, -0.35, 0);
            ps.mulPose(Axis.XP.rotationDegrees(70));   // nada tumbado, con la punta hacia delante
            ps.translate(0, 0.1, 0);
        } else if (anda) {
            salto = Math.abs(Mth.sin(t * 0.55F));
            ps.translate(0, salto * 0.25, 0);              // saltitos
            ps.mulPose(Axis.XP.rotationDegrees(-12 + salto * 18));
        }
        // aplastarse al caer y estirarse al saltar
        float estira = 1 + (anda && !sumergido ? (salto - 0.5F) * 0.16F : Mth.sin(t * 0.12F) * 0.03F);
        ps.scale(-0.72F / Mth.sqrt(estira), -0.72F * estira, 0.72F / Mth.sqrt(estira));
        ps.translate(0, -1.501, 0);
        for (int k = 0; k < 6; k++) {
            ModelPart te = cuerpo.getChild("tentaculo" + k);
            float ola = Mth.sin(t * (anda ? 0.55F : 0.18F) + k * 0.9F);
            te.xRot = ola * 0.22F + (anda || sumergido ? 0.35F : 0) * (k >= 4 ? -1 : 1);
            te.zRot = (k < 4 ? (k - 1.5F) * 0.12F : (k == 4 ? -0.15F : 0.15F));
            te.getChild("punta").xRot = ola * 0.35F + (anda ? 0.25F : 0.1F);
        }
        int luz = e.getPackedLight();
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        if (sumergido) {
            m.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(TEX)), luz, OverlayTexture.NO_OVERLAY, r, g, b, 0.45F);
        } else {
            m.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(TEX)), luz, OverlayTexture.NO_OVERLAY, r, g, b, 1);
            m.render(ps, e.getMultiBufferSource().getBuffer(RenderType.entityTranslucent(OJOS)), luz, OverlayTexture.NO_OVERLAY);
        }
        ps.popPose();
    }

    /** Al convertirte en calamar la cámara pasa sola a tercera persona; al salir vuelve a la que tenías. */
    @SubscribeEvent
    public static void camara(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        boolean ahora = mc.player != null && Calamar.es(mc.player);
        if (ahora && camaraAntes == null) {
            camaraAntes = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (!ahora && camaraAntes != null) {
            if (mc.options.getCameraType() == CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(camaraAntes);
            camaraAntes = null;
        }
    }

    @SubscribeEvent
    public static void manos(RenderHandEvent e) {
        var p = Minecraft.getInstance().player;
        if (p != null && Calamar.es(p)) e.setCanceled(true);
    }

    /** Medidor de tinta a la derecha de la mira (como el depósito de Splatoon). */
    public static void hud(ForgeGui gui, GuiGraphics g, float partial, int w, int h) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        var arma = ArmaTinta.enMano(mc.player);
        if (arma.isEmpty()) return;
        float nivel = Deposito.nivel(mc.player) / Deposito.MAX;
        int c = ArmaTinta.color(arma);
        int x = w / 2 + 12, y0 = h / 2 - 14, alto = 28;
        g.fill(x - 1, y0 - 1, x + 5, y0 + alto + 1, 0x90000000);
        int lleno = (int) (alto * nivel);
        boolean bajo = nivel < 0.1F && (mc.player.tickCount / 5) % 2 == 0;
        g.fill(x, y0 + alto - lleno, x + 4, y0 + alto, bajo ? 0xFFFF3030 : (0xFF000000 | c));
        g.fill(x, y0 + alto - lleno, x + 1, y0 + alto, 0x50FFFFFF);   // brillo
    }
}
